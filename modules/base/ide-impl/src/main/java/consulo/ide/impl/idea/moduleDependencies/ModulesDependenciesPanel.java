/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package consulo.ide.impl.idea.moduleDependencies;

import consulo.application.AllIcons;
import consulo.application.HelpManager;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.util.graph.GraphAlgorithms;
import consulo.component.util.graph.DFSTBuilder;
import consulo.component.util.graph.Graph;
import consulo.dataContext.DataProvider;
import consulo.disposer.Disposable;
import consulo.ide.impl.idea.ide.util.PropertiesComponent;
import consulo.language.editor.LangDataKeys;
import consulo.language.editor.scope.localize.AnalysisScopeLocalize;
import consulo.language.pom.NavigatableWithText;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.layer.event.ModuleRootEvent;
import consulo.module.content.layer.event.ModuleRootListener;
import consulo.navigation.Navigatable;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.project.ui.view.internal.ProjectSettingsService;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.TreeExpander;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.PopupHandler;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.ui.ex.awt.Splitter;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.speedSearch.TreeSpeedSearch;
import consulo.ui.ex.awt.tree.ColoredTreeCellRenderer;
import consulo.ui.ex.awt.tree.Tree;
import consulo.ui.ex.awt.tree.TreeUtil;
import consulo.ui.ex.content.Content;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * @author anna
 * @since 2005-02-10
 */
public class ModulesDependenciesPanel extends JPanel implements ModuleRootListener, Disposable {
  @NonNls private static final String DIRECTION = "FORWARD_ANALIZER";
  private Content myContent;
  private final Project myProject;
  private Tree myLeftTree;
  private DefaultTreeModel myLeftTreeModel;

  private final Tree myRightTree;
  private final DefaultTreeModel myRightTreeModel;

  private Graph<Module> myModulesGraph;
  private final Module[] myModules;

  private JTextField myPathField = new JTextField();

  private final Splitter mySplitter;
  @NonNls private static final String ourHelpID = "module.dependencies.tool.window";

  public ModulesDependenciesPanel(final Project project, final Module[] modules) {
    super(new BorderLayout());
    myProject = project;
    myModules = modules;

    //noinspection HardCodedStringLiteral
    myRightTreeModel = new DefaultTreeModel(new DefaultMutableTreeNode("Root"));
    myRightTree = new Tree(myRightTreeModel);
    initTree(myRightTree, true);

    initLeftTree();

    mySplitter = new Splitter();
    mySplitter.setFirstComponent(new MyTreePanel(myLeftTree, myProject));
    mySplitter.setSecondComponent(new MyTreePanel(myRightTree, myProject));

    setSplitterProportion();
    add(mySplitter, BorderLayout.CENTER);
    add(createNorthPanel(), BorderLayout.NORTH);

    project.getMessageBus().connect(this).subscribe(ModuleRootListener.class, this);
  }

  private void setSplitterProportion() {
    if (mySplitter == null){
      return;
    }
    myModulesGraph = buildGraph();
    DFSTBuilder<Module> builder = new DFSTBuilder<>(myModulesGraph);
    if (builder.isAcyclic()){
      mySplitter.setProportion(1.f);
    } else {
      mySplitter.setProportion(0.5f);
    }
  }

  @Override
  public void dispose() {
  }

  public ModulesDependenciesPanel(final Project project) {
    this(project, ModuleManager.getInstance(project).getModules());
  }

  private JComponent createNorthPanel(){
    DefaultActionGroup group = new DefaultActionGroup();

    group.add(new AnAction(
      CommonLocalize.actionClose(),
      AnalysisScopeLocalize.actionCloseModulesDependenciesDescription(),
      AllIcons.Actions.Cancel
    ){
      @Override
      public void actionPerformed(AnActionEvent e) {
        DependenciesAnalyzeManager.getInstance(myProject).closeContent(myContent);
      }
    });

    appendDependenciesAction(group);

    group.add(new ToggleAction(
      AnalysisScopeLocalize.actionModuleDependenciesDirection(),
      LocalizeValue.empty(),
      isForwardDirection() ? PlatformIconGroup.actionsMoveup() : PlatformIconGroup.actionsMovedown()
    ){
      @Override
      public boolean isSelected(AnActionEvent e) {
        return isForwardDirection();
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        PropertiesComponent.getInstance(myProject).setValue(DIRECTION, String.valueOf(state));
        initLeftTreeModel();
      }

      @Override
      public void update(final AnActionEvent e) {
        e.getPresentation().setIcon(isForwardDirection() ? PlatformIconGroup.actionsMoveup() : PlatformIconGroup.actionsMovedown());
      }
    });

    group.add(new ContextHelpAction(ourHelpID));

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(toolbar.getComponent(), BorderLayout.NORTH);
    panel.add(myPathField, BorderLayout.SOUTH);
    myPathField.setEditable(false);
    return panel;
  }

  private boolean isForwardDirection() {
    final String value = PropertiesComponent.getInstance(myProject).getValue(DIRECTION);
    return value == null || Boolean.parseBoolean(value);
  }

  private static void appendDependenciesAction(final DefaultActionGroup group) {
    final AnAction analyzeDepsAction = ActionManager.getInstance().getAction(IdeActions.ACTION_ANALYZE_DEPENDENCIES);
    group.add(new AnAction(
      analyzeDepsAction.getTemplatePresentation().getTextValue(),
      analyzeDepsAction.getTemplatePresentation().getDescriptionValue(),
      PlatformIconGroup.toolwindowsToolwindowmoduledependencies()
    ) {

      @Override
      public void actionPerformed(AnActionEvent e) {
        analyzeDepsAction.actionPerformed(e);
      }


      @Override
      public void update(AnActionEvent e) {
        analyzeDepsAction.update(e);
      }
    });
  }

  private void buildRightTree(Module module){
    final DefaultMutableTreeNode root = (DefaultMutableTreeNode)myRightTreeModel.getRoot();
    root.removeAllChildren();
    final Set<List<Module>> cycles = GraphAlgorithms.getInstance().findCycles(myModulesGraph, module);
    int index = 1;
    for (List<Module> modules : cycles) {
      final DefaultMutableTreeNode cycle = new DefaultMutableTreeNode(
        AnalysisScopeLocalize.moduleDependenciesCycleNodeText(Integer.toString(index++).toUpperCase()).get()
      );
      root.add(cycle);
      cycle.add(new DefaultMutableTreeNode(new MyUserObject(false, module)));
      for (Module moduleInCycle : modules) {
        cycle.add(new DefaultMutableTreeNode(new MyUserObject(false, moduleInCycle)));
      }
    }
    ((DefaultTreeModel)myRightTree.getModel()).reload();
    TreeUtil.expandAll(myRightTree);
  }

  private void initLeftTreeModel(){
    final DefaultMutableTreeNode root = (DefaultMutableTreeNode)myLeftTreeModel.getRoot();
    root.removeAllChildren();
    myModulesGraph = buildGraph();
    setSplitterProportion();
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> {
        final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        final Map<Module, Boolean> inCycle = new HashMap<>();
        for (Module module : myModules) {
          if (progressIndicator != null) {
            if (progressIndicator.isCanceled()) return;
            progressIndicator.setTextValue(AnalysisScopeLocalize.updateModuleTreeProgressText(module.getName()));
          }
          if (!module.isDisposed()) {
            Boolean isInCycle = inCycle.get(module);
            if (isInCycle == null) {
              isInCycle = !GraphAlgorithms.getInstance().findCycles(myModulesGraph, module).isEmpty();
              inCycle.put(module, isInCycle);
            }
            final DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode(new MyUserObject(isInCycle.booleanValue(), module));
            root.add(moduleNode);
            final Iterator<Module> out = myModulesGraph.getOut(module);
            while (out.hasNext()) {
              moduleNode.add(new DefaultMutableTreeNode(new MyUserObject(false, out.next())));
            }
          }
        }
      },
      AnalysisScopeLocalize.updateModuleTreeProgressTitle().get(),
      true,
      myProject
    );
    sortSubTree(root);
    myLeftTreeModel.reload();
  }

  private static void sortSubTree(final DefaultMutableTreeNode root) {
    TreeUtil.sort(root, (o1, o2) -> {
      DefaultMutableTreeNode node1 = (DefaultMutableTreeNode)o1;
      DefaultMutableTreeNode node2 = (DefaultMutableTreeNode)o2;
      if (!(node1.getUserObject() instanceof MyUserObject)){
        return 1;
      }
      else if (!(node2.getUserObject() instanceof MyUserObject)){
        return -1;
      }
      return (node1.getUserObject().toString().compareToIgnoreCase(node2.getUserObject().toString()));
    });
  }

  private void selectCycleUpward(final DefaultMutableTreeNode selection){
    ArrayList<DefaultMutableTreeNode> selectionNodes = new ArrayList<>();
    selectionNodes.add(selection);
    DefaultMutableTreeNode current = (DefaultMutableTreeNode)selection.getParent();
    boolean flag = false;
    while (current != null && current.getUserObject() != null){
      if (current.getUserObject().equals(selection.getUserObject())){
        flag = true;
        selectionNodes.add(current);
        break;
      }
      selectionNodes.add(current);
      current = (DefaultMutableTreeNode)current.getParent();
    }
    if (flag){
      for (DefaultMutableTreeNode node : selectionNodes) {
        ((MyUserObject)node.getUserObject()).setInCycle(true);
      }
    }
    if (current != null) current = (DefaultMutableTreeNode)current.getParent();
    while (current != null) {
      final Object userObject = current.getUserObject();
      if (userObject instanceof MyUserObject) {
        ((MyUserObject)userObject).setInCycle(false);
      }
      current = (DefaultMutableTreeNode)current.getParent();
    }
    myLeftTree.repaint();
  }

  private void initLeftTree(){
    //noinspection HardCodedStringLiteral
    final DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
    myLeftTreeModel = new DefaultTreeModel(root);
    initLeftTreeModel();
    myLeftTree = new Tree(myLeftTreeModel);
    initTree(myLeftTree, false);

    myLeftTree.addTreeExpansionListener(new TreeExpansionListener() {
      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
      }

      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        final DefaultMutableTreeNode expandedNode = (DefaultMutableTreeNode)event.getPath().getLastPathComponent();
        for(int i = 0; i < expandedNode.getChildCount(); i++){
          DefaultMutableTreeNode child = (DefaultMutableTreeNode)expandedNode.getChildAt(i);
          if (child.getChildCount() == 0){
            Module module = ((MyUserObject)child.getUserObject()).getModule();
            final Iterator<Module> out = myModulesGraph.getOut(module);
            while (out.hasNext()) {
              final Module nextModule = out.next();
              child.add(new DefaultMutableTreeNode(new MyUserObject(false, nextModule)));
            }
            sortSubTree(child);
          }
        }
      }
    });

    myLeftTree.addTreeSelectionListener(e -> {
      final TreePath selectionPath = myLeftTree.getSelectionPath();
      if (selectionPath != null) {

        myPathField.setText(StringUtil.join(selectionPath.getPath(), o -> {
          final Object userObject = ((DefaultMutableTreeNode)o).getUserObject();
          if (userObject instanceof MyUserObject) {
            return ((MyUserObject)userObject).getModule().getName();
          }
          return "";
        }, ":"));

        final DefaultMutableTreeNode selection = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
        if (selection != null){
          TreeUtil.traverseDepth(selection, node -> {
            DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)node;
            if (treeNode.getUserObject() instanceof MyUserObject){
              ((MyUserObject)treeNode.getUserObject()).setInCycle(false);
            }
            return true;
          });
          selectCycleUpward(selection);
          buildRightTree(((MyUserObject)selection.getUserObject()).getModule());
        }
      }
    });
    TreeUtil.selectFirstNode(myLeftTree);
  }

  private static ActionGroup createTreePopupActions(final boolean isRightTree, final Tree tree) {
    DefaultActionGroup group = new DefaultActionGroup();
    final TreeExpander treeExpander = new TreeExpander() {
      @Override
      public void expandAll() {
        TreeUtil.expandAll(tree);
      }

      @Override
      public boolean canExpand() {
        return isRightTree;
      }

      @Override
      public void collapseAll() {
        TreeUtil.collapseAll(tree, 3);
      }

      @Override
      public boolean canCollapse() {
        return true;
      }
    };

    final CommonActionsManager actionManager = CommonActionsManager.getInstance();
    if (isRightTree){
      group.add(actionManager.createExpandAllAction(treeExpander, tree));
    }
    group.add(actionManager.createCollapseAllAction(treeExpander, tree));
    final ActionManager globalActionManager = ActionManager.getInstance();
    group.add(globalActionManager.getAction(IdeActions.ACTION_EDIT_SOURCE));
    group.add(AnSeparator.getInstance());
    group.add(globalActionManager.getAction(IdeActions.ACTION_ANALYZE_DEPENDENCIES));
    group.add(globalActionManager.getAction(IdeActions.ACTION_ANALYZE_BACK_DEPENDENCIES));
    //non exists in platform group.add(globalActionManager.getAction(IdeActions.ACTION_ANALYZE_CYCLIC_DEPENDENCIES));
    return group;
  }

  private static void initTree(Tree tree, boolean isRightTree) {
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setCellRenderer(new MyTreeCellRenderer());
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(tree);

    TreeUtil.installActions(tree);
    new TreeSpeedSearch(tree, o -> o.getLastPathComponent().toString(), true);
    PopupHandler.installUnknownPopupHandler(tree, createTreePopupActions(isRightTree, tree), ActionManager.getInstance());
  }


  private Graph<Module> buildGraph() {
    final Graph<Module> graph = ModuleManager.getInstance(myProject).moduleGraph();
    if (isForwardDirection()) {
      return graph;
    }
    else {
      return GraphAlgorithms.getInstance().invertEdgeDirections(graph);
    }
  }

  public void setContent(final Content content) {
    myContent = content;
  }

  @Override
  public void beforeRootsChange(ModuleRootEvent event) {
  }

  @Override
  public void rootsChanged(ModuleRootEvent event) {
    initLeftTreeModel();
    TreeUtil.selectFirstNode(myLeftTree);
  }

  private static class MyUserObject implements NavigatableWithText{
    private boolean myInCycle;
    private final Module myModule;

    public MyUserObject(final boolean inCycle, final Module module) {
      myInCycle = inCycle;
      myModule = module;
    }

    public boolean isInCycle() {
      return myInCycle;
    }

    public void setInCycle(final boolean inCycle) {
      myInCycle = inCycle;
    }

    public Module getModule() {
      return myModule;
    }

    public boolean equals(Object object) {
      return object instanceof MyUserObject && myModule.equals(((MyUserObject)object).getModule());
    }

    public int hashCode() {
      return myModule.hashCode();
    }

    public String toString() {
      return myModule.getName();
    }

    @Override
    public void navigate(boolean requestFocus) {
      ProjectSettingsService.getInstance(myModule.getProject()).openModuleSettings(myModule);
    }

    @Override
    public boolean canNavigate() {
      return myModule != null && !myModule.isDisposed();
    }

    @Override
    public boolean canNavigateToSource() {
      return false;
    }

    @Override
    public String getNavigateActionText(boolean focusEditor) {
      return "Open Module Settings";
    }
  }

  private static class MyTreePanel extends JPanel implements DataProvider {
    private final Tree myTree;
    private final Project myProject;
    public MyTreePanel(final Tree tree, Project project) {
      super(new BorderLayout());
      myTree = tree;
      myProject = project;
      add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
    }

    @Override
    public Object getData(@Nonnull Key dataId) {
      if (Project.KEY == dataId){
        return myProject;
      }
      if (LangDataKeys.MODULE_CONTEXT == dataId){
        final TreePath selectionPath = myTree.getLeadSelectionPath();
        if (selectionPath != null && selectionPath.getLastPathComponent() instanceof DefaultMutableTreeNode){
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
          if (node.getUserObject() instanceof MyUserObject){
            return ((MyUserObject)node.getUserObject()).getModule();
          }
        }
      }
      if (HelpManager.HELP_ID == dataId) {
        return ourHelpID;
      }
      if (Navigatable.KEY == dataId) {
        final TreePath selectionPath = myTree.getLeadSelectionPath();
        if (selectionPath != null && selectionPath.getLastPathComponent() instanceof DefaultMutableTreeNode){
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
          if (node.getUserObject() instanceof MyUserObject){
            return node.getUserObject();
          }
        }
      }
      return null;
    }
  }
   private static class MyTreeCellRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(
    JTree tree,
    Object value,
    boolean selected,
    boolean expanded,
    boolean leaf,
    int row,
    boolean hasFocus
  ){
      final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
      if (!(userObject instanceof MyUserObject)){
        if (userObject != null){
          append(userObject.toString(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }
        return;
      }
      MyUserObject node = (MyUserObject)userObject;
      Module module = node.getModule();
      setIcon(AllIcons.Nodes.Module);
      if (node.isInCycle()){
        append(module.getName(), SimpleTextAttributes.ERROR_ATTRIBUTES);
      } else {
        append(module.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
  }
}
