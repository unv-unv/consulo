// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.desktop.awt.wm.navigationToolbar;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.ApplicationManager;
import consulo.application.ui.UISettings;
import consulo.application.util.Queryable;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.dataContext.DataProvider;
import consulo.desktop.awt.wm.navigationToolbar.ui.NavBarUI;
import consulo.desktop.awt.wm.navigationToolbar.ui.NavBarUIManager;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.ide.IdeView;
import consulo.ide.impl.idea.codeInsight.hint.HintManagerImpl;
import consulo.ide.impl.idea.ide.dnd.TransferableWrapper;
import consulo.ide.impl.idea.ide.ui.customization.CustomActionsSchemaImpl;
import consulo.ide.impl.idea.ide.util.DeleteHandler;
import consulo.ide.impl.idea.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.ide.impl.idea.ui.LightweightHintImpl;
import consulo.ide.impl.idea.ui.popup.AbstractPopup;
import consulo.ide.impl.idea.ui.popup.PopupOwner;
import consulo.ide.navigationToolbar.NavBarModelExtension;
import consulo.language.content.ProjectRootsUtil;
import consulo.language.editor.LangDataKeys;
import consulo.language.editor.hint.HintManager;
import consulo.language.editor.refactoring.ui.CopyPasteDelegator;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiDirectoryContainer;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.module.Module;
import consulo.navigation.Navigatable;
import consulo.platform.Platform;
import consulo.project.Project;
import consulo.project.ui.internal.ProjectIdeFocusManager;
import consulo.project.ui.view.ProjectView;
import consulo.project.ui.view.ProjectViewPane;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.project.ui.wm.WindowManager;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.*;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.PopupHandler;
import consulo.ui.ex.awt.UIExAWTDataKey;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.accessibility.AccessibleContextUtil;
import consulo.ui.ex.awt.accessibility.ScreenReader;
import consulo.ui.ex.awt.dnd.DnDDragStartBean;
import consulo.ui.ex.awt.dnd.DnDSupport;
import consulo.ui.ex.awt.event.PopupMenuListenerAdapter;
import consulo.ui.ex.awt.hint.HintHint;
import consulo.ui.ex.awt.util.ComponentUtil;
import consulo.ui.ex.awt.util.ListenerUtil;
import consulo.ui.ex.awt.util.ScreenUtil;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.util.TextAttributesUtil;
import consulo.util.collection.JBIterable;
import consulo.util.concurrent.AsyncResult;
import consulo.util.dataholder.Key;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VFileProperty;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.plaf.PanelUI;
import javax.swing.tree.TreeNode;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Konstantin Bulenkov
 * @author Anna Kozlova
 */
public class NavBarPanel extends JPanel implements DataProvider, PopupOwner, Disposable, Queryable {
    private final NavBarModel myModel;

    private final NavBarPresentation myPresentation;
    protected final Project myProject;

    private final ArrayList<NavBarItem> myList = new ArrayList<>();

    private final ModuleDeleteProvider myDeleteModuleProvider = new ModuleDeleteProvider();
    private final IdeView myIdeView;
    private FocusListener myNavBarItemFocusListener;

    private LightweightHintImpl myHint = null;
    private NavBarPopup myNodePopup = null;
    private JComponent myHintContainer;
    private Component myContextComponent;

    private final NavBarUpdateQueue myUpdateQueue;

    private NavBarItem myContextObject;
    private boolean myDisposed = false;
    private RelativePoint myLocationCache;

    public NavBarPanel(@Nonnull Project project, boolean docked) {
        super(new FlowLayout(FlowLayout.LEFT, 0, 0));
        myProject = project;
        myModel = createModel();
        myIdeView = new NavBarIdeView(this);
        myPresentation = new NavBarPresentation(myProject);
        myUpdateQueue = new NavBarUpdateQueue(this);

        installPopupHandler(this, -1);
        setOpaque(false);

        myUpdateQueue.queueModelUpdateFromFocus();
        myUpdateQueue.queueRebuildUi();

        Disposer.register(project, this);
        AccessibleContextUtil.setName(this, "Navigation Bar");
    }

    /**
     * Navigation bar entry point to determine if the keyboard/focus behavior should be
     * compatible with screen readers. This additional level of indirection makes it
     * easier to figure out the various locations in the various navigation bar components
     * that enable screen reader friendly behavior.
     */
    protected boolean allowNavItemsFocus() {
        return ScreenReader.isActive();
    }

    public boolean isFocused() {
        if (allowNavItemsFocus()) {
            return UIUtil.isFocusAncestor(this);
        }
        else {
            return hasFocus();
        }
    }

    public void addNavBarItemFocusListener(@Nullable FocusListener l) {
        if (l == null) {
            return;
        }
        myNavBarItemFocusListener = AWTEventMulticaster.add(myNavBarItemFocusListener, l);
    }

    public void removeNavBarItemFocusListener(@Nullable FocusListener l) {
        if (l == null) {
            return;
        }
        myNavBarItemFocusListener = AWTEventMulticaster.remove(myNavBarItemFocusListener, l);
    }

    protected void fireNavBarItemFocusGained(FocusEvent e) {
        FocusListener listener = myNavBarItemFocusListener;
        if (listener != null) {
            listener.focusGained(e);
        }
    }

    protected void fireNavBarItemFocusLost(FocusEvent e) {
        FocusListener listener = myNavBarItemFocusListener;
        if (listener != null) {
            listener.focusLost(e);
        }
    }

    protected NavBarModel createModel() {
        return new NavBarModel(myProject);
    }

    @Nullable
    public NavBarPopup getNodePopup() {
        return myNodePopup;
    }

    public boolean isNodePopupActive() {
        return myNodePopup != null && myNodePopup.isVisible();
    }

    public LightweightHintImpl getHint() {
        return myHint;
    }

    public NavBarPresentation getPresentation() {
        return myPresentation;
    }

    public void setContextComponent(@Nullable Component contextComponent) {
        myContextComponent = contextComponent;
    }

    public NavBarItem getContextObject() {
        return myContextObject;
    }

    public List<NavBarItem> getItems() {
        return Collections.unmodifiableList(myList);
    }

    public void addItem(NavBarItem item) {
        myList.add(item);
    }

    public void clearItems() {
        NavBarItem[] toDispose = myList.toArray(new NavBarItem[0]);
        myList.clear();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            for (NavBarItem item : toDispose) {
                Disposer.dispose(item);
            }
        });

        getNavBarUI().clearItems();
    }

    @Override
    public void setUI(PanelUI ui) {
        getNavBarUI().clearItems();
        super.setUI(ui);
    }

    public NavBarUpdateQueue getUpdateQueue() {
        return myUpdateQueue;
    }

    public void escape() {
        myModel.setSelectedIndex(-1);
        hideHint();
        ToolWindowManager.getInstance(myProject).activateEditorComponent();
    }

    public void enter() {
        int index = myModel.getSelectedIndex();
        if (index != -1) {
            ctrlClick(index);
        }
    }

    public void moveHome() {
        shiftFocus(-myModel.getSelectedIndex());
    }

    public void navigate() {
        if (myModel.getSelectedIndex() != -1) {
            doubleClick(myModel.getSelectedIndex());
        }
    }

    public void moveDown() {
        int index = myModel.getSelectedIndex();
        if (index != -1) {
            if (myModel.size() - 1 == index) {
                shiftFocus(-1);
                ctrlClick(index - 1);
            }
            else {
                ctrlClick(index);
            }
        }
    }

    public void moveEnd() {
        shiftFocus(myModel.size() - 1 - myModel.getSelectedIndex());
    }

    public Project getProject() {
        return myProject;
    }

    public NavBarModel getModel() {
        return myModel;
    }

    @Override
    public void dispose() {
        cancelPopup();
        getNavBarUI().clearItems();
        myDisposed = true;
        NavBarListener.unsubscribeFrom(this);
    }

    public boolean isDisposed() {
        return myDisposed;
    }

    boolean isSelectedInPopup(Object object) {
        return isNodePopupActive() && myNodePopup.getList().getSelectedValuesList().contains(object);
    }

    static Object expandDirsWithJustOneSubdir(Object target) {
        if (target instanceof PsiElement element && !element.isValid()) {
            return element;
        }
        if (target instanceof PsiDirectory directory) {
            for (VirtualFile file = directory.getVirtualFile(), next; ; file = next) {
                VirtualFile[] children = file.getChildren();
                VirtualFile child = children.length == 1 ? children[0] : null;
                //noinspection AssignmentToForLoopParameter
                next = child != null && child.isDirectory() && !child.is(VFileProperty.SYMLINK) ? child : null;
                if (next == null) {
                    return ObjectUtil.notNull(directory.getManager().findDirectory(file), directory);
                }
            }
        }
        return target;
    }

    protected void updateItems() {
        for (NavBarItem item : myList) {
            item.update();
        }
        if (UISettings.getInstance().getShowNavigationBar()) {
            NavBarRootPaneExtensionImpl.NavBarWrapperPanel wrapperPanel =
                ComponentUtil.getParentOfType(NavBarRootPaneExtensionImpl.NavBarWrapperPanel.class, this);

            if (wrapperPanel != null) {
                wrapperPanel.revalidate();
                wrapperPanel.repaint();
            }
        }
    }

    public void rebuildAndSelectItem(Function<List<NavBarItem>, Integer> indexToSelectCallback, boolean showPopup) {
        myUpdateQueue.queueModelUpdateFromFocus();
        myUpdateQueue.queueRebuildUi();
        myUpdateQueue.queueSelect(() -> {
            if (!myList.isEmpty()) {
                int index = indexToSelectCallback.apply(myList);
                myModel.setSelectedIndex(index);
                requestSelectedItemFocus();
                if (showPopup) {
                    ctrlClick(index);
                }

            }
        });

        myUpdateQueue.flush();
    }

    public void rebuildAndSelectTail(boolean requestFocus) {
        rebuildAndSelectItem((list) -> list.size() - 1, false);
    }

    public void requestSelectedItemFocus() {
        int index = myModel.getSelectedIndex();
        if (index >= 0 && index < myModel.size() && allowNavItemsFocus()) {
            ProjectIdeFocusManager.getInstance(myProject).requestFocus(getItem(index), true);
        }
        else {
            ProjectIdeFocusManager.getInstance(myProject).requestFocus(this, true);
        }
    }

    public void moveLeft() {
        shiftFocus(-1);
    }

    public void moveRight() {
        shiftFocus(1);
    }

    void shiftFocus(int direction) {
        int selectedIndex = myModel.getSelectedIndex();
        int index = myModel.getIndexByModel(selectedIndex + direction);
        myModel.setSelectedIndex(index);
        if (allowNavItemsFocus()) {
            requestSelectedItemFocus();
        }
    }

    protected void scrollSelectionToVisible() {
        int selectedIndex = myModel.getSelectedIndex();
        if (selectedIndex == -1 || selectedIndex >= myList.size()) {
            return;
        }
        scrollRectToVisible(myList.get(selectedIndex).getBounds());
    }

    @Nullable
    private NavBarItem getItem(int index) {
        if (index != -1 && index < myList.size()) {
            return myList.get(index);
        }
        return null;
    }

    public boolean isInFloatingMode() {
        return myHint != null && myHint.isVisible();
    }


    @Override
    public Dimension getPreferredSize() {
        if (myDisposed || !myList.isEmpty()) {
            return super.getPreferredSize();
        }
        else {
            NavBarItem item = new NavBarItem(this, null, 0, null);
            Dimension size = item.getPreferredSize();
            ApplicationManager.getApplication().executeOnPooledThread(() -> Disposer.dispose(item));
            return size;
        }
    }

    public boolean isRebuildUiNeeded() {
        myModel.revalidate();
        if (myList.size() == myModel.size()) {
            int index = 0;
            for (NavBarItem eachLabel : myList) {
                Object eachElement = myModel.get(index);
                if (eachLabel.getObject() == null || !eachLabel.getObject().equals(eachElement)) {
                    return true;
                }

                if (!StringUtil.equals(eachLabel.getText(), getPresentation().getPresentableText(eachElement, false))) {
                    return true;
                }

                SimpleTextAttributes modelAttributes1 = myPresentation.getTextAttributes(eachElement, true);
                SimpleTextAttributes modelAttributes2 = myPresentation.getTextAttributes(eachElement, false);
                SimpleTextAttributes labelAttributes = eachLabel.getAttributes();

                if (!TextAttributesUtil.toTextAttributes(modelAttributes1)
                    .equals(TextAttributesUtil.toTextAttributes(labelAttributes)) && !TextAttributesUtil.toTextAttributes(modelAttributes2)
                    .equals(TextAttributesUtil.toTextAttributes(labelAttributes))) {
                    return true;
                }
                index++;
            }
            return false;
        }
        else {
            return true;
        }
    }

    void installPopupHandler(@Nonnull JComponent component, int index) {
        ActionManager actionManager = ActionManager.getInstance();
        PopupHandler.installPopupHandler(
            component,
            new ActionGroup() {
                @Nonnull
                @Override
                public AnAction[] getChildren(@Nullable AnActionEvent e) {
                    if (e == null) {
                        return EMPTY_ARRAY;
                    }
                    String popupGroupId = myProject.getApplication().getExtensionPoint(NavBarModelExtension.class)
                        .computeSafeIfAny(ext -> ext.getPopupMenuGroup(NavBarPanel.this), IdeActions.GROUP_NAVBAR_POPUP);
                    ActionGroup group = (ActionGroup)CustomActionsSchemaImpl.getInstance().getCorrectedAction(popupGroupId);
                    return group == null ? EMPTY_ARRAY : group.getChildren(e);
                }
            },
            ActionPlaces.NAVIGATION_BAR_POPUP,
            actionManager,
            new PopupMenuListenerAdapter() {
                @Override
                public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                    if (index != -1) {
                        myModel.setSelectedIndex(index);
                    }
                }
            }
        );
    }

    public void installActions(int index, NavBarItem component) {
        //suppress it for a while
        //installDnD(index, component);
        installPopupHandler(component, index);
        ListenerUtil.addMouseListener(
            component,
            new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent e) {
                    if (Platform.current().os().isWindows()) {
                        click(e);
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (!Platform.current().os().isWindows()) {
                        click(e);
                    }
                }

                private void click(MouseEvent e) {
                    if (e.isConsumed()) {
                        return;
                    }

                    if (e.isPopupTrigger()) {
                        return;
                    }
                    if (e.getClickCount() == 1) {
                        ctrlClick(index);
                        e.consume();
                    }
                    else if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                        requestSelectedItemFocus();
                        doubleClick(index);
                        e.consume();
                    }
                }
            }
        );

        ListenerUtil.addKeyListener(
            component,
            new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getModifiers() == 0 && e.getKeyCode() == KeyEvent.VK_SPACE) {
                        ctrlClick(index);
                        myModel.setSelectedIndex(index);
                        e.consume();
                    }
                }
            }
        );
    }

    private void installDnD(int index, NavBarItem component) {
        DnDSupport.createBuilder(component).setBeanProvider(dnDActionInfo -> new DnDDragStartBean(new TransferableWrapper() {
            @Override
            public List<File> asFileList() {
                Object o = myModel.get(index);
                if (o instanceof PsiElement element) {
                    VirtualFile vf = o instanceof PsiDirectory directory
                        ? directory.getVirtualFile()
                        : element.getContainingFile().getVirtualFile();
                    if (vf != null) {
                        return Collections.singletonList(new File(vf.getPath()).getAbsoluteFile());
                    }
                }
                return Collections.emptyList();
            }

            @Override
            public TreeNode[] getTreeNodes() {
                return null;
            }

            @Override
            public PsiElement[] getPsiElements() {
                return null;
            }
        })).setDisposableParent(component).install();
    }

    private void doubleClick(int index) {
        doubleClick(myModel.getElement(index));
    }

    protected void doubleClick(Object object) {
        if (object instanceof Navigatable navigatable) {
            if (navigatable.canNavigate()) {
                navigatable.navigate(true);
            }
        }
        else if (object instanceof Module module) {
            ProjectView projectView = ProjectView.getInstance(myProject);
            ProjectViewPane projectViewPane = projectView.getProjectViewPaneById(projectView.getCurrentViewId());
            if (projectViewPane != null) {
                projectViewPane.selectModule(module, true);
            }
        }
        else if (object instanceof Project) {
            return;
        }
        hideHint(true);
    }

    private void ctrlClick(int index) {
        if (isNodePopupActive()) {
            cancelPopup();
            if (myModel.getSelectedIndex() == index) {
                return;
            }
        }

        Object object = myModel.getElement(index);
        List<Object> objects = myModel.getChildren(object);

        if (!objects.isEmpty()) {
            Object[] siblings = new Object[objects.size()];
            //Icon[] icons = new Icon[objects.size()];
            for (int i = 0; i < objects.size(); i++) {
                siblings[i] = objects.get(i);
                //icons[i] = NavBarPresentation.getIcon(siblings[i], false);
            }
            NavBarItem item = getItem(index);

            int selectedIndex = index < myModel.size() - 1 ? objects.indexOf(myModel.getElement(index + 1)) : 0;
            myNodePopup = new NavBarPopup(this, index, siblings, selectedIndex);
            // if (item != null && item.isShowing()) {
            myNodePopup.show(item);
            item.update();
            // }
        }
    }

    protected void navigateInsideBar(int sourceItemIndex, Object object) {
        //UIEventLogger.logUIEvent(UIEventId.NavBarNavigate);

        boolean restorePopup = shouldRestorePopupOnSelect(object, sourceItemIndex);
        Object obj = expandDirsWithJustOneSubdir(object);
        myContextObject = null;

        myUpdateQueue.cancelAllUpdates();
        if (myNodePopup != null && myNodePopup.isVisible()) {
            myUpdateQueue.queueModelUpdateForObject(obj);
        }
        myUpdateQueue.queueRebuildUi();

        myUpdateQueue.queueAfterAll(
            () -> {
                int index = myModel.indexOf(obj);
                if (index >= 0) {
                    myModel.setSelectedIndex(index);
                }

                if (myModel.hasChildren(obj) && restorePopup) {
                    restorePopup();
                }
                else {
                    doubleClick(obj);
                }
            },
            NavBarUpdateQueue.ID.NAVIGATE_INSIDE
        );
    }

    private boolean shouldRestorePopupOnSelect(Object obj, int sourceItemIndex) {
        //noinspection SimplifiableIfStatement
        if (sourceItemIndex < myModel.size() - 1 && myModel.get(sourceItemIndex + 1) == obj) {
            return true;
        }
        return !(obj instanceof PsiElement psiElement)
            || psiElement instanceof PsiDirectory
            || psiElement instanceof PsiDirectoryContainer;
    }

    void restorePopup() {
        cancelPopup();
        ctrlClick(myModel.getSelectedIndex());
    }

    void cancelPopup() {
        cancelPopup(false);
    }


    void cancelPopup(boolean ok) {
        if (myNodePopup != null) {
            myNodePopup.hide(ok);
            myNodePopup = null;
            if (allowNavItemsFocus()) {
                requestSelectedItemFocus();
            }
        }
    }

    void hideHint() {
        hideHint(false);
    }

    protected void hideHint(boolean ok) {
        cancelPopup(ok);
        if (myHint != null) {
            myHint.hide(ok);
            myHint = null;
        }
    }

    @Nullable
    @Override
    @RequiredReadAction
    public Object getData(@Nonnull Key<?> dataId) {
        Object data = myProject.getApplication().getExtensionPoint(NavBarModelExtension.class)
            .computeSafeIfAny(extension -> extension.getData(dataId, this::getDataInner));
        return data != null ? data : getDataInner(dataId);
    }

    @Nullable
    @RequiredReadAction
    private Object getDataInner(Key<?> dataId) {
        return getDataImpl(dataId, this, this::getSelection);
    }

    @Nonnull
    JBIterable<?> getSelection() {
        Object value = myModel.getSelectedValue();
        if (value != null) {
            return JBIterable.of(value);
        }
        int size = myModel.size();
        return JBIterable.of(size > 0 ? myModel.getElement(size - 1) : null);
    }

    @RequiredReadAction
    Object getDataImpl(Key<?> dataId, @Nonnull JComponent source, @Nonnull Supplier<? extends JBIterable<?>> selection) {
        if (Project.KEY == dataId) {
            return !myProject.isDisposed() ? myProject : null;
        }
        if (Module.KEY == dataId) {
            Module module = selection.get().filter(Module.class).first();
            if (module != null && !module.isDisposed()) {
                return module;
            }
            PsiElement element = selection.get().filter(PsiElement.class).first();
            if (element != null) {
                return element.getModule();
            }
            return null;
        }
        if (LangDataKeys.MODULE_CONTEXT == dataId) {
            PsiDirectory directory = selection.get().filter(PsiDirectory.class).first();
            if (directory != null) {
                VirtualFile dir = directory.getVirtualFile();
                if (ProjectRootsUtil.isModuleContentRoot(dir, myProject)) {
                    return directory.getModule();
                }
            }
            return null;
        }
        if (PsiElement.KEY == dataId) {
            PsiElement element = selection.get().filter(PsiElement.class).first();
            return element != null && element.isValid() ? element : null;
        }
        if (PsiElement.KEY_OF_ARRAY == dataId) {
            List<PsiElement> result = selection.get().filter(PsiElement.class).filter(e -> e != null && e.isValid()).toList();
            return result.isEmpty() ? null : result.toArray(PsiElement.EMPTY_ARRAY);
        }

        if (VirtualFile.KEY_OF_ARRAY == dataId) {
            Set<VirtualFile> files = selection.get()
                .filter(PsiElement.class)
                .filter(e -> e != null && e.isValid())
                .filterMap(PsiUtilCore::getVirtualFile)
                .toSet();
            return !files.isEmpty() ? VfsUtilCore.toVirtualFileArray(files) : null;
        }

        if (Navigatable.KEY_OF_ARRAY == dataId) {
            List<Navigatable> elements = selection.get().filter(Navigatable.class).toList();
            return elements.isEmpty() ? null : elements.toArray(new Navigatable[0]);
        }

        if (UIExAWTDataKey.CONTEXT_COMPONENT == dataId) {
            return this;
        }
        if (CutProvider.KEY == dataId) {
            return getCopyPasteDelegator(source).getCutProvider();
        }
        if (CopyProvider.KEY == dataId) {
            return getCopyPasteDelegator(source).getCopyProvider();
        }
        if (PasteProvider.KEY == dataId) {
            return getCopyPasteDelegator(source).getPasteProvider();
        }
        if (DeleteProvider.KEY == dataId) {
            return selection.get().filter(Module.class).isNotEmpty() ? myDeleteModuleProvider : new DeleteHandler.DefaultDeleteProvider();
        }

        if (IdeView.KEY == dataId) {
            return myIdeView;
        }

        return null;
    }

    @Nonnull
    private CopyPasteSupport getCopyPasteDelegator(@Nonnull JComponent source) {
        String key = "NavBarPanel.copyPasteDelegator";
        Object result = source.getClientProperty(key);
        if (!(result instanceof CopyPasteSupport)) {
            result = new CopyPasteDelegator(myProject, source);
            source.putClientProperty(key, result);
        }
        return (CopyPasteSupport)result;
    }

    @Override
    public Point getBestPopupPosition() {
        int index = myModel.getSelectedIndex();
        int modelSize = myModel.size();
        if (index == -1) {
            index = modelSize - 1;
        }
        if (index > -1 && index < modelSize) {
            NavBarItem item = getItem(index);
            if (item != null) {
                return new Point(item.getX(), item.getY() + item.getHeight());
            }
        }
        return null;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        NavBarListener.subscribeTo(this);
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (isDisposeOnRemove() && ScreenUtil.isStandardAddRemoveNotify(this)) {
            Disposer.dispose(this);
        }
    }

    protected boolean isDisposeOnRemove() {
        return true;
    }

    public void updateState(boolean show) {
        if (show) {
            myUpdateQueue.queueModelUpdateFromFocus();
            myUpdateQueue.queueRebuildUi();
        }
    }

    // ------ popup NavBar ----------
    @RequiredUIAccess
    public void showHint(@Nullable Editor editor, DataContext dataContext) {
        myModel.updateModel(dataContext);
        if (myModel.isEmpty()) {
            return;
        }
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(this);
        panel.setOpaque(true);
        panel.setBackground(UIUtil.getListBackground());

        myHint = new LightweightHintImpl(panel) {
            @Override
            public void hide() {
                super.hide();
                cancelPopup();
                Disposer.dispose(NavBarPanel.this);
            }
        };
        myHint.setForceShowAsPopup(true);
        myHint.setFocusRequestor(this);
        KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        myUpdateQueue.rebuildUi();
        if (editor == null) {
            myContextComponent = dataContext.getData(UIExAWTDataKey.CONTEXT_COMPONENT);
            getHintContainerShowPoint().doWhenDone(relativePoint -> {
                if (relativePoint.getComponent() instanceof JComponent component && component.isShowing()) {
                    myHint.show(
                        component,
                        relativePoint.getPoint().x,
                        relativePoint.getPoint().y,
                        focusManager.getFocusOwner() instanceof JComponent owner ? owner : null,
                        new HintHint(relativePoint.getComponent(), relativePoint.getPoint())
                    );
                }
            });
        }
        else {
            myHintContainer = editor.getContentComponent();
            getHintContainerShowPoint().doWhenDone(rp -> {
                Point p = rp.getPointOn(myHintContainer).getPoint();
                HintHint hintInfo = new HintHint(editor.getContentComponent(), p);
                HintManagerImpl.getInstanceImpl().showEditorHint(myHint, editor, p, HintManager.HIDE_BY_ESCAPE, 0, true, hintInfo);
            });
        }

        rebuildAndSelectTail(true);
    }

    AsyncResult<RelativePoint> getHintContainerShowPoint() {
        AsyncResult<RelativePoint> result = new AsyncResult<>();
        if (myLocationCache == null) {
            if (myHintContainer != null) {
                Point p = AbstractPopup.getCenterOf(myHintContainer, this);
                p.y -= myHintContainer.getVisibleRect().height / 4;
                myLocationCache = RelativePoint.fromScreen(p);
            }
            else {
                DataManager dataManager = DataManager.getInstance();
                if (myContextComponent != null) {
                    DataContext ctx = dataManager.getDataContext(myContextComponent);
                    myLocationCache = JBPopupFactory.getInstance().guessBestPopupLocation(ctx);
                }
                else {
                    dataManager.getDataContextFromFocus().doWhenDone(dataContext -> {
                        myContextComponent = dataContext.getData(UIExAWTDataKey.CONTEXT_COMPONENT);
                        DataContext ctx = dataManager.getDataContext(myContextComponent);
                        myLocationCache = JBPopupFactory.getInstance().guessBestPopupLocation(ctx);
                    });
                }
            }
        }
        Component c = myLocationCache.getComponent();
        if (!(c instanceof JComponent && c.isShowing())) {
            //Yes. It happens sometimes.
            // 1. Empty frame. call nav bar, select some package and open it in Project View
            // 2. Call nav bar, then Esc
            // 3. Hide all tool windows (Ctrl+Shift+F12), so we've got empty frame again
            // 4. Call nav bar. NPE. ta da
            JComponent ideFrame = WindowManager.getInstance().getIdeFrame(getProject()).getComponent();
            JRootPane rootPane = UIUtil.getRootPane(ideFrame);
            myLocationCache = JBPopupFactory.getInstance().guessBestPopupLocation(rootPane);
        }
        result.setDone(myLocationCache);
        return result;
    }

    @Override
    public void putInfo(@Nonnull Map<String, String> info) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < myList.size(); i++) {
            NavBarItem each = myList.get(i);
            if (each.isSelected()) {
                result.append("[").append(each.getText()).append("]");
            }
            else {
                result.append(each.getText());
            }
            if (i < myList.size() - 1) {
                result.append(">");
            }
        }
        info.put("navBar", result.toString());

        if (isNodePopupActive()) {
            StringBuilder popupText = new StringBuilder();
            JBList list = myNodePopup.getList();
            for (int i = 0; i < list.getModel().getSize(); i++) {
                Object eachElement = list.getModel().getElementAt(i);
                String text = new NavBarItem(this, eachElement, myNodePopup, true).getText();
                int selectedIndex = list.getSelectedIndex();
                if (selectedIndex != -1 && eachElement.equals(list.getSelectedValue())) {
                    popupText.append("[").append(text).append("]");
                }
                else {
                    popupText.append(text);
                }
                if (i < list.getModel().getSize() - 1) {
                    popupText.append(">");
                }
            }
            info.put("navBarPopup", popupText.toString());
        }
    }

    @Nonnull
    public NavBarUI getNavBarUI() {
        return NavBarUIManager.getUI();
    }

    boolean isUpdating() {
        return myUpdateQueue.isUpdating();
    }
}
