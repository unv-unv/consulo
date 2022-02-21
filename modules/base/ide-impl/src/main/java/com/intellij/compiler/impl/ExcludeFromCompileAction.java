/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.compiler.impl;

import com.intellij.ide.errorTreeView.ErrorTreeElement;
import com.intellij.ide.errorTreeView.ErrorTreeNodeDescriptor;
import com.intellij.ide.errorTreeView.GroupingElement;
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import consulo.ui.ex.awt.tree.NodeDescriptor;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.compiler.CompilerBundle;
import consulo.compiler.CompilerManager;
import consulo.compiler.setting.ExcludeEntryDescription;
import consulo.project.Project;
import com.intellij.openapi.vcs.FileStatusManager;
import consulo.virtualFileSystem.VirtualFile;

/**
* @author Eugene Zhuravlev
*         Date: 9/12/12
*/
class ExcludeFromCompileAction extends AnAction {
  private final Project myProject;
  private final NewErrorTreeViewPanel myErrorTreeView;

  public ExcludeFromCompileAction(Project project, NewErrorTreeViewPanel errorTreeView) {
    super(CompilerBundle.message("actions.exclude.from.compile.text"));
    myProject = project;
    myErrorTreeView = errorTreeView;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    VirtualFile file = getSelectedFile();

    if (file != null && file.isValid()) {
      ExcludeEntryDescription description = new ExcludeEntryDescription(file, false, true, myProject);
      CompilerManager.getInstance(myProject).getExcludedEntriesConfiguration().addExcludeEntryDescription(description);
      FileStatusManager.getInstance(myProject).fileStatusesChanged();
    }
  }

  @javax.annotation.Nullable
  private VirtualFile getSelectedFile() {
    final ErrorTreeNodeDescriptor descriptor = myErrorTreeView.getSelectedNodeDescriptor();
    ErrorTreeElement element = descriptor != null? descriptor.getElement() : null;
    if (element != null && !(element instanceof GroupingElement)) {
      NodeDescriptor parent = descriptor.getParentDescriptor();
      if (parent instanceof ErrorTreeNodeDescriptor) {
        element = ((ErrorTreeNodeDescriptor)parent).getElement();
      }
    }
    return element instanceof GroupingElement? ((GroupingElement)element).getFile() : null;
  }

  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final boolean isApplicable = getSelectedFile() != null;
    presentation.setEnabled(isApplicable);
    presentation.setVisible(isApplicable);
  }
}
