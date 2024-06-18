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
package consulo.ide.impl.updateSettings.impl;

import consulo.application.Application;
import consulo.application.ApplicationPropertiesComponent;
import consulo.application.internal.ApplicationInfo;
import consulo.application.progress.ProgressIndicator;
import consulo.application.util.DateFormatUtil;
import consulo.component.ProcessCanceledException;
import consulo.container.boot.ContainerPathManager;
import consulo.container.plugin.PluginDescriptor;
import consulo.container.plugin.PluginId;
import consulo.container.plugin.PluginIds;
import consulo.container.plugin.PluginManager;
import consulo.externalService.impl.internal.update.PlatformOrPluginNode;
import consulo.externalService.impl.internal.update.PlatformOrPluginUpdateResult;
import consulo.externalService.update.UpdateChannel;
import consulo.ide.IdeBundle;
import consulo.ide.impl.idea.ide.actions.SettingsEntryPointAction;
import consulo.ide.impl.idea.ide.plugins.PluginManagerMain;
import consulo.ide.impl.idea.ide.plugins.PluginNode;
import consulo.ide.impl.idea.ide.plugins.RepositoryHelper;
import consulo.ide.impl.plugins.InstalledPluginsState;
import consulo.ide.impl.plugins.PluginIconHolder;
import consulo.ide.impl.plugins.pluginsAdvertisement.PluginsAdvertiserHolder;
import consulo.externalService.impl.internal.update.UpdateSettingsImpl;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.platform.CpuArchitecture;
import consulo.platform.Platform;
import consulo.platform.PlatformOperatingSystem;
import consulo.project.Project;
import consulo.project.ui.notification.*;
import consulo.ui.Alert;
import consulo.ui.Alerts;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.concurrent.AsyncResult;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author VISTALL
 * @since 10-Oct-16
 */
public class PlatformOrPluginUpdateChecker {
  private static final Logger LOG = Logger.getInstance(PlatformOrPluginUpdateChecker.class);

  public static final NotificationGroup ourGroup =
    new NotificationGroup("Platform Or Plugins Update", NotificationDisplayType.STICKY_BALLOON, false);

  // windows ids
  private static final PluginId ourWinNoJre = PluginId.getId("consulo.dist.windows.no.jre");
  private static final PluginId ourWin = PluginId.getId("consulo.dist.windows");
  private static final PluginId ourWin64 = PluginId.getId("consulo.dist.windows64");
  private static final PluginId ourWinA64 = PluginId.getId("consulo.dist.windowsA64");

  private static final PluginId ourWinNoJreZip = PluginId.getId("consulo.dist.windows.no.jre.zip");
  private static final PluginId ourWinZip = PluginId.getId("consulo.dist.windows.zip");
  private static final PluginId ourWin64Zip = PluginId.getId("consulo.dist.windows64.zip");
  private static final PluginId ourWinA64Zip = PluginId.getId("consulo.dist.windowsA64.zip");

  // windows installers
  private static final PluginId ourWin64Installer = PluginId.getId("consulo.dist.windows64.installer");

  // linux ids
  private static final PluginId ourLinuxNoJre = PluginId.getId("consulo.dist.linux.no.jre");
  private static final PluginId ourLinux = PluginId.getId("consulo.dist.linux");
  private static final PluginId ourLinux64 = PluginId.getId("consulo.dist.linux64");

  // mac ids
  private static final PluginId ourMac64NoJre = PluginId.getId("consulo.dist.mac64.no.jre");
  private static final PluginId ourMacA64NoJre = PluginId.getId("consulo.dist.macA64.no.jre");
  private static final PluginId ourMac64 = PluginId.getId("consulo.dist.mac64");
  private static final PluginId ourMacA64 = PluginId.getId("consulo.dist.macA64");

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
    // win 64 installer
    ourWin64Installer
  };

  private static final String ourForceJREBuild = "force.jre.build.on.update";
  private static final String ourForceJREBuildVersion = "force.jre.build.on.update.version";

  @Nonnull
  public static PluginId getPlatformPluginId() {
    boolean isJreBuild = isJreBuild();

    Platform platform = Platform.current();
    PlatformOperatingSystem os = platform.os();
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
    UpdateSettingsImpl updateSettings = UpdateSettingsImpl.getInstance();
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

    final UpdateSettingsImpl updateSettings = UpdateSettingsImpl.getInstance();
    if (updateSettings.isEnable()) {
      app.executeOnPooledThread(() -> checkAndNotifyForUpdates(null, false, null, lastUIAccess, result));
    }
    else {
      registerSettingsGroupUpdate(result);

      result.setDone(PlatformOrPluginUpdateResult.Type.NO_UPDATE);
    }
    return result;
  }

  public static void showErrorMessage(boolean showErrorDialog, Throwable e, UIAccess uiAccess, @Nullable Project project) {
    LOG.warn(e);

    if (showErrorDialog) {
      uiAccess.give(() -> {
        LocalizeValue className = LocalizeValue.of(e.getClass().getSimpleName());
        LocalizeValue message = LocalizeValue.of(e.getLocalizedMessage());
        Alert<Object> alert = Alerts.okError(LocalizeValue.join(className, LocalizeValue.colon(), LocalizeValue.space(), message));
        if (project != null) {
          alert.showAsync(project);
        }
        else {
          alert.showAsync();
        }
      });
    }
  }

  @RequiredUIAccess
  private static void showUpdateResult(@Nullable Project project,
                                       final PlatformOrPluginUpdateResult targetsForUpdate,
                                       final boolean showResults) {
    PlatformOrPluginUpdateResult.Type type = targetsForUpdate.getType();
    switch (type) {
      case NO_UPDATE:
        if (showResults) {
          ourGroup.createNotification(IdeBundle.message("update.available.group"),
                                      IdeBundle.message("update.there.are.no.updates"),
                                      NotificationType.INFORMATION,
                                      null).notify(project);
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
          Notification notification = ourGroup.createNotification(IdeBundle.message("update.available.group"),
                                                                  IdeBundle.message("update.available"),
                                                                  NotificationType.INFORMATION,
                                                                  null);
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

      UpdateSettingsImpl updateSettings = UpdateSettingsImpl.getInstance();
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

    PlatformOrPluginUpdateResult updateResult = checkForUpdates(showResults, indicator, uiAccess, project);
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
  private static PlatformOrPluginUpdateResult checkForUpdates(final boolean showResults,
                                                              @Nullable ProgressIndicator indicator,
                                                              @Nonnull UIAccess uiAccess,
                                                              @Nullable Project project) {
    PluginId platformPluginId = getPlatformPluginId();

    ApplicationInfo appInfo = ApplicationInfo.getInstance();
    String currentBuildNumber = appInfo.getBuild().asString();

    List<PluginDescriptor> remotePlugins = Collections.emptyList();
    UpdateChannel channel = UpdateSettingsImpl.getInstance().getChannel();
    try {
      remotePlugins = RepositoryHelper.loadPluginsFromRepository(indicator, channel);
      PluginsAdvertiserHolder.update(remotePlugins);
    }
    catch (ProcessCanceledException e) {
      return PlatformOrPluginUpdateResult.CANCELED;
    }
    catch (Exception e) {
      showErrorMessage(showResults, e, uiAccess, project);
      return PlatformOrPluginUpdateResult.CANCELED;
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
    final Set<PluginId> disabledPlugins = PluginManager.getDisabledPlugins();
    for (PluginDescriptor installedPlugin : installedPlugins) {
      if (PluginIds.isPlatformPlugin(installedPlugin.getPluginId())) {
        continue;
      }

      if (!disabledPlugins.contains(installedPlugin.getPluginId())) {
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
        showErrorMessage(showResults, e, uiAccess, project);
        return PlatformOrPluginUpdateResult.CANCELED;
      }
    }

    if (newPlatformPlugin != null) {
      return new PlatformOrPluginUpdateResult(PlatformOrPluginUpdateResult.Type.PLATFORM_UPDATE, targets);
    }

    if (alreadyVisited && targets.isEmpty()) {
      return PlatformOrPluginUpdateResult.RESTART_REQUIRED;
    }
    return targets.isEmpty() ? PlatformOrPluginUpdateResult.NO_UPDATE : new PlatformOrPluginUpdateResult(PlatformOrPluginUpdateResult.Type.PLUGIN_UPDATE,
                                                                                                         targets);
  }

  private static void processDependencies(@Nonnull PluginDescriptor target,
                                          List<PlatformOrPluginNode> targets,
                                          List<PluginDescriptor> remotePlugins) {
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
