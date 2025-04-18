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
package consulo.ide.impl.idea.ide.util.scopeChooser;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.application.progress.ProgressManager;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.component.ProcessCanceledException;
import consulo.configurable.ConfigurationException;
import consulo.content.scope.*;
import consulo.execution.ui.awt.RawCommandLineEditor;
import consulo.ide.impl.idea.packageDependencies.DependencyUISettings;
import consulo.ide.impl.idea.packageDependencies.ui.*;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.ex.awt.tree.SmartExpander;
import consulo.ide.impl.psi.search.scope.packageSet.IntersectionPackageSet;
import consulo.ide.impl.psi.search.scope.packageSet.UnionPackageSet;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.ide.localize.IdeLocalize;
import consulo.project.Project;
import consulo.ui.UIAccessScheduler;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.DarculaColors;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.PopupHandler;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.VerticalFlowLayout;
import consulo.ui.ex.awt.action.ComboBoxAction;
import consulo.ui.ex.awt.speedSearch.TreeSpeedSearch;
import consulo.ui.ex.awt.tree.ColoredTreeCellRenderer;
import consulo.ui.ex.awt.tree.Tree;
import consulo.ui.ex.awt.tree.TreeUtil;
import consulo.ui.ex.awt.update.UiNotifyConnector;
import consulo.ui.ex.update.Activatable;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ScopeEditorPanel {
    private JPanel myButtonsPanel;
    private RawCommandLineEditor myPatternField;
    private JPanel myTreeToolbar;
    private final Tree myPackageTree;
    private JPanel myPanel;
    private JPanel myTreePanel;
    private JLabel myMatchingCountLabel;
    private JPanel myLegendPanel;

    private final Project myProject;
    private final TreeExpansionMonitor myTreeExpansionMonitor;
    private final Marker myTreeMarker;
    private PackageSet myCurrentScope = null;
    private boolean myIsInUpdate = false;
    private String myErrorMessage;
    private Future<?> myUpdateAlarm = CompletableFuture.completedFuture(null);

    private JLabel myCaretPositionLabel;
    private int myCaretPosition = 0;
    private boolean myTextChanged = false;
    private JPanel myMatchingCountPanel;
    private JPanel myPositionPanel;
    private JLabel myRecursivelyIncluded;
    private JLabel myPartiallyIncluded;
    private PanelProgressIndicator myCurrentProgress;
    private NamedScopesHolder myHolder;

    public ScopeEditorPanel(Project project, NamedScopesHolder holder) {
        myProject = project;
        myHolder = holder;

        myPackageTree = new Tree(new RootNode(project));

        myButtonsPanel.add(createActionsPanel());

        myTreePanel.setLayout(new BorderLayout());
        myTreePanel.add(ScrollPaneFactory.createScrollPane(myPackageTree), BorderLayout.CENTER);

        myTreeToolbar.setLayout(new BorderLayout());
        myTreeToolbar.add(createTreeToolbar(), BorderLayout.WEST);

        myTreeExpansionMonitor = PackageTreeExpansionMonitor.install(myPackageTree, myProject);

        myTreeMarker = file -> myCurrentScope != null && myCurrentScope.contains(file, project, myHolder);

        myPatternField.setDialogCaption("Pattern");
        myPatternField.addValueListener(event -> onTextChange());

        //myPatternField.getTextField().addCaretListener(new CaretListener() {
        //  @Override
        //  public void caretUpdate(CaretEvent e) {
        //    myCaretPosition = e.getDot();
        //    updateCaretPositionText();
        //  }
        //});

        myPatternField.getTextField().addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                if (myErrorMessage != null) {
                    myPositionPanel.setVisible(true);
                    myPanel.revalidate();
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                myPositionPanel.setVisible(false);
                myPanel.revalidate();
            }
        });

        initTree(myPackageTree);
        new UiNotifyConnector(myPanel, new Activatable() {
            @Override
            public void showNotify() {
            }

            @Override
            public void hideNotify() {
                cancelCurrentProgress();
            }
        });
        myPartiallyIncluded.setBackground(MyTreeCellRenderer.PARTIAL_INCLUDED);
        myRecursivelyIncluded.setBackground(MyTreeCellRenderer.WHOLE_INCLUDED);
    }

    private void updateCaretPositionText() {
        if (myErrorMessage != null) {
            myCaretPositionLabel.setText(IdeLocalize.labelScopeEditorCaretPosition(myCaretPosition + 1).get());
        }
        else {
            myCaretPositionLabel.setText("");
        }
        myPositionPanel.setVisible(myErrorMessage != null);
        myCaretPositionLabel.setVisible(myErrorMessage != null);
        myPanel.revalidate();
    }

    public JPanel getPanel() {
        return myPanel;
    }

    public JPanel getTreePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(myTreePanel, BorderLayout.CENTER);
        panel.add(myLegendPanel, BorderLayout.SOUTH);
        return panel;
    }

    public JPanel getTreeToolbar() {
        return myTreeToolbar;
    }

    private void onTextChange() {
        if (!myIsInUpdate) {
            myUpdateAlarm.cancel(false);
            myTextChanged = true;
            String text = myPatternField.getText();
            myCurrentScope = new InvalidPackageSet(text);
            try {
                if (!StringUtil.isEmpty(text)) {
                    myCurrentScope = PackageSetFactory.getInstance().compile(text);
                }
                myErrorMessage = null;
            }
            catch (Exception e) {
                myErrorMessage = e.getMessage();
                showErrorMessage();
            }
            rebuild(false);
        }
        else if (!invalidScopeInside(myCurrentScope)) {
            myErrorMessage = null;
        }
    }

    private static boolean invalidScopeInside(PackageSet currentScope) {
        if (currentScope instanceof InvalidPackageSet) {
            return true;
        }
        if (currentScope instanceof UnionPackageSet unionPackageSet) {
            if (invalidScopeInside(unionPackageSet.getFirstSet()) || invalidScopeInside(unionPackageSet.getSecondSet())) {
                return true;
            }
        }
        if (currentScope instanceof IntersectionPackageSet intersectionPackageSet) {
            if (invalidScopeInside(intersectionPackageSet.getFirstSet()) || invalidScopeInside(intersectionPackageSet.getSecondSet())) {
                return true;
            }
        }
        return currentScope instanceof ComplementPackageSet complementPackageSet
            && invalidScopeInside(complementPackageSet.getComplementarySet());
    }

    private void showErrorMessage() {
        myMatchingCountLabel.setText(StringUtil.capitalize(myErrorMessage));
        myMatchingCountLabel.setForeground(JBColor.red);
        myMatchingCountLabel.setToolTipText(myErrorMessage);
    }

    private JComponent createActionsPanel() {
        JButton include = new JButton(IdeLocalize.buttonInclude().get());
        JButton includeRec = new JButton(IdeLocalize.buttonIncludeRecursively().get());
        JButton exclude = new JButton(IdeLocalize.buttonExclude().get());
        JButton excludeRec = new JButton(IdeLocalize.buttonExcludeRecursively().get());
        myPackageTree.getSelectionModel().addTreeSelectionListener(e -> {
            boolean recursiveEnabled = isButtonEnabled(true, e.getPaths(), e);
            includeRec.setEnabled(recursiveEnabled);
            excludeRec.setEnabled(recursiveEnabled);

            boolean nonRecursiveEnabled = isButtonEnabled(false, e.getPaths(), e);
            include.setEnabled(nonRecursiveEnabled);
            exclude.setEnabled(nonRecursiveEnabled);
        });

        JPanel buttonsPanel = new JPanel(new VerticalFlowLayout());
        buttonsPanel.add(include);
        buttonsPanel.add(includeRec);
        buttonsPanel.add(exclude);
        buttonsPanel.add(excludeRec);

        include.addActionListener(e -> includeSelected(false));
        includeRec.addActionListener(e -> includeSelected(true));
        exclude.addActionListener(e -> excludeSelected(false));
        excludeRec.addActionListener(e -> excludeSelected(true));

        return buttonsPanel;
    }

    static boolean isButtonEnabled(boolean rec, TreePath[] paths, TreeSelectionEvent e) {
        if (paths != null) {
            for (TreePath path : paths) {
                if (!e.isAddedPath(path)) {
                    continue;
                }
                PackageDependenciesNode node = (PackageDependenciesNode)path.getLastPathComponent();
                if (PatternDialectProvider.findById(DependencyUISettings.getInstance().getScopeType())
                    .createPackageSet(node, rec) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean isButtonEnabled(boolean rec) {
        TreePath[] paths = myPackageTree.getSelectionPaths();
        if (paths != null) {
            for (TreePath path : paths) {
                PackageDependenciesNode node = (PackageDependenciesNode)path.getLastPathComponent();
                if (PatternDialectProvider.findById(DependencyUISettings.getInstance().getScopeType())
                    .createPackageSet(node, rec) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private void excludeSelected(boolean recurse) {
        ArrayList<PackageSet> selected = getSelectedSets(recurse);
        if (selected == null || selected.isEmpty()) {
            return;
        }
        for (PackageSet set : selected) {
            if (myCurrentScope == null) {
                myCurrentScope = new ComplementPackageSet(set);
            }
            else if (myCurrentScope instanceof InvalidPackageSet) {
                myCurrentScope = StringUtil.isEmpty(myCurrentScope.getText())
                    ? new ComplementPackageSet(set)
                    : new IntersectionPackageSet(myCurrentScope, new ComplementPackageSet(set));
            }
            else {
                boolean[] append = {true};
                PackageSet simplifiedScope = processComplementaryScope(myCurrentScope, set, false, append);
                if (!append[0]) {
                    myCurrentScope = simplifiedScope;
                }
                else {
                    myCurrentScope = simplifiedScope != null ? new IntersectionPackageSet(
                        simplifiedScope,
                        new ComplementPackageSet(set)
                    ) : new ComplementPackageSet(set);
                }
            }
        }
        rebuild(true);
    }

    private void includeSelected(boolean recurse) {
        ArrayList<PackageSet> selected = getSelectedSets(recurse);
        if (selected == null || selected.isEmpty()) {
            return;
        }
        for (PackageSet set : selected) {
            if (myCurrentScope == null) {
                myCurrentScope = set;
            }
            else if (myCurrentScope instanceof InvalidPackageSet) {
                myCurrentScope = StringUtil.isEmpty(myCurrentScope.getText()) ? set : new UnionPackageSet(myCurrentScope, set);
            }
            else {
                boolean[] append = {true};
                PackageSet simplifiedScope = processComplementaryScope(myCurrentScope, set, true, append);
                if (!append[0]) {
                    myCurrentScope = simplifiedScope;
                }
                else {
                    myCurrentScope = simplifiedScope != null ? new UnionPackageSet(simplifiedScope, set) : set;
                }
            }
        }
        rebuild(true);
    }

    @Nullable
    static PackageSet processComplementaryScope(
        @Nonnull PackageSet current,
        PackageSet added,
        boolean checkComplementSet,
        boolean[] append
    ) {
        String text = added.getText();
        if (current instanceof ComplementPackageSet complementPackageSet &&
            Comparing.strEqual(complementPackageSet.getComplementarySet().getText(), text)) {
            if (checkComplementSet) {
                append[0] = false;
            }
            return null;
        }
        if (Comparing.strEqual(current.getText(), text)) {
            if (!checkComplementSet) {
                append[0] = false;
            }
            return null;
        }

        if (current instanceof UnionPackageSet unionPackageSet) {
            PackageSet left = processComplementaryScope(unionPackageSet.getFirstSet(), added, checkComplementSet, append);
            PackageSet right = processComplementaryScope(unionPackageSet.getSecondSet(), added, checkComplementSet, append);
            if (left == null) {
                return right;
            }
            if (right == null) {
                return left;
            }
            return new UnionPackageSet(left, right);
        }

        if (current instanceof IntersectionPackageSet intersectionPackageSet) {
            PackageSet left = processComplementaryScope(intersectionPackageSet.getFirstSet(), added, checkComplementSet, append);
            PackageSet right = processComplementaryScope(intersectionPackageSet.getSecondSet(), added, checkComplementSet, append);
            if (left == null) {
                return right;
            }
            if (right == null) {
                return left;
            }
            return new IntersectionPackageSet(left, right);
        }

        return current;
    }

    @Nullable
    private ArrayList<PackageSet> getSelectedSets(boolean recursively) {
        int[] rows = myPackageTree.getSelectionRows();
        if (rows == null) {
            return null;
        }
        ArrayList<PackageSet> result = new ArrayList<>();
        for (int row : rows) {
            PackageDependenciesNode node = (PackageDependenciesNode)myPackageTree.getPathForRow(row).getLastPathComponent();
            PackageSet set = PatternDialectProvider.findById(DependencyUISettings.getInstance().getScopeType())
                .createPackageSet(node, recursively);
            if (set != null) {
                result.add(set);
            }
        }
        return result;
    }

    @RequiredReadAction
    private JComponent createTreeToolbar() {
        DefaultActionGroup group = new DefaultActionGroup();
        Runnable update = () -> rebuild(true);
        group.add(new FlattenPackagesAction(update));
        PatternDialectProvider[] dialectProviders = PatternDialectProvider.EP_NAME.getExtensions();
        for (PatternDialectProvider provider : dialectProviders) {
            for (AnAction action : provider.createActions(myProject, update)) {
                group.add(action);
            }
        }
        group.add(new ShowFilesAction(update));
        Module[] modules = ModuleManager.getInstance(myProject).getModules();
        if (modules.length > 1) {
            group.add(new ShowModulesAction(update));
            group.add(new ShowModuleGroupsAction(update));
        }
        group.add(new FilterLegalsAction(update));

        if (dialectProviders.length > 1) {
            group.add(new ChooseScopeTypeAction(update));
        }

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
        return toolbar.getComponent();
    }

    private void rebuild(boolean updateText, @Nullable Runnable runnable, boolean requestFocus, int delayMillis) {
        myUpdateAlarm.cancel(false);
        Runnable request = () -> Application.get().executeOnPooledThread((Runnable)() -> {
            if (updateText) {
                String text = myCurrentScope != null ? myCurrentScope.getText() : null;
                SwingUtilities.invokeLater(() -> {
                    try {
                        myIsInUpdate = true;
                        myPatternField.setText(text);
                    }
                    finally {
                        myIsInUpdate = false;
                    }
                });
            }

            try {
                if (!myProject.isDisposed()) {
                    updateTreeModel(requestFocus);
                }
            }
            catch (ProcessCanceledException e) {
                return;
            }
            if (runnable != null) {
                runnable.run();
            }
        });
        myUpdateAlarm = AppExecutorUtil.getAppScheduledExecutorService().schedule(request, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void rebuild(boolean updateText) {
        rebuild(updateText, null, true, 300);
    }

    public void setHolder(NamedScopesHolder holder) {
        myHolder = holder;
    }

    private void initTree(Tree tree) {
        tree.setCellRenderer(new MyTreeCellRenderer());
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setLineStyleAngled();

        TreeUtil.installActions(tree);
        SmartExpander.installOn(tree);
        new TreeSpeedSearch(tree);
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
                ((PackageDependenciesNode)event.getPath().getLastPathComponent()).sortChildren();
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
            }
        });

        PopupHandler.installUnknownPopupHandler(tree, createTreePopupActions(), ActionManager.getInstance());
    }

    private ActionGroup createTreePopupActions() {
        DefaultActionGroup actionGroup = new DefaultActionGroup();
        actionGroup.add(new AnAction(IdeLocalize.buttonInclude()) {
            @Override
            @RequiredUIAccess
            public void actionPerformed(@Nonnull AnActionEvent e) {
                includeSelected(false);
            }
        });
        actionGroup.add(new AnAction(IdeLocalize.buttonIncludeRecursively()) {
            @Override
            @RequiredUIAccess
            public void actionPerformed(@Nonnull AnActionEvent e) {
                includeSelected(true);
            }

            @Override
            @RequiredUIAccess
            public void update(@Nonnull AnActionEvent e) {
                e.getPresentation().setEnabled(isButtonEnabled(true));
            }
        });

        actionGroup.add(new AnAction(IdeLocalize.buttonExclude()) {
            @Override
            @RequiredUIAccess
            public void actionPerformed(@Nonnull AnActionEvent e) {
                excludeSelected(false);
            }
        });
        actionGroup.add(new AnAction(IdeLocalize.buttonExcludeRecursively()) {
            @Override
            @RequiredUIAccess
            public void actionPerformed(@Nonnull AnActionEvent e) {
                excludeSelected(true);
            }

            @Override
            @RequiredUIAccess
            public void update(@Nonnull AnActionEvent e) {
                e.getPresentation().setEnabled(isButtonEnabled(true));
            }
        });

        return actionGroup;
    }

    private void updateTreeModel(boolean requestFocus) throws ProcessCanceledException {
        PanelProgressIndicator progress = createProgressIndicator(requestFocus);
        progress.setBordersVisible(false);
        myCurrentProgress = progress;
        Runnable updateModel = () -> {
            ProcessCanceledException[] ex = new ProcessCanceledException[1];
            myProject.getApplication().runReadAction(() -> {
                if (myProject.isDisposed()) {
                    return;
                }
                try {
                    myTreeExpansionMonitor.freeze();
                    TreeModel model = PatternDialectProvider.findById(DependencyUISettings.getInstance().getScopeType())
                        .createTreeModel(myProject, myTreeMarker);
                    ((PackageDependenciesNode)model.getRoot()).sortChildren();
                    if (myErrorMessage == null) {
                        myMatchingCountLabel.setText(
                            IdeLocalize.labelScopeContainsFiles(model.getMarkedFileCount(), model.getTotalFileCount()).get()
                        );
                        myMatchingCountLabel.setForeground(new JLabel().getForeground());
                    }
                    else {
                        showErrorMessage();
                    }

                    SwingUtilities.invokeLater(() -> { //not under progress
                        myPackageTree.setModel(model);
                        myTreeExpansionMonitor.restore();
                    });
                }
                catch (ProcessCanceledException e) {
                    ex[0] = e;
                }
                finally {
                    myCurrentProgress = null;
                    //update label
                    setToComponent(myMatchingCountLabel, requestFocus);
                }
            });
            if (ex[0] != null) {
                throw ex[0];
            }
        };
        ProgressManager.getInstance().runProcess(updateModel, progress);
    }

    protected PanelProgressIndicator createProgressIndicator(boolean requestFocus) {
        return new MyPanelProgressIndicator(myProject.getUIAccess().getScheduler(), requestFocus);
    }

    public void cancelCurrentProgress() {
        if (myCurrentProgress != null && myCurrentProgress.isRunning()) {
            myCurrentProgress.cancel();
        }
    }

    public void apply() throws ConfigurationException {
    }

    public PackageSet getCurrentScope() {
        return myCurrentScope;
    }

    public String getPatternText() {
        return myPatternField.getText();
    }

    public void reset(PackageSet packageSet, @Nullable Runnable runnable) {
        myCurrentScope = packageSet;
        myPatternField.setText(myCurrentScope == null ? "" : myCurrentScope.getText());
        rebuild(false, runnable, false, 0);
    }

    private void setToComponent(JComponent cmp, boolean requestFocus) {
        myMatchingCountPanel.removeAll();
        myMatchingCountPanel.add(cmp, BorderLayout.CENTER);
        myMatchingCountPanel.revalidate();
        myMatchingCountPanel.repaint();
        if (requestFocus) {
            SwingUtilities.invokeLater(() -> myPatternField.getTextField().requestFocusInWindow());
        }
    }

    public void restoreCanceledProgress() {
        if (myIsInUpdate) {
            rebuild(false);
        }
    }

    public void clearCaches() {
        FileTreeModelBuilder.clearCaches(myProject);
    }

    private static class MyTreeCellRenderer extends ColoredTreeCellRenderer {
        private static final Color WHOLE_INCLUDED = new JBColor(new Color(10, 119, 0), new Color(0xA5C25C));
        private static final Color PARTIAL_INCLUDED = new JBColor(new Color(0, 50, 160), DarculaColors.BLUE);

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
            if (value instanceof PackageDependenciesNode node) {
                setIcon(node.getIcon());

                setForeground(selected && hasFocus ? UIUtil.getTreeSelectionForeground(true) : UIUtil.getTreeForeground());
                if (!(selected && hasFocus) && node.hasMarked() && !DependencyUISettings.getInstance().UI_FILTER_LEGALS) {
                    setForeground(node.hasUnmarked() ? PARTIAL_INCLUDED : WHOLE_INCLUDED);
                }
                append(node.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                String locationString = node.getComment();
                if (!StringUtil.isEmpty(locationString)) {
                    append(" (" + locationString + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
                }
            }
        }
    }

    private final class ChooseScopeTypeAction extends ComboBoxAction {
        private final Runnable myUpdate;

        public ChooseScopeTypeAction(Runnable update) {
            myUpdate = update;
        }

        @Override
        @Nonnull
        public DefaultActionGroup createPopupActionGroup(JComponent component) {
            DefaultActionGroup group = new DefaultActionGroup();
            for (PatternDialectProvider provider : PatternDialectProvider.EP_NAME.getExtensionList()) {
                group.add(new AnAction(provider.getDisplayName()) {
                    @Override
                    @RequiredUIAccess
                    public void actionPerformed(@Nonnull AnActionEvent e) {
                        DependencyUISettings.getInstance().setScopeType(provider.getId());
                        myUpdate.run();
                    }
                });
            }
            return group;
        }

        @Override
        @RequiredUIAccess
        public void update(@Nonnull AnActionEvent e) {
            super.update(e);
            PatternDialectProvider provider = PatternDialectProvider.findById(DependencyUISettings.getInstance().getScopeType());
            e.getPresentation().setText(provider.getDisplayName());
            e.getPresentation().setIcon(provider.getIcon());
        }
    }

    private final class FilterLegalsAction extends ToggleAction {
        private final Runnable myUpdate;

        public FilterLegalsAction(Runnable update) {
            super(
                IdeLocalize.actionShowIncludedOnly(),
                IdeLocalize.actionDescriptionShowIncludedOnly(),
                PlatformIconGroup.generalFilter()
            );
            myUpdate = update;
        }

        @Override
        public boolean isSelected(@Nonnull AnActionEvent event) {
            return DependencyUISettings.getInstance().UI_FILTER_LEGALS;
        }

        @Override
        public void setSelected(@Nonnull AnActionEvent event, boolean flag) {
            DependencyUISettings.getInstance().UI_FILTER_LEGALS = flag;
            UIUtil.setEnabled(myLegendPanel, !flag, true);
            myUpdate.run();
        }
    }

    protected class MyPanelProgressIndicator extends PanelProgressIndicator {
        private final boolean myRequestFocus;

        public MyPanelProgressIndicator(UIAccessScheduler uiAccessScheduler, boolean requestFocus) {
            super(uiAccessScheduler, component -> setToComponent(component, requestFocus));
            myRequestFocus = requestFocus;
        }

        @Override
        public void stop() {
            super.stop();
            setToComponent(myMatchingCountLabel, myRequestFocus);
        }

        @Nonnull
        @Override
        public LocalizeValue getTextValue() { //just show non-blocking progress
            return LocalizeValue.empty();
        }

        @Nonnull
        @Override
        public LocalizeValue getText2Value() {
            return LocalizeValue.empty();
        }
    }
}
