// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ide.impl.idea.openapi.vcs.impl;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.Service;
import consulo.annotation.component.ServiceImpl;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.application.impl.internal.progress.CoreProgressManager;
import consulo.ide.impl.idea.openapi.progress.util.BackgroundTaskUtil;
import consulo.application.impl.internal.progress.StandardProgressIndicatorBase;
import consulo.project.Project;
import consulo.project.event.ProjectManagerListener;
import consulo.project.startup.IdeaStartupActivity;
import consulo.util.lang.function.Condition;
import consulo.ide.impl.idea.openapi.vcs.VcsBundle;
import consulo.util.lang.TimeoutUtil;
import consulo.application.util.concurrent.QueueProcessor;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.logging.Logger;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Predicate;

@Singleton
@Service(ComponentScope.PROJECT)
@ServiceImpl
public final class VcsInitialization {
  private static final Logger LOG = Logger.getInstance(VcsInitialization.class);
  @Nonnull
  public static VcsInitialization getInstance(Project project) {
    return project.getInstance(VcsInitialization.class);
  }
  
  private final Object myLock = new Object();

  @Nonnull
  private final Project myProject;
  private enum Status {
    PENDING,
    RUNNING_INIT,
    RUNNING_POST,
    FINISHED

  }
  // guarded by myLock
  private Status myStatus = Status.PENDING;
  private final List<VcsStartupActivity> myInitActivities = new ArrayList<>();

  private final List<VcsStartupActivity> myPostActivities = new ArrayList<>();
  private volatile Future<?> myFuture;

  private final ProgressIndicator myIndicator = new StandardProgressIndicatorBase();

  @Inject
  VcsInitialization(@Nonnull Project project) {
    myProject = project;

    //if (ApplicationManager.getApplication().isUnitTestMode()) {
    //  // Fix "MessageBusImpl is already disposed: (disposed temporarily)" during LightPlatformTestCase
    //  Disposable disposable = ((ProjectEx)project).getEarlyDisposable();
    //  Disposer.register(disposable, () -> cancelBackgroundInitialization());
    //}
  }

  private void startInitialization() {
    myFuture = ((CoreProgressManager)ProgressManager.getInstance()).runProcessWithProgressAsynchronously(new Task.Backgroundable(myProject, VcsBundle.message("impl.vcs.initialization")) {
      @Override
      public void run(@Nonnull ProgressIndicator indicator) {
        execute();
      }
    }, myIndicator, null);
  }

  void add(@Nonnull VcsInitObject vcsInitObject, @Nonnull Runnable runnable) {
    if (myProject.isDefault()) return;
    boolean wasScheduled = scheduleActivity(vcsInitObject, runnable);
    if (!wasScheduled) {
      BackgroundTaskUtil.executeOnPooledThread(myProject, runnable);
    }
  }

  private boolean scheduleActivity(@Nonnull VcsInitObject vcsInitObject, @Nonnull Runnable runnable) {
    synchronized (myLock) {
      ProxyVcsStartupActivity activity = new ProxyVcsStartupActivity(vcsInitObject, runnable);
      if (isInitActivity(activity)) {
        if (myStatus == Status.PENDING) {
          myInitActivities.add(activity);
          return true;
        }
        else {
          LOG.warn(String.format("scheduling late initialization: %s", activity));
          return false;
        }
      }
      else {
        if (myStatus == Status.PENDING || myStatus == Status.RUNNING_INIT) {
          myPostActivities.add(activity);
          return true;
        }
        else {
          if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("scheduling late post activity: %s", activity));
          }
          return false;
        }
      }
    }
  }

  private void execute() {
    LOG.assertTrue(!myProject.isDefault());
    try {
      runInitStep(Status.PENDING, Status.RUNNING_INIT, it -> isInitActivity(it), myInitActivities);
      runInitStep(Status.RUNNING_INIT, Status.RUNNING_POST, it -> !isInitActivity(it), myPostActivities);
    }
    finally {
      synchronized (myLock) {
        myStatus = Status.FINISHED;
      }
    }
  }

  private void runInitStep(@Nonnull Status current, @Nonnull Status next, @Nonnull Condition<VcsStartupActivity> extensionFilter, @Nonnull List<VcsStartupActivity> pendingActivities) {
    List<VcsStartupActivity> epActivities = ContainerUtil.filter(VcsStartupActivity.EP.getExtensionList(myProject.getApplication()), extensionFilter);

    List<VcsStartupActivity> activities = new ArrayList<>();
    synchronized (myLock) {
      assert myStatus == current;
      myStatus = next;

      activities.addAll(epActivities);
      activities.addAll(pendingActivities);
      pendingActivities.clear();
    }

    runActivities(activities);
  }

  private void runActivities(@Nonnull List<VcsStartupActivity> activities) {
    Future<?> future = myFuture;
    if (future != null && future.isCancelled()) return;

    Collections.sort(activities, Comparator.comparingInt(VcsStartupActivity::getOrder));

    for (VcsStartupActivity activity : activities) {
      ProgressManager.checkCanceled();
      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("running activity: %s", activity));
      }

      QueueProcessor.runSafely(() -> activity.runActivity(myProject));
    }
  }

  private void cancelBackgroundInitialization() {
    myIndicator.cancel();

    // do not leave VCS initialization run in background when the project is closed
    Future<?> future = myFuture;
    LOG.debug(String.format("cancelBackgroundInitialization() future=%s from %s with write access=%s", future, Thread.currentThread(), ApplicationManager.getApplication().isWriteAccessAllowed()));
    if (future != null) {
      future.cancel(false);
      if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
        // dispose happens without prior project close (most likely light project case in tests)
        // get out of write action and wait there
        SwingUtilities.invokeLater(this::waitNotRunning);
      }
      else {
        waitNotRunning();
      }
    }
  }

  private void waitNotRunning() {
    boolean success = waitFor(status -> status == Status.PENDING || status == Status.FINISHED);
    if (!success) {
      LOG.warn("Failed to wait for VCS initialization cancellation for project " + myProject, new Throwable());
    }
  }

  @TestOnly
  void waitFinished() {
    boolean success = waitFor(status -> status == Status.FINISHED);
    if (!success) {
      LOG.error("Failed to wait for VCS initialization completion for project " + myProject, new Throwable());
    }
  }

  private boolean waitFor(@Nonnull Predicate<? super Status> predicate) {
    if (myProject.isDefault()) throw new IllegalArgumentException();
    // have to wait for task completion to avoid running it in background for closed project
    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() < start + 10000) {
      synchronized (myLock) {
        if (predicate.test(myStatus)) {
          return true;
        }
      }
      TimeoutUtil.sleep(10);
    }
    return false;
  }

  private static boolean isInitActivity(@Nonnull VcsStartupActivity activity) {
    return activity.getOrder() < VcsInitObject.AFTER_COMMON.getOrder();
  }

  static final class StartUpActivity implements IdeaStartupActivity.DumbAware {
    @Override
    public void runActivity(@Nonnull Project project) {
      if (project.isDefault()) return;
      VcsInitialization vcsInitialization = project.getInstance(VcsInitialization.class);
      vcsInitialization.startInitialization();
    }
  }

  static final class ShutDownProjectListener implements ProjectManagerListener {
    @Override
    public void projectClosing(@Nonnull Project project) {
      if (project.isDefault()) return;
      VcsInitialization vcsInitialization = project.getInstanceIfCreated(VcsInitialization.class);
      if (vcsInitialization != null) {
        // Wait for the task to terminate, to avoid running it in background for closed project
        vcsInitialization.cancelBackgroundInitialization();
      }
    }
  }

  private static final class ProxyVcsStartupActivity implements VcsStartupActivity {
    @Nonnull
    private final Runnable myRunnable;
    private final int myOrder;

    private ProxyVcsStartupActivity(@Nonnull VcsInitObject vcsInitObject, @Nonnull Runnable runnable) {
      myOrder = vcsInitObject.getOrder();
      myRunnable = runnable;
    }

    @Override
    public void runActivity(@Nonnull Project project) {
      myRunnable.run();
    }

    @Override
    public int getOrder() {
      return myOrder;
    }

    @Override
    public String toString() {
      return String.format("ProxyVcsStartupActivity{runnable=%s, order=%s}", myRunnable, myOrder); //NON-NLS
    }
  }
}
