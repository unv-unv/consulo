/*
 * Copyright 2013-2024 consulo.io
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
package consulo.desktop.awt.startup;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.util.HiDPIUtils;
import com.google.gson.Gson;
import consulo.application.Application;
import consulo.application.ApplicationProperties;
import consulo.application.impl.internal.start.ApplicationStarter;
import consulo.application.impl.internal.start.CommandLineArgs;
import consulo.application.internal.ApplicationEx;
import consulo.application.internal.StartupProgress;
import consulo.application.internal.plugin.CompositeMessage;
import consulo.application.internal.plugin.PluginsInitializeInfo;
import consulo.awt.hacking.X11Hacking;
import consulo.builtinWebServer.http.HttpRequestHandler;
import consulo.builtinWebServer.json.JsonBaseRequestHandler;
import consulo.builtinWebServer.json.JsonGetRequestHandler;
import consulo.builtinWebServer.json.JsonPostRequestHandler;
import consulo.component.extension.ExtensionPoint;
import consulo.component.impl.internal.ComponentBinding;
import consulo.container.util.StatCollector;
import consulo.desktop.application.jna.windows.WindowsAutoRestartManager;
import consulo.desktop.awt.application.impl.AWTExceptionHandler;
import consulo.desktop.awt.application.impl.DesktopApplicationImpl;
import consulo.desktop.awt.startup.customize.FirstStartCustomizeUtil;
import consulo.desktop.awt.startup.splash.DesktopSplash;
import consulo.desktop.awt.ui.IdeEventQueue;
import consulo.desktop.awt.ui.util.AppIconUtil;
import consulo.desktop.awt.uiOld.DesktopAWTFontRegistry;
import consulo.desktop.awt.wm.impl.DesktopWindowManagerImpl;
import consulo.desktop.awt.wm.impl.MacTopMenuInitializer;
import consulo.desktop.awt.wm.impl.TopMenuInitializer;
import consulo.externalService.impl.internal.PlatformOrPluginsNotificationGroupContributor;
import consulo.externalService.plugin.PluginsConfigurable;
import consulo.externalService.statistic.UsageTrigger;
import consulo.ide.impl.idea.ide.CommandLineProcessor;
import consulo.ide.impl.idea.ide.RecentProjectsManagerImpl;
import consulo.ide.impl.idea.ide.ui.LafManager;
import consulo.ide.impl.idea.openapi.wm.impl.SystemDock;
import consulo.ide.localize.IdeLocalize;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.logging.internal.FatalErrorReporter;
import consulo.platform.os.UnixOperationSystem;
import consulo.project.Project;
import consulo.project.internal.RecentProjectsManager;
import consulo.project.ui.internal.WindowManagerEx;
import consulo.project.ui.notification.Notification;
import consulo.project.ui.notification.NotificationService;
import consulo.project.ui.notification.event.NotificationListener;
import consulo.project.ui.wm.IdeFrame;
import consulo.project.ui.wm.WelcomeFrameManager;
import consulo.project.ui.wm.WindowManager;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.concurrent.AsyncResult;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

/**
 * @author VISTALL
 * @since 2018-05-10
 */
public class DesktopApplicationStarter extends ApplicationStarter {
    private static final Logger LOG = Logger.getInstance(DesktopApplicationStarter.class);

    public DesktopApplicationStarter(@Nonnull CommandLineArgs args, @Nonnull StatCollector stat) {
        super(args, stat);

        FatalErrorReporter.INSTANCE = new DefaultIdeaErrorLogger();
    }

    @Override
    protected boolean needSetVersionChecker() {
        // if inside sandbox - do not check plugin versions
        return !Boolean.parseBoolean(myPlatform.jvm().getRuntimeProperty(ApplicationProperties.CONSULO_IN_SANDBOX));
    }

    @Nullable
    @Override
    public StartupProgress createSplash(CommandLineArgs args) {
        if (!args.isNoSplash()) {
            SplashScreen splashScreen = getSplashScreen();
            if (splashScreen == null) {
                DesktopSplash splash = new DesktopSplash(false);
                splash.show();
                return splash;
            }
        }
        return null;
    }

    @Nonnull
    @Override
    protected Application createApplication(
        ComponentBinding componentBinding,
        boolean isHeadlessMode,
        SimpleReference<StartupProgress> splashRef,
        CommandLineArgs args
    ) {
        return new DesktopApplicationImpl(componentBinding, isHeadlessMode, splashRef);
    }

    @Nullable
    private SplashScreen getSplashScreen() {
        try {
            return SplashScreen.getSplashScreen();
        }
        catch (Throwable t) {
            LOG.warn(t);
            return null;
        }
    }

    @Override
    protected void initializeEnviroment(boolean isHeadlessMode, CommandLineArgs args, StatCollector stat) {
        AWTExceptionHandler.register(); // do not crash AWT on exceptions

        System.setProperty("sun.awt.noerasebackground", "true");

        // replace system event queue and set exception handler
        invokeAtUIAndWait(() -> {
            Thread thread = Thread.currentThread();
            thread.setUncaughtExceptionHandler((t, e) -> Logger.getInstance(IdeEventQueue.class).error(e));
            IdeEventQueue.initialize();
        });

        ForkJoinPool pool = ForkJoinPool.commonPool();

        // execute it in parallel
        pool.execute(DesktopAWTFontRegistry::registerBundledFonts);

        // region FlatLaf
        // disable safe triangle hacks, due we use own event queue
        System.setProperty("flatlaf.useSubMenuSafeTriangle", "false");
        // remap native file path
        System.setProperty("flatlaf.nativeLibraryPath", "system");

        // preload all flat native libraries
        pool.execute(() -> {
            FlatLaf.setPreferredFontFamily("Inter");
            FlatLaf.setPreferredLightFontFamily("Inter Light");
            FlatLaf.setPreferredSemiboldFontFamily("Inter SemiBold");

            FlatLaf.setPreferredMonospacedFontFamily("JetBrains Mono");

            HiDPIUtils.installHiDPIRepaintManager();
        });
        // endregion

        SwingUtilities.invokeLater(() -> {
            if (myPlatform.os() instanceof UnixOperationSystem os) {
                if (os.isXWindow()) {
                    X11Hacking.updateFrameClass(ApplicationStarter.getFrameClass());
                }

                // enable by default since we use FlatLaf always
                JFrame.setDefaultLookAndFeelDecorated(true);
                JDialog.setDefaultLookAndFeelDecorated(true);
            }
        });

        super.initializeEnviroment(isHeadlessMode, args, stat);

        // wait until icon library loaded
        stat.markWith("awt.update.window.icon", () -> AppIconUtil.updateWindowIcon(JOptionPane.getRootFrame()));
    }

    @Override
    public void main(
        StatCollector stat,
        Runnable appInitializeMark,
        ApplicationEx app,
        boolean newConfigFolder,
        @Nonnull CommandLineArgs args
    ) {
        IdeEventQueue.getInstance().addIdleTimeCounterRequest();

        appInitializeMark.run();

        SwingUtilities.invokeLater(() -> {
            StartupProgress desktopSplash = mySplashRef.get();
            if (desktopSplash != null) {
                desktopSplash.dispose();
                mySplashRef.set(null);
            }
        });

        // init laf settings
        LafManager.getInstance();

        TopMenuInitializer.register(app);

        if (myPlatform.os().isMac()) {
            MacTopMenuInitializer.installAutoUpdateMenu();
        }
        else if (myPlatform.os().isWindows()) {
            ForkJoinPool.commonPool().execute(WindowsAutoRestartManager::register);
        }

        if (Boolean.getBoolean("consulo.first.start.testing") || newConfigFolder && !ApplicationProperties.isInSandbox()) {
            SwingUtilities.invokeLater(() -> FirstStartCustomizeUtil.showDialog(app));
        }
        else {
            SystemDock.getInstance().updateMenu();

            // Event queue should not be changed during initialization of application components.
            // It also cannot be changed before initialization of application components because IdeEventQueue uses other
            // application components. So it is proper to perform replacement only here.
            DesktopWindowManagerImpl windowManager = (DesktopWindowManagerImpl)WindowManager.getInstance();
            IdeEventQueue.getInstance().setWindowManager(windowManager);

            RecentProjectsManagerImpl recentProjectsManager = (RecentProjectsManagerImpl)RecentProjectsManager.getInstance();

            if (recentProjectsManager.willReopenProjectOnStart() && !args.isNoRecentProjects()) {
                SwingUtilities.invokeLater(windowManager::showFrame);
            }
            else {
                SwingUtilities.invokeLater(() -> WelcomeFrameManager.getInstance().showFrame());
            }

            app.invokeLater(
                () -> {
                    if (!args.isNoRecentProjects()) {
                        AsyncResult<Project> projectFromCommandLine = AsyncResult.rejected();

                        if (isPerformProjectLoad()) {
                            projectFromCommandLine = CommandLineProcessor.processExternalCommandLine(args, null);
                        }

                        projectFromCommandLine.doWhenRejected(recentProjectsManager::doReopenLastProject);
                    }

                    if (args.getJson() != null) {
                        runJsonRequest(args.getJson());
                    }

                    SwingUtilities.invokeLater(() -> reportPluginError(myPluginsInitializeInfo));

                    UsageTrigger.trigger("consulo.app.started");
                },
                app.getNoneModalityState()
            );

            stat.dump("Startup statistics", LOG::info);
        }
    }

    @SuppressWarnings("unchecked")
    private void runJsonRequest(String jsonFile) {
        CommandLineJsonValue jsonValue = readJsonFile(jsonFile);

        if (jsonValue == null) {
            return;
        }

        ExtensionPoint<HttpRequestHandler> extensionPoint = Application.get().getExtensionPoint(HttpRequestHandler.class);
        HttpRequestHandler targetRequestHandler = extensionPoint.findFirstSafe(
            requestHandler -> requestHandler instanceof JsonBaseRequestHandler jsonBaseRequestHandler
                && jsonValue.url.equals(jsonBaseRequestHandler.getApiUrl())
        );

        if (targetRequestHandler == null) {
            return;
        }

        if (targetRequestHandler instanceof JsonPostRequestHandler jsonPostRequestHandler) {
            if (jsonValue.body == null) {
                return;
            }

            Class requestClass = jsonPostRequestHandler.getRequestClass();
            Object content = new Gson().fromJson(jsonValue.body, requestClass);
            jsonPostRequestHandler.handle(content);
        }
        else if (targetRequestHandler instanceof JsonGetRequestHandler jsonGetRequestHandler) {
            jsonGetRequestHandler.handle(null);
        }
    }

    private CommandLineJsonValue readJsonFile(String jsonFile) {
        try (Reader reader = Files.newBufferedReader(Paths.get(jsonFile))) {
            return new Gson().fromJson(reader, CommandLineJsonValue.class);
        }
        catch (Exception ignored) {
            return null;
        }
    }

    static void reportPluginError(PluginsInitializeInfo info) {
        List<CompositeMessage> pluginErrors = info.getPluginErrors();

        if (pluginErrors != null) {
            for (CompositeMessage pluginError : pluginErrors) {
                NotificationService.getInstance()
                    .newError(PlatformOrPluginsNotificationGroupContributor.ourPluginsLifecycleGroup)
                    .title(IdeLocalize.titlePluginNotificationTitle())
                    .content(LocalizeValue.localizeTODO(pluginError.toString()))
                    .hyperlinkListener(new NotificationListener() {
                        @Override
                        @RequiredUIAccess
                        public void hyperlinkUpdate(@Nonnull Notification notification, @Nonnull HyperlinkEvent event) {
                            notification.expire();

                            String description = event.getDescription();
                            if (PluginsInitializeInfo.EDIT.equals(description)) {
                                IdeFrame ideFrame = WindowManagerEx.getInstanceEx().findFrameFor(null);
                                ShowSettingsUtil.getInstance().showSettingsDialog(
                                    ideFrame == null ? null : ideFrame.getProject(),
                                    PluginsConfigurable.CONFIGURABLE_ID,
                                    null
                                );
                            }
                        }
                    })
                    .notify(null);
            }
        }
    }

    private static void invokeAtUIAndWait(@RequiredUIAccess Runnable runnable) {
        try {
            SwingUtilities.invokeAndWait(runnable);
        }
        catch (InterruptedException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
