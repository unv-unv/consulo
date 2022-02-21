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
package com.intellij.openapi.fileChooser.impl;

import consulo.ui.ex.awt.tree.AbstractTreeBuilder;
import consulo.ui.ex.awt.tree.AbstractTreeStructure;
import consulo.ui.ex.awt.tree.NodeDescriptor;
import consulo.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileChooser.ex.RootFileElement;
import consulo.application.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.StatusBarProgress;
import consulo.application.util.SystemInfo;
import consulo.virtualFileSystem.*;
import consulo.virtualFileSystem.event.VirtualFileAdapter;
import consulo.virtualFileSystem.event.VirtualFileEvent;
import consulo.virtualFileSystem.event.VirtualFileMoveEvent;
import consulo.virtualFileSystem.event.VirtualFilePropertyEvent;

import javax.annotation.Nonnull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.util.Comparator;

/**
 * @author Yura Cangea
 */
public class FileTreeBuilder extends AbstractTreeBuilder {
  private final FileChooserDescriptor myChooserDescriptor;

  public FileTreeBuilder(JTree tree,
                         DefaultTreeModel treeModel,
                         AbstractTreeStructure treeStructure,
                         Comparator<NodeDescriptor> comparator,
                         FileChooserDescriptor chooserDescriptor,
                         @SuppressWarnings("UnusedParameters") Runnable onInitialized) {
    super(tree, treeModel, treeStructure, comparator, false);
    myChooserDescriptor = chooserDescriptor;

    initRootNode();

    VirtualFileAdapter listener = new VirtualFileAdapter() {
      @Override
      public void propertyChanged(@Nonnull VirtualFilePropertyEvent event) {
        doUpdate();
      }

      @Override
      public void fileCreated(@Nonnull VirtualFileEvent event) {
        doUpdate();
      }

      @Override
      public void fileDeleted(@Nonnull VirtualFileEvent event) {
        doUpdate();
      }

      @Override
      public void fileMoved(@Nonnull VirtualFileMoveEvent event) {
        doUpdate();
      }

      private void doUpdate() {
        queueUpdateFrom(getRootNode(), false);
      }
    };
    VirtualFileManager.getInstance().addVirtualFileListener(listener, this);
  }

  @Override
  protected boolean isAlwaysShowPlus(NodeDescriptor nodeDescriptor) {
    Object element = nodeDescriptor.getElement();
    if (element != null) {
      FileElement descriptor = (FileElement)element;
      VirtualFile file = descriptor.getFile();
      if (file != null) {
        if (myChooserDescriptor.isChooseJarContents() && FileElement.isArchive(file)) {
          return true;
        }
        return file.isDirectory();
      }
    }
    return true;
  }

  @Override
  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    if (nodeDescriptor.getElement() instanceof RootFileElement) {
      return true;
    }
    else if (!SystemInfo.isWindows) {
      NodeDescriptor parent = nodeDescriptor.getParentDescriptor();
      return parent != null && parent.getElement() instanceof RootFileElement;
    }

    return false;
  }

  @Override
  protected ProgressIndicator createProgressIndicator() {
    return new StatusBarProgress();
  }
}
