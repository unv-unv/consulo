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
package consulo.application.impl.internal.performance;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.ApplicationProperties;
import consulo.application.internal.ApplicationInfo;
import consulo.application.internal.JobScheduler;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.application.impl.internal.concurent.AppScheduledExecutorService;
import consulo.application.util.concurrent.ThreadDump;
import consulo.application.util.concurrent.ThreadDumper;
import consulo.application.util.registry.Registry;
import consulo.container.boot.ContainerPathManager;
import consulo.disposer.Disposable;
import consulo.logging.Logger;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.SystemProperties;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.swing.*;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author yole
 */
@Singleton
@ServiceAPI(value = ComponentScope.APPLICATION, lazy = false)
@ServiceImpl
public class PerformanceWatcher implements Disposable {
  private static final Logger LOG = Logger.getInstance(PerformanceWatcher.class);
  private static final int TOLERABLE_LATENCY = 100;
  private final ScheduledFuture<?> myThread;
  private final DateFormat myDateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
  private final File mySessionLogDir;
  private final ContainerPathManager myContainerPathManager;
  private File myCurHangLogDir;
  private List<StackTraceElement> myStacktraceCommonPart;
  private final IdePerformanceListener myPublisher;

  private volatile ApdexData mySwingApdex = ApdexData.EMPTY;
  private volatile ApdexData myGeneralApdex = ApdexData.EMPTY;
  private volatile long myLastSampling = System.currentTimeMillis();
  private volatile long myLastAliveEdt = System.currentTimeMillis();
  private long myLastDumpTime;
  private long myFreezeStart;

  /**
   * If the product is unresponsive for UNRESPONSIVE_THRESHOLD_SECONDS, dump threads every UNRESPONSIVE_INTERVAL_SECONDS
   */
  private int UNRESPONSIVE_THRESHOLD_SECONDS = 5;
  private int UNRESPONSIVE_INTERVAL_SECONDS = 5;
  private static final int SAMPLING_INTERVAL_MS = 1000;

  public static PerformanceWatcher getInstance() {
    //LoadingState.CONFIGURATION_STORE_INITIALIZED.checkOccurred();
    return Application.get().getInstance(PerformanceWatcher.class);
  }

  @Inject
  public PerformanceWatcher(ContainerPathManager containerPathManager) {
    myContainerPathManager = containerPathManager;
    myCurHangLogDir =
    mySessionLogDir = new File(containerPathManager.getLogPath(), "/threadDumps-" + myDateFormat.format(new Date()) + "-" + ApplicationInfo.getInstance().getBuild().asString());
    myPublisher = ApplicationManager.getApplication().getMessageBus().syncPublisher(IdePerformanceListener.class);
    myThread = JobScheduler.getScheduler().scheduleWithFixedDelay((Runnable)() -> samplePerformance(), SAMPLING_INTERVAL_MS, SAMPLING_INTERVAL_MS, TimeUnit.MILLISECONDS);

    UNRESPONSIVE_THRESHOLD_SECONDS = SystemProperties.getIntProperty("performance.watcher.threshold", 5);
    UNRESPONSIVE_INTERVAL_SECONDS = SystemProperties.getIntProperty("performance.watcher.interval", 5);

    if (shouldWatch()) {
      final AppScheduledExecutorService service = (AppScheduledExecutorService)AppExecutorUtil.getAppScheduledExecutorService();
      service.setNewThreadListener(new Consumer<Thread>() {
        private final int ourReasonableThreadPoolSize = Registry.intValue("core.pooled.threads");

        @Override
        public void accept(Thread thread) {
          if (service.getBackendPoolExecutorSize() > ourReasonableThreadPoolSize && ApplicationProperties.isInSandbox()) {
            File file = dumpThreads("newPooledThread/", true);
            LOG.info("Not enough pooled threads" + (file != null ? "; dumped threads into file '" + file.getPath() + "'" : ""));
          }
        }
      });

      ApplicationManager.getApplication().executeOnPooledThread((Runnable)this::deleteOldThreadDumps);

      for (MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans()) {
        if ("Code Cache".equals(bean.getName())) {
          watchCodeCache(bean);
          break;
        }
      }
    }
  }

  private void watchCodeCache(final MemoryPoolMXBean bean) {
    final long threshold = bean.getUsage().getMax() - 5 * 1024 * 1024;
    if (!bean.isUsageThresholdSupported() || threshold <= 0) return;

    bean.setUsageThreshold(threshold);
    final NotificationEmitter emitter = (NotificationEmitter)ManagementFactory.getMemoryMXBean();
    emitter.addNotificationListener(new NotificationListener() {
      @Override
      public void handleNotification(Notification n, Object hb) {
        if (bean.getUsage().getUsed() > threshold) {
          LOG.info("Code Cache is almost full");
          dumpThreads("codeCacheFull", true);
          try {
            emitter.removeNotificationListener(this);
          }
          catch (ListenerNotFoundException e) {
            LOG.error(e);
          }
        }
      }
    }, null, null);
  }

  private void deleteOldThreadDumps() {
    File allLogsDir = myContainerPathManager.getLogPath();
    if (allLogsDir.isDirectory()) {
      final String[] dirs = allLogsDir.list(new FilenameFilter() {
        @Override
        public boolean accept(@Nonnull final File dir, @Nonnull final String name) {
          return name.startsWith("threadDumps-");
        }
      });
      if (dirs != null) {
        Arrays.sort(dirs);
        for (int i = 0; i < dirs.length - 11; i++) {
          FileUtil.delete(new File(allLogsDir, dirs[i]));
        }
      }
    }
  }

  @Override
  public void dispose() {
    if (myThread != null) {
      myThread.cancel(true);
    }
  }

  private boolean shouldWatch() {
    return !ApplicationManager.getApplication().isHeadlessEnvironment() && UNRESPONSIVE_INTERVAL_SECONDS != 0 && UNRESPONSIVE_THRESHOLD_SECONDS != 0;
  }

  private void samplePerformance() {
    long millis = System.currentTimeMillis();
    long diff = millis - myLastSampling - SAMPLING_INTERVAL_MS;
    myLastSampling = millis;

    // an unexpected delay of 3 seconds is considered as several delays: of 3, 2 and 1 seconds, because otherwise
    // this background thread would be sampled 3 times.
    while (diff >= 0) {
      myGeneralApdex = myGeneralApdex.withEvent(TOLERABLE_LATENCY, diff);
      diff -= SAMPLING_INTERVAL_MS;
    }

    long sinceLastEdt = millis - myLastAliveEdt;
    if (sinceLastEdt >= UNRESPONSIVE_THRESHOLD_SECONDS * 1000 + 10) {
      edtFrozen(millis);
    }
    else if (sinceLastEdt <= SAMPLING_INTERVAL_MS) {
      edtResponds(millis);
    }
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new SwingThreadRunnable(millis));
  }

  private void edtFrozen(long currentMillis) {
    if (currentMillis - myLastDumpTime >= UNRESPONSIVE_INTERVAL_SECONDS * 1000) {
      myLastDumpTime = currentMillis;
      if (myFreezeStart == 0) {
        myFreezeStart = myLastAliveEdt;
        myPublisher.uiFreezeStarted();
      }
      if (myCurHangLogDir == mySessionLogDir) {
        //System.out.println("EDT is not responding at " + myPrintDateFormat.format(new Date()));
        myCurHangLogDir = new File(mySessionLogDir, myDateFormat.format(new Date()));
      }
      dumpThreads("", false);
    }
  }

  private void edtResponds(long currentMillis) {
    if (myFreezeStart != 0) {
      if (myCurHangLogDir != mySessionLogDir && myCurHangLogDir.exists()) {
        int unresponsiveDuration = (int)(currentMillis - myFreezeStart) / 1000;
        //noinspection ResultOfMethodCallIgnored
        myCurHangLogDir.renameTo(new File(mySessionLogDir, getLogDirForHang(unresponsiveDuration)));
        myPublisher.uiFreezeFinished(unresponsiveDuration);
      }
      myFreezeStart = 0;
      myCurHangLogDir = mySessionLogDir;

      myStacktraceCommonPart = null;
    }
  }

  private String getLogDirForHang(int unresponsiveDuration) {
    StringBuilder name = new StringBuilder("freeze-" + myCurHangLogDir.getName());
    name.append("-").append(unresponsiveDuration);
    if (myStacktraceCommonPart != null && !myStacktraceCommonPart.isEmpty()) {
      final StackTraceElement element = myStacktraceCommonPart.get(0);
      name.append("-").append(StringUtil.getShortName(element.getClassName())).append(".").append(element.getMethodName());
    }
    return name.toString();
  }

  @Nullable
  public File dumpThreads(@Nonnull String pathPrefix, boolean millis) {
    if (!shouldWatch()) return null;

    String suffix = millis ? "-" + System.currentTimeMillis() : "";
    File file = new File(myCurHangLogDir, pathPrefix + "threadDump-" + myDateFormat.format(new Date()) + suffix + ".txt");

    File dir = file.getParentFile();
    if (!(dir.isDirectory() || dir.mkdirs())) {
      return null;
    }

    checkMemoryUsage(file);

    ThreadDump threadDump = ThreadDumper.getThreadDumpInfo(ThreadDumper.getThreadInfos());
    try {
      FileUtil.writeToFile(file, threadDump.getRawDump());
      StackTraceElement[] edtStack = threadDump.getEDTStackTrace();
      if (edtStack != null) {
        if (myStacktraceCommonPart == null) {
          myStacktraceCommonPart = ContainerUtil.newArrayList(edtStack);
        }
        else {
          updateStacktraceCommonPart(edtStack);
        }
      }

      myPublisher.dumpedThreads(file, threadDump);
    }
    catch (IOException e) {
      LOG.info("failed to write thread dump file: " + e.getMessage());
    }
    return file;
  }

  private static void checkMemoryUsage(File file) {
    Runtime rt = Runtime.getRuntime();
    long maxMemory = rt.maxMemory();
    long usedMemory = rt.totalMemory() - rt.freeMemory();
    long freeMemory = maxMemory - usedMemory;
    if (freeMemory < maxMemory / 5) {
      LOG.info("High memory usage (free " + freeMemory / 1024 / 1024 + " of " + maxMemory / 1024 / 1024 + " MB) while dumping threads to " + file);
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void dumpThreadsToConsole(String message) {
    System.err.println(message);
    System.err.println(ThreadDumper.dumpThreadsToString());
  }

  private void updateStacktraceCommonPart(final StackTraceElement[] stackTraceElements) {
    for (int i = 0; i < myStacktraceCommonPart.size() && i < stackTraceElements.length; i++) {
      StackTraceElement el1 = myStacktraceCommonPart.get(myStacktraceCommonPart.size() - i - 1);
      StackTraceElement el2 = stackTraceElements[stackTraceElements.length - i - 1];
      if (!el1.equals(el2)) {
        myStacktraceCommonPart = myStacktraceCommonPart.subList(myStacktraceCommonPart.size() - i, myStacktraceCommonPart.size());
        break;
      }
    }
  }

  private class SwingThreadRunnable implements Runnable {
    private final long myCreationMillis;

    SwingThreadRunnable(long creationMillis) {
      myCreationMillis = creationMillis;
    }

    @Override
    public void run() {
      long millis = System.currentTimeMillis();
      mySwingApdex = mySwingApdex.withEvent(TOLERABLE_LATENCY, millis - myCreationMillis);
      myLastAliveEdt = millis;
    }
  }

  public class Snapshot {
    private final ApdexData myStartGeneralSnapshot = myGeneralApdex;
    private final ApdexData myStartSwingSnapshot = mySwingApdex;
    private final long myStartMillis = System.currentTimeMillis();

    private Snapshot() {
    }

    public void logResponsivenessSinceCreation(@Nonnull String activityName) {
      LOG.info(activityName +
               " took " +
               (System.currentTimeMillis() - myStartMillis) +
               "ms" +
               "; general responsiveness: " +
               myGeneralApdex.summarizePerformanceSince(myStartGeneralSnapshot) +
               "; EDT responsiveness: " +
               mySwingApdex.summarizePerformanceSince(myStartSwingSnapshot));
    }

  }

  @Nonnull
  public static Snapshot takeSnapshot() {
    return getInstance().new Snapshot();
  }
}
