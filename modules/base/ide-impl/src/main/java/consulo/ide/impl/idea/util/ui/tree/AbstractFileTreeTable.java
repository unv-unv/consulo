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

package consulo.ide.impl.idea.util.ui.tree;

import consulo.application.AllIcons;
import consulo.ui.ex.action.CommonActionsManager;
import consulo.ui.ex.awt.tree.DefaultTreeExpander;
import consulo.project.Project;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.ui.ex.awt.Messages;
import consulo.util.lang.Comparing;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.ui.ex.awt.tree.TreeUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileFilter;
import consulo.ui.ex.awt.util.TableUtil;
import consulo.ui.ex.awt.speedSearch.TreeTableSpeedSearch;
import consulo.ui.ex.awt.tree.table.TreeTable;
import consulo.ui.ex.awt.tree.table.TreeTableCellRenderer;
import consulo.ui.ex.awt.tree.table.TreeTableModel;
import consulo.ide.impl.idea.util.containers.Convertor;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ide.impl.virtualFileSystem.VfsIconUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import javax.swing.table.TableColumn;
import javax.swing.tree.*;
import java.awt.*;
import java.util.List;
import java.util.*;

public abstract class AbstractFileTreeTable<T> extends TreeTable {
  private final MyModel<T> myModel;
  private final Project myProject;

  public AbstractFileTreeTable(@Nonnull Project project,
                               @Nonnull Class<T> valueClass,
                               @Nonnull String valueTitle,
                               @Nonnull VirtualFileFilter filter,
                               boolean showProjectNode) {
    super(new MyModel<T>(project, valueClass, valueTitle, filter));
    myProject = project;

    myModel = (MyModel)getTableModel();
    myModel.setTreeTable(this);

    new TreeTableSpeedSearch(this, new Convertor<TreePath, String>() {
      @Override
      public String convert(final TreePath o) {
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)o.getLastPathComponent();
        final Object userObject = node.getUserObject();
        if (userObject == null) {
          return getProjectNodeText();
        }
        if (userObject instanceof VirtualFile virtualFile) {
          return virtualFile.getName();
        }
        return node.toString();
      }
    });
    final DefaultTreeExpander treeExpander = new DefaultTreeExpander(getTree());
    CommonActionsManager.getInstance().createExpandAllAction(treeExpander, this);
    CommonActionsManager.getInstance().createCollapseAllAction(treeExpander, this);

    getTree().setShowsRootHandles(true);
    getTree().setLineStyleAngled();
    getTree().setRootVisible(showProjectNode);
    getTree().setCellRenderer(new DefaultTreeCellRenderer() {
      @Override
      public Component getTreeCellRendererComponent(final JTree tree, final Object value, final boolean sel, final boolean expanded,
                                                    final boolean leaf, final int row, final boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        if (value instanceof ProjectRootNode) {
          setText(getProjectNodeText());
          setIcon(TargetAWT.to(AllIcons.Nodes.Project));
          return this;
        }
        FileNode fileNode = (FileNode)value;
        VirtualFile file = fileNode.getObject();
        if (fileNode.getParent() instanceof FileNode) {
          setText(file.getName());
        }
        else {
          setText(file.getPresentableUrl());
        }

        consulo.ui.image.Image icon = file.isDirectory() ? AllIcons.Nodes.TreeClosed : VfsIconUtil.getIcon(file, 0, null);
        setIcon(TargetAWT.to(icon));
        return this;
      }
    });
    getTableHeader().setReorderingAllowed(false);


    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    setPreferredScrollableViewportSize(new Dimension(300, getRowHeight() * 10));

    getColumnModel().getColumn(0).setPreferredWidth(280);
    getColumnModel().getColumn(1).setPreferredWidth(60);
  }

  protected boolean isNullObject(final T value) {
    return false;
  }

  private static String getProjectNodeText() {
    return "Project";
  }

  public Project getProject() {
    return myProject;
  }

  public TableColumn getValueColumn() {
    return getColumnModel().getColumn(1);
  }

  protected boolean isValueEditableForFile(final VirtualFile virtualFile) {
    return true;
  }

  public static void press(final Container comboComponent) {
    if (comboComponent instanceof JButton button) {
      button.doClick();
    }
    else {
      for (int i = 0; i < comboComponent.getComponentCount(); i++) {
        Component child = comboComponent.getComponent(i);
        if (child instanceof Container container) {
          press(container);
        }
      }
    }
  }

  public boolean clearSubdirectoriesOnDemandOrCancel(final VirtualFile parent, final String message, final String title) {
    Map<VirtualFile, T> mappings = myModel.myCurrentMapping;
    Map<VirtualFile, T> subdirectoryMappings = new HashMap<VirtualFile, T>();
    for (VirtualFile file : mappings.keySet()) {
      if (file != null && (parent == null || VfsUtilCore.isAncestor(parent, file, true))) {
        subdirectoryMappings.put(file, mappings.get(file));
      }
    }
    if (subdirectoryMappings.isEmpty()) {
      return true;
    }
    int ret = Messages.showYesNoCancelDialog(myProject, message, title, "Override", "Do Not Override", "Cancel",
                                             Messages.getWarningIcon());
    if (ret == 0) {
      for (VirtualFile file : subdirectoryMappings.keySet()) {
        myModel.setValueAt(null, new DefaultMutableTreeNode(file), 1);
      }
    }
    return ret != 2;
  }

  @Nonnull
  public Map<VirtualFile, T> getValues() {
    return myModel.getValues();
  }

  @Override
  public TreeTableCellRenderer createTableRenderer(TreeTableModel treeTableModel) {
    TreeTableCellRenderer tableRenderer = super.createTableRenderer(treeTableModel);
    UIUtil.setLineStyleAngled(tableRenderer);
    tableRenderer.setRootVisible(false);
    tableRenderer.setShowsRootHandles(true);

    return tableRenderer;
  }

  public void reset(@Nonnull Map<VirtualFile, T> mappings) {
    myModel.reset(mappings);
    final TreeNode root = (TreeNode)myModel.getRoot();
    myModel.nodeChanged(root);
    getTree().setModel(null);
    getTree().setModel(myModel);
    TreeUtil.expandRootChildIfOnlyOne(getTree());
  }

  public void select(@Nullable final VirtualFile toSelect) {
    if (toSelect != null) {
      select(toSelect, (TreeNode)myModel.getRoot());
    }
  }

  private void select(@Nonnull VirtualFile toSelect, final TreeNode root) {
    for (int i = 0; i < root.getChildCount(); i++) {
      TreeNode child = root.getChildAt(i);
      VirtualFile file = ((FileNode)child).getObject();
      if (VfsUtilCore.isAncestor(file, toSelect, false)) {
        if (Comparing.equal(file, toSelect)) {
          TreeUtil.selectNode(getTree(), child);
          getSelectionModel().clearSelection();
          addSelectedPath(TreeUtil.getPathFromRoot(child));
          TableUtil.scrollSelectionToVisible(this);
        }
        else {
          select(toSelect, child);
        }
        return;
      }
    }
  }


  private static class MyModel<T> extends DefaultTreeModel implements TreeTableModel {
    private final Map<VirtualFile, T> myCurrentMapping = new HashMap<VirtualFile, T>();
    private final Class<T> myValueClass;
    private final String myValueTitle;
    private AbstractFileTreeTable<T> myTreeTable;

    private MyModel(@Nonnull Project project, @Nonnull Class<T> valueClass, @Nonnull String valueTitle, @Nonnull VirtualFileFilter filter) {
      super(new ProjectRootNode(project, filter));
      myValueClass = valueClass;
      myValueTitle = valueTitle;
    }

    private Map<VirtualFile, T> getValues() {
      return new HashMap<VirtualFile, T>(myCurrentMapping);
    }

    @Override
    public void setTree(JTree tree) {
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public String getColumnName(final int column) {
      switch (column) {
        case 0:
          return "File/Directory";
        case 1:
          return myValueTitle;
        default:
          throw new RuntimeException("invalid column " + column);
      }
    }

    @Override
    public Class getColumnClass(final int column) {
      switch (column) {
        case 0:
          return TreeTableModel.class;
        case 1:
          return myValueClass;
        default:
          throw new RuntimeException("invalid column " + column);
      }
    }

    @Override
    public Object getValueAt(final Object node, final int column) {
      Object userObject = ((DefaultMutableTreeNode)node).getUserObject();
      if (userObject instanceof Project) {
        switch (column) {
          case 0:
            return userObject;
          case 1:
            return myCurrentMapping.get(null);
        }
      }
      VirtualFile file = (VirtualFile)userObject;
      switch (column) {
        case 0:
          return file;
        case 1:
          return myCurrentMapping.get(file);
        default:
          throw new RuntimeException("invalid column " + column);
      }
    }

    @Override
    public boolean isCellEditable(final Object node, final int column) {
      switch (column) {
        case 0:
          return false;
        case 1:
          final Object userObject = ((DefaultMutableTreeNode)node).getUserObject();
          return !(userObject instanceof VirtualFile || userObject == null) || myTreeTable.isValueEditableForFile((VirtualFile)userObject);
        default:
          throw new RuntimeException("invalid column " + column);
      }
    }

    @Override
    public void setValueAt(final Object aValue, final Object node, final int column) {
      final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)node;
      final Object userObject = treeNode.getUserObject();
      if (userObject instanceof Project) return;
      final VirtualFile file = (VirtualFile)userObject;
      final T t = (T)aValue;
      if (t == null || myTreeTable.isNullObject(t)) {
        myCurrentMapping.remove(file);
      }
      else {
        myCurrentMapping.put(file, t);
      }
      fireTreeNodesChanged(this, new Object[]{getRoot()}, null, null);
    }

    public void reset(@Nonnull Map<VirtualFile, T> mappings) {
      myCurrentMapping.clear();
      myCurrentMapping.putAll(mappings);
      ((ProjectRootNode)getRoot()).clearCachedChildren();
    }

    void setTreeTable(final AbstractFileTreeTable<T> treeTable) {
      myTreeTable = treeTable;
    }
  }

  public static class ProjectRootNode extends ConvenientNode<Project> {
    private VirtualFileFilter myFilter;

    public ProjectRootNode(@Nonnull Project project) {
      this(project, VirtualFileFilter.ALL);
    }

    public ProjectRootNode(@Nonnull Project project, @Nonnull VirtualFileFilter filter) {
      super(project);
      myFilter = filter;
    }

    @Override
    protected void appendChildrenTo(@Nonnull final Collection<ConvenientNode> children) {
      Project project = getObject();
      VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();

      NextRoot:
      for (VirtualFile root : roots) {
        for (VirtualFile candidate : roots) {
          if (VfsUtilCore.isAncestor(candidate, root, true)) continue NextRoot;
        }
        if (myFilter.accept(root)) {
          children.add(new FileNode(root, project, myFilter));
        }
      }
    }
  }

  public abstract static class ConvenientNode<T> extends DefaultMutableTreeNode {
    private final T myObject;

    private ConvenientNode(T object) {
      myObject = object;
    }

    public T getObject() {
      return myObject;
    }

    protected abstract void appendChildrenTo(@Nonnull Collection<ConvenientNode> children);

    @Override
    public int getChildCount() {
      init();
      return super.getChildCount();
    }

    @Override
    public TreeNode getChildAt(final int childIndex) {
      init();
      return super.getChildAt(childIndex);
    }

    @Override
    public Enumeration children() {
      init();
      return super.children();
    }

    private void init() {
      if (getUserObject() == null) {
        setUserObject(myObject);
        final List<ConvenientNode> children = new ArrayList<ConvenientNode>();
        appendChildrenTo(children);
        Collections.sort(children, new Comparator<ConvenientNode>() {
          @Override
          public int compare(final ConvenientNode node1, final ConvenientNode node2) {
            Object o1 = node1.getObject();
            Object o2 = node2.getObject();
            if (o1 == o2) return 0;
            if (o1 instanceof Project) return -1;
            if (o2 instanceof Project) return 1;
            VirtualFile file1 = (VirtualFile)o1;
            VirtualFile file2 = (VirtualFile)o2;
            if (file1.isDirectory() != file2.isDirectory()) {
              return file1.isDirectory() ? -1 : 1;
            }
            return file1.getName().compareTo(file2.getName());
          }
        });
        int i = 0;
        for (ConvenientNode child : children) {
          insert(child, i++);
        }
      }
    }

    public void clearCachedChildren() {
      if (children != null) {
        for (Object child : children) {
          ConvenientNode<T> node = (ConvenientNode<T>)child;
          node.clearCachedChildren();
        }
      }
      removeAllChildren();
      setUserObject(null);
    }
  }

  public static class FileNode extends ConvenientNode<VirtualFile> {
    private final Project myProject;
    private VirtualFileFilter myFilter;

    public FileNode(@Nonnull VirtualFile file, @Nonnull final Project project) {
      this(file, project, VirtualFileFilter.ALL);
    }

    public FileNode(@Nonnull VirtualFile file, @Nonnull final Project project, @Nonnull VirtualFileFilter filter) {
      super(file);
      myProject = project;
      myFilter = filter;
    }

    @Override
    protected void appendChildrenTo(@Nonnull final Collection<ConvenientNode> children) {
      VirtualFile[] childrenf = getObject().getChildren();
      ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
      for (VirtualFile child : childrenf) {
        if (myFilter.accept(child) && fileIndex.isInContent(child)) {
          children.add(new FileNode(child, myProject, myFilter));
        }
      }
    }
  }

}
