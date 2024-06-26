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
package consulo.ide.impl.idea.packageDependencies.ui;

import consulo.util.lang.Comparing;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.*;
import java.util.function.BiPredicate;

public abstract class TreeExpansionMonitor<T> {

  public static TreeExpansionMonitor<DefaultMutableTreeNode> install(final JTree tree) {
    return install(tree, (o1, o2) -> Comparing.equal(o1.getUserObject(), o2.getUserObject()));
  }

  public static TreeExpansionMonitor<DefaultMutableTreeNode> install(final JTree tree, final BiPredicate<DefaultMutableTreeNode, DefaultMutableTreeNode> equality) {
    return new TreeExpansionMonitor<>(tree) {
      @Override
      protected TreePath findPathByNode(final DefaultMutableTreeNode node) {
        Enumeration enumeration = ((DefaultMutableTreeNode)tree.getModel().getRoot()).breadthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
          final Object nextElement = enumeration.nextElement();
          if (nextElement instanceof DefaultMutableTreeNode) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode)nextElement;
            if (equality.test(child, node)) {
              return new TreePath(child.getPath());
            }
          }
        }
        return null;
      }
    };
  }

  private final Set<TreePath> myExpandedPaths = new HashSet<>();
  private List<T> mySelectionNodes = new ArrayList<>();
  private final JTree myTree;
  private boolean myFrozen = false;

  protected TreeExpansionMonitor(JTree tree) {
    myTree = tree;
    myTree.getSelectionModel().addTreeSelectionListener(e -> {
      if (myFrozen) return;
      mySelectionNodes = new ArrayList<>();
      TreePath[] paths = myTree.getSelectionPaths();
      if (paths != null) {
        for (TreePath path : paths) {
          mySelectionNodes.add((T)path.getLastPathComponent());
        }
      }
    });

    myTree.addTreeExpansionListener(new TreeExpansionListener() {
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        if (myFrozen) return;
        TreePath path = event.getPath();
        if (path != null) {
          myExpandedPaths.add(path);
        }
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
        if (myFrozen) return;
        TreePath path = event.getPath();
        if (path != null) {
          TreePath[] allPaths = myExpandedPaths.toArray(new TreePath[myExpandedPaths.size()]);
          for (TreePath treePath : allPaths) {
            if (treePath.equals(path) || path.isDescendant(treePath)) {
              myExpandedPaths.remove(treePath);
            }
          }
        }
      }
    });
  }

  public void freeze() {
    myFrozen = true;
  }


  public void restore() {
    freeze();
    for (T mySelectionNode : mySelectionNodes) {
      myTree.getSelectionModel().addSelectionPath(findPathByNode(mySelectionNode));
    }
    for (final TreePath myExpandedPath : myExpandedPaths) {
      myTree.expandPath(findPathByNode((T)myExpandedPath.getLastPathComponent()));
    }
    myFrozen = false;
  }

  protected abstract TreePath findPathByNode(final T node);

  public boolean isFreeze() {
    return myFrozen;
  }
}
