/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.versionControlSystem.impl.internal;

import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.project.Project;
import consulo.logging.Logger;
import consulo.component.ProcessCanceledException;
import consulo.application.progress.ProgressIndicator;

import jakarta.annotation.Nonnull;

import java.util.ArrayDeque;
import java.util.Queue;

public class ProgressManagerQueue {
  private static final Logger LOG = Logger.getInstance(ProgressManagerQueue.class);

  private final ProgressManager myProgressManager;
  private final Task.Backgroundable myQueuePollTask;
  private final Object myLock;
  private final Queue<Runnable> myQueue;
  private final Runnable myQueueWorker;
  @Nonnull
  private final Project myProject;
  private volatile boolean myIsStarted;
  private boolean myActive;

  public ProgressManagerQueue(@Nonnull Project project, @Nonnull String title) {
    myProject = project;
    myLock = new Object();
    myQueue = new ArrayDeque<>();
    myActive = false;
    myQueueWorker = new MyWorker();
    myProgressManager = ProgressManager.getInstance();
    myQueuePollTask = new Task.Backgroundable(project, title) {
      @Override
      public void run(@Nonnull ProgressIndicator indicator) {
        myQueueWorker.run();
      }
    };
  }

  public void start() {
    myIsStarted = true;
    runMe();
  }

  /**
   * !!! done under lock! (to allow single failures when putting into the execution queue)
   * Should run {@link #myQueueWorker}
   */
  private void runMe() {
    if (!myIsStarted) return;
    if (ApplicationManager.getApplication().isDispatchThread()) {
      if (!myProject.isDisposed()) {
        myProgressManager.run(myQueuePollTask);
      }
    }
    else {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!myProject.isDisposed()) {
          myProgressManager.run(myQueuePollTask);
        }
      });
    }
  }

  private static void runStuff(final Runnable stuff) {
    try {
      stuff.run();
    }
    catch (ProcessCanceledException e) {
      //
    }
  }

  public void run(@Nonnull final Runnable stuff) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runStuff(stuff);
      return;
    }
    synchronized (myLock) {
      try {
        myQueue.add(stuff);
        if (!myActive) {
          runMe();
        }
      }
      catch (Throwable t) {
        LOG.info(t);
        throw t instanceof RuntimeException ? ((RuntimeException)t) : new RuntimeException(t);
      }
      finally {
        myActive = true;
      }
    }
  }

  private class MyWorker implements Runnable {
    @Override
    public void run() {
      while (true) {
        try {
          final Runnable stuff;
          synchronized (myLock) {
            stuff = myQueue.poll();
          }
          if (stuff != null) {
            // each task is executed only once, once it has been taken from the queue..
            runStuff(stuff);
          }
        }
        catch (Throwable t) {
          LOG.info(t);
        }
        finally {
          synchronized (myLock) {
            if (myQueue.isEmpty()) {
              myActive = false;
              return;
            }
          }
        }
      }
    }
  }
}
