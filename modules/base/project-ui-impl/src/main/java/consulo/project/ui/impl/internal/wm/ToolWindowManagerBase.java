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
package consulo.project.ui.impl.internal.wm;

import consulo.application.dumb.DumbAwareRunnable;
import consulo.application.ui.wm.IdeFocusManager;
import consulo.component.ProcessCanceledException;
import consulo.component.messagebus.MessageBusConnection;
import consulo.component.persist.PersistentStateComponentWithUIState;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.externalService.statistic.UsageTrigger;
import consulo.fileEditor.FileEditorManager;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.content.layer.event.ModuleRootEvent;
import consulo.module.content.layer.event.ModuleRootListener;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.project.ui.impl.internal.wm.action.ActivateToolWindowAction;
import consulo.project.ui.internal.ProjectIdeFocusManager;
import consulo.project.ui.internal.ToolWindowLayout;
import consulo.project.ui.internal.ToolWindowManagerEx;
import consulo.project.ui.internal.WindowInfoImpl;
import consulo.project.ui.wm.ToolWindowFactory;
import consulo.project.ui.wm.ToolWindowManagerListener;
import consulo.project.ui.wm.WindowManager;
import consulo.proxy.EventDispatcher;
import consulo.ui.Rectangle2D;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.internal.ToolWindowEx;
import consulo.ui.ex.toolWindow.*;
import consulo.ui.image.Image;
import consulo.util.collection.ArrayUtil;
import consulo.util.concurrent.AsyncResult;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Provider;
import kava.beans.PropertyChangeEvent;
import kava.beans.PropertyChangeListener;
import org.jdom.Element;

import javax.swing.*;
import java.util.*;

/**
 * @author VISTALL
 * @since 2017-09-25
 */
public abstract class ToolWindowManagerBase extends ToolWindowManagerEx implements PersistentStateComponentWithUIState<Element, Element>, Disposable {
    public static final String ID = "ToolWindowManager";

    /**
     * Spies on IdeToolWindow properties and applies them to the window
     * state.
     */
    private final class MyToolWindowPropertyChangeListener implements PropertyChangeListener {
        @Override
        @RequiredUIAccess
        public void propertyChange(PropertyChangeEvent e) {
            ToolWindow toolWindow = (ToolWindow) e.getSource();
            if (ToolWindowEx.PROP_AVAILABLE.equals(e.getPropertyName())) {
                WindowInfoImpl info = getInfo(toolWindow.getId());
                if (!toolWindow.isAvailable() && info.isVisible()) {
                    hideToolWindow(toolWindow.getId(), false);
                }
            }
            ToolWindowStripeButton button = getStripeButton(toolWindow.getId());
            if (button != null) {
                button.updatePresentation();
            }
            ActivateToolWindowAction.updateToolWindowActionPresentation(toolWindow);
        }
    }

    /**
     * Translates events from InternalDecorator into ToolWindowManager method invocations.
     */
    protected abstract class MyInternalDecoratorListenerBase implements InternalDecoratorListener {
        @Override
        @RequiredUIAccess
        public void anchorChanged(@Nonnull ToolWindowInternalDecorator source, @Nonnull ToolWindowAnchor anchor) {
            setToolWindowAnchor(source.getToolWindow().getId(), anchor);
        }

        @Override
        @RequiredUIAccess
        public void autoHideChanged(@Nonnull ToolWindowInternalDecorator source, boolean autoHide) {
            setToolWindowAutoHide(source.getToolWindow().getId(), autoHide);
        }

        @Override
        @RequiredUIAccess
        public void hidden(@Nonnull ToolWindowInternalDecorator source) {
            hideToolWindow(source.getToolWindow().getId(), false);
        }

        @Override
        @RequiredUIAccess
        public void hiddenSide(@Nonnull ToolWindowInternalDecorator source) {
            hideToolWindow(source.getToolWindow().getId(), true);
        }

        @Override
        public void contentUiTypeChanges(@Nonnull ToolWindowInternalDecorator source, @Nonnull ToolWindowContentUiType type) {
            setContentUiType(source.getToolWindow().getId(), type);
        }

        @Override
        @RequiredUIAccess
        public void activated(@Nonnull ToolWindowInternalDecorator source) {
            activateToolWindow(source.getToolWindow().getId(), true, true);
        }

        @Override
        @RequiredUIAccess
        public void typeChanged(@Nonnull ToolWindowInternalDecorator source, @Nonnull ToolWindowType type) {
            setToolWindowType(source.getToolWindow().getId(), type);
        }

        @Override
        @RequiredUIAccess
        public void sideStatusChanged(@Nonnull ToolWindowInternalDecorator source, boolean isSideTool) {
            setSideTool(source.getToolWindow().getId(), isSideTool);
        }

        @Override
        public void visibleStripeButtonChanged(@Nonnull ToolWindowInternalDecorator source, boolean visible) {
            setShowStripeButton(source.getToolWindow().getId(), visible);
        }
    }

    protected static final String EDITOR_ELEMENT = "editor";
    protected static final String ACTIVE_ATTR_VALUE = "active";
    protected static final String FRAME_ELEMENT = "frame";
    protected static final String X_ATTR = "x";
    protected static final String Y_ATTR = "y";
    protected static final String WIDTH_ATTR = "width";
    protected static final String HEIGHT_ATTR = "height";
    protected static final String EXTENDED_STATE_ATTR = "extended-state";
    protected static final String LAYOUT_TO_RESTORE = "layout-to-restore";

    private static final Logger LOG = Logger.getInstance(ToolWindowManagerBase.class);

    protected final Map<String, ToolWindowInternalDecorator> myId2InternalDecorator = new HashMap<>();
    protected final Map<String, ToolWindowFloatingDecorator> myId2FloatingDecorator = new HashMap<>();
    protected final Map<String, ToolWindowWindowedDecorator> myId2WindowedDecorator = new HashMap<>();

    protected final Map<String, ToolWindowStripeButton> myId2StripeButton = new HashMap<>();

    protected final ToolWindowSideStack mySideStack = new ToolWindowSideStack();
    protected final ToolWindowActiveStack myActiveStack = new ToolWindowActiveStack();

    protected final Project myProject;
    protected final Provider<WindowManager> myWindowManager;
    protected final EventDispatcher<ToolWindowManagerListener> myDispatcher = EventDispatcher.create(ToolWindowManagerListener.class);
    protected final ToolWindowLayout myLayout = new ToolWindowLayout();
    protected ToolWindowLayout myLayoutToRestoreLater = null;
    protected ToolWindowPanel myToolWindowPanel;

    protected final MyToolWindowPropertyChangeListener myToolWindowPropertyChangeListener = new MyToolWindowPropertyChangeListener();

    protected final InternalDecoratorListener myInternalDecoratorListener;

    protected ToolWindowManagerBase(Project project, Provider<WindowManager> windowManager) {
        myProject = project;
        myWindowManager = windowManager;

        myInternalDecoratorListener = createInternalDecoratorListener();

        MessageBusConnection busConnection = project.getMessageBus().connect(this);
        busConnection.subscribe(ToolWindowManagerListener.class, myDispatcher.getMulticaster());
    }

    // region Factory Abstract KeymapGroupImpl

    @Nonnull
    protected abstract InternalDecoratorListener createInternalDecoratorListener();

    @Nonnull
    protected abstract ToolWindowStripeButton createStripeButton(ToolWindowInternalDecorator internalDecorator);

    @Nonnull
    protected abstract ToolWindowEx createToolWindow(
        String id,
        LocalizeValue displayName,
        boolean canCloseContent,
        @Nullable Object component,
        boolean shouldBeAvailable
    );

    @Nonnull
    protected abstract ToolWindowInternalDecorator createInternalDecorator(
        Project project,
        @Nonnull WindowInfoImpl info,
        ToolWindowEx toolWindow,
        boolean dumbAware
    );

    // endregion

    // region help methods
    protected void installFocusWatcher(String id, ToolWindow toolWindow) {
    }

    protected void uninstallFocusWatcher(String id) {
    }

    public abstract void initializeUI();

    @RequiredUIAccess
    protected abstract void initializeEditorComponent();

    @RequiredUIAccess
    protected void postInitialize() {
        updateToolWindowsPane();
    }

    // endregion

    // region Abstract Platform Dependent Staff
    @RequiredUIAccess
    protected abstract void requestFocusInToolWindow(String id, boolean forced);

    @RequiredUIAccess
    protected abstract void removeWindowedDecorator(WindowInfoImpl info);

    @RequiredUIAccess
    protected abstract void addFloatingDecorator(ToolWindowInternalDecorator internalDecorator, WindowInfoImpl toBeShownInfo);

    @RequiredUIAccess
    protected abstract void addWindowedDecorator(ToolWindowInternalDecorator internalDecorator, WindowInfoImpl toBeShownInfo);

    @RequiredUIAccess
    protected abstract void updateToolWindowsPane();

    @RequiredUIAccess
    public void setEditorComponent(@Nullable Object component) {
        if (myToolWindowPanel == null) {
            return;
        }

        myToolWindowPanel.setEditorComponent(component);
    }

    // endregion

    @RequiredUIAccess
    protected void applyInfo(@Nonnull String id, WindowInfoImpl info) {
        info.setVisible(false);
        if (info.isFloating()) {
            removeFloatingDecorator(info);
        }
        else if (info.isWindowed()) {
            removeWindowedDecorator(info);
        }
        else { // floating and sliding windows
            removeDecorator(id, false);
        }
    }

    @Nonnull
    protected AsyncResult<Void> registerToolWindowsFromBeans(@Nonnull UIAccess uiAccess) {
        List<AsyncResult<Void>> results = new ArrayList<>();

        for (ToolWindowFactory factory : myProject.getApplication().getExtensionPoint(ToolWindowFactory.class).getExtensionList()) {
            AsyncResult<Void> toolWindowResult = AsyncResult.undefined();
            results.add(toolWindowResult);

            uiAccess.give(() -> {
                if (factory.validate(myProject)) {
                    try {
                        initToolWindow(factory);
                    }
                    catch (ProcessCanceledException e) {
                        throw e;
                    }
                    catch (Throwable t) {
                        LOG.error("failed to init toolwindow " + factory.getClass().getName(), t);
                    }
                }
            }).notify(toolWindowResult);
        }

        return AsyncResult.merge(results);
    }

    @RequiredUIAccess
    protected void connectModuleExtensionListener() {
        myProject.getMessageBus().connect().subscribe(ModuleRootListener.class, new ModuleRootListener() {
            @Override
            public void rootsChanged(ModuleRootEvent event) {
                myProject.getUIAccess().give(() -> revalidateToolWindows());
            }
        });

        revalidateToolWindows();
    }

    @RequiredUIAccess
    private void revalidateToolWindows() {
        for (ToolWindowFactory factory : myProject.getApplication().getExtensionPoint(ToolWindowFactory.class).getExtensionList()) {
            boolean value = factory.validate(myProject);

            if (value) {
                if (isToolWindowRegistered(factory.getId())) {
                    continue;
                }
                initToolWindow(factory);
            }
            else {
                unregisterToolWindow(factory.getId());
            }
        }
    }

    @RequiredUIAccess
    public void activateOnProjectOpening() {
        for (ToolWindowFactory factory : myProject.getApplication().getExtensionPoint(ToolWindowFactory.class).getExtensionList()) {
            if (factory.activateOnProjectOpening() && isToolWindowRegistered(factory.getId())) {
                ToolWindow toolWindow = getToolWindow(factory.getId());
                if (toolWindow != null && !toolWindow.isActive()) {
                    toolWindow.activate(null);
                }
            }
        }
    }

    public boolean isToolWindowRegistered(String id) {
        return myLayout.isToolWindowRegistered(id);
    }

    /**
     * Checkes whether the specified <code>id</code> defines installed tool
     * window. If it's not then throws <code>IllegalStateException</code>.
     *
     * @throws IllegalStateException if tool window isn't installed.
     */
    protected void checkId(String id) {
        if (!myLayout.isToolWindowRegistered(id)) {
            throw new IllegalStateException("window with id=\"" + id + "\" isn't registered");
        }
    }

    @Nonnull
    protected ToolWindow registerDisposable(
        @Nonnull String id,
        @Nonnull Disposable parentDisposable,
        @Nonnull ToolWindow window
    ) {
        Disposer.register(parentDisposable, () -> unregisterToolWindow(id));
        return window;
    }

    /**
     * @return internal decorator for the tool window with specified <code>ID</code>.
     */
    @Nullable
    protected ToolWindowInternalDecorator getInternalDecorator(String id) {
        return myId2InternalDecorator.get(id);
    }

    /**
     * @return floating decorator for the tool window with specified <code>ID</code>.
     */
    @Nullable
    protected ToolWindowFloatingDecorator getFloatingDecorator(String id) {
        return myId2FloatingDecorator.get(id);
    }

    /**
     * @return windowed decorator for the tool window with specified <code>ID</code>.
     */
    @Nullable
    protected ToolWindowWindowedDecorator getWindowedDecorator(String id) {
        return myId2WindowedDecorator.get(id);
    }


    /**
     * @return tool button for the window with specified <code>ID</code>.
     */
    @Nullable
    protected ToolWindowStripeButton getStripeButton(String id) {
        return myId2StripeButton.get(id);
    }

    protected void fireToolWindowRegistered(String id) {
        myProject.getMessageBus().syncPublisher(ToolWindowManagerListener.class).toolWindowsRegistered(List.of(id), this);
    }

    protected void fireStateChanged() {
        myProject.getMessageBus().syncPublisher(ToolWindowManagerListener.class).stateChanged(this);
    }

    public ToolWindowAnchor getToolWindowAnchor(String id) {
        checkId(id);
        return getInfo(id).getAnchor();
    }

    @RequiredUIAccess
    public ToolWindowType getToolWindowInternalType(String id) {
        UIAccess.assertIsUIThread();
        checkId(id);
        return getInfo(id).getInternalType();
    }

    public ToolWindowType getToolWindowType(String id) {
        checkId(id);
        return getInfo(id).getType();
    }

    @RequiredUIAccess
    public boolean isToolWindowActive(String id) {
        UIAccess.assertIsUIThread();
        checkId(id);
        return getInfo(id).isActive();
    }

    @RequiredUIAccess
    public boolean isToolWindowAutoHide(String id) {
        UIAccess.assertIsUIThread();
        checkId(id);
        return getInfo(id).isAutoHide();
    }

    public boolean isToolWindowVisible(String id) {
        checkId(id);
        return getInfo(id).isVisible();
    }

    /**
     * @return info for the tool window with specified <code>ID</code>.
     */
    protected WindowInfoImpl getInfo(String id) {
        return myLayout.getInfo(id, true);
    }

    @RequiredUIAccess
    public void setDefaultContentUiType(ToolWindow toolWindow, ToolWindowContentUiType type) {
        WindowInfoImpl info = getInfo(toolWindow.getId());
        if (info.wasRead()) {
            return;
        }
        toolWindow.setContentUiType(type, null);
    }

    /**
     * Helper method. It deactivates (and hides) window with specified <code>id</code>.
     *
     * @param id         <code>id</code> of the tool window to be deactivated.
     * @param shouldHide if <code>true</code> then also hides specified tool window.
     */
    @RequiredUIAccess
    protected void deactivateToolWindowImpl(@Nonnull String id, boolean shouldHide) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("enter: deactivateToolWindowImpl(" + id + "," + shouldHide + ")");
        }

        WindowInfoImpl info = getInfo(id);
        if (shouldHide && info.isVisible()) {
            applyInfo(id, info);
        }
        info.setActive(false);
        applyWindowInfo(info);
    }

    @RequiredUIAccess
    public boolean isSplitMode(String id) {
        UIAccess.assertIsUIThread();
        checkId(id);
        return getInfo(id).isSplit();
    }

    @RequiredUIAccess
    public void setSideTool(String id, boolean isSide) {
        setSplitModeImpl(id, isSide);

        fireStateChanged();
    }

    @RequiredUIAccess
    protected void activateToolWindowImpl(String id, boolean forced, boolean autoFocusContents) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("enter: activateToolWindowImpl(" + id + ")");
        }
        if (!getToolWindow(id).isAvailable()) {
            // Tool window can be "logically" active but not focused. For example,
            // when the user switched to another application. So we just need to bring
            // tool window's window to front.
            ToolWindowInternalDecorator decorator = getInternalDecorator(id);
            if (!decorator.hasFocus() && autoFocusContents) {
                requestFocusInToolWindow(id, forced);
            }
            return;
        }
        deactivateWindows(id);
        showAndActivate(id, false, autoFocusContents, forced);
    }

    /**
     * Helper method. It makes window visible, activates it and request focus into the tool window.
     * But it doesn't deactivate other tool windows. Use <code>prepareForActivation</code> method to
     * deactivates other tool windows.
     *
     * @param dirtyMode if <code>true</code> then all UI operations are performed in "dirty" mode.
     *                  It means that UI isn't validated and repainted just after each add/remove operation.
     * @see #prepareForActivation
     */
    @RequiredUIAccess
    protected void showAndActivate(String id, boolean dirtyMode, boolean autoFocusContents, boolean forcedFocusRequest) {
        if (!getToolWindow(id).isAvailable()) {
            return;
        }
        // show activated
        WindowInfoImpl info = getInfo(id);
        boolean toApplyInfo = false;
        if (!info.isActive()) {
            info.setActive(true);
            toApplyInfo = true;
        }

        showToolWindowImpl(id, dirtyMode);

        // activate
        if (toApplyInfo) {
            applyWindowInfo(info);
            myActiveStack.push(id);
        }

        if (autoFocusContents && myProject.getApplication().isActive()) {
            requestFocusInToolWindow(id, forcedFocusRequest);
        }
    }

    /**
     * @param dirtyMode if <code>true</code> then all UI operations are performed in dirty mode.
     */
    @RequiredUIAccess
    protected void showToolWindowImpl(String id, boolean dirtyMode) {
        WindowInfoImpl toBeShownInfo = getInfo(id);
        ToolWindow toolWindow = getToolWindow(id);
        if (toBeShownInfo.isVisible() || !toolWindow.isAvailable()) {
            return;
        }

        toBeShownInfo.setVisible(true);
        ToolWindowInternalDecorator decorator = getInternalDecorator(id);

        if (toBeShownInfo.isFloating()) {
            addFloatingDecorator(decorator, toBeShownInfo);
        }
        else if (toBeShownInfo.isWindowed()) {
            addWindowedDecorator(decorator, toBeShownInfo);
        }
        else { // docked and sliding windows

            // If there is tool window on the same side then we have to hide it, i.e.
            // clear place for tool window to be shown.
            //
            // We store WindowInfo of hidden tool window in the SideStack (if the tool window
            // is docked and not auto-hide one). Therefore it's possible to restore the
            // hidden tool window when showing tool window will be closed.

            WindowInfoImpl[] infos = myLayout.getInfos();
            for (WindowInfoImpl info : infos) {
                if (id.equals(info.getId())) {
                    continue;
                }
                if (info.isVisible()
                    && info.getType() == toBeShownInfo.getType()
                    && info.getAnchor() == toBeShownInfo.getAnchor()
                    && info.isSplit() == toBeShownInfo.isSplit()) {
                    // hide and deactivate tool window
                    info.setVisible(false);
                    removeDecorator(info.getId(), false);
                    if (info.isActive()) {
                        info.setActive(false);
                    }
                    applyWindowInfo(info);
                    // store WindowInfo into the SideStack
                    if (info.isDocked() && !info.isAutoHide()) {
                        mySideStack.push(info);
                    }
                }
            }
            addDecorator(decorator, toBeShownInfo, dirtyMode);

            // Remove tool window from the SideStack.

            mySideStack.remove(id);
        }

        applyWindowInfo(toBeShownInfo);

        myProject.getMessageBus().syncPublisher(ToolWindowManagerListener.class).toolWindowShown(toolWindow);
    }

    protected void applyWindowInfo(WindowInfoImpl info) {
        ToolWindowStripeButton button = getStripeButton(info.getId());
        ToolWindowInternalDecorator decorator = getInternalDecorator(info.getId());

        assert button != null;
        assert decorator != null;

        button.apply(info);
        decorator.apply(info);
    }

    @RequiredUIAccess
    protected void addButton(ToolWindowStripeButton button, WindowInfoImpl info) {
        Comparator<ToolWindowStripeButton> comparator = myLayout.comparator(info.getAnchor());

        myToolWindowPanel.addButton(button, info, comparator);
    }

    @RequiredUIAccess
    protected void addDecorator(ToolWindowInternalDecorator decorator, WindowInfoImpl info, boolean dirtyMode) {
        myToolWindowPanel.addDecorator(decorator, info, dirtyMode);
    }

    @RequiredUIAccess
    protected void removeButton(String id) {
        myToolWindowPanel.removeButton(id);
    }

    protected void removeFloatingDecorator(WindowInfoImpl info) {
        ToolWindowFloatingDecorator decorator = getFloatingDecorator(info.getId());
        assert decorator != null;
        myId2FloatingDecorator.remove(info.getId());
        info.setFloatingBounds(decorator.getDecoratorBounds());

        decorator.dispose();
    }

    @RequiredUIAccess
    protected void removeDecorator(String id, boolean dirtyMode) {
        myToolWindowPanel.removeDecorator(id, dirtyMode);
    }

    @RequiredUIAccess
    public ToolWindowContentUiType getContentUiType(String id) {
        UIAccess.assertIsUIThread();
        checkId(id);
        return getInfo(id).getContentUiType();
    }

    public void setContentUiType(String id, ToolWindowContentUiType type) {
        checkId(id);
        WindowInfoImpl info = getInfo(id);
        info.setContentUiType(type);
        applyWindowInfo(info);

        fireStateChanged();
    }

    @RequiredUIAccess
    public void setToolWindowAnchor(String id, ToolWindowAnchor anchor) {
        UIAccess.assertIsUIThread();
        setToolWindowAnchor(id, anchor, -1);

        fireStateChanged();
    }

    @RequiredUIAccess
    public void setToolWindowAnchor(String id, ToolWindowAnchor anchor, int order) {
        UIAccess.assertIsUIThread();

        setToolWindowAnchorImpl(id, anchor, order);
    }

    @RequiredUIAccess
    private void setToolWindowAnchorImpl(String id, ToolWindowAnchor anchor, int order) {
        checkId(id);
        WindowInfoImpl info = getInfo(id);
        if (anchor == info.getAnchor() && order == info.getOrder()) {
            return;
        }
        // if tool window isn't visible or only order number is changed then just remove/add stripe button
        if (!info.isVisible() || anchor == info.getAnchor() || info.isFloating()) {
            removeButton(id);
            myLayout.setAnchor(id, anchor, order);
            // update infos for all window. Actually we have to update only infos affected by
            // setAnchor method
            WindowInfoImpl[] infos = myLayout.getInfos();
            for (WindowInfoImpl info1 : infos) {
                applyWindowInfo(info1);
            }
            addButton(getStripeButton(id), info);
        }
        else { // for docked and sliding windows we have to move buttons and window's decorators
            info.setVisible(false);
            removeDecorator(id, false);
            removeButton(id);
            myLayout.setAnchor(id, anchor, order);
            // update infos for all window. Actually we have to update only infos affected by
            // setAnchor method
            WindowInfoImpl[] infos = myLayout.getInfos();
            for (WindowInfoImpl info1 : infos) {
                applyWindowInfo(info1);
            }
            addButton(getStripeButton(id), info);
            showToolWindowImpl(id, false);
            if (info.isActive()) {
                requestFocusInToolWindow(id, true);
            }
        }
    }

    @RequiredUIAccess
    public void setSideToolAndAnchor(String id, ToolWindowAnchor anchor, int order, boolean isSide) {
        UIAccess.assertIsUIThread();

        setToolWindowAnchorImpl(id, anchor, order);

        setSplitModeImpl(id, isSide);

        fireStateChanged();
    }

    @RequiredUIAccess
    private void setSplitModeImpl(String id, boolean isSplit) {
        checkId(id);
        WindowInfoImpl info = getInfo(id);
        if (isSplit == info.isSplit()) {
            return;
        }

        myLayout.setSplitMode(id, isSplit);

        boolean wasActive = info.isActive();
        if (wasActive) {
            deactivateToolWindowImpl(id, true);
        }
        WindowInfoImpl[] infos = myLayout.getInfos();
        for (WindowInfoImpl info1 : infos) {
            applyWindowInfo(info1);
        }
        if (wasActive) {
            activateToolWindowImpl(id, true, true);
        }

        myToolWindowPanel.updateButtonPosition(id);
    }

    @RequiredUIAccess
    public void setToolWindowAutoHide(String id, boolean autoHide) {
        UIAccess.assertIsUIThread();

        setToolWindowAutoHideImpl(id, autoHide);

        fireStateChanged();
    }

    @RequiredUIAccess
    protected void setToolWindowAutoHideImpl(String id, boolean autoHide) {
        checkId(id);
        WindowInfoImpl info = getInfo(id);
        if (info.isAutoHide() == autoHide) {
            return;
        }
        info.setAutoHide(autoHide);
        applyWindowInfo(info);
        if (info.isVisible()) {
            deactivateWindows(id);
            showAndActivate(id, false, true, true);
        }
    }

    @RequiredUIAccess
    public void setToolWindowType(String id, ToolWindowType type) {
        UIAccess.assertIsUIThread();

        setToolWindowTypeImpl(id, type);

        fireStateChanged();
    }

    @RequiredUIAccess
    protected void setToolWindowTypeImpl(String id, ToolWindowType type) {
        checkId(id);
        WindowInfoImpl info = getInfo(id);
        if (info.getType() == type) {
            return;
        }
        if (info.isVisible()) {
            boolean dirtyMode = info.isDocked() || info.isSliding();
            info.setVisible(false);
            if (info.isFloating()) {
                removeFloatingDecorator(info);
            }
            else if (info.isWindowed()) {
                removeWindowedDecorator(info);
            }
            else { // docked and sliding windows
                removeDecorator(id, dirtyMode);
            }
            info.setType(type);
            applyWindowInfo(info);
            deactivateWindows(id);
            showAndActivate(id, dirtyMode, true, true);
            updateToolWindowsPane();
        }
        else {
            info.setType(type);
            applyWindowInfo(info);
        }
    }

    @RequiredUIAccess
    public void activateToolWindow(String id, boolean forced, boolean autoFocusContents) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("enter: activateToolWindow(" + id + ")");
        }
        UIAccess.assertIsUIThread();
        checkId(id);

        activateToolWindowImpl(id, forced, autoFocusContents);

        fireStateChanged();
    }

    @RequiredUIAccess
    private void deactivateWindows(@Nonnull String idToIgnore) {
        for (WindowInfoImpl info : myLayout.getInfos()) {
            if (!idToIgnore.equals(info.getId())) {
                deactivateToolWindowImpl(info.getId(), isToHideOnDeactivation(info));
            }
        }
    }

    private static boolean isToHideOnDeactivation(@Nonnull WindowInfoImpl info) {
        return !info.isFloating() && !info.isWindowed() && (info.isAutoHide() || info.isSliding());
    }

    @RequiredUIAccess
    public void showToolWindow(String id) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("enter: showToolWindow(" + id + ")");
        }
        UIAccess.assertIsUIThread();

        showToolWindowImpl(id, false);

        fireStateChanged();
    }

    @Override
    @RequiredUIAccess
    public void hideToolWindow(@Nonnull String id, boolean hideSide) {
        hideToolWindow(id, hideSide, true);
    }

    @Override
    @RequiredUIAccess
    public void hideToolWindow(String id, boolean hideSide, boolean moveFocus) {
        UIAccess.assertIsUIThread();

        hideToolWindowImpl(id, hideSide, moveFocus);

        fireStateChanged();
    }

    @RequiredUIAccess
    private void hideToolWindowImpl(String id, boolean hideSide, boolean moveFocus) {
        checkId(id);
        WindowInfoImpl info = getInfo(id);
        if (!info.isVisible()) {
            return;
        }

        // hide and deactivate
        deactivateToolWindowImpl(id, true);

        if (hideSide && !info.isFloating()) {
            List<String> ids = myLayout.getVisibleIdsOn(info.getAnchor(), this);
            for (String each : ids) {
                myActiveStack.remove(each, true);
            }

            while (!mySideStack.isEmpty(info.getAnchor())) {
                mySideStack.pop(info.getAnchor());
            }

            String[] all = getToolWindowIds();
            for (String eachId : all) {
                WindowInfoImpl eachInfo = getInfo(eachId);
                if (eachInfo.isVisible() && eachInfo.getAnchor() == info.getAnchor()) {
                    deactivateToolWindowImpl(eachId, true);
                }
            }
        }

        if (moveFocus) {
            activateEditorComponent();
        }
    }

    @RequiredUIAccess
    protected void focusToolWindowByDefault(@Nullable String idToIngore) {
        String toFocus = null;

        for (String each : myActiveStack.getStack()) {
            if (idToIngore != null && idToIngore.equalsIgnoreCase(each)) {
                continue;
            }

            if (getInfo(each).isVisible()) {
                toFocus = each;
                break;
            }
        }

        if (toFocus == null) {
            for (String each : myActiveStack.getPersistentStack()) {
                if (idToIngore != null && idToIngore.equalsIgnoreCase(each)) {
                    continue;
                }

                if (getInfo(each).isVisible()) {
                    toFocus = each;
                    break;
                }
            }
        }

        if (toFocus != null) {
            activateToolWindow(toFocus, false, true);
        }
    }

    protected boolean hasOpenEditorFiles() {
        return FileEditorManager.getInstance(myProject).getOpenFiles().length > 0;
    }

    @Override
    @RequiredUIAccess
    public void initToolWindow(@Nonnull ToolWindowFactory factory) {
        WindowInfoImpl before = myLayout.getInfo(factory.getId(), false);
        boolean visible = before != null && before.isVisible();
        Object label = createInitializingLabel();
        ToolWindowAnchor toolWindowAnchor = factory.getAnchor();
        ToolWindow window = registerToolWindow(
            factory.getId(),
            factory.getDisplayName(),
            label,
            toolWindowAnchor,
            factory.isSecondary(),
            factory.canCloseContents(),
            DumbService.isDumbAware(factory),
            factory.shouldBeAvailable(myProject)
        );
        ToolWindowBase toolWindow = (ToolWindowBase) registerDisposable(factory.getId(), myProject, window);

        toolWindow.setContentFactory(factory);

        factory.init(myProject, toolWindow);

        if (toolWindow.getIcon() == null) {
            toolWindow.setIcon(factory.getIcon());
        }

        WindowInfoImpl info = getInfo(factory.getId());
        if (!info.isSplit() && factory.isSecondary() && !info.wasRead()) {
            toolWindow.setSplitMode(true, null);
        }

        DumbAwareRunnable runnable = () -> {
            if (toolWindow.isDisposed()) {
                return;
            }

            toolWindow.ensureContentInitialized();
        };
        if (visible) {
            runnable.run();
        }
        else {
            doWhenFirstShown(label, runnable);
        }
    }

    @Nonnull
    protected abstract Object createInitializingLabel();

    @RequiredUIAccess
    protected abstract void doWhenFirstShown(Object component, Runnable runnable);

    public boolean isUnified() {
        return false;
    }

    @Nonnull
    @RequiredUIAccess
    protected ToolWindow registerToolWindow(
        @Nonnull String id,
        @Nullable Object component,
        @Nonnull ToolWindowAnchor anchor,
        boolean sideTool,
        boolean canCloseContent,
        boolean canWorkInDumbMode,
        boolean shouldBeAvailable
    ) {
        return registerToolWindow(id, null, component, anchor, sideTool, canCloseContent, canWorkInDumbMode, shouldBeAvailable);
    }

    @Nonnull
    @RequiredUIAccess
    protected ToolWindow registerToolWindow(
        @Nonnull String id,
        @Nullable LocalizeValue displayName,
        @Nullable Object component,
        @Nonnull ToolWindowAnchor anchor,
        boolean sideTool,
        boolean canCloseContent,
        boolean canWorkInDumbMode,
        boolean shouldBeAvailable
    ) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("enter: installToolWindow(" + id + "," + component + "," + anchor + "\")");
        }
        UIAccess.assertIsUIThread();
        if (myLayout.isToolWindowRegistered(id)) {
            throw new IllegalArgumentException("window with id=\"" + id + "\" is already registered");
        }

        WindowInfoImpl info = myLayout.register(id, anchor, sideTool);
        boolean wasActive = info.isActive();
        boolean wasVisible = info.isVisible();
        info.setActive(false);
        info.setVisible(false);

        LocalizeValue displayNameNonnull = displayName == null ? LocalizeValue.of(id) : displayName;

        // Create decorator
        ToolWindowEx toolWindow = createToolWindow(id, displayNameNonnull, canCloseContent, component, shouldBeAvailable);

        ToolWindowInternalDecorator decorator = createInternalDecorator(myProject, info.copy(), toolWindow, canWorkInDumbMode);
        ActivateToolWindowAction.ensureToolWindowActionRegistered(toolWindow);
        myId2InternalDecorator.put(id, decorator);
        decorator.addInternalDecoratorListener(myInternalDecoratorListener);
        toolWindow.addPropertyChangeListener(myToolWindowPropertyChangeListener);

        installFocusWatcher(id, toolWindow);

        // Create and show tool button

        ToolWindowStripeButton button = createStripeButton(decorator);
        myId2StripeButton.put(id, button);
        addButton(button, info);

        // If preloaded info is visible or active then we have to show/activate the installed
        // tool window. This step has sense only for windows which are not in the auto hide
        // mode. But if tool window was active but its mode doesn't allow to activate it again
        // (for example, tool window is in auto hide mode) then we just activate editor component.

        if (!info.isAutoHide() && (info.isDocked() || info.isFloating())) {
            if (wasActive) {
                activateToolWindowImpl(info.getId(), true, true);
            }
            else if (wasVisible) {
                showToolWindowImpl(info.getId(), false);
            }
        }

        fireToolWindowRegistered(id);
        fireStateChanged();
        return toolWindow;
    }

    @RequiredUIAccess
    @Override
    public void unregisterToolWindow(@Nonnull String id) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("enter: unregisterToolWindow(" + id + ")");
        }
        UIAccess.assertIsUIThread();
        if (!myLayout.isToolWindowRegistered(id)) {
            return;
        }

        WindowInfoImpl info = getInfo(id);
        ToolWindowEx toolWindow = (ToolWindowEx) getToolWindow(id);
        // Save recent appearance of tool window
        myLayout.unregister(id);
        // Remove decorator and tool button from the screen
        if (info.isVisible()) {
            applyInfo(id, info);
        }

        removeButton(id);

        applyWindowInfo(info);

        // Remove all references on tool window and save its last properties
        assert toolWindow != null;
        toolWindow.removePropertyChangeListener(myToolWindowPropertyChangeListener);
        myActiveStack.remove(id, true);
        mySideStack.remove(id);
        // Destroy stripe button
        ToolWindowStripeButton button = getStripeButton(id);
        Disposer.dispose(button);
        myId2StripeButton.remove(id);

        uninstallFocusWatcher(id);

        // Destroy decorator
        ToolWindowInternalDecorator decorator = getInternalDecorator(id);
        decorator.dispose();
        decorator.removeInternalDecoratorListener(myInternalDecoratorListener);
        myId2InternalDecorator.remove(id);

        fireStateChanged();
    }

    @Override
    public ToolWindow getToolWindow(String id) {
        if (!myLayout.isToolWindowRegistered(id)) {
            return null;
        }
        ToolWindowInternalDecorator decorator = getInternalDecorator(id);
        return decorator != null ? decorator.getToolWindow() : null;
    }

    @Override
    public void invokeLater(@Nonnull Runnable runnable) {
        myProject.getApplication().invokeLater(runnable, myProject.getDisposed());
    }

    @Nonnull
    @Override
    @RequiredUIAccess
    public ToolWindow registerToolWindow(@Nonnull String id, boolean canCloseContent, @Nonnull ToolWindowAnchor anchor) {
        return registerToolWindow(id, null, anchor, false, canCloseContent, false, true);
    }

    @Nonnull
    @Override
    @RequiredUIAccess
    public ToolWindow registerToolWindow(
        @Nonnull String id,
        boolean canCloseContent,
        @Nonnull ToolWindowAnchor anchor,
        boolean secondary
    ) {
        return registerToolWindow(id, null, anchor, secondary, canCloseContent, false, true);
    }


    @Nonnull
    @Override
    @RequiredUIAccess
    public ToolWindow registerToolWindow(
        @Nonnull String id,
        boolean canCloseContent,
        @Nonnull ToolWindowAnchor anchor,
        @Nonnull Disposable parentDisposable,
        boolean canWorkInDumbMode
    ) {
        return registerToolWindow(id, canCloseContent, anchor, parentDisposable, canWorkInDumbMode, false);
    }

    @Nonnull
    @Override
    @RequiredUIAccess
    public ToolWindow registerToolWindow(
        @Nonnull String id,
        boolean canCloseContent,
        @Nonnull ToolWindowAnchor anchor,
        Disposable parentDisposable,
        boolean canWorkInDumbMode,
        boolean secondary
    ) {
        ToolWindow window = registerToolWindow(id, null, anchor, secondary, canCloseContent, canWorkInDumbMode, true);
        return registerDisposable(id, parentDisposable, window);
    }

    @Override
    public void addToolWindowManagerListener(@Nonnull ToolWindowManagerListener l) {
        myDispatcher.addListener(l);
    }

    @Override
    public void addToolWindowManagerListener(@Nonnull ToolWindowManagerListener l, @Nonnull Disposable parentDisposable) {
        myProject.getMessageBus().connect(parentDisposable).subscribe(ToolWindowManagerListener.class, l);
    }

    @Override
    public void removeToolWindowManagerListener(@Nonnull ToolWindowManagerListener l) {
        myDispatcher.removeListener(l);
    }

    @Override
    public void loadState(Element state) {
        for (Element e : state.getChildren()) {
            if (ToolWindowLayout.TAG.equals(e.getName())) {
                myLayout.readExternal(e);
            }
            else if (LAYOUT_TO_RESTORE.equals(e.getName())) {
                myLayoutToRestoreLater = new ToolWindowLayout();
                myLayoutToRestoreLater.readExternal(e);
            }
        }
    }

    @Nonnull
    @RequiredUIAccess
    @Override
    public ToolWindowLayout getLayout() {
        UIAccess.assertIsUIThread();
        return myLayout;
    }

    @Override
    public List<String> getIdsOn(@Nonnull ToolWindowAnchor anchor) {
        return myLayout.getVisibleIdsOn(anchor, this);
    }

    @Override
    public List<ToolWindow> getToolWindows() {
        List<ToolWindow> toolWindows = new ArrayList<>();
        for (ToolWindowInternalDecorator decorator : myId2InternalDecorator.values()) {
            toolWindows.add(decorator.getToolWindow());
        }
        return toolWindows;
    }

    @Nonnull
    @Override
    public String[] getToolWindowIds() {
        WindowInfoImpl[] infos = myLayout.getInfos();
        String[] ids = ArrayUtil.newStringArray(infos.length);
        for (int i = 0; i < infos.length; i++) {
            ids[i] = infos[i].getId();
        }
        return ids;
    }

    @Override
    public void setLayoutToRestoreLater(ToolWindowLayout layout) {
        myLayoutToRestoreLater = layout;
    }

    @Override
    public ToolWindowLayout getLayoutToRestoreLater() {
        return myLayoutToRestoreLater;
    }

    @Nonnull
    @Override
    public IdeFocusManager getFocusManager() {
        return ProjectIdeFocusManager.getInstance(myProject);
    }

    @RequiredUIAccess
    @Override
    public String getActiveToolWindowId() {
        UIAccess.assertIsUIThread();
        return myLayout.getActiveId();
    }

    @Override
    public void clearSideStack() {
        mySideStack.clear();
    }

    @Nonnull
    @Override
    public Image getLocationIcon(@Nonnull String toolWindowId, @Nonnull Image fallbackImage) {
        WindowInfoImpl info = myLayout.getInfo(toolWindowId, false);
        if (info == null) {
            return fallbackImage;
        }

        ToolWindowType type = info.getType();
        if (type == ToolWindowType.FLOATING || type == ToolWindowType.WINDOWED) {
            return PlatformIconGroup.actionsMovetowindow();
        }

        ToolWindowAnchor anchor = info.getAnchor();
        boolean splitMode = info.isSplit();
        if (anchor == ToolWindowAnchor.BOTTOM) {
            if (splitMode) {
                return PlatformIconGroup.actionsMovetobottomright();
            }
            else {
                return PlatformIconGroup.actionsMovetobottomleft();
            }
        }
        else if (anchor == ToolWindowAnchor.LEFT) {
            if (splitMode) {
                return PlatformIconGroup.actionsMovetoleftbottom();
            }
            else {
                return PlatformIconGroup.actionsMovetolefttop();
            }
        }
        else if (anchor == ToolWindowAnchor.RIGHT) {
            if (splitMode) {
                return PlatformIconGroup.actionsMovetorightbottom();
            }
            else {
                return PlatformIconGroup.actionsMovetorighttop();
            }
        }
        else if (anchor == ToolWindowAnchor.TOP) {
            if (splitMode) {
                return PlatformIconGroup.actionsMovetotopright();
            }
            else {
                return PlatformIconGroup.actionsMovetotopleft();
            }
        }
        return super.getLocationIcon(toolWindowId, fallbackImage);
    }

    @Override
    @RequiredUIAccess
    public void dispose() {
        for (String id : new ArrayList<>(myId2StripeButton.keySet())) {
            unregisterToolWindow(id);
        }

        assert myId2StripeButton.isEmpty();
    }

    @RequiredUIAccess
    @Override
    public void setLayout(@Nonnull ToolWindowLayout layout) {
        UIAccess.assertIsUIThread();
        // hide tool window that are invisible in new layout
        WindowInfoImpl[] currentInfos = myLayout.getInfos();
        for (WindowInfoImpl currentInfo : currentInfos) {
            WindowInfoImpl info = layout.getInfo(currentInfo.getId(), false);
            if (info == null) {
                continue;
            }
            if (currentInfo.isVisible() && !info.isVisible()) {
                deactivateToolWindowImpl(currentInfo.getId(), true);
            }
        }
        // change anchor of tool windows
        for (WindowInfoImpl currentInfo : currentInfos) {
            WindowInfoImpl info = layout.getInfo(currentInfo.getId(), false);
            if (info == null) {
                continue;
            }
            if (currentInfo.getAnchor() != info.getAnchor() || currentInfo.getOrder() != info.getOrder()) {
                setToolWindowAnchorImpl(currentInfo.getId(), info.getAnchor(), info.getOrder());
            }
        }
        // change types of tool windows
        for (WindowInfoImpl currentInfo : currentInfos) {
            WindowInfoImpl info = layout.getInfo(currentInfo.getId(), false);
            if (info == null) {
                continue;
            }
            if (currentInfo.getType() != info.getType()) {
                setToolWindowTypeImpl(currentInfo.getId(), info.getType());
            }
        }
        // change auto-hide state
        for (WindowInfoImpl currentInfo : currentInfos) {
            WindowInfoImpl info = layout.getInfo(currentInfo.getId(), false);
            if (info == null) {
                continue;
            }
            if (currentInfo.isAutoHide() != info.isAutoHide()) {
                setToolWindowAutoHideImpl(currentInfo.getId(), info.isAutoHide());
            }
        }
        // restore visibility
        for (WindowInfoImpl currentInfo : currentInfos) {
            WindowInfoImpl info = layout.getInfo(currentInfo.getId(), false);
            if (info == null) {
                continue;
            }
            if (info.isVisible()) {
                showToolWindowImpl(currentInfo.getId(), false);
            }
        }
        fireStateChanged();
    }

    @RequiredUIAccess
    public void setDefaultState(
        @Nonnull ToolWindow toolWindow,
        @Nullable ToolWindowAnchor anchor,
        @Nullable ToolWindowType type,
        @Nullable Rectangle2D floatingBounds
    ) {
        WindowInfoImpl info = getInfo(toolWindow.getId());
        if (info.wasRead()) {
            return;
        }

        if (floatingBounds != null) {
            info.setFloatingBounds(floatingBounds);
        }

        if (anchor != null) {
            toolWindow.setAnchor(anchor, null);
        }

        if (type != null) {
            toolWindow.setType(type, null);
        }
    }

    public void setShowStripeButton(@Nonnull String id, boolean visibleOnPanel) {
        checkId(id);
        WindowInfoImpl info = getInfo(id);
        if (visibleOnPanel == info.isShowStripeButton()) {
            return;
        }
        info.setShowStripeButton(visibleOnPanel);

        String feature = "StripeButton[" + id + "]." + (visibleOnPanel ? "shown" : "hidden");
        UsageTrigger.trigger(feature);

        applyWindowInfo(info);

        fireStateChanged();
    }

    public boolean isShowStripeButton(@Nonnull String id) {
        WindowInfoImpl info = getInfo(id);
        return info == null || info.isShowStripeButton();
    }

    @Nonnull
    public Project getProject() {
        return myProject;
    }

    // TODO [VISTALL]  AWT & Swing dependency

    // region AWT & Swing dependency
    @Nonnull
    @Override
    @RequiredUIAccess
    public ToolWindow registerToolWindow(@Nonnull String id, @Nonnull JComponent component, @Nonnull ToolWindowAnchor anchor) {
        return registerToolWindow(id, component, anchor, false);
    }

    @Nonnull
    @Override
    @RequiredUIAccess
    public ToolWindow registerToolWindow(
        @Nonnull String id,
        @Nonnull JComponent component,
        @Nonnull ToolWindowAnchor anchor,
        @Nonnull Disposable parentDisposable,
        boolean canWorkInDumbMode,
        boolean canCloseContents
    ) {
        return registerDisposable(
            id,
            parentDisposable,
            registerToolWindow(id, component, anchor, false, canCloseContents, canWorkInDumbMode, true)
        );
    }

    @Nonnull
    @RequiredUIAccess
    private ToolWindow registerToolWindow(
        @Nonnull String id,
        @Nonnull JComponent component,
        @Nonnull ToolWindowAnchor anchor,
        boolean canWorkInDumbMode
    ) {
        return registerToolWindow(id, component, anchor, false, false, canWorkInDumbMode, true);
    }

    // endregion
}
