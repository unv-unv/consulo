// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.tree.AbstractTreeNodeVisitor;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import javax.swing.tree.TreePath;
import java.util.function.Predicate;

import static com.intellij.psi.SmartPointerManager.createPointer;
import static com.intellij.psi.util.PsiUtilCore.getVirtualFile;

class ProjectViewNodeVisitor extends AbstractTreeNodeVisitor<PsiElement> {
  private final VirtualFile file;

  ProjectViewNodeVisitor(@Nonnull PsiElement element, @Nullable VirtualFile file, @Nullable Predicate<? super TreePath> predicate) {
    super(createPointer(element)::getElement, predicate);
    this.file = file;
    LOG.debug("create visitor for element: " + element);
  }

  /**
   * @return a virtual file corresponding to searching element or {@code null} if it is not set
   */
  @Nullable
  public final VirtualFile getFile() {
    return file;
  }

  @Override
  protected boolean contains(@Nonnull AbstractTreeNode node, @Nonnull PsiElement element) {
    return node instanceof ProjectViewNode && contains((ProjectViewNode)node, element) || super.contains(node, element);
  }

  private boolean contains(@Nonnull ProjectViewNode node, @Nonnull PsiElement element) {
    return contains(node, file) || contains(node, getVirtualFile(element));
  }

  private static boolean contains(@Nonnull ProjectViewNode node, VirtualFile file) {
    return file != null && node.contains(file);
  }

  @Override
  protected PsiElement getContent(@Nonnull AbstractTreeNode node) {
    Object value = node.getValue();
    return value instanceof PsiElement ? (PsiElement)value : null;
  }

  @Override
  protected boolean isAncestor(@Nonnull PsiElement content, @Nonnull PsiElement element) {
    return PsiTreeUtil.isAncestor(content, element, true);
  }
}
