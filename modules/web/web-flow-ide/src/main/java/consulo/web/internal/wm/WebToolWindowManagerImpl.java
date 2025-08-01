/*
 * Copyright 2013-2017 consulo.io
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
package consulo.web.internal.wm;

import consulo.annotation.component.ServiceImpl;
import consulo.component.messagebus.MessageBusConnection;
import consulo.component.persist.RoamingType;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.fileEditor.internal.FileEditorManagerEx;
import consulo.project.ui.impl.internal.wm.ToolWindowManagerBase;
import consulo.project.ui.impl.internal.wm.UnifiedToolWindowImpl;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.event.ProjectManagerListener;
import consulo.project.ui.internal.IdeFrameEx;
import consulo.project.ui.internal.WindowInfoImpl;
import consulo.project.ui.internal.WindowManagerEx;
import consulo.project.ui.wm.WindowManager;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.NotificationType;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.internal.ToolWindowEx;
import consulo.ui.ex.popup.Balloon;
import consulo.ui.ex.toolWindow.InternalDecoratorListener;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.ui.ex.toolWindow.ToolWindowInternalDecorator;
import consulo.ui.ex.toolWindow.ToolWindowStripeButton;
import consulo.ui.layout.DockLayout;
import consulo.web.internal.ui.WebRootPaneImpl;
import consulo.web.internal.wm.toolWindow.WebToolWindowInternalDecorator;
import consulo.web.internal.wm.toolWindow.WebToolWindowPanelImpl;
import consulo.web.internal.wm.toolWindow.WebToolWindowStripeButtonImpl;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jdom.Element;

/**
 * @author VISTALL
 * @since 2017-09-24
 */
@Singleton
@ServiceImpl
@State(name = ToolWindowManagerBase.ID, storages = @Storage(value = StoragePathMacros.WORKSPACE_FILE, roamingType = RoamingType.DISABLED))
public class WebToolWindowManagerImpl extends ToolWindowManagerBase {
    private IdeFrameEx myFrame;

    @Inject
    public WebToolWindowManagerImpl(Project project, Provider<WindowManager> windowManager) {
        super(project, windowManager);

        if (project.isDefault()) {
            return;
        }

        MessageBusConnection busConnection = project.getMessageBus().connect();
        busConnection.subscribe(
            ProjectManagerListener.class,
            new ProjectManagerListener() {
                @Override
                public void projectClosed(@Nonnull Project project, @Nonnull UIAccess uiAccess) {
                    if (project == myProject) {
                        WebToolWindowManagerImpl.this.projectClosed();
                    }
                }
            }
        );
    }

    @Override
    @RequiredUIAccess
    public void initializeUI() {
        WindowManagerEx windowManager = (WindowManagerEx) myWindowManager.get();

        myFrame = windowManager.getIdeFrame(myProject);

        WebToolWindowPanelImpl toolWindowPanel = new WebToolWindowPanelImpl();

        myToolWindowPanel = toolWindowPanel;

        WebRootPaneImpl rootPanel = ((WebIdeFrameImpl) myFrame).getRootPanel();

        rootPanel.setCenterComponent(toolWindowPanel);

        ((WebIdeFrameImpl) myFrame).show();
    }

    private void projectClosed() {
        WindowManagerEx windowManager = (WindowManagerEx) myWindowManager.get();

        windowManager.releaseFrame(myFrame);

        myFrame = null;
    }

    @Override
    @RequiredUIAccess
    protected void initializeEditorComponent() {
        Component editorComponent = getEditorComponent(myProject);

        setEditorComponent(editorComponent);
    }

    private Component getEditorComponent(Project project) {
        return FileEditorManagerEx.getInstanceEx(project).getUIComponent();
    }

    @Override
    @Nonnull
    @RequiredUIAccess
    protected Component createInitializingLabel() {
        Label label = Label.create("Initializing...");
        DockLayout dock = DockLayout.create();
        dock.center(label);
        return label;
    }

    @Override
    @RequiredUIAccess
    protected void doWhenFirstShown(Object component, Runnable runnable) {
        UIAccess.get().give(runnable);
    }

    @Nonnull
    @Override
    protected InternalDecoratorListener createInternalDecoratorListener() {
        return new MyInternalDecoratorListenerBase() {
            @Override
            public void resized(@Nonnull ToolWindowInternalDecorator source) {
            }
        };
    }

    @Nonnull
    @Override
    protected ToolWindowStripeButton createStripeButton(ToolWindowInternalDecorator internalDecorator) {
        return new WebToolWindowStripeButtonImpl(
            (WebToolWindowInternalDecorator) internalDecorator,
            (WebToolWindowPanelImpl) myToolWindowPanel
        );
    }

    @Nonnull
    @Override
    @RequiredUIAccess
    protected ToolWindowEx createToolWindow(
        String id,
        LocalizeValue displayName,
        boolean canCloseContent,
        @Nullable Object component,
        boolean shouldBeAvailable
    ) {
        return new UnifiedToolWindowImpl(this, id, displayName, canCloseContent, component, shouldBeAvailable);
    }

    @Nonnull
    @Override
    @RequiredUIAccess
    protected ToolWindowInternalDecorator createInternalDecorator(
        Project project,
        @Nonnull WindowInfoImpl info,
        ToolWindowEx toolWindow,
        boolean dumbAware
    ) {
        return new WebToolWindowInternalDecorator(project, info, (UnifiedToolWindowImpl) toolWindow, dumbAware);
    }

    @Override
    public boolean isUnified() {
        return true;
    }

    @Override
    @RequiredUIAccess
    protected void requestFocusInToolWindow(String id, boolean forced) {
    }

    @Override
    @RequiredUIAccess
    protected void removeWindowedDecorator(WindowInfoImpl info) {
    }

    @Override
    @RequiredUIAccess
    protected void addFloatingDecorator(ToolWindowInternalDecorator internalDecorator, WindowInfoImpl toBeShownInfo) {
    }

    @Override
    @RequiredUIAccess
    protected void addWindowedDecorator(ToolWindowInternalDecorator internalDecorator, WindowInfoImpl toBeShownInfo) {
    }

    @Override
    @RequiredUIAccess
    protected void updateToolWindowsPane() {
    }

    @Nullable
    @Override
    @RequiredUIAccess
    public Element getStateFromUI() {
        return new Element("state");
    }

    @Nullable
    @Override
    @RequiredUIAccess
    public Element getState(Element element) {
        return element;
    }

    @Override
    public boolean canShowNotification(@Nonnull String toolWindowId) {
        return false;
    }

    @Override
    public void activateEditorComponent() {
    }

    @Override
    public boolean isEditorComponentActive() {
        return false;
    }

    @Override
    public void notifyByBalloon(@Nonnull String toolWindowId, @Nonnull NotificationType type, @Nonnull String htmlBody) {
    }

    @Nullable
    @Override
    public Balloon getToolWindowBalloon(String id) {
        return null;
    }

    @Override
    public boolean isMaximized(@Nonnull ToolWindow wnd) {
        return false;
    }

    @Override
    public void setMaximized(@Nonnull ToolWindow wnd, boolean maximized) {
    }
}
