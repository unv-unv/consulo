/*
 * Copyright 2013-2016 consulo.io
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
package consulo.ide.updateSettings.impl;

import consulo.ide.IdeBundle;
import com.intellij.ide.actions.SettingsEntryPointAction;
import com.intellij.ide.plugins.PluginManagerMain;
import com.intellij.ide.plugins.PluginNode;
import com.intellij.ide.plugins.RepositoryHelper;
import com.intellij.notification.NotificationAction;
import consulo.application.impl.internal.ApplicationInfo;
import consulo.ui.ex.awt.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import consulo.application.Application;
import consulo.application.ApplicationPropertiesComponent;
import consulo.application.progress.ProgressIndicator;
import consulo.component.ProcessCanceledException;
import consulo.container.boot.ContainerPathManager;
import consulo.container.plugin.PluginDescriptor;
import consulo.container.plugin.PluginId;
import consulo.container.plugin.PluginIds;
import consulo.container.plugin.PluginManager;
import consulo.ide.plugins.InstalledPluginsState;
import consulo.ide.plugins.PluginIconHolder;
import consulo.ide.plugins.pluginsAdvertisement.PluginsAdvertiserHolder;
import consulo.ide.updateSettings.UpdateChannel;
import consulo.ide.updateSettings.UpdateSettings;
import consulo.logging.Logger;
import consulo.platform.CpuArchitecture;
import consulo.platform.Platform;
import consulo.project.Project;
import consulo.project.ui.notification.Notification;
import consulo.project.ui.notification.NotificationDisplayType;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationType;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.concurrent.AsyncResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.*;

/**
 * @author VISTALL
 * @since 10-Oct-16
 */
public class PlatformOrPluginUpdateChecker {
  private static final Logger LOG = Logger.getInstance(PlatformOrPluginUpdateChecker.class);

  private static final NotificationGroup ourGroup = new NotificationGroup("Platform Or Plugins Update", NotificationDisplayType.STICKY_BALLOON, false);

  // windows ids
  private static final PluginId ourWinNoJre = PluginId.getId("consulo-win-no-jre");
  private static final PluginId ourWin = PluginId.getId("consulo-win");
  private static final PluginId ourWin64 = PluginId.getId("consulo-win64");
  private static final PluginId ourWinA64 = PluginId.getId("consulo-winA64");

  // windows dummy zip ids
  private static final PluginId ourWinNoJreZip = PluginId.getId("consulo-win-no-jre-zip");
  private static final PluginId ourWinZip = PluginId.getId("consulo-win-zip");
  private static final PluginId ourWin64Zip = PluginId.getId("consulo-win64-zip");
  private static final PluginId ourWinA64Zip = PluginId.getId("consulo-winA64-zip");

  // linux ids
  private static final PluginId ourLinuxNoJre = PluginId.getId("consulo-linux-no-jre");
  private static final PluginId ourLinux = PluginId.getId("consulo-linux");
  private static final PluginId ourLinux64 = PluginId.getId("consulo-linux64");

  // mac ids
  private static final PluginId ourMac64NoJre = PluginId.getId("consulo-mac-no-jre");
  private static final PluginId ourMacA64NoJre = PluginId.getId("consulo-macA64-no-jre");
  private static final PluginId ourMac64 = PluginId.getId("consulo-mac64");
  private static final PluginId ourMacA64 = PluginId.getId("consulo-macA64");

  private static final PluginId[] ourPlatformIds = {
          // win no jre (tar)
          ourWinNoJre,
          // win x32 (tar)
          ourWin,
          // win x64 (tar)
          ourWin64,
          // win ARM64 (tar)
          ourWinA64,
          // linux no jre (tar)
          ourLinuxNoJre,
          // linux x32 (tar)
          ourLinux,
          // linux x64 (tar)
          ourLinux64,
          // mac x64 no jre (tar)
          ourMac64NoJre,
          // mac x64 with jre (tar)
          ourMac64,
          // win no jre (zip)
          ourWinNoJreZip,
          // win x32 (zip)
          ourWinZip,
          // win x64 (zip)
          ourWin64Zip,
          // win ARM64 (zip)
          ourWinA64Zip,
          // mac ARM64 no jre (tar)
          ourMacA64NoJre,
          // mac ARM64 with jre (tar)
          ourMacA64,
          // ...
  };

  private static final String ourForceJREBuild = "force.jre.build.on.update";
  private static final String ourForceJREBuildVersion = "force.jre.build.on.update.version";

  @Nonnull
  public static PluginId getPlatformPluginId() {
    boolean isJreBuild = isJreBuild();

    Platform platform = Platform.current();
    Platform.OperatingSystem os = platform.os();
    CpuArchitecture arch = platform.jvm().arch();

    if (os.isWindows()) {
      if (isJreBuild) {
        if (arch == CpuArchitecture.AARCH64) {
          return ourWinA64;
        }
        else if (arch == CpuArchitecture.X86_64) {
          return ourWin64;
        }
        else if (arch == CpuArchitecture.X86) {
          return ourWin;
        }
      }

      return ourWinNoJre;
    }
    else if (os.isMac()) {
      if (arch == CpuArchitecture.AARCH64) {
        return isJreBuild ? ourMacA64 : ourMacA64NoJre;
      }
      return isJreBuild ? ourMac64 : ourMac64NoJre;
    }
    else {
      if (isJreBuild) {
        if (arch == CpuArchitecture.AARCH64) {
          // TODO [VISTALL] linux aarch64 support?
        }
        else if (arch == CpuArchitecture.X86_64) {
          return ourLinux64;
        }
        else if (arch == CpuArchitecture.X86) {
          return ourLinux;
        }
      }

      return ourLinuxNoJre;
    }
  }

  public static boolean isJreBuild() {
    return new File(ContainerPathManager.get().getHomePath(), "jre").exists() || isForceBundledJreAtUpdate();
  }

  public static boolean isForceBundledJreAtUpdate() {
    validateForceBundledJreVersion();
    return ApplicationPropertiesComponent.getInstance().getBoolean(ourForceJREBuild);
  }

  public static void setForceBundledJreAtUpdate() {
    ApplicationPropertiesComponent.getInstance().setValue(ourForceJREBuildVersion, ApplicationInfo.getInstance().getBuild().toString());
    ApplicationPropertiesComponent.getInstance().setValue(ourForceJREBuild, true);
  }

  /**
   * Validate force bundle jre flag. If flag set version changed - it will be dropped
   */
  private static void validateForceBundledJreVersion() {
    String oldVer = ApplicationPropertiesComponent.getInstance().getValue(ourForceJREBuildVersion);

    String curVer = ApplicationInfo.getInstance().getBuild().toString();

    if (!Objects.equals(oldVer, curVer)) {
      ApplicationPropertiesComponent.getInstance().unsetValue(ourForceJREBuild);
    }
  }

  public static boolean isPlatform(@Nonnull PluginId pluginId) {
    return ArrayUtil.contains(pluginId, ourPlatformIds);
  }

  public static boolean checkNeeded() {
    UpdateSettings updateSettings = UpdateSettings.getInstance();
    if (!updateSettings.isEnable()) {
      return false;
    }

    final long timeDelta = System.currentTimeMillis() - updateSettings.getLastTimeCheck();
    return Math.abs(timeDelta) >= DateFormatUtil.DAY;
  }

  @Nonnull
  public static AsyncResult<PlatformOrPluginUpdateResult.Type> updateAndShowResult() {
    final AsyncResult<PlatformOrPluginUpdateResult.Type> result = AsyncResult.undefined();
    final Application app = Application.get();

    UIAccess lastUIAccess = app.getLastUIAccess();

    final UpdateSettings updateSettings = UpdateSettings.getInstance();
    if (updateSettings.isEnable()) {
      app.executeOnPooledThread(() -> checkAndNotifyForUpdates(null, false, null, lastUIAccess, result));
    }
    else {
      registerSettingsGroupUpdate(result);

      result.setDone(PlatformOrPluginUpdateResult.Type.NO_UPDATE);
    }
    return result;
  }

  public static void showErrorMessage(boolean showErrorDialog, final String failedMessage) {
    if (showErrorDialog) {
      UIUtil.invokeLaterIfNeeded(() -> Messages.showErrorDialog(failedMessage, IdeBundle.message("title.connection.error")));
    }
    else {
      LOG.info(failedMessage);
    }
  }

  @RequiredUIAccess
  private static void showUpdateResult(@Nullable Project project, final PlatformOrPluginUpdateResult targetsForUpdate, final boolean showResults) {
    PlatformOrPluginUpdateResult.Type type = targetsForUpdate.getType();
    switch (type) {
      case NO_UPDATE:
        if (showResults) {
          ourGroup.createNotification(IdeBundle.message("update.available.group"), IdeBundle.message("update.there.are.no.updates"), NotificationType.INFORMATION, null).notify(project);
        }
        break;
      case RESTART_REQUIRED:
        PluginManagerMain.notifyPluginsWereInstalled(Collections.emptyList(), null);
        break;
      case PLUGIN_UPDATE:
      case PLATFORM_UPDATE:
        if (showResults) {
          new PlatformOrPluginDialog(project, targetsForUpdate, null, null).showAsync();
        }
        else {
          Notification notification = ourGroup.createNotification(IdeBundle.message("update.available.group"), IdeBundle.message("update.available"), NotificationType.INFORMATION, null);
          notification.addAction(new NotificationAction(IdeBundle.message("update.view.updates")) {
            @RequiredUIAccess
            @Override
            public void actionPerformed(@Nonnull AnActionEvent e, @Nonnull Notification notification) {
              new PlatformOrPluginDialog(project, targetsForUpdate, null, null).showAsync();
            }
          });
          notification.notify(project);
        }
        break;
    }
  }

  private static void registerSettingsGroupUpdate(@Nonnull AsyncResult<PlatformOrPluginUpdateResult.Type> result) {
    result.doWhenDone(type -> {
      UIAccess lastUIAccess = Application.get().getLastUIAccess();

      UpdateSettings updateSettings = UpdateSettings.getInstance();
      updateSettings.setLastCheckResult(type);
      lastUIAccess.give(() -> SettingsEntryPointAction.updateState(updateSettings));
    });
  }

  public static void checkAndNotifyForUpdates(@Nullable Project project,
                                              boolean showResults,
                                              @Nullable ProgressIndicator indicator,
                                              @Nonnull UIAccess uiAccess,
                                              @Nonnull AsyncResult<PlatformOrPluginUpdateResult.Type> result) {
    UIAccess.assetIsNotUIThread();

    registerSettingsGroupUpdate(result);

    PlatformOrPluginUpdateResult updateResult = checkForUpdates(showResults, indicator);
    if (updateResult == PlatformOrPluginUpdateResult.CANCELED) {
      result.setDone(PlatformOrPluginUpdateResult.Type.CANCELED);
      return;
    }

    uiAccess.give(() -> {
      result.setDone(updateResult.getType());

      showUpdateResult(project, updateResult, showResults);
    });
  }

  @Nonnull
  private static PlatformOrPluginUpdateResult checkForUpdates(final boolean showResults, @Nullable ProgressIndicator indicator) {
    PluginId platformPluginId = getPlatformPluginId();

    ApplicationInfo appInfo = ApplicationInfo.getInstance();
    String currentBuildNumber = appInfo.getBuild().asString();

    List<PluginDescriptor> remotePlugins = Collections.emptyList();
    UpdateChannel channel = UpdateSettings.getInstance().getChannel();
    try {
      remotePlugins = RepositoryHelper.loadPluginsFromRepository(indicator, channel);
      PluginsAdvertiserHolder.update(remotePlugins);
    }
    catch (ProcessCanceledException e) {
      return PlatformOrPluginUpdateResult.CANCELED;
    }
    catch (Exception e) {
      LOG.info(e);
    }

    boolean alreadyVisited = false;
    final InstalledPluginsState state = InstalledPluginsState.getInstance();

    PluginDescriptor newPlatformPlugin = null;
    // try to search platform number
    for (PluginDescriptor pluginDescriptor : remotePlugins) {
      PluginId pluginId = pluginDescriptor.getPluginId();
      // platform already downloaded for update
      if (state.wasUpdated(pluginId)) {
        alreadyVisited = true;
        break;
      }
      if (platformPluginId.equals(pluginId)) {
        if (StringUtil.compareVersionNumbers(pluginDescriptor.getVersion(), currentBuildNumber) > 0) {
          // change current build
          currentBuildNumber = pluginDescriptor.getVersion();
          newPlatformPlugin = pluginDescriptor;
          break;
        }
      }
    }

    final List<PlatformOrPluginNode> targets = new ArrayList<>();
    if (newPlatformPlugin != null) {
      PluginNode thisPlatform = new PluginNode(platformPluginId);
      thisPlatform.setVersion(appInfo.getBuild().asString());
      thisPlatform.setName(newPlatformPlugin.getName());

      PluginIconHolder.put(newPlatformPlugin, Application.get().getBigIcon());

      targets.add(new PlatformOrPluginNode(platformPluginId, thisPlatform, newPlatformPlugin));

      // load new plugins with new app build
      try {
        remotePlugins = RepositoryHelper.loadPluginsFromRepository(indicator, channel, currentBuildNumber);
      }
      catch (ProcessCanceledException e) {
        return PlatformOrPluginUpdateResult.CANCELED;
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    }

    final Map<PluginId, PluginDescriptor> ourPlugins = new HashMap<>();
    final List<PluginDescriptor> installedPlugins = PluginManager.getPlugins();
    final List<String> disabledPlugins = PluginManager.getDisabledPlugins();
    for (PluginDescriptor installedPlugin : installedPlugins) {
      if (PluginIds.isPlatformPlugin(installedPlugin.getPluginId())) {
        continue;
      }

      if (!disabledPlugins.contains(installedPlugin.getPluginId().getIdString())) {
        ourPlugins.put(installedPlugin.getPluginId(), installedPlugin);
      }
    }

    state.getOutdatedPlugins().clear();
    if (!ourPlugins.isEmpty()) {
      try {
        for (final Map.Entry<PluginId, PluginDescriptor> entry : ourPlugins.entrySet()) {
          final PluginId pluginId = entry.getKey();

          PluginDescriptor filtered = ContainerUtil.find(remotePlugins, it -> pluginId.equals(it.getPluginId()));

          if (filtered == null) {
            // if platform updated - but we not found new plugin in new remote list, notify user about it
            if (newPlatformPlugin != null) {
              targets.add(new PlatformOrPluginNode(pluginId, entry.getValue(), null));
            }
            continue;
          }

          if (state.wasUpdated(filtered.getPluginId())) {
            alreadyVisited = true;
            continue;
          }

          if (StringUtil.compareVersionNumbers(filtered.getVersion(), entry.getValue().getVersion()) > 0) {
            state.getOutdatedPlugins().add(pluginId);

            processDependencies(filtered, targets, remotePlugins);

            targets.add(new PlatformOrPluginNode(pluginId, entry.getValue(), filtered));
          }
        }
      }
      catch (ProcessCanceledException ignore) {
        return PlatformOrPluginUpdateResult.CANCELED;
      }
      catch (Exception e) {
        showErrorMessage(showResults, e.getMessage());
      }
    }

    if (newPlatformPlugin != null) {
      return new PlatformOrPluginUpdateResult(PlatformOrPluginUpdateResult.Type.PLATFORM_UPDATE, targets);
    }

    if (alreadyVisited && targets.isEmpty()) {
      return PlatformOrPluginUpdateResult.RESTART_REQUIRED;
    }
    return targets.isEmpty() ? PlatformOrPluginUpdateResult.NO_UPDATE : new PlatformOrPluginUpdateResult(PlatformOrPluginUpdateResult.Type.PLUGIN_UPDATE, targets);
  }

  private static void processDependencies(@Nonnull PluginDescriptor target, List<PlatformOrPluginNode> targets, List<PluginDescriptor> remotePlugins) {
    PluginId[] dependentPluginIds = target.getDependentPluginIds();
    for (PluginId pluginId : dependentPluginIds) {
      PluginDescriptor depPlugin = PluginManager.findPlugin(pluginId);
      // if plugin is not installed
      if (depPlugin == null) {
        PluginDescriptor filtered = ContainerUtil.find(remotePlugins, it -> pluginId.equals(it.getPluginId()));

        if (filtered != null) {
          targets.add(new PlatformOrPluginNode(filtered.getPluginId(), null, filtered));
        }
      }
    }
  }
}
