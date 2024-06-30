/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package consulo.ide.impl.idea.ide;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AccessRule;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.ApplicationPropertiesComponent;
import consulo.application.impl.internal.JobScheduler;
import consulo.application.progress.ProgressIndicator;
import consulo.application.ui.RemoteDesktopService;
import consulo.application.util.SystemInfo;
import consulo.application.util.function.ThrowableComputable;
import consulo.container.boot.ContainerPathManager;
import consulo.container.plugin.PluginDescriptor;
import consulo.container.plugin.PluginId;
import consulo.container.plugin.PluginIds;
import consulo.container.plugin.PluginManager;
import consulo.ide.impl.idea.diagnostic.VMOptions;
import consulo.ide.impl.idea.ide.plugins.UninstallPluginAction;
import consulo.ide.impl.idea.openapi.application.PreloadingActivity;
import consulo.ide.impl.plugins.PluginActionListener;
import consulo.ide.impl.updateSettings.impl.UpdateHistory;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.ide.localize.IdeLocalize;
import consulo.project.ui.internal.NotificationsConfiguration;
import consulo.project.ui.notification.*;
import consulo.project.ui.notification.event.NotificationListener;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.awt.HyperlinkAdapter;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.jna.JnaLoader;
import consulo.util.lang.StringUtil;
import consulo.util.lang.SystemProperties;
import consulo.util.lang.TimeoutUtil;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@ExtensionImpl
public class SystemHealthMonitor extends PreloadingActivity {
  private static final Logger LOG = Logger.getInstance(SystemHealthMonitor.class);

  public static final NotificationGroup GROUP = new NotificationGroup("System Health", NotificationDisplayType.STICKY_BALLOON, false);

  private final ApplicationPropertiesComponent myProperties;
  @Nonnull
  private final Provider<UpdateHistory> myUpdateHistoryProvider;

  @Inject
  public SystemHealthMonitor(@Nonnull ApplicationPropertiesComponent properties, @Nonnull Provider<UpdateHistory> updateHistoryProvider) {
    myProperties = properties;
    myUpdateHistoryProvider = updateHistoryProvider;
  }

  @Override
  public void preload(@Nonnull ProgressIndicator indicator) {
    checkEARuntime();
    checkExperimentalPlugins();
    checkObsoletePlugins();
    checkReservedCodeCacheSize();
    checkSignalBlocking();
    checkHiDPIMode();
    startDiskSpaceMonitoring();
  }

  @NonNls
  private void checkObsoletePlugins() {
    List<PluginDescriptor> plugins = PluginManager.getPlugins().stream()
      .filter(it -> PluginIds.getObsoletePlugins().contains(it.getPluginId()))
      .collect(Collectors.toList());
    if (plugins.isEmpty()) {
      return;
    }

    StringBuilder builder = new StringBuilder();
    builder.append(StringUtil.join(plugins, PluginDescriptor::getName, ", "));
    if (plugins.size() == 1) {
      builder.append(" is ");
    }
    else {
      builder.append(" are ");
    }
    builder.append(" obsolete. You can uninstall without consequences.");

    Application app = Application.get();
    app.invokeLater(() -> {
      Notification notification = GROUP.createNotification(builder.toString(), NotificationType.WARNING);
      notification.addAction(new DumbAwareAction("Uninstall...") {
        @RequiredUIAccess
        @Override
        public void actionPerformed(@Nonnull AnActionEvent e) {
          notification.expire();

          List<PluginId> pluginIds = new ArrayList<>();
          for (PluginDescriptor plugin : plugins) {
            if (UninstallPluginAction.uninstallPlugin(plugin, null)) {
              pluginIds.add(plugin.getPluginId());
            }
          }

          if (!pluginIds.isEmpty()) {
            Application.get().getMessageBus()
              .syncPublisher(PluginActionListener.class)
              .pluginsUninstalled(pluginIds.toArray(PluginId[]::new));
          }
        }
      });
      notification.setImportant(true);
      Notifications.Bus.notify(notification);
    });
  }

  private void checkExperimentalPlugins() {
    List<PluginDescriptor> plugins = PluginManager.getPlugins().stream()
      .filter(PluginDescriptor::isExperimental)
      .collect(Collectors.toList());
    if (plugins.isEmpty()) {
      return;
    }

    UpdateHistory updateHistory = myUpdateHistoryProvider.get();
    if (!updateHistory.isShowExperimentalWarning()) {
      return;
    }

    StringBuilder builder = new StringBuilder();
    builder.append(StringUtil.join(plugins, (it) -> "<b>" + it.getName() + "</b>", ", "));
    if (plugins.size() == 1) {
      builder.append(" is ");
    }
    else {
      builder.append(" are ");
    }
    builder.append(" experimental");

    Application app = Application.get();
    app.invokeLater(() -> {
      Notification notification = GROUP.createNotification(builder.toString(), NotificationType.WARNING);
      notification.addAction(new DumbAwareAction("Got it!") {
        @RequiredUIAccess
        @Override
        public void actionPerformed(@Nonnull AnActionEvent e) {
          updateHistory.setShowExperimentalWarning(false);
          notification.expire();
        }
      });
      notification.setImportant(true);
      Notifications.Bus.notify(notification);
    });
  }

  private void checkEARuntime() {
    if (StringUtil.endsWithIgnoreCase(System.getProperty("java.version", ""), "-ea")) {
      showNotification(new KeyHyperlinkAdapter("unsupported.jvm.ea.message"), IdeLocalize.unsupportedJvmEaMessage());
    }
  }

  private void checkReservedCodeCacheSize() {
    int minReservedCodeCacheSize = 240;
    int reservedCodeCacheSize = VMOptions.readOption(VMOptions.MemoryKind.CODE_CACHE, true);
    if (reservedCodeCacheSize > 0 && reservedCodeCacheSize < minReservedCodeCacheSize) {
      showNotification(
        new KeyHyperlinkAdapter("vmoptions.warn.message"),
        IdeLocalize.vmoptionsWarnMessage(reservedCodeCacheSize, minReservedCodeCacheSize)
      );
    }
  }

  private void checkSignalBlocking() {
    if (Platform.current().os().isUnix() && JnaLoader.isLoaded()) {
      try {
        LibC lib = Native.load("c", LibC.class);
        Memory buf = new Memory(1024);
        if (lib.sigaction(LibC.SIGINT, null, buf) == 0) {
          long handler = Native.POINTER_SIZE == 8 ? buf.getLong(0) : buf.getInt(0);
          if (handler == LibC.SIG_IGN) {
            showNotification(new KeyHyperlinkAdapter("ide.sigint.ignored.message"), IdeLocalize.ideSigintIgnoredMessage());
          }
        }
      }
      catch (Throwable t) {
        LOG.warn(t);
      }
    }
  }

  private void checkHiDPIMode() {
    // if switched from JRE-HiDPI to IDE-HiDPI
    boolean switchedHiDPIMode = SystemInfo.isJetBrainsJvm
      && "true".equalsIgnoreCase(System.getProperty("sun.java2d.uiScale.enabled"))
      && !UIUtil.isJreHiDPIEnabled();
    if (
      Platform.current().os().isWindows()
        && ((switchedHiDPIMode && JBUI.isHiDPI(JBUI.sysScale())) || RemoteDesktopService.isRemoteSession())
    ) {
      showNotification(new KeyHyperlinkAdapter("ide.set.hidpi.mode"), IdeLocalize.ideSetHidpiMode());
    }
  }

  private void showNotification(KeyHyperlinkAdapter adapter, LocalizeValue message) {
    boolean ignored = adapter.isIgnored();
    LOG.info("issue detected: " + message + (ignored ? " (ignored)" : ""));
    if (ignored) return;

    Application app = Application.get();
    app.invokeLater(() -> {
      Notification notification = GROUP.createNotification(
        "",
        message.get(),
        NotificationType.WARNING,
        new NotificationListener.Adapter() {
          @Override
          protected void hyperlinkActivated(@Nonnull Notification notification, @Nonnull HyperlinkEvent e) {
            adapter.hyperlinkActivated(e);
          }
        }
      );

      notification.addAction(new DumbAwareAction("Do not show again") {
        @RequiredUIAccess
        @Override
        public void actionPerformed(@Nonnull AnActionEvent e) {
          myProperties.setValue("ignore." + adapter.key, "true");
          notification.expire();
        }
      });
      notification.setImportant(true);
      Notifications.Bus.notify(notification);
    });
  }

  private static void startDiskSpaceMonitoring() {
    if (SystemProperties.getBooleanProperty("idea.no.system.path.space.monitoring", false)) {
      return;
    }

    final File file = new File(ContainerPathManager.get().getSystemPath());
    final AtomicBoolean reported = new AtomicBoolean();
    final ThreadLocal<Future<Long>> ourFreeSpaceCalculation = new ThreadLocal<>();

    JobScheduler.getScheduler().schedule(
      new Runnable() {
        private static final long
          LOW_DISK_SPACE_THRESHOLD = 50 * 1024 * 1024,
          MAX_WRITE_SPEED_IN_BPS = 500 * 1024 * 1024;  // 500 MB/sec is near max SSD sequential write speed

        @Override
        public void run() {
          if (!reported.get()) {
            Future<Long> future = ourFreeSpaceCalculation.get();
            if (future == null) {
              ourFreeSpaceCalculation.set(future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
                // file.getUsableSpace() can fail and return 0 e.g. after MacOSX restart or awakening from sleep
                // so several times try to recalculate usable space on receiving 0 to be sure
                long fileUsableSpace = file.getUsableSpace();
                while (fileUsableSpace == 0) {
                  TimeoutUtil.sleep(5000);  // hopefully we will not hummer disk too much
                  fileUsableSpace = file.getUsableSpace();
                }

                return fileUsableSpace;
              }));
            }
            if (!future.isDone() || future.isCancelled()) {
              restart(1);
              return;
            }

            try {
              final long fileUsableSpace = future.get();
              final long rawTimeout = (fileUsableSpace - LOW_DISK_SPACE_THRESHOLD) / MAX_WRITE_SPEED_IN_BPS;
              final long timeout = Math.min(3600, Math.max(5, rawTimeout));
              ourFreeSpaceCalculation.set(null);

              if (fileUsableSpace < LOW_DISK_SPACE_THRESHOLD) {
                ThrowableComputable<NotificationsConfiguration, RuntimeException> action =
                  () -> NotificationsConfiguration.getNotificationsConfiguration();
                if (AccessRule.read(action) == null) {
                  ourFreeSpaceCalculation.set(future);
                  restart(1);
                  return;
                }
                reported.compareAndSet(false, true);

                //noinspection SSBasedInspection
                SwingUtilities.invokeLater(() -> {
                  LocalizeValue message = IdeLocalize.lowDiskSpaceMessage(Application.get().getName());
                  if (fileUsableSpace < 100 * 1024) {
                    LOG.warn(message + " (" + fileUsableSpace + ")");
                    Messages.showErrorDialog(message.get(), "Fatal Configuration Problem");
                    reported.compareAndSet(true, false);
                    restart(timeout);
                  }
                  else {
                    GROUP.createNotification(message.get(), file.getPath(), NotificationType.ERROR, null)
                      .whenExpired(() -> {
                        reported.compareAndSet(true, false);
                        restart(timeout);
                      })
                      .notify(null);
                  }
                });
              }
              else {
                restart(timeout);
              }
            }
            catch (Exception ex) {
              LOG.error(ex);
            }
          }
        }

        private void restart(long timeout) {
          JobScheduler.getScheduler().schedule(this, timeout, TimeUnit.SECONDS);
        }
      },
      1,
      TimeUnit.SECONDS
    );
  }

  @SuppressWarnings({"SpellCheckingInspection", "SameParameterValue"})
  private interface LibC extends Library {
    int SIGINT = 2;
    long SIG_IGN = 1L;

    int sigaction(int signum, Pointer act, Pointer oldact);
  }

  private class KeyHyperlinkAdapter extends HyperlinkAdapter {
    private final String key;

    private KeyHyperlinkAdapter(String key) {
      this.key = key;
    }

    private boolean isIgnored() {
      return myProperties.isValueSet("ignore." + key);
    }

    @Override
    protected void hyperlinkActivated(HyperlinkEvent e) {
      String url = e.getDescription();
      BrowserUtil.browse(url);
    }
  }
}