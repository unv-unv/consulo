// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ui.ex.awt.tree;

import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import java.awt.*;

/**
 * Temporary solution to restore selection in a tree.
 * It should be integrated into TreeUI to process path removing more accurately.
 */
public final class RestoreSelectionListener implements TreeSelectionListener {
  @Override
  public void valueChanged(TreeSelectionEvent event) {
    if (null == event.getNewLeadSelectionPath()) {
      TreePath path = event.getOldLeadSelectionPath();
      if (path != null && null != path.getParentPath()) {
        Object source = event.getSource();
        if (source instanceof JTree) {
          JTree tree = (JTree)source;
          if (tree.getSelectionModel().isSelectionEmpty()) {
            EventQueue.invokeLater(() -> {
              // restore selection later, because nodes are removed before they are inserted
              if (tree.getSelectionModel().isSelectionEmpty()) {
                // restore a path selection only if nothing is selected now
                SimpleReference<TreePath> reference = new SimpleReference<>();
                TreeVisitor visitor = new TreeVisitor.ByTreePath<>(path, o -> o) {
                  @Nonnull
                  @Override
                  protected Action visit(@Nonnull TreePath path, Object component) {
                    Action action = super.visit(path, component);
                    if (action == Action.CONTINUE || action == Action.INTERRUPT) reference.set(path);
                    return action;
                  }
                };
                TreeUtil.visitVisibleRows(tree, visitor);
                tree.setSelectionPath(reference.get());
              }
            });
          }
        }
      }
    }
  }
}
