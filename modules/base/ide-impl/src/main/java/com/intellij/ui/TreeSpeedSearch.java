// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import consulo.ui.ex.awt.tree.LoadingNode;
import consulo.ui.ex.awt.tree.NodeDescriptor;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.DumbAwareAction;
import consulo.ui.ex.awt.tree.Tree;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import consulo.util.collection.JBIterable;
import consulo.ui.ex.awt.tree.TreeUtil;
import javax.annotation.Nonnull;

import javax.swing.*;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import static javax.swing.tree.TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION;

public class TreeSpeedSearch extends SpeedSearchBase<JTree> {
  protected boolean myCanExpand;

  private static final Convertor<TreePath, String> TO_STRING = path -> path.getLastPathComponent().toString();
  private final Convertor<? super TreePath, String> myToStringConvertor;
  public static final Convertor<TreePath, String> NODE_DESCRIPTOR_TOSTRING = path -> {
    NodeDescriptor descriptor = TreeUtil.getLastUserObject(NodeDescriptor.class, path);
    if (descriptor != null) return descriptor.toString();
    return TO_STRING.convert(path);
  };

  public TreeSpeedSearch(JTree tree, Convertor<? super TreePath, String> toStringConvertor) {
    this(tree, toStringConvertor, false);
  }

  public TreeSpeedSearch(JTree tree) {
    this(tree, TO_STRING);
  }

  public TreeSpeedSearch(Tree tree, Convertor<? super TreePath, String> toString) {
    this(tree, toString, false);
  }

  public TreeSpeedSearch(Tree tree, Convertor<? super TreePath, String> toString, boolean canExpand) {
    this((JTree)tree, toString, canExpand);
  }

  public TreeSpeedSearch(JTree tree, Convertor<? super TreePath, String> toString, boolean canExpand) {
    super(tree);
    setComparator(new SpeedSearchComparator(false, true));
    myToStringConvertor = toString;
    myCanExpand = canExpand;

    new MySelectAllAction(tree, this).registerCustomShortcutSet(tree, null);
  }

  @Override
  protected void selectElement(Object element, String selectedText) {
    TreeUtil.selectPath(myComponent, (TreePath)element);
  }

  @Override
  protected int getSelectedIndex() {
    if (myCanExpand) {
      return ArrayUtilRt.find(getAllElements(), myComponent.getSelectionPath());
    }
    int[] selectionRows = myComponent.getSelectionRows();
    return selectionRows == null || selectionRows.length == 0 ? -1 : selectionRows[0];
  }

  @Nonnull
  @Override
  protected Object[] getAllElements() {
    JBIterable<TreePath> paths;
    if (myCanExpand) {
      paths = TreeUtil.treePathTraverser(myComponent).traverse();
    }
    else {
      TreePath[] arr = new TreePath[myComponent.getRowCount()];
      for (int i = 0; i < arr.length; i++) {
        arr[i] = myComponent.getPathForRow(i);
      }
      paths = JBIterable.of(arr);
    }
    List<TreePath> result = paths.filter(o -> !(o.getLastPathComponent() instanceof LoadingNode)).toList();
    return result.toArray(new TreePath[0]);
  }

  @Override
  protected String getElementText(Object element) {
    TreePath path = (TreePath)element;
    String string = myToStringConvertor.convert(path);
    if (string == null) return TO_STRING.convert(path);
    return string;
  }

  @Nonnull
  private List<TreePath> findAllFilteredElements(String s) {
    List<TreePath> paths = new ArrayList<>();
    String _s = s.trim();

    ListIterator<Object> it = getElementIterator(0);
    while (it.hasNext()) {
      Object element = it.next();
      if (isMatchingElement(element, _s)) paths.add((TreePath)element);
    }
    return paths;
  }

  private static class MySelectAllAction extends DumbAwareAction {
    @Nonnull
    private final JTree myTree;
    @Nonnull
    private final TreeSpeedSearch mySearch;

    MySelectAllAction(@Nonnull JTree tree, @Nonnull TreeSpeedSearch search) {
      myTree = tree;
      mySearch = search;
      copyShortcutFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_SELECT_ALL));
      setEnabledInModalContext(true);
    }

    @Override
    public void update(@Nonnull AnActionEvent e) {
      e.getPresentation().setEnabled(mySearch.isPopupActive() && myTree.getSelectionModel().getSelectionMode() == DISCONTIGUOUS_TREE_SELECTION);
    }

    @Override
    public void actionPerformed(@Nonnull AnActionEvent e) {
      TreeSelectionModel sm = myTree.getSelectionModel();

      String query = mySearch.getEnteredPrefix();
      if (query == null) return;

      List<TreePath> filtered = mySearch.findAllFilteredElements(query);
      if (filtered.isEmpty()) return;

      boolean alreadySelected = sm.getSelectionCount() == filtered.size() && ContainerUtil.and(filtered, (path) -> sm.isPathSelected(path));

      if (alreadySelected) {
        TreePath anchor = myTree.getAnchorSelectionPath();

        sm.setSelectionPath(anchor);
        myTree.setAnchorSelectionPath(anchor);

        mySearch.findAndSelectElement(query);
      }
      else {
        TreePath currentElement = (TreePath)mySearch.findElement(query);
        TreePath anchor = ObjectUtils.chooseNotNull(currentElement, filtered.get(0));

        sm.setSelectionPaths(filtered.toArray(new TreePath[0]));
        myTree.setAnchorSelectionPath(anchor);
      }
    }
  }
}
