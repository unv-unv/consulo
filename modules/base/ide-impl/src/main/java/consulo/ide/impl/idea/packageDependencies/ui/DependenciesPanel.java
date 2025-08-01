/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package consulo.ide.impl.idea.packageDependencies.ui;

import consulo.application.HelpManager;
import consulo.application.dumb.DumbAware;
import consulo.application.progress.ProgressManager;
import consulo.content.scope.NamedScope;
import consulo.content.scope.PackageSet;
import consulo.dataContext.DataProvider;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.ide.impl.idea.analysis.PerformAnalysisInBackgroundOption;
import consulo.ide.impl.idea.openapi.util.JDOMUtil;
import consulo.ide.impl.idea.packageDependencies.*;
import consulo.ide.impl.idea.packageDependencies.actions.AnalyzeDependenciesHandler;
import consulo.ide.impl.idea.packageDependencies.actions.BackwardDependenciesHandler;
import consulo.ide.impl.idea.xml.util.XmlStringUtil;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.language.editor.packageDependency.DependencyRule;
import consulo.language.editor.packageDependency.DependencyValidationManager;
import consulo.language.editor.scope.AnalysisScope;
import consulo.language.editor.scope.localize.AnalysisScopeLocalize;
import consulo.language.editor.ui.awt.HintUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiManager;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.ModuleManager;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.navigation.Navigatable;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.project.macro.ProjectPathMacroManager;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.action.ComboBoxAction;
import consulo.ui.ex.awt.speedSearch.TreeSpeedSearch;
import consulo.ui.ex.awt.tree.ColoredTreeCellRenderer;
import consulo.ui.ex.awt.tree.SmartExpander;
import consulo.ui.ex.awt.tree.Tree;
import consulo.ui.ex.awt.tree.TreeUtil;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.usage.localize.UsageLocalize;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.util.lang.SystemProperties;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jdom.Document;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;

public class DependenciesPanel extends JPanel implements Disposable, DataProvider {
    private final Map<PsiFile, Set<PsiFile>> myDependencies;
    private Map<PsiFile, Map<DependencyRule, Set<PsiFile>>> myIllegalDependencies;
    private final MyTree myLeftTree = new MyTree();
    private final MyTree myRightTree = new MyTree();
    private final DependenciesUsagesPanel myUsagesPanel;

    private static final HashSet<PsiFile> EMPTY_FILE_SET = new HashSet<>(0);
    private final TreeExpansionMonitor myRightTreeExpansionMonitor;
    private final TreeExpansionMonitor myLeftTreeExpansionMonitor;

    private final Marker myRightTreeMarker;
    private final Marker myLeftTreeMarker;
    private Set<PsiFile> myIllegalsInRightTree = new HashSet<>();

    private final Project myProject;
    private List<DependenciesBuilder> myBuilders;
    private final Set<PsiFile> myExcluded;
    private Content myContent;
    private final DependencyPanelSettings mySettings = new DependencyPanelSettings();
    private static final Logger LOG = Logger.getInstance(DependenciesPanel.class);

    private final boolean myForward;
    private final AnalysisScope myScopeOfInterest;
    private final int myTransitiveBorder;

    public DependenciesPanel(Project project, DependenciesBuilder builder) {
        this(project, Collections.singletonList(builder), new HashSet<>());
    }

    public DependenciesPanel(Project project, List<DependenciesBuilder> builders, Set<PsiFile> excluded) {
        super(new BorderLayout());
        myBuilders = builders;
        myExcluded = excluded;
        DependenciesBuilder main = myBuilders.get(0);
        myForward = !main.isBackward();
        myScopeOfInterest = main.getScopeOfInterest();
        myTransitiveBorder = main.getTransitiveBorder();
        myDependencies = new HashMap<>();
        myIllegalDependencies = new HashMap<>();
        for (DependenciesBuilder builder : builders) {
            myDependencies.putAll(builder.getDependencies());
            myIllegalDependencies.putAll(builder.getIllegalDependencies());
        }
        exclude(excluded);
        myProject = project;
        myUsagesPanel = new DependenciesUsagesPanel(myProject, myBuilders);
        Disposer.register(this, myUsagesPanel);

        Splitter treeSplitter = new Splitter();
        Disposer.register(this, treeSplitter::dispose);
        treeSplitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myLeftTree));
        treeSplitter.setSecondComponent(ScrollPaneFactory.createScrollPane(myRightTree));

        Splitter splitter = new Splitter(true);
        Disposer.register(this, splitter::dispose);
        splitter.setFirstComponent(treeSplitter);
        splitter.setSecondComponent(myUsagesPanel);
        add(splitter, BorderLayout.CENTER);
        add(createToolbar(), BorderLayout.NORTH);

        myRightTreeExpansionMonitor = PackageTreeExpansionMonitor.install(myRightTree, myProject);
        myLeftTreeExpansionMonitor = PackageTreeExpansionMonitor.install(myLeftTree, myProject);

        myRightTreeMarker = file -> myIllegalsInRightTree.contains(file);
        myLeftTreeMarker = file -> myIllegalDependencies.containsKey(file);

        updateLeftTreeModel();
        updateRightTreeModel();

        myLeftTree.getSelectionModel().addTreeSelectionListener(e -> {
            updateRightTreeModel();
            StringBuffer denyRules = new StringBuffer();
            StringBuffer allowRules = new StringBuffer();
            TreePath[] paths = myLeftTree.getSelectionPaths();
            if (paths == null) {
                return;
            }
            for (TreePath path : paths) {
                PackageDependenciesNode selectedNode = (PackageDependenciesNode)path.getLastPathComponent();
                traverseToLeaves(selectedNode, denyRules, allowRules);
            }
        });

        myRightTree.getSelectionModel().addTreeSelectionListener(e -> SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                Set<PsiFile> searchIn = getSelectedScope(myLeftTree);
                Set<PsiFile> searchFor = getSelectedScope(myRightTree);
                if (searchIn.isEmpty() || searchFor.isEmpty()) {
                    myUsagesPanel.setToInitialPosition();
                    //todo do not show too many usages
                    processDependencies(
                        searchIn,
                        searchFor,
                        path -> {
                            searchFor.add(path.get(1));
                            return true;
                        }
                    );
                }
                else {
                    myUsagesPanel.findUsages(searchIn, searchFor);
                }
            }
        }));

        initTree(myLeftTree, false);
        initTree(myRightTree, true);

        if (builders.size() == 1) {
            AnalysisScope scope = builders.get(0).getScope();
            if (scope.getScopeType() == AnalysisScope.FILE) {
                Set<PsiFile> oneFileSet = myDependencies.keySet();
                if (oneFileSet.size() == 1) {
                    selectElementInLeftTree(oneFileSet.iterator().next());
                    return;
                }
            }
        }
        TreeUtil.selectFirstNode(myLeftTree);
    }

    private void processDependencies(Set<PsiFile> searchIn, Set<PsiFile> searchFor, Predicate<List<PsiFile>> processor) {
        if (myTransitiveBorder == 0) {
            return;
        }
        Set<PsiFile> initialSearchFor = new HashSet<>(searchFor);
        for (DependenciesBuilder builder : myBuilders) {
            for (PsiFile from : searchIn) {
                for (PsiFile to : initialSearchFor) {
                    List<List<PsiFile>> paths = builder.findPaths(from, to);
                    Collections.sort(paths, (p1, p2) -> p1.size() - p2.size());
                    for (List<PsiFile> path : paths) {
                        if (!path.isEmpty()) {
                            path.add(0, from);
                            path.add(to);
                            if (!processor.test(path)) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private void exclude(Set<PsiFile> excluded) {
        for (PsiFile psiFile : excluded) {
            myDependencies.remove(psiFile);
            myIllegalDependencies.remove(psiFile);
        }
    }

    private void traverseToLeaves(PackageDependenciesNode treeNode, StringBuffer denyRules, StringBuffer allowRules) {
        Enumeration enumeration = treeNode.breadthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
            PsiElement childPsiElement = ((PackageDependenciesNode)enumeration.nextElement()).getPsiElement();
            if (myIllegalDependencies.containsKey(childPsiElement)) {
                Map<DependencyRule, Set<PsiFile>> illegalDeps = myIllegalDependencies.get(childPsiElement);
                for (DependencyRule rule : illegalDeps.keySet()) {
                    if (rule.isDenyRule()) {
                        if (denyRules.indexOf(rule.getDisplayText()) == -1) {
                            denyRules.append(rule.getDisplayText());
                            denyRules.append("\n");
                        }
                    }
                    else {
                        if (allowRules.indexOf(rule.getDisplayText()) == -1) {
                            allowRules.append(rule.getDisplayText());
                            allowRules.append("\n");
                        }
                    }
                }
            }
        }
    }

    @RequiredUIAccess
    private JComponent createToolbar() {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new CloseAction());
        group.add(new RerunAction(this));
        group.add(new FlattenPackagesAction());
        group.add(new ShowFilesAction());
        if (ModuleManager.getInstance(myProject).getModules().length > 1) {
            group.add(new ShowModulesAction());
            group.add(new ShowModuleGroupsAction());
        }
        group.add(new GroupByScopeTypeAction());
        //group.add(new GroupByFilesAction());
        group.add(new FilterLegalsAction());
        group.add(new MarkAsIllegalAction());
        group.add(new ChooseScopeTypeAction());
        group.add(new EditDependencyRulesAction());
        group.add(CommonActionsManager.getInstance().createExportToTextFileAction(new DependenciesExporterToTextFile()));
        group.add(new ContextHelpAction("dependency.viewer.tool.window"));

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
        return toolbar.getComponent();
    }

    private void rebuild() {
        myIllegalDependencies = new HashMap<>();
        for (DependenciesBuilder builder : myBuilders) {
            myIllegalDependencies.putAll(builder.getIllegalDependencies());
        }
        updateLeftTreeModel();
        updateRightTreeModel();
    }

    private void initTree(MyTree tree, boolean isRightTree) {
        tree.setCellRenderer(new MyTreeCellRenderer());
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        UIUtil.setLineStyleAngled(tree);

        TreeUtil.installActions(tree);
        SmartExpander.installOn(tree);
        EditSourceOnDoubleClickHandler.install(tree);
        new TreeSpeedSearch(tree);

        PopupHandler.installUnknownPopupHandler(tree, createTreePopupActions(isRightTree), ActionManager.getInstance());
    }

    private void updateRightTreeModel() {
        Set<PsiFile> deps = new HashSet<>();
        Set<PsiFile> scope = getSelectedScope(myLeftTree);
        myIllegalsInRightTree = new HashSet<>();
        for (PsiFile psiFile : scope) {
            Map<DependencyRule, Set<PsiFile>> illegalDeps = myIllegalDependencies.get(psiFile);
            if (illegalDeps != null) {
                for (DependencyRule rule : illegalDeps.keySet()) {
                    myIllegalsInRightTree.addAll(illegalDeps.get(rule));
                }
            }
            Set<PsiFile> psiFiles = myDependencies.get(psiFile);
            if (psiFiles != null) {
                for (PsiFile file : psiFiles) {
                    if (file != null && file.isValid()) {
                        deps.add(file);
                    }
                }
            }
        }
        deps.removeAll(scope);
        myRightTreeExpansionMonitor.freeze();
        myRightTree.setModel(buildTreeModel(deps, myRightTreeMarker));
        myRightTreeExpansionMonitor.restore();
        expandFirstLevel(myRightTree);
    }

    @RequiredUIAccess
    private ActionGroup createTreePopupActions(boolean isRightTree) {
        DefaultActionGroup group = new DefaultActionGroup();
        ActionManager actionManager = ActionManager.getInstance();
        group.add(actionManager.getAction(IdeActions.ACTION_EDIT_SOURCE));
        group.add(actionManager.getAction(IdeActions.GROUP_VERSION_CONTROLS));

        if (isRightTree) {
            group.add(actionManager.getAction(IdeActions.GROUP_ANALYZE));
            group.add(new AddToScopeAction());
            group.add(new SelectInLeftTreeAction());
            group.add(new ShowDetailedInformationAction());
        }
        else {
            group.add(new RemoveFromScopeAction());
        }

        return group;
    }

    private TreeModel buildTreeModel(Set<PsiFile> deps, Marker marker) {
        return PatternDialectProvider.findById(mySettings.SCOPE_TYPE).createTreeModel(myProject, deps, marker, mySettings);
    }

    private void updateLeftTreeModel() {
        Set<PsiFile> psiFiles = myDependencies.keySet();
        myLeftTreeExpansionMonitor.freeze();
        myLeftTree.setModel(buildTreeModel(psiFiles, myLeftTreeMarker));
        myLeftTreeExpansionMonitor.restore();
        expandFirstLevel(myLeftTree);
    }

    private static void expandFirstLevel(Tree tree) {
        PackageDependenciesNode root = (PackageDependenciesNode)tree.getModel().getRoot();
        int count = root.getChildCount();
        if (count < 10) {
            for (int i = 0; i < count; i++) {
                PackageDependenciesNode child = (PackageDependenciesNode)root.getChildAt(i);
                expandNodeIfNotTooWide(tree, child);
            }
        }
    }

    private static void expandNodeIfNotTooWide(Tree tree, PackageDependenciesNode node) {
        int count = node.getChildCount();
        if (count > 5) {
            return;
        }
        //another level of nesting
        if (count == 1 && node.getChildAt(0).getChildCount() > 5) {
            return;
        }
        tree.expandPath(new TreePath(node.getPath()));
    }

    private Set<PsiFile> getSelectedScope(Tree tree) {
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null) {
            return EMPTY_FILE_SET;
        }
        Set<PsiFile> result = new HashSet<>();
        for (TreePath path : paths) {
            PackageDependenciesNode node = (PackageDependenciesNode)path.getLastPathComponent();
            node.fillFiles(result, !mySettings.UI_FLATTEN_PACKAGES);
        }
        return result;
    }

    public void setContent(Content content) {
        myContent = content;
    }

    public JTree getLeftTree() {
        return myLeftTree;
    }

    public JTree getRightTree() {
        return myRightTree;
    }

    @Override
    public void dispose() {
        FileTreeModelBuilder.clearCaches(myProject);
    }

    @Nullable
    @Override
    public Object getData(@Nonnull Key dataId) {
        if (PsiElement.KEY == dataId) {
            PackageDependenciesNode selectedNode = myRightTree.getSelectedNode();
            if (selectedNode != null) {
                PsiElement element = selectedNode.getPsiElement();
                return element != null && element.isValid() ? element : null;
            }
        }
        if (HelpManager.HELP_ID == dataId) {
            return "dependency.viewer.tool.window";
        }
        return null;
    }

    private static class MyTreeCellRenderer extends ColoredTreeCellRenderer {
        @Override
        public void customizeCellRenderer(
            @Nonnull JTree tree,
            Object value,
            boolean selected,
            boolean expanded,
            boolean leaf,
            int row,
            boolean hasFocus
        ) {
            PackageDependenciesNode node = (PackageDependenciesNode)value;
            if (node.isValid()) {
                setIcon(node.getIcon());
            }
            else {
                append(UsageLocalize.nodeInvalid() + " ", SimpleTextAttributes.ERROR_ATTRIBUTES);
            }
            append(
                node.toString(),
                node.hasMarked() && !selected ? SimpleTextAttributes.ERROR_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES
            );
            append(node.getPresentableFilesCount(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
    }

    private final class CloseAction extends AnAction implements DumbAware {
        public CloseAction() {
            super(
                CommonLocalize.actionClose(),
                AnalysisScopeLocalize.actionCloseDependencyDescription(),
                PlatformIconGroup.actionsCancel()
            );
        }

        @RequiredUIAccess
        @Override
        public void actionPerformed(@Nonnull AnActionEvent e) {
            Disposer.dispose(myUsagesPanel);
            DependenciesToolWindow.getInstance(myProject).closeContent(myContent);
            mySettings.copyToApplicationDependencySettings();
        }
    }

    private final class FlattenPackagesAction extends ToggleAction {
        FlattenPackagesAction() {
            super(
                AnalysisScopeLocalize.actionFlattenPackages(),
                AnalysisScopeLocalize.actionFlattenPackages(),
                PlatformIconGroup.objectbrowserFlattenpackages()
            );
        }

        @Override
        public boolean isSelected(@Nonnull AnActionEvent event) {
            return mySettings.UI_FLATTEN_PACKAGES;
        }

        @Override
        public void setSelected(@Nonnull AnActionEvent event, boolean flag) {
            DependencyUISettings.getInstance().UI_FLATTEN_PACKAGES = flag;
            mySettings.UI_FLATTEN_PACKAGES = flag;
            rebuild();
        }
    }

    private final class ShowFilesAction extends ToggleAction {
        ShowFilesAction() {
            super(
                AnalysisScopeLocalize.actionShowFiles(),
                AnalysisScopeLocalize.actionShowFilesDescription(),
                PlatformIconGroup.filetypesUnknown()
            );
        }

        @Override
        public boolean isSelected(@Nonnull AnActionEvent event) {
            return mySettings.UI_SHOW_FILES;
        }

        @Override
        public void setSelected(@Nonnull AnActionEvent event, boolean flag) {
            DependencyUISettings.getInstance().UI_SHOW_FILES = flag;
            mySettings.UI_SHOW_FILES = flag;
            if (!flag && myLeftTree.getSelectionPath() != null
                && myLeftTree.getSelectionPath().getLastPathComponent() instanceof FileNode) {
                TreeUtil.selectPath(myLeftTree, myLeftTree.getSelectionPath().getParentPath());
            }
            rebuild();
        }
    }

  /*private final class GroupByFilesAction extends ToggleAction {
        private GroupByFilesAction() {
            super(
                IdeBundle.message("action.show.file.structure"),
                IdeBundle.message("action.description.show.file.structure"),
                IconLoader.getIcon("/objectBrowser/showGlobalInspections.png")
            );
        }

        public boolean isSelected(AnActionEvent e) {
            return mySettings.SCOPE_TYPE;
        }

        public void setSelected(AnActionEvent e, boolean state) {
            mySettings.SCOPE_TYPE = state;
            mySettings.copyToApplicationDependencySettings();
            rebuild();
        }
    }*/

    private final class ShowModulesAction extends ToggleAction {
        ShowModulesAction() {
            super(
                AnalysisScopeLocalize.actionShowModules(),
                AnalysisScopeLocalize.actionShowModulesDescription(),
                PlatformIconGroup.actionsGroupbymodule()
            );
        }

        @Override
        public boolean isSelected(@Nonnull AnActionEvent event) {
            return mySettings.UI_SHOW_MODULES;
        }

        @Override
        public void setSelected(@Nonnull AnActionEvent event, boolean flag) {
            DependencyUISettings.getInstance().UI_SHOW_MODULES = flag;
            mySettings.UI_SHOW_MODULES = flag;
            rebuild();
        }
    }

    private final class ShowModuleGroupsAction extends ToggleAction {
        ShowModuleGroupsAction() {
            super(
                LocalizeValue.localizeTODO("Show module groups"),
                LocalizeValue.localizeTODO("Show module groups"),
                PlatformIconGroup.actionsGroupbymodulegroup()
            );
        }

        @Override
        public boolean isSelected(@Nonnull AnActionEvent event) {
            return mySettings.UI_SHOW_MODULE_GROUPS;
        }

        @Override
        public void setSelected(@Nonnull AnActionEvent event, boolean flag) {
            DependencyUISettings.getInstance().UI_SHOW_MODULE_GROUPS = flag;
            mySettings.UI_SHOW_MODULE_GROUPS = flag;
            rebuild();
        }

        @Override
        public void update(@Nonnull AnActionEvent e) {
            super.update(e);
            e.getPresentation().setEnabled(mySettings.UI_SHOW_MODULES);
        }
    }

    private final class GroupByScopeTypeAction extends ToggleAction {
        GroupByScopeTypeAction() {
            super(
                AnalysisScopeLocalize.actionGroupByScopeType(),
                AnalysisScopeLocalize.actionGroupByScopeTypeDescription(),
                PlatformIconGroup.actionsGroupbytestproduction()
            );
        }

        @Override
        public boolean isSelected(@Nonnull AnActionEvent event) {
            return mySettings.UI_GROUP_BY_SCOPE_TYPE;
        }

        @Override
        public void setSelected(@Nonnull AnActionEvent event, boolean flag) {
            DependencyUISettings.getInstance().UI_GROUP_BY_SCOPE_TYPE = flag;
            mySettings.UI_GROUP_BY_SCOPE_TYPE = flag;
            rebuild();
        }
    }


    private final class FilterLegalsAction extends ToggleAction {
        FilterLegalsAction() {
            super(
                AnalysisScopeLocalize.actionShowIllegalsOnly(),
                AnalysisScopeLocalize.actionShowIllegalsOnlyDescription(),
                PlatformIconGroup.generalFilter()
            );
        }

        @Override
        public boolean isSelected(@Nonnull AnActionEvent event) {
            return mySettings.UI_FILTER_LEGALS;
        }

        @Override
        public void setSelected(@Nonnull AnActionEvent event, boolean flag) {
            DependencyUISettings.getInstance().UI_FILTER_LEGALS = flag;
            mySettings.UI_FILTER_LEGALS = flag;
            rebuild();
        }
    }

    private final class EditDependencyRulesAction extends AnAction {
        public EditDependencyRulesAction() {
            super(
                AnalysisScopeLocalize.actionEditRules(),
                AnalysisScopeLocalize.actionEditRulesDescription(),
                PlatformIconGroup.generalSettings()
            );
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(@Nonnull AnActionEvent e) {
            ShowSettingsUtil.getInstance()
                .editConfigurable(DependenciesPanel.this, new DependencyConfigurable(myProject))
                .doWhenDone(DependenciesPanel.this::rebuild);
        }
    }


    private class DependenciesExporterToTextFile implements ExporterToTextFile {

        @Override
        public JComponent getSettingsEditor() {
            return null;
        }

        @Override
        public void addSettingsChangedListener(ChangeListener listener) throws TooManyListenersException {
        }

        @Override
        public void removeSettingsChangedListener(ChangeListener listener) {
        }

        @Nonnull
        @Override
        public String getReportText() {
            Element rootElement = new Element("root");
            rootElement.setAttribute("isBackward", String.valueOf(!myForward));
            List<PsiFile> files = new ArrayList<>(myDependencies.keySet());
            Collections.sort(files, (f1, f2) -> {
                VirtualFile virtualFile1 = f1.getVirtualFile();
                VirtualFile virtualFile2 = f2.getVirtualFile();
                if (virtualFile1 != null && virtualFile2 != null) {
                    return virtualFile1.getPath().compareToIgnoreCase(virtualFile2.getPath());
                }
                return 0;
            });
            for (PsiFile file : files) {
                Element fileElement = new Element("file");
                fileElement.setAttribute("path", file.getVirtualFile().getPath());
                for (PsiFile dep : myDependencies.get(file)) {
                    Element depElement = new Element("dependency");
                    depElement.setAttribute("path", dep.getVirtualFile().getPath());
                    fileElement.addContent(depElement);
                }
                rootElement.addContent(fileElement);
            }
            ProjectPathMacroManager.getInstance(myProject).collapsePaths(rootElement);
            return JDOMUtil.writeDocument(new Document(rootElement), SystemProperties.getLineSeparator());
        }

        @Nonnull
        @Override
        public String getDefaultFilePath() {
            return "";
        }

        @Override
        public void exportedTo(@Nonnull String filePath) {
        }

        @Override
        public boolean canExport() {
            return true;
        }
    }


    private class RerunAction extends AnAction {
        public RerunAction(JComponent comp) {
            super(CommonLocalize.actionRerun(), AnalysisScopeLocalize.actionRerunDependency(), PlatformIconGroup.actionsRerun());
            registerCustomShortcutSet(CommonShortcuts.getRerun(), comp);
        }

        @Override
        public void update(@Nonnull AnActionEvent e) {
            boolean enabled = true;
            for (DependenciesBuilder builder : myBuilders) {
                enabled &= builder.getScope().isValid();
            }
            e.getPresentation().setEnabled(enabled);
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(@Nonnull AnActionEvent e) {
            DependenciesToolWindow.getInstance(myProject).closeContent(myContent);
            mySettings.copyToApplicationDependencySettings();
            SwingUtilities.invokeLater(() -> {
                List<AnalysisScope> scopes = new ArrayList<>();
                for (DependenciesBuilder builder : myBuilders) {
                    AnalysisScope scope = builder.getScope();
                    scope.invalidate();
                    scopes.add(scope);
                }
                if (!myForward) {
                    new BackwardDependenciesHandler(myProject, scopes, myScopeOfInterest, myExcluded).analyze();
                }
                else {
                    new AnalyzeDependenciesHandler(myProject, scopes, myTransitiveBorder, myExcluded).analyze();
                }
            });
        }
    }

    private static class MyTree extends Tree implements DataProvider {
        @Override
        public Object getData(@Nonnull Key<?> dataId) {
            PackageDependenciesNode node = getSelectedNode();
            if (Navigatable.KEY == dataId) {
                return node;
            }
            if (PsiElement.KEY == dataId && node != null) {
                PsiElement element = node.getPsiElement();
                return element != null && element.isValid() ? element : null;
            }
            return null;
        }

        @Nullable
        public PackageDependenciesNode getSelectedNode() {
            TreePath[] paths = getSelectionPaths();
            if (paths == null || paths.length != 1) {
                return null;
            }
            return (PackageDependenciesNode)paths[0].getLastPathComponent();
        }
    }

    private class ShowDetailedInformationAction extends AnAction {
        private ShowDetailedInformationAction() {
            super(LocalizeValue.localizeTODO("Show indirect dependencies"));
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(AnActionEvent e) {
            String delim = "&nbsp;-&gt;&nbsp;";
            StringBuffer buf = new StringBuffer();
            processDependencies(
                getSelectedScope(myLeftTree),
                getSelectedScope(myRightTree),
                path -> {
                    if (buf.length() > 0) {
                        buf.append("<br>");
                    }
                    buf.append(StringUtil.join(path, PsiFileSystemItem::getName, delim));
                    return true;
                }
            );
            JEditorPane pane = new JEditorPane(UIUtil.HTML_MIME, XmlStringUtil.wrapInHtml(buf));
            pane.setForeground(JBColor.foreground());
            pane.setBackground(HintUtil.INFORMATION_COLOR);
            pane.setOpaque(true);
            JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(pane);
            Dimension dimension = pane.getPreferredSize();
            scrollPane.setMinimumSize(new Dimension(dimension.width, dimension.height + 20));
            scrollPane.setPreferredSize(new Dimension(dimension.width, dimension.height + 20));
            JBPopupFactory.getInstance().createComponentPopupBuilder(scrollPane, pane).setTitle("Dependencies")
                .setMovable(true).createPopup().showInBestPositionFor(e.getDataContext());
        }

        @Override
        public void update(@Nonnull AnActionEvent e) {
            boolean[] direct = new boolean[]{true};
            processDependencies(getSelectedScope(myLeftTree), getSelectedScope(myRightTree), path -> {
                direct[0] = false;
                return false;
            });
            e.getPresentation().setEnabled(!direct[0]);
        }
    }

    private class RemoveFromScopeAction extends AnAction {
        private RemoveFromScopeAction() {
            super(LocalizeValue.localizeTODO("Remove from scope"));
        }

        @Override
        public void update(@Nonnull AnActionEvent e) {
            super.update(e);
            e.getPresentation().setEnabled(!getSelectedScope(myLeftTree).isEmpty());
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(@Nonnull AnActionEvent e) {
            Set<PsiFile> selectedScope = getSelectedScope(myLeftTree);
            exclude(selectedScope);
            myExcluded.addAll(selectedScope);
            TreePath[] paths = myLeftTree.getSelectionPaths();
            for (TreePath path : paths) {
                TreeUtil.removeLastPathComponent(myLeftTree, path);
            }
        }
    }

    private class AddToScopeAction extends AnAction {
        private AddToScopeAction() {
            super(LocalizeValue.localizeTODO("Add to scope"));
        }

        @Override
        public void update(@Nonnull AnActionEvent e) {
            super.update(e);
            e.getPresentation().setEnabled(getScope() != null);
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(@Nonnull AnActionEvent e) {
            AnalysisScope scope = getScope();
            LOG.assertTrue(scope != null);
            DependenciesBuilder builder;
            if (!myForward) {
                builder = new BackwardDependenciesBuilder(myProject, scope, myScopeOfInterest);
            }
            else {
                builder = new ForwardDependenciesBuilder(myProject, scope, myTransitiveBorder);
            }
            ProgressManager.getInstance().runProcessWithProgressAsynchronously(
                myProject,
                AnalysisScopeLocalize.packageDependenciesProgressTitle().get(),
                builder::analyze,
                () -> {
                    myBuilders.add(builder);
                    myDependencies.putAll(builder.getDependencies());
                    myIllegalDependencies.putAll(builder.getIllegalDependencies());
                    exclude(myExcluded);
                    rebuild();
                },
                null,
                new PerformAnalysisInBackgroundOption(myProject)
            );
        }

        @Nullable
        private AnalysisScope getScope() {
            Set<PsiFile> selectedScope = getSelectedScope(myRightTree);
            Set<PsiFile> result = new HashSet<>();
            ((PackageDependenciesNode)myLeftTree.getModel().getRoot()).fillFiles(result, !mySettings.UI_FLATTEN_PACKAGES);
            selectedScope.removeAll(result);
            if (selectedScope.isEmpty()) {
                return null;
            }
            List<VirtualFile> files = new ArrayList<>();
            ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
            for (PsiFile psiFile : selectedScope) {
                VirtualFile file = psiFile.getVirtualFile();
                LOG.assertTrue(file != null);
                if (fileIndex.isInContent(file)) {
                    files.add(file);
                }
            }
            if (!files.isEmpty()) {
                return new AnalysisScope(myProject, files);
            }
            return null;
        }
    }

    private class SelectInLeftTreeAction extends AnAction {
        public SelectInLeftTreeAction() {
            super(
                AnalysisScopeLocalize.actionSelectInLeftTree(),
                AnalysisScopeLocalize.actionSelectInLeftTreeDescription(),
                null
            );
        }

        @Override
        public void update(@Nonnull AnActionEvent e) {
            PackageDependenciesNode node = myRightTree.getSelectedNode();
            e.getPresentation().setEnabled(node != null && node.canSelectInLeftTree(myDependencies));
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(@Nonnull AnActionEvent e) {
            PackageDependenciesNode node = myRightTree.getSelectedNode();
            if (node != null) {
                PsiElement elt = node.getPsiElement();
                if (elt != null) {
                    DependencyUISettings.getInstance().UI_FILTER_LEGALS = false;
                    mySettings.UI_FILTER_LEGALS = false;
                    selectElementInLeftTree(elt);

                }
            }
        }
    }

    private void selectElementInLeftTree(PsiElement elt) {
        PsiManager manager = PsiManager.getInstance(myProject);

        PackageDependenciesNode root = (PackageDependenciesNode)myLeftTree.getModel().getRoot();
        Enumeration enumeration = root.breadthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
            PackageDependenciesNode child = (PackageDependenciesNode)enumeration.nextElement();
            if (manager.areElementsEquivalent(child.getPsiElement(), elt)) {
                myLeftTree.setSelectionPath(new TreePath(((DefaultTreeModel)myLeftTree.getModel()).getPathToRoot(child)));
                break;
            }
        }
    }

    private class MarkAsIllegalAction extends AnAction {
        public MarkAsIllegalAction() {
            super(
                AnalysisScopeLocalize.markDependencyIllegalText(),
                AnalysisScopeLocalize.markDependencyIllegalText(),
                PlatformIconGroup.actionsLightning()
            );
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(@Nonnull AnActionEvent e) {
            PackageDependenciesNode leftNode = myLeftTree.getSelectedNode();
            PackageDependenciesNode rightNode = myRightTree.getSelectedNode();
            if (leftNode != null && rightNode != null) {
                boolean hasDirectDependencies = myTransitiveBorder == 0;
                if (myTransitiveBorder > 0) {
                    Set<PsiFile> searchIn = getSelectedScope(myLeftTree);
                    Set<PsiFile> searchFor = getSelectedScope(myRightTree);
                    for (DependenciesBuilder builder : myBuilders) {
                        if (hasDirectDependencies) {
                            break;
                        }
                        for (PsiFile from : searchIn) {
                            if (hasDirectDependencies) {
                                break;
                            }
                            for (PsiFile to : searchFor) {
                                if (hasDirectDependencies) {
                                    break;
                                }
                                List<List<PsiFile>> paths = builder.findPaths(from, to);
                                for (List<PsiFile> path : paths) {
                                    if (path.isEmpty()) {
                                        hasDirectDependencies = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                PatternDialectProvider provider = PatternDialectProvider.findById(mySettings.SCOPE_TYPE);
                PackageSet leftPackageSet = provider.createPackageSet(leftNode, true);
                if (leftPackageSet == null) {
                    leftPackageSet = provider.createPackageSet(leftNode, false);
                }
                LOG.assertTrue(leftPackageSet != null);
                PackageSet rightPackageSet = provider.createPackageSet(rightNode, true);
                if (rightPackageSet == null) {
                    rightPackageSet = provider.createPackageSet(rightNode, false);
                }
                LOG.assertTrue(rightPackageSet != null);
                if (hasDirectDependencies) {
                    DependencyValidationManager.getInstance(myProject)
                        .addRule(new DependencyRule(
                            new NamedScope.UnnamedScope(leftPackageSet),
                            new NamedScope.UnnamedScope(rightPackageSet),
                            true
                        ));
                    rebuild();
                }
                else {
                    Messages.showErrorDialog(
                        DependenciesPanel.this,
                        "Rule was not added.\n There is no direct dependency between \'" + leftPackageSet.getText() + "\'" +
                            " and \'" + rightPackageSet.getText() + "\'",
                        AnalysisScopeLocalize.markDependencyIllegalText().get()
                    );
                }
            }
        }

        @Override
        public void update(@Nonnull AnActionEvent e) {
            Presentation presentation = e.getPresentation();
            presentation.setEnabled(false);
            PackageDependenciesNode leftNode = myLeftTree.getSelectedNode();
            PackageDependenciesNode rightNode = myRightTree.getSelectedNode();
            if (leftNode != null && rightNode != null) {
                PatternDialectProvider provider = PatternDialectProvider.findById(mySettings.SCOPE_TYPE);
                presentation.setEnabled(
                    (provider.createPackageSet(leftNode, true) != null || provider.createPackageSet(leftNode, false) != null)
                        && (provider.createPackageSet(rightNode, true) != null || provider.createPackageSet(rightNode, false) != null)
                );
            }
        }
    }

    private final class ChooseScopeTypeAction extends ComboBoxAction {
        @Nonnull
        @Override
        public DefaultActionGroup createPopupActionGroup(JComponent component) {
            DefaultActionGroup group = new DefaultActionGroup();
            for (PatternDialectProvider provider : PatternDialectProvider.EP_NAME.getExtensionList()) {
                group.add(new AnAction(provider.getDisplayName()) {
                    @Override
                    @RequiredUIAccess
                    public void actionPerformed(@Nonnull AnActionEvent e) {
                        mySettings.SCOPE_TYPE = provider.getId();
                        DependencyUISettings.getInstance().setScopeType(provider.getId());
                        rebuild();
                    }
                });
            }
            return group;
        }

        @Override
        public void update(@Nonnull AnActionEvent e) {
            super.update(e);
            PatternDialectProvider provider = PatternDialectProvider.findById(mySettings.SCOPE_TYPE);
            e.getPresentation().setText(provider.getDisplayName());
            e.getPresentation().setIcon(provider.getIcon());
        }
    }

    public static class DependencyPanelSettings {
        public boolean UI_FLATTEN_PACKAGES = true;
        public boolean UI_SHOW_FILES = false;
        public boolean UI_SHOW_MODULES = true;
        public boolean UI_SHOW_MODULE_GROUPS = true;
        public boolean UI_FILTER_LEGALS = false;
        public boolean UI_GROUP_BY_SCOPE_TYPE = true;
        public String SCOPE_TYPE;
        public boolean UI_COMPACT_EMPTY_MIDDLE_PACKAGES = true;
        public boolean UI_FILTER_OUT_OF_CYCLE_PACKAGES = true;

        public DependencyPanelSettings() {
            DependencyUISettings settings = DependencyUISettings.getInstance();
            UI_FLATTEN_PACKAGES = settings.UI_FLATTEN_PACKAGES;
            UI_SHOW_FILES = settings.UI_SHOW_FILES;
            UI_SHOW_MODULES = settings.UI_SHOW_MODULES;
            UI_SHOW_MODULE_GROUPS = settings.UI_SHOW_MODULE_GROUPS;
            UI_FILTER_LEGALS = settings.UI_FILTER_LEGALS;
            UI_GROUP_BY_SCOPE_TYPE = settings.UI_GROUP_BY_SCOPE_TYPE;
            SCOPE_TYPE = settings.getScopeType();
            UI_COMPACT_EMPTY_MIDDLE_PACKAGES = settings.UI_COMPACT_EMPTY_MIDDLE_PACKAGES;
            UI_FILTER_OUT_OF_CYCLE_PACKAGES = settings.UI_FILTER_OUT_OF_CYCLE_PACKAGES;
        }

        public void copyToApplicationDependencySettings() {
            DependencyUISettings settings = DependencyUISettings.getInstance();
            settings.UI_FLATTEN_PACKAGES = UI_FLATTEN_PACKAGES;
            settings.UI_SHOW_FILES = UI_SHOW_FILES;
            settings.UI_SHOW_MODULES = UI_SHOW_MODULES;
            settings.UI_SHOW_MODULE_GROUPS = UI_SHOW_MODULE_GROUPS;
            settings.UI_FILTER_LEGALS = UI_FILTER_LEGALS;
            settings.UI_GROUP_BY_SCOPE_TYPE = UI_GROUP_BY_SCOPE_TYPE;
            settings.setScopeType(SCOPE_TYPE);
            settings.UI_COMPACT_EMPTY_MIDDLE_PACKAGES = UI_COMPACT_EMPTY_MIDDLE_PACKAGES;
            settings.UI_FILTER_OUT_OF_CYCLE_PACKAGES = UI_FILTER_OUT_OF_CYCLE_PACKAGES;
        }
    }
}
