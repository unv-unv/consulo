/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package consulo.desktop.awt.language.editor;

import consulo.application.AllIcons;
import consulo.application.ApplicationManager;
import consulo.application.ApplicationPropertiesComponent;
import consulo.dataContext.DataSink;
import consulo.dataContext.TypeSafeDataProvider;
import consulo.ide.IdeBundle;
import consulo.ide.impl.idea.ide.util.PropertiesComponent;
import consulo.ide.impl.idea.util.containers.Convertor;
import consulo.language.editor.generation.*;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.ide.localize.IdeLocalize;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.VerticalFlowLayout;
import consulo.ui.ex.awt.event.DoubleClickListener;
import consulo.ui.ex.awt.speedSearch.SpeedSearchComparator;
import consulo.ui.ex.awt.speedSearch.TreeSpeedSearch;
import consulo.ui.ex.awt.tree.ColoredTreeCellRenderer;
import consulo.ui.ex.awt.tree.Tree;
import consulo.ui.ex.awt.tree.TreeUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.keymap.KeymapManager;
import consulo.ui.image.Image;
import consulo.util.collection.FactoryMap;
import consulo.util.dataholder.Key;
import consulo.util.dataholder.KeyWithDefaultValue;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.Ref;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;

public class MemberChooserImpl<T extends ClassMember> extends DialogWrapper implements TypeSafeDataProvider {
    protected Tree myTree;
    private DefaultTreeModel myTreeModel;

    private final ArrayList<MemberNode> mySelectedNodes = new ArrayList<>();

    private final SortEmAction mySortAction;

    private boolean myAlphabeticallySorted = false;
    private boolean myShowClasses = true;
    protected boolean myAllowEmptySelection = false;
    private final boolean myAllowMultiSelection;
    private final JComponent myHeaderPanel;

    protected T[] myElements;
    protected Comparator<ElementNode> myComparator = new OrderComparator();

    protected final HashMap<MemberNode, ParentNode> myNodeToParentMap = new HashMap<>();
    protected final HashMap<ClassMember, MemberNode> myElementToNodeMap = new HashMap<>();
    protected final ArrayList<ContainerNode> myContainerNodes = new ArrayList<>();

    protected LinkedHashSet<T> mySelectedElements;

    private static final String PROP_SORTED = "MemberChooserImpl.sorted";
    private static final String PROP_SHOWCLASSES = "MemberChooserImpl.showClasses";

    private final List<Pair<KeyWithDefaultValue<Boolean>, LocalizeValue>> myOptions;
    private Map<Key<Boolean>, CheckBox> myOptionComponents = new LinkedHashMap<>();

    public MemberChooserImpl(
        T[] elements,
        boolean allowEmptySelection,
        boolean allowMultiSelection,
        @Nonnull List<Pair<KeyWithDefaultValue<Boolean>, LocalizeValue>> options,
        @Nonnull Project project
    ) {
        super(project, true);
        myAllowEmptySelection = allowEmptySelection;
        myAllowMultiSelection = allowMultiSelection;
        myOptions = options;
        myHeaderPanel = null;
        myTree = createTree();
        mySortAction = new SortEmAction();
        mySortAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.ALT_MASK)), myTree);

        resetElements(elements);
        init();
    }

    protected void resetElementsWithDefaultComparator(T[] elements, final boolean restoreSelectedElements) {
        myComparator = myAlphabeticallySorted ? new AlphaComparator() : new OrderComparator();
        resetElements(elements, null, restoreSelectedElements);
    }

    public void resetElements(T[] elements) {
        resetElements(elements, null, false);
    }

    @SuppressWarnings("unchecked")
    @RequiredUIAccess
    public void resetElements(T[] elements, final @Nullable Comparator<T> sortComparator, final boolean restoreSelectedElements) {
        final List<T> selectedElements = restoreSelectedElements && mySelectedElements != null ? new ArrayList<>(mySelectedElements) : null;
        myElements = elements;
        if (sortComparator != null) {
            myComparator = new ElementNodeComparatorWrapper(sortComparator);
        }
        mySelectedNodes.clear();
        myNodeToParentMap.clear();
        myElementToNodeMap.clear();
        myContainerNodes.clear();

        ApplicationManager.getApplication().runReadAction(() -> {
            myTreeModel = buildModel();
        });

        myTree.setModel(myTreeModel);
        myTree.setRootVisible(false);

        doSort();

        defaultExpandTree();

        myOptionComponents.clear();
        for (Pair<KeyWithDefaultValue<Boolean>, LocalizeValue> option : myOptions) {
            CheckBox optionComponent = CheckBox.create(option.getSecond());
            optionComponent.setValue(option.getKey().getDefaultValue());

            myOptionComponents.put(option.getFirst(), optionComponent);
        }

        myTree.doLayout();
        setOKActionEnabled(myAllowEmptySelection || myElements != null && myElements.length > 0);

        if (selectedElements != null) {
            selectElements(selectedElements.toArray(new ClassMember[selectedElements.size()]));
        }
        if (mySelectedElements == null || mySelectedElements.isEmpty()) {
            expandFirst();
        }
    }

    /**
     * should be invoked in read action
     */
    private DefaultTreeModel buildModel() {
        final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
        final Ref<Integer> count = new Ref<>(0);
        Ref<Map<MemberChooserObject, ParentNode>> mapRef = new Ref<>();
        mapRef.set(FactoryMap.create(key -> {
            ParentNode node = null;
            DefaultMutableTreeNode parentNode1 = rootNode;

            if (supportsNestedContainers() && key instanceof ClassMember) {
                MemberChooserObject parentNodeDelegate = ((ClassMember) key).getParentNodeDelegate();

                if (parentNodeDelegate != null) {
                    parentNode1 = mapRef.get().get(parentNodeDelegate);
                }
            }
            if (isContainerNode(key)) {
                final ContainerNode containerNode = new ContainerNode(parentNode1, key, count);
                node = containerNode;
                myContainerNodes.add(containerNode);
            }
            if (node == null) {
                node = new ParentNode(parentNode1, key, count);
            }
            return node;
        }));

        final Map<MemberChooserObject, ParentNode> map = mapRef.get();

        for (T object : myElements) {
            final ParentNode parentNode = map.get(object.getParentNodeDelegate());
            final MemberNode elementNode = createMemberNode(count, object, parentNode);
            myNodeToParentMap.put(elementNode, parentNode);
            myElementToNodeMap.put(object, elementNode);
        }
        return new DefaultTreeModel(rootNode);
    }

    protected MemberNode createMemberNode(Ref<Integer> count, T object, ParentNode parentNode) {
        return new MemberNodeImpl(parentNode, object, count);
    }

    protected boolean supportsNestedContainers() {
        return false;
    }

    protected void defaultExpandTree() {
        TreeUtil.expandAll(myTree);
    }

    protected boolean isContainerNode(MemberChooserObject key) {
        return key instanceof PsiElementMemberChooserObject;
    }

    public void selectElements(ClassMember[] elements) {
        ArrayList<TreePath> selectionPaths = new ArrayList<>();
        for (ClassMember element : elements) {
            MemberNode treeNode = myElementToNodeMap.get(element);
            if (treeNode != null) {
                selectionPaths.add(new TreePath(((DefaultMutableTreeNode) treeNode).getPath()));
            }
        }
        myTree.setSelectionPaths(selectionPaths.toArray(new TreePath[selectionPaths.size()]));
    }


    @Override
    @Nonnull
    protected Action[] createActions() {
        final List<Action> actions = new ArrayList<>();
        actions.add(getOKAction());
        if (myAllowEmptySelection) {
            actions.add(new SelectNoneAction());
        }
        actions.add(getCancelAction());
        if (getHelpId() != null) {
            actions.add(getHelpAction());
        }
        return actions.toArray(new Action[actions.size()]);
    }

    @Override
    protected void doHelpAction() {
        if (getHelpId() == null) {
            return;
        }
        super.doHelpAction();
    }

    @Override
    protected JComponent createSouthPanel() {
        JPanel panel = new JPanel(new GridBagLayout());

        JPanel optionsPanel = new JPanel(new VerticalFlowLayout());
        for (final CheckBox component : myOptionComponents.values()) {
            optionsPanel.add(TargetAWT.to(component));
        }

        panel.add(
            optionsPanel,
            new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 5), 0, 0)
        );

        if (!myAllowEmptySelection && (myElements == null || myElements.length == 0)) {
            setOKActionEnabled(false);
        }
        panel.add(
            super.createSouthPanel(),
            new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.SOUTH, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0)
        );
        return panel;
    }

    public Map<Key<Boolean>, CheckBox> getOptionComponents() {
        return myOptionComponents;
    }

    @Override
    protected JComponent createNorthPanel() {
        return myHeaderPanel;
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Toolbar

        ActionGroup.Builder group = ActionGroup.newImmutableBuilder();

        fillToolbarActions(group);

        group.addSeparator();

        ExpandAllAction expandAllAction = new ExpandAllAction();
        expandAllAction.registerCustomShortcutSet(new CustomShortcutSet(KeymapManager.getInstance()
            .getActiveKeymap()
            .getShortcuts(IdeActions.ACTION_EXPAND_ALL)), myTree);
        group.add(expandAllAction);

        CollapseAllAction collapseAllAction = new CollapseAllAction();
        collapseAllAction.registerCustomShortcutSet(new CustomShortcutSet(KeymapManager.getInstance()
            .getActiveKeymap()
            .getShortcuts(IdeActions.ACTION_COLLAPSE_ALL)), myTree);
        group.add(collapseAllAction);

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group.build(), true);
        toolbar.setTargetComponent(myTree);

        panel.add(toolbar.getComponent(), BorderLayout.NORTH);

        // Tree
        expandFirst();
        defaultExpandTree();
        installSpeedSearch();

        JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
        scrollPane.setPreferredSize(new Dimension(350, 450));
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private void expandFirst() {
        if (getRootNode().getChildCount() > 0) {
            myTree.expandRow(0);
            myTree.setSelectionRow(1);
        }
    }

    protected Tree createTree() {
        final Tree tree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));

        tree.setCellRenderer(getTreeCellRenderer());
        UIUtil.setLineStyleAngled(tree);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.addKeyListener(new TreeKeyListener());
        tree.addTreeSelectionListener(new MyTreeSelectionListener());

        if (!myAllowMultiSelection) {
            tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        }

        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(MouseEvent e) {
                if (tree.getPathForLocation(e.getX(), e.getY()) != null) {
                    doOKAction();
                    return true;
                }
                return false;
            }
        }.installOn(tree);

        TreeUtil.installActions(tree);
        return tree;
    }

    protected TreeCellRenderer getTreeCellRenderer() {
        return new ColoredTreeCellRenderer() {
            @Override
            public void customizeCellRenderer(
                JTree tree,
                Object value,
                boolean selected,
                boolean expanded,
                boolean leaf,
                int row,
                boolean hasFocus
            ) {
                if (value instanceof ElementNode) {
                    ((ElementNode) value).getDelegate().renderTreeNode(this, tree);
                }
            }
        };
    }

    @Nonnull
    protected String convertElementText(@Nonnull String originalElementText) {
        String res = originalElementText;

        int i = res.indexOf(':');
        if (i >= 0) {
            res = res.substring(0, i);
        }
        i = res.indexOf('(');
        if (i >= 0) {
            res = res.substring(0, i);
        }

        return res;
    }

    protected void installSpeedSearch() {
        final TreeSpeedSearch treeSpeedSearch = new TreeSpeedSearch(myTree, new Convertor<>() {
            @Override
            @Nullable
            public String convert(TreePath path) {
                final ElementNode lastPathComponent = (ElementNode) path.getLastPathComponent();
                if (lastPathComponent == null) {
                    return null;
                }
                String text = lastPathComponent.getDelegate().getText();
                if (text != null) {
                    text = convertElementText(text);
                }
                return text;
            }
        });
        treeSpeedSearch.setComparator(getSpeedSearchComparator());
    }

    protected SpeedSearchComparator getSpeedSearchComparator() {
        return new SpeedSearchComparator(false);
    }

    protected void disableAlphabeticalSorting(final AnActionEvent event) {
        mySortAction.setSelected(event, false);
    }

    protected void onAlphabeticalSortingEnabled(final AnActionEvent event) {
        //do nothing by default
    }

    protected void fillToolbarActions(ActionGroup.Builder group) {
        final boolean alphabeticallySorted = PropertiesComponent.getInstance().isTrueValue(PROP_SORTED);
        if (alphabeticallySorted) {
            setSortComparator(new AlphaComparator());
        }
        myAlphabeticallySorted = alphabeticallySorted;
        group.add(mySortAction);

        if (!supportsNestedContainers()) {
            ShowContainersAction showContainersAction = getShowContainersAction();
            showContainersAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(
                KeyEvent.VK_C,
                InputEvent.ALT_MASK
            )), myTree);
            setShowClasses(PropertiesComponent.getInstance().getBoolean(PROP_SHOWCLASSES, true));
            group.add(showContainersAction);
        }
    }

    @Override
    protected String getDimensionServiceKey() {
        return "#consulo.desktop.awt.language.editor.MemberChooserImpl";
    }

    @RequiredUIAccess
    @Override
    public JComponent getPreferredFocusedComponent() {
        return myTree;
    }

    @Nullable
    private LinkedHashSet<T> getSelectedElementsList() {
        return getExitCode() == OK_EXIT_CODE ? mySelectedElements : null;
    }

    @Nullable
    public List<T> getSelectedElements() {
        final LinkedHashSet<T> list = getSelectedElementsList();
        return list == null ? null : new ArrayList<>(list);
    }

    @Nullable
    public T[] getSelectedElements(T[] a) {
        LinkedHashSet<T> list = getSelectedElementsList();
        if (list == null) {
            return null;
        }
        return list.toArray(a);
    }

    protected final boolean areElementsSelected() {
        return mySelectedElements != null && !mySelectedElements.isEmpty();
    }

    private boolean isAlphabeticallySorted() {
        return myAlphabeticallySorted;
    }

    @SuppressWarnings("unchecked")
    protected void changeSortComparator(final Comparator<T> comparator) {
        setSortComparator(new ElementNodeComparatorWrapper(comparator));
    }

    private void setSortComparator(Comparator<ElementNode> sortComparator) {
        if (myComparator.equals(sortComparator)) {
            return;
        }
        myComparator = sortComparator;
        doSort();
    }

    protected void doSort() {
        Pair<ElementNode, List<ElementNode>> pair = storeSelection();

        Enumeration<TreeNode> children = getRootNodeChildren();
        while (children.hasMoreElements()) {
            ParentNode classNode = (ParentNode) children.nextElement();
            sortNode(classNode, myComparator);
            myTreeModel.nodeStructureChanged(classNode);
        }

        restoreSelection(pair);
    }

    private static void sortNode(ParentNode node, final Comparator<ElementNode> sortComparator) {
        ArrayList<ElementNode> arrayList = new ArrayList<>();
        Enumeration<TreeNode> children = node.children();
        while (children.hasMoreElements()) {
            arrayList.add((ElementNode) children.nextElement());
        }

        Collections.sort(arrayList, sortComparator);

        replaceChildren(node, arrayList);
    }

    private static void replaceChildren(final DefaultMutableTreeNode node, final Collection<? extends ElementNode> arrayList) {
        node.removeAllChildren();
        for (ElementNode child : arrayList) {
            node.add(child);
        }
    }

    protected void restoreTree() {
        Pair<ElementNode, List<ElementNode>> selection = storeSelection();

        DefaultMutableTreeNode root = getRootNode();
        if (!myShowClasses || myContainerNodes.isEmpty()) {
            List<ParentNode> otherObjects = new ArrayList<>();
            Enumeration<TreeNode> children = getRootNodeChildren();
            ParentNode newRoot = new ParentNode(null, new MemberChooserObjectBase(getAllContainersNodeName()), new Ref<>(0));
            while (children.hasMoreElements()) {
                final ParentNode nextElement = (ParentNode) children.nextElement();
                if (nextElement instanceof ContainerNode) {
                    final ContainerNode containerNode = (ContainerNode) nextElement;
                    Enumeration<TreeNode> memberNodes = containerNode.children();
                    List<MemberNode> memberNodesList = new ArrayList<>();
                    while (memberNodes.hasMoreElements()) {
                        memberNodesList.add((MemberNode) memberNodes.nextElement());
                    }
                    for (MemberNode memberNode : memberNodesList) {
                        newRoot.add(memberNode);
                    }
                }
                else {
                    otherObjects.add(nextElement);
                }
            }
            replaceChildren(root, otherObjects);
            sortNode(newRoot, myComparator);
            if (newRoot.children().hasMoreElements()) {
                root.add(newRoot);
            }
        }
        else {
            Enumeration<TreeNode> children = getRootNodeChildren();
            while (children.hasMoreElements()) {
                ParentNode allClassesNode = (ParentNode) children.nextElement();
                Enumeration<TreeNode> memberNodes = allClassesNode.children();
                ArrayList<MemberNode> arrayList = new ArrayList<>();
                while (memberNodes.hasMoreElements()) {
                    arrayList.add((MemberNode) memberNodes.nextElement());
                }
                Collections.sort(arrayList, myComparator);
                for (MemberNode memberNode : arrayList) {
                    myNodeToParentMap.get(memberNode).add(memberNode);
                }
            }
            replaceChildren(root, myContainerNodes);
        }
        myTreeModel.nodeStructureChanged(root);

        defaultExpandTree();

        restoreSelection(selection);
    }

    private void setShowClasses(boolean showClasses) {
        myShowClasses = showClasses;
        restoreTree();
    }

    protected String getAllContainersNodeName() {
        return IdeBundle.message("node.memberchooser.all.classes");
    }

    private Enumeration<TreeNode> getRootNodeChildren() {
        return getRootNode().children();
    }

    protected DefaultMutableTreeNode getRootNode() {
        return (DefaultMutableTreeNode) myTreeModel.getRoot();
    }

    private Pair<ElementNode, List<ElementNode>> storeSelection() {
        List<ElementNode> selectedNodes = new ArrayList<>();
        TreePath[] paths = myTree.getSelectionPaths();
        if (paths != null) {
            for (TreePath path : paths) {
                selectedNodes.add((ElementNode) path.getLastPathComponent());
            }
        }
        TreePath leadSelectionPath = myTree.getLeadSelectionPath();
        return Pair.create(leadSelectionPath != null ? (ElementNode) leadSelectionPath.getLastPathComponent() : null, selectedNodes);
    }


    private void restoreSelection(Pair<ElementNode, List<ElementNode>> pair) {
        List<ElementNode> selectedNodes = pair.second;

        DefaultMutableTreeNode root = getRootNode();

        ArrayList<TreePath> toSelect = new ArrayList<>();
        for (ElementNode node : selectedNodes) {
            DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) node;
            if (root.isNodeDescendant(treeNode)) {
                toSelect.add(new TreePath(treeNode.getPath()));
            }
        }

        if (!toSelect.isEmpty()) {
            myTree.setSelectionPaths(toSelect.toArray(new TreePath[toSelect.size()]));
        }

        ElementNode leadNode = pair.first;
        if (leadNode != null) {
            myTree.setLeadSelectionPath(new TreePath(((DefaultMutableTreeNode) leadNode).getPath()));
        }
    }

    @Override
    public void dispose() {
        ApplicationPropertiesComponent instance = ApplicationPropertiesComponent.getInstance();
        instance.setValue(PROP_SORTED, Boolean.toString(isAlphabeticallySorted()));
        instance.setValue(PROP_SHOWCLASSES, Boolean.toString(myShowClasses));

        final Container contentPane = getContentPane();
        if (contentPane != null) {
            contentPane.removeAll();
        }
        mySelectedNodes.clear();
        myElements = null;
        super.dispose();
    }

    @Override
    public void calcData(final Key key, final DataSink sink) {
        if (PsiElement.KEY == key) {
            if (mySelectedElements != null && !mySelectedElements.isEmpty()) {
                T selectedElement = mySelectedElements.iterator().next();
                if (selectedElement instanceof ClassMemberWithElement) {
                    sink.put(PsiElement.KEY, ((ClassMemberWithElement) selectedElement).getElement());
                }
            }
        }
    }

    private class MyTreeSelectionListener implements TreeSelectionListener {
        @Override
        public void valueChanged(TreeSelectionEvent e) {
            TreePath[] paths = e.getPaths();
            if (paths == null) {
                return;
            }
            for (int i = 0; i < paths.length; i++) {
                Object node = paths[i].getLastPathComponent();
                if (node instanceof MemberNode) {
                    final MemberNode memberNode = (MemberNode) node;
                    if (e.isAddedPath(i)) {
                        if (!mySelectedNodes.contains(memberNode)) {
                            mySelectedNodes.add(memberNode);
                        }
                    }
                    else {
                        mySelectedNodes.remove(memberNode);
                    }
                }
            }
            mySelectedElements = new LinkedHashSet<>();
            for (MemberNode selectedNode : mySelectedNodes) {
                mySelectedElements.add((T) selectedNode.getDelegate());
            }
        }
    }

    protected interface ElementNode extends MutableTreeNode {
        MemberChooserObject getDelegate();

        int getOrder();
    }

    protected interface MemberNode extends ElementNode {
    }

    protected abstract static class ElementNodeImpl extends DefaultMutableTreeNode implements ElementNode {
        private final int myOrder;
        private final MemberChooserObject myDelegate;

        public ElementNodeImpl(@Nullable DefaultMutableTreeNode parent, MemberChooserObject delegate, Ref<Integer> order) {
            myOrder = order.get();
            order.set(myOrder + 1);
            myDelegate = delegate;
            if (parent != null) {
                parent.add(this);
            }
        }

        @Override
        public MemberChooserObject getDelegate() {
            return myDelegate;
        }

        @Override
        public int getOrder() {
            return myOrder;
        }
    }

    protected static class MemberNodeImpl extends ElementNodeImpl implements MemberNode {
        public MemberNodeImpl(ParentNode parent, ClassMember delegate, Ref<Integer> order) {
            super(parent, delegate, order);
        }
    }

    protected static class ParentNode extends ElementNodeImpl {
        public ParentNode(@Nullable DefaultMutableTreeNode parent, MemberChooserObject delegate, Ref<Integer> order) {
            super(parent, delegate, order);
        }
    }

    protected static class ContainerNode extends ParentNode {
        public ContainerNode(DefaultMutableTreeNode parent, MemberChooserObject delegate, Ref<Integer> order) {
            super(parent, delegate, order);
        }
    }

    private class SelectNoneAction extends AbstractAction {
        public SelectNoneAction() {
            super(IdeBundle.message("action.select.none"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            myTree.clearSelection();
            doOKAction();
        }
    }

    private class TreeKeyListener extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            TreePath path = myTree.getLeadSelectionPath();
            if (path == null) {
                return;
            }
            final Object lastComponent = path.getLastPathComponent();
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                if (lastComponent instanceof ParentNode) {
                    return;
                }
                doOKAction();
                e.consume();
            }
            else if (e.getKeyCode() == KeyEvent.VK_INSERT) {
                if (lastComponent instanceof ElementNode) {
                    final DefaultMutableTreeNode node = (DefaultMutableTreeNode) lastComponent;
                    if (!mySelectedNodes.contains(node)) {
                        if (node.getNextNode() != null) {
                            myTree.setSelectionPath(new TreePath(node.getNextNode().getPath()));
                        }
                    }
                    else {
                        if (node.getNextNode() != null) {
                            myTree.removeSelectionPath(new TreePath(node.getPath()));
                            myTree.setSelectionPath(new TreePath(node.getNextNode().getPath()));
                            myTree.repaint();
                        }
                    }
                    e.consume();
                }
            }
        }
    }

    private class SortEmAction extends ToggleAction {
        public SortEmAction() {
            super(
                IdeBundle.message("action.sort.alphabetically"),
                IdeBundle.message("action.sort.alphabetically"),
                AllIcons.ObjectBrowser.Sorted
            );
        }

        @Override
        public boolean isSelected(AnActionEvent event) {
            return isAlphabeticallySorted();
        }

        @Override
        public void setSelected(AnActionEvent event, boolean flag) {
            myAlphabeticallySorted = flag;
            setSortComparator(flag ? new AlphaComparator() : new OrderComparator());
            if (flag) {
                MemberChooserImpl.this.onAlphabeticalSortingEnabled(event);
            }
        }
    }

    protected ShowContainersAction getShowContainersAction() {
        return new ShowContainersAction(IdeLocalize.actionShowClasses(), AllIcons.Nodes.Class);
    }

    protected class ShowContainersAction extends ToggleAction {
        public ShowContainersAction(final LocalizeValue text, final Image icon) {
            super(text, text, icon);
        }

        @Override
        public boolean isSelected(@Nonnull AnActionEvent event) {
            return myShowClasses;
        }

        @Override
        public void setSelected(@Nonnull AnActionEvent event, boolean flag) {
            setShowClasses(flag);
        }

        @Override
        public void update(@Nonnull AnActionEvent e) {
            super.update(e);
            Presentation presentation = e.getPresentation();
            presentation.setEnabled(myContainerNodes.size() > 1);
        }
    }

    private class ExpandAllAction extends AnAction {
        public ExpandAllAction() {
            super(IdeLocalize.actionExpandAll(), IdeLocalize.actionExpandAll(), PlatformIconGroup.actionsExpandall());
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(@Nonnull AnActionEvent e) {
            TreeUtil.expandAll(myTree);
        }
    }

    private class CollapseAllAction extends AnAction {
        public CollapseAllAction() {
            super(IdeLocalize.actionCollapseAll(), IdeLocalize.actionCollapseAll(), PlatformIconGroup.actionsCollapseall());
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(@Nonnull AnActionEvent e) {
            TreeUtil.collapseAll(myTree, 1);
        }
    }

    private static class AlphaComparator implements Comparator<ElementNode> {
        @Override
        public int compare(ElementNode n1, ElementNode n2) {
            return n1.getDelegate().getText().compareToIgnoreCase(n2.getDelegate().getText());
        }
    }

    protected static class OrderComparator implements Comparator<ElementNode> {
        public OrderComparator() {
        } // To make this class instanceable from the subclasses

        @Override
        public int compare(ElementNode n1, ElementNode n2) {
            if (n1.getDelegate() instanceof ClassMemberWithElement && n2.getDelegate() instanceof ClassMemberWithElement) {
                PsiElement element1 = ((ClassMemberWithElement) n1.getDelegate()).getElement();
                PsiElement element2 = ((ClassMemberWithElement) n2.getDelegate()).getElement();
                if (!(element1 instanceof PsiCompiledElement) && !(element2 instanceof PsiCompiledElement)) {
                    return element1.getTextOffset() - element2.getTextOffset();
                }
            }
            return n1.getOrder() - n2.getOrder();
        }
    }

    private static class ElementNodeComparatorWrapper<T> implements Comparator<ElementNode> {
        private final Comparator<T> myDelegate;

        public ElementNodeComparatorWrapper(final Comparator<T> delegate) {
            myDelegate = delegate;
        }

        @SuppressWarnings("unchecked")
        @Override
        public int compare(final ElementNode o1, final ElementNode o2) {
            return myDelegate.compare((T) o1.getDelegate(), (T) o2.getDelegate());
        }
    }
}
