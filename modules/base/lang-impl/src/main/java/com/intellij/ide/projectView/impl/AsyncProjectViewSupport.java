// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.CopyPasteUtil;
import com.intellij.ide.bookmarks.Bookmark;
import com.intellij.ide.bookmarks.BookmarksListener;
import com.intellij.ide.projectView.ProjectViewPsiTreeChangeListener;
import com.intellij.ide.projectView.impl.ProjectViewPaneSelectionHelper.SelectionDescriptor;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.ProblemListener;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.tree.*;
import com.intellij.ui.tree.project.ProjectFileNodeUpdater;
import com.intellij.util.SmartList;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.tree.TreeUtil;
import javax.annotation.Nonnull;

import consulo.logging.Logger;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static com.intellij.ide.util.treeView.TreeState.expand;
import static com.intellij.ui.tree.project.ProjectFileNode.findArea;

class AsyncProjectViewSupport {
  private static final Logger LOG = Logger.getInstance(AsyncProjectViewSupport.class);
  private final ProjectFileNodeUpdater myNodeUpdater;
  private final StructureTreeModel myStructureTreeModel;
  private final AsyncTreeModel myAsyncTreeModel;

  AsyncProjectViewSupport(@Nonnull Disposable parent, @Nonnull Project project, @Nonnull JTree tree, @Nonnull AbstractTreeStructure structure, @Nonnull Comparator<NodeDescriptor> comparator) {
    myStructureTreeModel = new StructureTreeModel<>(structure, comparator, parent);
    myAsyncTreeModel = new AsyncTreeModel(myStructureTreeModel, parent);
    myAsyncTreeModel.setRootImmediately(myStructureTreeModel.getRootImmediately());
    myNodeUpdater = new ProjectFileNodeUpdater(project, myStructureTreeModel.getInvoker()) {
      @Override
      protected void updateStructure(boolean fromRoot, @Nonnull Set<? extends VirtualFile> updatedFiles) {
        if (fromRoot) {
          updateAll(null);
        }
        else {
          long time = System.currentTimeMillis();
          LOG.debug("found ", updatedFiles.size(), " changed files");
          TreeCollector<VirtualFile> collector = TreeCollector.VirtualFileRoots.create();
          for (VirtualFile file : updatedFiles) {
            if (!file.isDirectory()) file = file.getParent();
            if (file != null && findArea(file, project) != null) collector.add(file);
          }
          List<VirtualFile> roots = collector.get();
          LOG.debug("found ", roots.size(), " roots in ", System.currentTimeMillis() - time, "ms");
          myStructureTreeModel.getInvoker().runOrInvokeLater(() -> roots.forEach(root -> updateByFile(root, true)));
        }
      }
    };
    setModel(tree, myAsyncTreeModel);
    MessageBusConnection connection = project.getMessageBus().connect(parent);
    connection.subscribe(BookmarksListener.TOPIC, new BookmarksListener() {
      @Override
      public void bookmarkAdded(@Nonnull Bookmark bookmark) {
        updateByFile(bookmark.getFile(), false);
      }

      @Override
      public void bookmarkRemoved(@Nonnull Bookmark bookmark) {
        updateByFile(bookmark.getFile(), false);
      }

      @Override
      public void bookmarkChanged(@Nonnull Bookmark bookmark) {
        updateByFile(bookmark.getFile(), false);
      }
    });
    PsiManager.getInstance(project).addPsiTreeChangeListener(new ProjectViewPsiTreeChangeListener(project) {
      @Override
      protected boolean isFlattenPackages() {
        return structure instanceof AbstractProjectTreeStructure && ((AbstractProjectTreeStructure)structure).isFlattenPackages();
      }

      @Override
      protected AbstractTreeUpdater getUpdater() {
        return null;
      }

      @Override
      protected DefaultMutableTreeNode getRootNode() {
        return null;
      }

      @Override
      protected void addSubtreeToUpdateByRoot() {
        myNodeUpdater.updateFromRoot();
      }

      @Override
      protected boolean addSubtreeToUpdateByElement(@Nonnull PsiElement element) {
        VirtualFile file = PsiUtilCore.getVirtualFile(element);
        if (file != null) {
          myNodeUpdater.updateFromFile(file);
        }
        else {
          updateByElement(element, true);
        }
        return true;
      }
    }, parent);
    FileStatusManager.getInstance(project).addFileStatusListener(new FileStatusListener() {
      @Override
      public void fileStatusesChanged() {
        updateAllPresentations();
      }

      @Override
      public void fileStatusChanged(@Nonnull VirtualFile file) {
        updateByFile(file, false);
      }
    }, parent);
    CopyPasteUtil.addDefaultListener(parent, element -> updateByElement(element, false));
    project.getMessageBus().connect(parent).subscribe(ProblemListener.TOPIC, new ProblemListener() {
      @Override
      public void problemsAppeared(@Nonnull VirtualFile file) {
        updatePresentationsFromRootTo(file);
      }

      @Override
      public void problemsDisappeared(@Nonnull VirtualFile file) {
        updatePresentationsFromRootTo(file);
      }
    });
  }

  public void setComparator(@Nonnull Comparator<? super NodeDescriptor> comparator) {
    myStructureTreeModel.setComparator(comparator);
  }

  public void select(JTree tree, Object object, VirtualFile file) {
    if (object instanceof AbstractTreeNode) {
      AbstractTreeNode node = (AbstractTreeNode)object;
      object = node.getValue();
      LOG.debug("select AbstractTreeNode");
    }
    PsiElement element = object instanceof PsiElement ? (PsiElement)object : null;
    LOG.debug("select object: ", object, " in file: ", file);
    SmartList<TreePath> pathsToSelect = new SmartList<>();
    TreeVisitor visitor = AbstractProjectViewPane.createVisitor(element, file, pathsToSelect);
    if (visitor != null) {
      //noinspection CodeBlock2Expr
      myNodeUpdater.updateImmediately(() -> expand(tree, promise -> {
        myAsyncTreeModel.accept(visitor).onProcessed(path -> {
          if (selectPaths(tree, pathsToSelect, visitor) || element == null || file == null || Registry.is("async.project.view.support.extra.select.disabled")) {
            promise.setResult(null);
          }
          else {
            // try to search the specified file instead of element,
            // because Kotlin files cannot represent containing functions
            pathsToSelect.clear();
            TreeVisitor fileVisitor = AbstractProjectViewPane.createVisitor(null, file, pathsToSelect);
            myAsyncTreeModel.accept(fileVisitor).onProcessed(path2 -> {
              selectPaths(tree, pathsToSelect, fileVisitor);
              promise.setResult(null);
            });
          }
        });
      }));
    }
  }

  private static boolean selectPaths(@Nonnull JTree tree, @Nonnull List<TreePath> paths, @Nonnull TreeVisitor visitor) {
    if (paths.isEmpty()) return false;
    if (paths.size() > 1) {
      if (visitor instanceof ProjectViewNodeVisitor) {
        ProjectViewNodeVisitor nodeVisitor = (ProjectViewNodeVisitor)visitor;
        return selectPaths(tree, new SelectionDescriptor(nodeVisitor.getElement(), nodeVisitor.getFile(), paths));
      }
      if (visitor instanceof ProjectViewFileVisitor) {
        ProjectViewFileVisitor fileVisitor = (ProjectViewFileVisitor)visitor;
        return selectPaths(tree, new SelectionDescriptor(null, fileVisitor.getElement(), paths));
      }
    }
    TreePath path = paths.get(0);
    tree.expandPath(path); // request to expand found path
    TreeUtil.selectPath(tree, path); // select and scroll to center
    return true;
  }

  private static boolean selectPaths(@Nonnull JTree tree, @Nonnull SelectionDescriptor selectionDescriptor) {
    List<? extends TreePath> adjustedPaths = ProjectViewPaneSelectionHelper.getAdjustedPaths(selectionDescriptor);
    adjustedPaths.forEach(it -> tree.expandPath(it));
    TreeUtil.selectPaths(tree, adjustedPaths);
    return true;
  }

  public void updateAll(Runnable onDone) {
    LOG.debug(new RuntimeException("reload a whole tree"));
    Promise<?> promise = myStructureTreeModel.invalidate();
    if (onDone != null) promise.onSuccess(res -> myAsyncTreeModel.onValidThread(onDone));
  }

  public void update(@Nonnull TreePath path, boolean structure) {
    myStructureTreeModel.invalidate(path, structure);
  }

  public void update(@Nonnull List<? extends TreePath> list, boolean structure) {
    for (TreePath path : list) update(path, structure);
  }

  public void updateByFile(@Nonnull VirtualFile file, boolean structure) {
    LOG.debug(structure ? "updateChildrenByFile: " : "updatePresentationByFile: ", file);
    update(null, file, structure);
  }

  public void updateByElement(@Nonnull PsiElement element, boolean structure) {
    LOG.debug(structure ? "updateChildrenByElement: " : "updatePresentationByElement: ", element);
    update(element, null, structure);
  }

  private void update(PsiElement element, VirtualFile file, boolean structure) {
    SmartList<TreePath> list = new SmartList<>();
    TreeVisitor visitor = AbstractProjectViewPane.createVisitor(element, file, list);
    if (visitor != null) acceptAndUpdate(visitor, list, structure);
  }

  private void acceptAndUpdate(@Nonnull TreeVisitor visitor, List<? extends TreePath> list, boolean structure) {
    myAsyncTreeModel.accept(visitor, false).onSuccess(path -> update(list, structure));
  }

  private void updatePresentationsFromRootTo(@Nonnull VirtualFile file) {
    // find first valid parent for removed file
    while (!file.isValid()) {
      file = file.getParent();
      if (file == null) return;
    }
    SmartList<TreePath> structures = new SmartList<>();
    SmartList<TreePath> presentations = new SmartList<>();
    myAsyncTreeModel.accept(new ProjectViewFileVisitor(file, structures::add) {
      @Nonnull
      @Override
      protected Action visit(@Nonnull TreePath path, @Nonnull AbstractTreeNode node, @Nonnull VirtualFile element) {
        Action action = super.visit(path, node, element);
        if (action == Action.CONTINUE) presentations.add(path);
        return action;
      }
    }, false).onSuccess(path -> {
      update(presentations, false);
      update(structures, true);
    });
  }

  private void updateAllPresentations() {
    SmartList<TreePath> list = new SmartList<>();
    acceptAndUpdate(new TreeVisitor() {
      @Nonnull
      @Override
      public Action visit(@Nonnull TreePath path) {
        list.add(path);
        return Action.CONTINUE;
      }
    }, list, false);
  }

  private static void setModel(@Nonnull JTree tree, @Nonnull AsyncTreeModel model) {
    RestoreSelectionListener listener = new RestoreSelectionListener();
    tree.addTreeSelectionListener(listener);
    tree.setModel(model);
    Disposer.register(model, () -> {
      tree.setModel(null);
      tree.removeTreeSelectionListener(listener);
    });
  }
}