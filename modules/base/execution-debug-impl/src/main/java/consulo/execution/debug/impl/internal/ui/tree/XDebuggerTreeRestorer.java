/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package consulo.execution.debug.impl.internal.ui.tree;

import consulo.execution.debug.frame.presentation.XValueExtendedPresentation;
import consulo.execution.debug.frame.presentation.XValuePresentation;
import consulo.execution.debug.impl.internal.ui.tree.node.RestorableStateNode;
import consulo.execution.debug.impl.internal.ui.tree.node.XDebuggerTreeNode;
import consulo.execution.debug.impl.internal.ui.tree.node.XValueContainerNode;
import consulo.execution.debug.impl.internal.ui.tree.node.XValueNodeImpl;
import consulo.execution.debug.ui.XNamedTreeNode;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class XDebuggerTreeRestorer implements XDebuggerTreeListener, TreeSelectionListener {
  public static final String SELECTION_PATH_PROPERTY = "selection.path";
  private final XDebuggerTree myTree;
  private final Rectangle myLastVisibleNodeRect;
  private final Map<XDebuggerTreeNode, XDebuggerTreeState.NodeInfo> myNode2State = new HashMap<>();
  private final Map<RestorableStateNode, XDebuggerTreeState.NodeInfo> myNode2ParentState = new HashMap<>();
  private boolean myStopRestoringSelection;
  private boolean myInsideRestoring;
  private TreePath mySelectionPath;
  private boolean myFinished;

  public XDebuggerTreeRestorer(final XDebuggerTree tree, Rectangle lastVisibleNodeRect) {
    myTree = tree;
    myLastVisibleNodeRect = lastVisibleNodeRect;
    mySelectionPath = (TreePath)myTree.getClientProperty(SELECTION_PATH_PROPERTY);
    myTree.putClientProperty(SELECTION_PATH_PROPERTY, null);
    tree.addTreeListener(this);
    tree.addTreeSelectionListener(this);
  }

  private void restoreChildren(final XDebuggerTreeNode treeNode, final XDebuggerTreeState.NodeInfo nodeInfo) {
    if (nodeInfo.isExpanded()) {
      myTree.expandPath(treeNode.getPath());
      treeNode.getLoadedChildren().forEach(child -> restoreNode(child, nodeInfo));
      myNode2State.put(treeNode, nodeInfo);
    }
  }

  void restore(final XDebuggerTreeNode treeNode, final XDebuggerTreeState.NodeInfo parentInfo) {
    if (treeNode instanceof RestorableStateNode) {
      doRestoreNode((RestorableStateNode)treeNode, parentInfo);
    }
    else {
      restoreChildren(treeNode, parentInfo);
    }
  }

  private void restoreNode(final XDebuggerTreeNode treeNode, final XDebuggerTreeState.NodeInfo parentInfo) {
    if (treeNode instanceof RestorableStateNode) {
      RestorableStateNode node = (RestorableStateNode)treeNode;
      if (node.isComputed()) {
        doRestoreNode(node, parentInfo.getChild(node));
      }
      else {
        myNode2ParentState.put(node, parentInfo);
      }
    }
  }

  private void doRestoreNode(final RestorableStateNode treeNode, final XDebuggerTreeState.NodeInfo nodeInfo) {
    if (nodeInfo != null) {
      if (!checkExtendedModified(treeNode) && !(Comparing.equal(nodeInfo.getValue(), treeNode.getRawValue()))) {
        treeNode.markChanged();
      }
      if (!myStopRestoringSelection && nodeInfo.isSelected() && mySelectionPath == null) {
        try {
          myInsideRestoring = true;
          myTree.addSelectionPath(treeNode.getPath());
        }
        finally {
          myInsideRestoring = false;
        }
      }

      if (!(treeNode.isComputed() && treeNode.isLeaf())) { // do not restore computed leafs children
        restoreChildren((XDebuggerTreeNode)treeNode, nodeInfo);
      }
    }
    else {
      if (!checkExtendedModified(treeNode)) {
        treeNode.markChanged();
      }
      if (mySelectionPath != null && !myStopRestoringSelection && pathsEqual(mySelectionPath, treeNode.getPath())) {
        myTree.addSelectionPath(treeNode.getPath());
      }
    }
  }

  // comparing only named nodes
  private static boolean pathsEqual(@Nonnull TreePath path1, @Nonnull TreePath path2) {
    if (path1.getPathCount() != path2.getPathCount()) {
      return false;
    }
    do {
      Object component1 = path1.getLastPathComponent();
      Object component2 = path2.getLastPathComponent();
      if (component1 instanceof XNamedTreeNode && component2 instanceof XNamedTreeNode) {
        if (!Comparing.equal(((XNamedTreeNode)component1).getName(), ((XNamedTreeNode)component2).getName())) {
          return false;
        }
      }
      path1 = path1.getParentPath();
      path2 = path2.getParentPath();
    } while (path1 != null && path2 != null);
    return true;
  }

  private static boolean checkExtendedModified(RestorableStateNode treeNode) {
    if (treeNode instanceof XValueNodeImpl) {
      XValuePresentation presentation = ((XValueNodeImpl)treeNode).getValuePresentation();
      if (presentation instanceof XValueExtendedPresentation) {
        if (((XValueExtendedPresentation)presentation).isModified()) {
          treeNode.markChanged();
        }
        return true;
      }
    }
    return false;
  }

  @Override
  public void nodeLoaded(@Nonnull final RestorableStateNode node, final String name) {
    XDebuggerTreeState.NodeInfo parentInfo = myNode2ParentState.remove(node);
    if (parentInfo != null) {
      doRestoreNode(node, parentInfo.getChild(node));
    }
    disposeIfFinished();
  }

  private void disposeIfFinished() {
    if (myNode2ParentState.isEmpty() && myNode2State.isEmpty()) {
      myFinished = true;
      if (myLastVisibleNodeRect != null) {
        myTree.scrollRectToVisible(myLastVisibleNodeRect);
      }
      dispose();
    }
  }

  @Override
  public void childrenLoaded(@Nonnull final XDebuggerTreeNode node, @Nonnull final List<XValueContainerNode<?>> children, final boolean last) {
    XDebuggerTreeState.NodeInfo nodeInfo = myNode2State.get(node);
    if (nodeInfo != null) {
      for (XDebuggerTreeNode child : children) {
        restoreNode(child, nodeInfo);
      }
    }
    if (last) {
      myNode2State.remove(node);
      disposeIfFinished();
    }
  }

  public void dispose() {
    myNode2ParentState.clear();
    myNode2State.clear();
    myTree.removeTreeListener(this);
    myTree.removeTreeSelectionListener(this);
  }

  public boolean isFinished() {
    return myFinished;
  }

  @Override
  public void valueChanged(TreeSelectionEvent e) {
    if (!myInsideRestoring) {
      myStopRestoringSelection = true;
    }
  }
}
