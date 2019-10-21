// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.projectView.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.PsiCopyPasteManager;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.dnd.*;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.RootsProvider;
import com.intellij.ide.projectView.impl.nodes.AbstractModuleNode;
import com.intellij.ide.projectView.impl.nodes.AbstractProjectNode;
import com.intellij.ide.projectView.impl.nodes.ModuleGroupNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.*;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.problems.ProblemListener;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.ui.tree.TreePathUtil;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.tree.project.ProjectFileNode;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import consulo.annotations.DeprecationInfo;
import consulo.ide.projectView.impl.ProjectViewPaneOptionProvider;
import consulo.logging.Logger;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public abstract class AbstractProjectViewPane extends UserDataHolderBase implements DataProvider, Disposable, BusyObject {
  private static final Logger LOG = Logger.getInstance(AbstractProjectViewPane.class);
  public static final ExtensionPointName<AbstractProjectViewPane> EP_NAME = ExtensionPointName.create("com.intellij.projectViewPane");

  @Nonnull
  protected final Project myProject;
  protected DnDAwareTree myTree;
  protected AbstractTreeStructure myTreeStructure;
  private AbstractTreeBuilder myTreeBuilder;
  // subId->Tree state; key may be null
  private final Map<String, TreeState> myReadTreeState = new HashMap<>();
  private final AtomicBoolean myTreeStateRestored = new AtomicBoolean();
  private String mySubId;
  @NonNls
  private static final String ELEMENT_SUB_PANE = "subPane";
  @NonNls
  private static final String ATTRIBUTE_SUB_ID = "subId";

  private DnDTarget myDropTarget;
  private DnDSource myDragSource;
  private DnDManager myDndManager;

  private void queueUpdateByProblem() {
    if (Registry.is("projectView.showHierarchyErrors")) {
      if (myTreeBuilder != null) {
        myTreeBuilder.queueUpdate();
      }
    }
  }

  protected AbstractProjectViewPane(@Nonnull Project project) {
    myProject = project;
    ProblemListener problemListener = new ProblemListener() {
      @Override
      public void problemsAppeared(@Nonnull VirtualFile file) {
        queueUpdateByProblem();
      }

      @Override
      public void problemsChanged(@Nonnull VirtualFile file) {
        queueUpdateByProblem();
      }

      @Override
      public void problemsDisappeared(@Nonnull VirtualFile file) {
        queueUpdateByProblem();
      }
    };
    project.getMessageBus().connect(this).subscribe(ProblemListener.TOPIC, problemListener);
    Disposer.register(project, this);
  }

  @Deprecated
  protected final void fireTreeChangeListener() {
  }

  @Nonnull
  public abstract String getTitle();

  @Nonnull
  public abstract consulo.ui.image.Image getIcon();

  @Nonnull
  public abstract String getId();

  @Nullable
  public final String getSubId() {
    return mySubId;
  }

  public final void setSubId(@Nullable String subId) {
    if (Comparing.strEqual(mySubId, subId)) return;
    saveExpandedPaths();
    mySubId = subId;
    onSubIdChange();
  }

  protected void onSubIdChange() {
  }

  public boolean isInitiallyVisible() {
    return true;
  }

  public boolean supportsManualOrder() {
    return false;
  }

  @Nonnull
  protected String getManualOrderOptionText() {
    return IdeBundle.message("action.manual.order");
  }

  /**
   * @return all supported sub views IDs.
   * should return empty array if there is no subViews as in Project/Packages view.
   */
  @Nonnull
  public String[] getSubIds() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Nonnull
  public String getPresentableSubIdName(@Nonnull final String subId) {
    throw new IllegalStateException("should not call");
  }

  @Nonnull
  public consulo.ui.image.Image getPresentableSubIdIcon(@Nonnull String subId) {
    return getIcon();
  }

  @Nonnull
  public abstract JComponent createComponent();

  public JComponent getComponentToFocus() {
    return myTree;
  }

  public void expand(@Nullable final Object[] path, final boolean requestFocus) {
    if (getTreeBuilder() == null || path == null) return;
    AbstractTreeUi ui = getTreeBuilder().getUi();
    if (ui != null) ui.buildNodeForPath(path);

    DefaultMutableTreeNode node = ui == null ? null : ui.getNodeForPath(path);
    if (node == null) {
      return;
    }
    TreePath treePath = new TreePath(node.getPath());
    myTree.expandPath(treePath);
    if (requestFocus) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myTree, true));
    }
    TreeUtil.selectPath(myTree, treePath);
  }

  @Override
  public void dispose() {
    if (myDndManager != null) {
      if (myDropTarget != null) {
        myDndManager.unregisterTarget(myDropTarget, myTree);
        myDropTarget = null;
      }
      if (myDragSource != null) {
        myDndManager.unregisterSource(myDragSource, myTree);
        myDragSource = null;
      }
      myDndManager = null;
    }
    setTreeBuilder(null);
    myTree = null;
    myTreeStructure = null;
  }

  @Nonnull
  public abstract ActionCallback updateFromRoot(boolean restoreExpandedPaths);

  public void updateFrom(Object element, boolean forceResort, boolean updateStructure) {
    AbstractTreeBuilder builder = getTreeBuilder();
    if (builder != null) {
      builder.queueUpdateFrom(element, forceResort, updateStructure);
    }
    else if (element instanceof PsiElement) {
      AsyncProjectViewSupport support = getAsyncSupport();
      if (support != null) support.updateByElement((PsiElement)element, updateStructure);
    }
  }

  public abstract void select(Object element, VirtualFile file, boolean requestFocus);

  public void selectModule(@Nonnull Module module, final boolean requestFocus) {
    doSelectModuleOrGroup(module, requestFocus);
  }

  private void doSelectModuleOrGroup(@Nonnull Object toSelect, final boolean requestFocus) {
    ToolWindowManager windowManager = ToolWindowManager.getInstance(myProject);
    final Runnable runnable = () -> {
      if (requestFocus) {
        ProjectView projectView = ProjectView.getInstance(myProject);
        if (projectView != null) {
          projectView.changeView(getId(), getSubId());
        }
      }
      BaseProjectTreeBuilder builder = (BaseProjectTreeBuilder)getTreeBuilder();
      if (builder != null) {
        builder.selectInWidth(toSelect, requestFocus, node -> node instanceof AbstractModuleNode || node instanceof ModuleGroupNode || node instanceof AbstractProjectNode);
      }
    };
    if (requestFocus) {
      windowManager.getToolWindow(ToolWindowId.PROJECT_VIEW).activate(runnable);
    }
    else {
      runnable.run();
    }
  }

  public void selectModuleGroup(@Nonnull ModuleGroup moduleGroup, boolean requestFocus) {
    doSelectModuleOrGroup(moduleGroup, requestFocus);
  }

  public TreePath[] getSelectionPaths() {
    return myTree == null ? null : myTree.getSelectionPaths();
  }

  public void addToolbarActions(@Nonnull DefaultActionGroup actionGroup) {
  }

  public void addToolbarActionsImpl(DefaultActionGroup actionGroup) {
    addToolbarActions(actionGroup);
    for (ProjectViewPaneOptionProvider provider : ProjectViewPaneOptionProvider.EX_NAME.getExtensions()) {
      provider.addToolbarActions(this, actionGroup);
    }
  }

  @Nonnull
  protected <T extends NodeDescriptor> List<T> getSelectedNodes(@Nonnull Class<T> nodeClass) {
    TreePath[] paths = getSelectionPaths();
    if (paths == null) return Collections.emptyList();
    final ArrayList<T> result = new ArrayList<>();
    for (TreePath path : paths) {
      T userObject = TreeUtil.getLastUserObject(nodeClass, path);
      if (userObject != null) {
        result.add(userObject);
      }
    }
    return result;
  }

  @Override
  public Object getData(@Nonnull Key<?> dataId) {
    Object data = myTreeStructure instanceof AbstractTreeStructureBase ? ((AbstractTreeStructureBase)myTreeStructure).getDataFromProviders(getSelectedNodes(AbstractTreeNode.class), dataId) : null;
    if (data != null) {
      return data;
    }
    if (CommonDataKeys.NAVIGATABLE_ARRAY == dataId) {
      TreePath[] paths = getSelectionPaths();
      if (paths == null) return null;
      final ArrayList<Navigatable> navigatables = new ArrayList<>();
      for (TreePath path : paths) {
        Object node = path.getLastPathComponent();
        Object userObject = TreeUtil.getUserObject(node);
        if (userObject instanceof Navigatable) {
          navigatables.add((Navigatable)userObject);
        }
        else if (node instanceof Navigatable) {
          navigatables.add((Navigatable)node);
        }
      }
      return navigatables.isEmpty() ? null : navigatables.toArray(new Navigatable[0]);
    }
    return null;
  }

  // used for sorting tabs in the tabbed pane
  public abstract int getWeight();

  @Nonnull
  public abstract SelectInTarget createSelectInTarget();

  public final TreePath getSelectedPath() {
    return myTree == null ? null : TreeUtil.getSelectedPathIfOne(myTree);
  }

  public final NodeDescriptor getSelectedDescriptor() {
    return TreeUtil.getLastUserObject(NodeDescriptor.class, getSelectedPath());
  }

  /**
   * @see TreeUtil#getUserObject(Object)
   * @deprecated AbstractProjectViewPane#getSelectedPath
   */
  @Deprecated
  public final DefaultMutableTreeNode getSelectedNode() {
    TreePath path = getSelectedPath();
    return path == null ? null : ObjectUtils.tryCast(path.getLastPathComponent(), DefaultMutableTreeNode.class);
  }

  public final Object getSelectedElement() {
    final Object[] elements = getSelectedElements();
    return elements.length == 1 ? elements[0] : null;
  }

  @Nonnull
  public final PsiElement[] getSelectedPSIElements() {
    TreePath[] paths = getSelectionPaths();
    if (paths == null) return PsiElement.EMPTY_ARRAY;
    List<PsiElement> result = new ArrayList<>();
    for (TreePath path : paths) {
      result.addAll(getElementsFromNode(path.getLastPathComponent()));
    }
    return PsiUtilCore.toPsiElementArray(result);
  }

  @Nonnull
  public List<PsiElement> getElementsFromNode(@Nullable Object node) {
    Object value = getValueFromNode(node);
    JBIterable<?> it = value instanceof PsiElement || value instanceof VirtualFile
                       ? JBIterable.of(value)
                       : value instanceof Object[] ? JBIterable.of((Object[])value) : value instanceof Iterable ? JBIterable.from((Iterable<?>)value) : JBIterable.of(TreeUtil.getUserObject(node));
    return it.flatten(o -> o instanceof RootsProvider ? ((RootsProvider)o).getRoots() : Collections.singleton(o))
            .map(o -> o instanceof VirtualFile ? PsiUtilCore.findFileSystemItem(myProject, (VirtualFile)o) : o).filter(PsiElement.class).filter(PsiElement::isValid).toList();
  }

  /**
   * @deprecated use {@link AbstractProjectViewPane#getElementsFromNode(Object)}
   **/
  @Deprecated
  @Nullable
  public PsiElement getPSIElementFromNode(@Nullable TreeNode node) {
    return ContainerUtil.getFirstItem(getElementsFromNode(node));
  }

  @Nullable
  protected Module getNodeModule(@Nullable final Object element) {
    if (element instanceof PsiElement) {
      PsiElement psiElement = (PsiElement)element;
      return ModuleUtilCore.findModuleForPsiElement(psiElement);
    }
    return null;
  }

  @Nonnull
  public final Object[] getSelectedElements() {
    TreePath[] paths = getSelectionPaths();
    if (paths == null) return PsiElement.EMPTY_ARRAY;
    ArrayList<Object> list = new ArrayList<>(paths.length);
    for (TreePath path : paths) {
      Object lastPathComponent = path.getLastPathComponent();
      Object element = getValueFromNode(lastPathComponent);
      if (element instanceof Object[]) {
        Collections.addAll(list, (Object[])element);
      }
      else if (element != null) {
        list.add(element);
      }
    }
    return ArrayUtil.toObjectArray(list);
  }

  @Nullable
  public Object getValueFromNode(@Nullable Object node) {
    return extractValueFromNode(node);
  }

  /**
   * @deprecated use {@link AbstractProjectViewPane#getValueFromNode(Object)}
   **/
  @Deprecated
  protected Object exhumeElementFromNode(DefaultMutableTreeNode node) {
    return getValueFromNode(node);
  }

  @Nullable
  public static Object extractValueFromNode(@Nullable Object node) {
    Object userObject = TreeUtil.getUserObject(node);
    Object element = null;
    if (userObject instanceof AbstractTreeNode) {
      AbstractTreeNode descriptor = (AbstractTreeNode)userObject;
      element = descriptor.getValue();
    }
    else if (userObject instanceof NodeDescriptor) {
      NodeDescriptor descriptor = (NodeDescriptor)userObject;
      element = descriptor.getElement();
      if (element instanceof AbstractTreeNode) {
        element = ((AbstractTreeNode)element).getValue();
      }
    }
    else if (userObject != null) {
      element = userObject;
    }
    return element;
  }

  public AbstractTreeBuilder getTreeBuilder() {
    return myTreeBuilder;
  }

  public AbstractTreeStructure getTreeStructure() {
    return myTreeStructure;
  }

  public void readExternal(@Nonnull Element element) {
    List<Element> subPanes = element.getChildren(ELEMENT_SUB_PANE);
    for (Element subPane : subPanes) {
      String subId = subPane.getAttributeValue(ATTRIBUTE_SUB_ID);
      TreeState treeState = TreeState.createFrom(subPane);
      if (!treeState.isEmpty()) {
        myReadTreeState.put(subId, treeState);
      }
    }

    for (ProjectViewPaneOptionProvider provider : ProjectViewPaneOptionProvider.EX_NAME.getExtensionList()) {
      KeyWithDefaultValue key = provider.getKey();

      String valueOfKey = JDOMExternalizerUtil.readField(element, key.toString());
      if (valueOfKey != null) {
        putUserData(key, provider.parseValue(valueOfKey));
      }
    }
  }

  public void writeExternal(Element element) {
    saveExpandedPaths();
    for (Map.Entry<String, TreeState> entry : myReadTreeState.entrySet()) {
      String subId = entry.getKey();
      TreeState treeState = entry.getValue();
      Element subPane = new Element(ELEMENT_SUB_PANE);
      if (subId != null) {
        subPane.setAttribute(ATTRIBUTE_SUB_ID, subId);
      }
      treeState.writeExternal(subPane);
      element.addContent(subPane);
    }

    for (ProjectViewPaneOptionProvider provider : ProjectViewPaneOptionProvider.EX_NAME.getExtensionList()) {
      KeyWithDefaultValue key = provider.getKey();
      Object value = getUserData(key);
      //noinspection unchecked
      String stringValue = provider.toString(value);
      if (stringValue != null) {
        JDOMExternalizerUtil.writeField(element, key.toString(), stringValue);
      }
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getUserData(@Nonnull Key<T> key) {
    T value = super.getUserData(key);
    if (value == null && key instanceof KeyWithDefaultValue) {
      return (T)((KeyWithDefaultValue)key).getDefaultValue();
    }
    return value;
  }

  protected void saveExpandedPaths() {
    myTreeStateRestored.set(false);
    if (myTree != null) {
      TreeState treeState = TreeState.createOn(myTree);
      if (!treeState.isEmpty()) {
        myReadTreeState.put(getSubId(), treeState);
      }
      else {
        myReadTreeState.remove(getSubId());
      }
    }
  }

  public final void restoreExpandedPaths() {
    if (myTree == null || myTreeStateRestored.getAndSet(true)) return;
    TreeState treeState = myReadTreeState.get(getSubId());
    if (treeState != null && !treeState.isEmpty()) {
      treeState.applyTo(myTree);
    }
    else if (myTree.isSelectionEmpty()) {
      TreeUtil.promiseSelectFirst(myTree);
    }
  }

  @Deprecated
  @DeprecationInfo("Don't use this method. Use project from AnEvent")
  public Project getProject() {
    return myProject;
  }

  @Nonnull
  protected Comparator<NodeDescriptor> createComparator() {
    return new GroupByTypeComparator(ProjectView.getInstance(myProject), getId());
  }

  public void installComparator() {
    installComparator(getTreeBuilder());
  }

  void installComparator(AbstractTreeBuilder treeBuilder) {
    installComparator(treeBuilder, createComparator());
  }

  @TestOnly
  public void installComparator(@Nonnull Comparator<? super NodeDescriptor> comparator) {
    installComparator(getTreeBuilder(), comparator);
  }

  protected void installComparator(AbstractTreeBuilder builder, @Nonnull Comparator<? super NodeDescriptor> comparator) {
    if (builder != null) builder.setNodeDescriptorComparator(comparator);
  }

  public JTree getTree() {
    return myTree;
  }

  @Nonnull
  public PsiDirectory[] getSelectedDirectories() {
    List<PsiDirectory> directories = ContainerUtil.newArrayList();
    for (PsiDirectoryNode node : getSelectedNodes(PsiDirectoryNode.class)) {
      PsiDirectory directory = node.getValue();
      if (directory != null) {
        directories.add(directory);
        Object parentValue = node.getParent().getValue();
        if (parentValue instanceof PsiDirectory && Registry.is("projectView.choose.directory.on.compacted.middle.packages")) {
          while (true) {
            directory = directory.getParentDirectory();
            if (directory == null || directory.equals(parentValue)) {
              break;
            }
            directories.add(directory);
          }
        }
      }
    }
    if (!directories.isEmpty()) {
      return directories.toArray(PsiDirectory.EMPTY_ARRAY);
    }

    final PsiElement[] elements = getSelectedPSIElements();
    if (elements.length == 1) {
      final PsiElement element = elements[0];
      if (element instanceof PsiDirectory) {
        return new PsiDirectory[]{(PsiDirectory)element};
      }
      else if (element instanceof PsiDirectoryContainer) {
        return ((PsiDirectoryContainer)element).getDirectories();
      }
      else {
        final PsiFile containingFile = element.getContainingFile();
        if (containingFile != null) {
          final PsiDirectory psiDirectory = containingFile.getContainingDirectory();
          if (psiDirectory != null) {
            return new PsiDirectory[]{psiDirectory};
          }
          final VirtualFile file = containingFile.getVirtualFile();
          if (file instanceof VirtualFileWindow) {
            final VirtualFile delegate = ((VirtualFileWindow)file).getDelegate();
            final PsiFile delegatePsiFile = containingFile.getManager().findFile(delegate);
            if (delegatePsiFile != null && delegatePsiFile.getContainingDirectory() != null) {
              return new PsiDirectory[]{delegatePsiFile.getContainingDirectory()};
            }
          }
          return PsiDirectory.EMPTY_ARRAY;
        }
      }
    }
    else {
      TreePath path = getSelectedPath();
      if (path != null) {
        Object component = path.getLastPathComponent();
        if (component instanceof DefaultMutableTreeNode) {
          return getSelectedDirectoriesInAmbiguousCase(((DefaultMutableTreeNode)component).getUserObject());
        }
        return getSelectedDirectoriesInAmbiguousCase(component);
      }
    }
    return PsiDirectory.EMPTY_ARRAY;
  }

  @Nonnull
  protected PsiDirectory[] getSelectedDirectoriesInAmbiguousCase(Object userObject) {
    if (userObject instanceof AbstractModuleNode) {
      final Module module = ((AbstractModuleNode)userObject).getValue();
      if (module != null) {
        final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        final VirtualFile[] sourceRoots = moduleRootManager.getSourceRoots();
        List<PsiDirectory> dirs = new ArrayList<>(sourceRoots.length);
        final PsiManager psiManager = PsiManager.getInstance(myProject);
        for (final VirtualFile sourceRoot : sourceRoots) {
          final PsiDirectory directory = psiManager.findDirectory(sourceRoot);
          if (directory != null) {
            dirs.add(directory);
          }
        }
        return dirs.toArray(PsiDirectory.EMPTY_ARRAY);
      }
    }
    else if (userObject instanceof ProjectViewNode) {
      VirtualFile file = ((ProjectViewNode)userObject).getVirtualFile();
      if (file != null && file.isValid() && file.isDirectory()) {
        PsiDirectory directory = PsiManager.getInstance(myProject).findDirectory(file);
        if (directory != null) {
          return new PsiDirectory[]{directory};
        }
      }
    }
    return PsiDirectory.EMPTY_ARRAY;
  }

  // Drag'n'Drop stuff

  @Nullable
  public static PsiElement[] getTransferedPsiElements(@Nonnull Transferable transferable) {
    try {
      final Object transferData = transferable.getTransferData(DnDEventImpl.ourDataFlavor);
      if (transferData instanceof TransferableWrapper) {
        return ((TransferableWrapper)transferData).getPsiElements();
      }
      return null;
    }
    catch (Exception e) {
      return null;
    }
  }

  @Nullable
  public static TreeNode[] getTransferedTreeNodes(@Nonnull Transferable transferable) {
    try {
      final Object transferData = transferable.getTransferData(DnDEventImpl.ourDataFlavor);
      if (transferData instanceof TransferableWrapper) {
        return ((TransferableWrapper)transferData).getTreeNodes();
      }
      return null;
    }
    catch (Exception e) {
      return null;
    }
  }

  protected void enableDnD() {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      myDropTarget = new ProjectViewDropTarget(myTree, myProject) {
        @Nullable
        @Override
        protected PsiElement getPsiElement(@Nonnull TreePath path) {
          return ContainerUtil.getFirstItem(getElementsFromNode(path.getLastPathComponent()));
        }

        @Nullable
        @Override
        protected Module getModule(@Nonnull PsiElement element) {
          return getNodeModule(element);
        }

        @Override
        public void cleanUpOnLeave() {
          beforeDnDLeave();
          super.cleanUpOnLeave();
        }

        @Override
        public boolean update(DnDEvent event) {
          beforeDnDUpdate();
          return super.update(event);
        }
      };
      myDragSource = new MyDragSource();
      myDndManager = DnDManager.getInstance();
      myDndManager.registerSource(myDragSource, myTree);
      myDndManager.registerTarget(myDropTarget, myTree);
    }
  }

  protected void beforeDnDUpdate() {
  }

  protected void beforeDnDLeave() {
  }

  public void setTreeBuilder(final AbstractTreeBuilder treeBuilder) {
    if (treeBuilder != null) {
      Disposer.register(this, treeBuilder);
// needs refactoring for project view first
//      treeBuilder.setCanYieldUpdate(true);
    }
    myTreeBuilder = treeBuilder;
  }

  public boolean supportsFoldersAlwaysOnTop() {
    return true;
  }

  public boolean supportsSortByType() {
    return true;
  }

  private class MyDragSource implements DnDSource {
    @Override
    public boolean canStartDragging(DnDAction action, Point dragOrigin) {
      if ((action.getActionId() & DnDConstants.ACTION_COPY_OR_MOVE) == 0) return false;
      final Object[] elements = getSelectedElements();
      final PsiElement[] psiElements = getSelectedPSIElements();
      DataContext dataContext = DataManager.getInstance().getDataContext(myTree);
      return psiElements.length > 0 || canDragElements(elements, dataContext, action.getActionId());
    }

    @Override
    public DnDDragStartBean startDragging(DnDAction action, Point dragOrigin) {
      final PsiElement[] psiElements = getSelectedPSIElements();
      TreePath[] paths = getSelectionPaths();
      return new DnDDragStartBean(new TransferableWrapper() {

        @Override
        public List<File> asFileList() {
          return PsiCopyPasteManager.asFileList(psiElements);
        }

        @Nullable
        @Override
        public TreePath[] getTreePaths() {
          return paths;
        }

        @Override
        public TreeNode[] getTreeNodes() {
          return TreePathUtil.toTreeNodes(getTreePaths());
        }

        @Override
        public PsiElement[] getPsiElements() {
          return psiElements;
        }
      });
    }

    @Nullable
    @Override
    public Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin, @Nonnull DnDDragStartBean bean) {
      final TreePath[] paths = getSelectionPaths();
      if (paths == null) return null;

      final int count = paths.length;

      final JLabel label = new JLabel(String.format("%s item%s", count, count == 1 ? "" : "s"));
      label.setOpaque(true);
      label.setForeground(myTree.getForeground());
      label.setBackground(myTree.getBackground());
      label.setFont(myTree.getFont());
      label.setSize(label.getPreferredSize());
      final BufferedImage image = UIUtil.createImage(label.getWidth(), label.getHeight(), BufferedImage.TYPE_INT_ARGB);

      Graphics2D g2 = (Graphics2D)image.getGraphics();
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
      label.paint(g2);
      g2.dispose();

      return new Pair<>(image, new Point(-image.getWidth(null), -image.getHeight(null)));
    }

    @Override
    public void dragDropEnd() {
    }

    @Override
    public void dropActionChanged(int gestureModifiers) {
    }
  }

  private static boolean canDragElements(@Nonnull Object[] elements, @Nonnull DataContext dataContext, int dragAction) {
    for (Object element : elements) {
      if (element instanceof Module) {
        return true;
      }
    }
    return dragAction == DnDConstants.ACTION_MOVE && MoveHandler.canMove(dataContext);
  }

  @Nonnull
  @Override
  public AsyncResult<Void> getReady(@Nonnull Object requestor) {
    if (myTreeBuilder == null) return AsyncResult.resolved();
    if (myTreeBuilder.isDisposed()) return AsyncResult.rejected();
    ActionCallback ready = myTreeBuilder.getUi().getReady(requestor);
    AsyncResult<Void> result = new AsyncResult<>();
    ready.doWhenDone(() -> result.setDone());
    ready.doWhenRejected((er) -> result.reject(er));
    return result;
  }

  @TestOnly
  @Deprecated
  @Nonnull
  public Promise<TreePath> promisePathToElement(@Nonnull Object element) {
    AbstractTreeBuilder builder = getTreeBuilder();
    if (builder != null) {
      DefaultMutableTreeNode node = builder.getNodeForElement(element);
      if (node == null) return Promises.rejectedPromise();
      return Promises.resolvedPromise(new TreePath(node.getPath()));
    }
    TreeVisitor visitor = createVisitor(element);
    if (visitor == null || myTree == null) return Promises.rejectedPromise();
    return TreeUtil.promiseVisit(myTree, visitor);
  }

  AsyncProjectViewSupport getAsyncSupport() {
    return null;
  }

  @Nonnull
  static List<TreeVisitor> createVisitors(@Nonnull Object... objects) {
    List<TreeVisitor> list = new ArrayList<>();
    for (Object object : objects) {
      TreeVisitor visitor = createVisitor(object);
      ContainerUtil.addIfNotNull(list, visitor);
    }
    return ContainerUtil.immutableList(list);
  }

  @Nullable
  public static TreeVisitor createVisitor(@Nonnull Object object) {
    if (object instanceof AbstractTreeNode) {
      AbstractTreeNode node = (AbstractTreeNode)object;
      object = node.getValue();
    }
    if (object instanceof ProjectFileNode) {
      ProjectFileNode node = (ProjectFileNode)object;
      object = node.getVirtualFile();
    }
    if (object instanceof VirtualFile) return createVisitor((VirtualFile)object);
    if (object instanceof PsiElement) return createVisitor((PsiElement)object);
    LOG.warn("unsupported object: " + object);
    return null;
  }

  @Nonnull
  public static TreeVisitor createVisitor(@Nonnull VirtualFile file) {
    return createVisitor(null, file);
  }

  @Nullable
  public static TreeVisitor createVisitor(@Nonnull PsiElement element) {
    return createVisitor(element, null);
  }

  @Nullable
  public static TreeVisitor createVisitor(@Nullable PsiElement element, @Nullable VirtualFile file) {
    return createVisitor(element, file, null);
  }

  @Nullable
  static TreeVisitor createVisitor(@Nullable PsiElement element, @Nullable VirtualFile file, @Nullable List<? super TreePath> collector) {
    Predicate<? super TreePath> predicate = collector == null ? null : path -> {
      collector.add(path);
      return false;
    };
    if (element != null && element.isValid()) return new ProjectViewNodeVisitor(element, file, predicate);
    if (file != null) return new ProjectViewFileVisitor(file, predicate);
    LOG.warn(element != null ? "element invalidated: " + element : "cannot create visitor without element and/or file");
    return null;
  }
}
