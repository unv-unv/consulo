/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package consulo.ide.impl.idea.execution.filters.impl;

import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorEx;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.execution.localize.ExecutionLocalize;
import consulo.execution.ui.console.FileHyperlinkInfo;
import consulo.execution.ui.console.HyperlinkInfoBase;
import consulo.execution.ui.console.HyperlinkInfoFactory;
import consulo.fileEditor.FileEditorManager;
import consulo.fileEditor.impl.internal.OpenFileDescriptorImpl;
import consulo.ide.impl.idea.ide.util.gotoByName.GotoFileCellRenderer;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.project.Project;
import consulo.project.ui.wm.WindowManager;
import consulo.ui.ex.RelativePoint;
import consulo.ui.ex.popup.JBPopup;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

class MultipleFilesHyperlinkInfo extends HyperlinkInfoBase implements FileHyperlinkInfo {
  private final List<? extends VirtualFile> myVirtualFiles;
  private final int myLineNumber;
  private final Project myProject;
  private final HyperlinkInfoFactory.HyperlinkHandler myAction;

  MultipleFilesHyperlinkInfo(@Nonnull List<? extends VirtualFile> virtualFiles, int lineNumber, @Nonnull Project project) {
    this(virtualFiles, lineNumber, project, null);
  }

  MultipleFilesHyperlinkInfo(@Nonnull List<? extends VirtualFile> virtualFiles, int lineNumber, @Nonnull Project project, @Nullable HyperlinkInfoFactory.HyperlinkHandler action) {
    myVirtualFiles = virtualFiles;
    myLineNumber = lineNumber;
    myProject = project;
    myAction = action;
  }

  @Override
  public void navigate(@Nonnull final Project project, @Nullable RelativePoint hyperlinkLocationPoint) {
    List<PsiFile> currentFiles = new ArrayList<>();
    Editor originalEditor;
    if (hyperlinkLocationPoint != null) {
      DataManager dataManager = DataManager.getInstance();
      DataContext dataContext = dataManager.getDataContext(hyperlinkLocationPoint.getOriginalComponent());
      originalEditor = dataContext.getData(Editor.KEY);
    }
    else {
      originalEditor = null;
    }

    Application.get().runReadAction(() -> {
      for (VirtualFile file : myVirtualFiles) {
        if (!file.isValid()) continue;

        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile != null) {
          PsiElement navigationElement = psiFile.getNavigationElement(); // Sources may be downloaded.
          if (navigationElement instanceof PsiFile) {
            currentFiles.add((PsiFile)navigationElement);
            continue;
          }
          currentFiles.add(psiFile);
        }
      }
    });

    if (currentFiles.isEmpty()) return;

    if (currentFiles.size() == 1) {
      open(currentFiles.get(0).getVirtualFile(), originalEditor);
    }
    else {
      JFrame frame = WindowManager.getInstance().getFrame(project);
      int width = frame != null ? frame.getSize().width : 200;
      JBPopup popup = JBPopupFactory.getInstance()
        .createPopupChooserBuilder(currentFiles)
        .setRenderer(new GotoFileCellRenderer(width))
        .setTitle(ExecutionLocalize.popupTitleChooseTargetFile().get())
        .setItemChosenCallback(file -> open(file.getVirtualFile(), originalEditor))
        .createPopup();
      if (hyperlinkLocationPoint != null) {
        popup.show(hyperlinkLocationPoint);
      }
      else {
        popup.showInFocusCenter();
      }
    }
  }

  private void open(@Nonnull VirtualFile file, Editor originalEditor) {
    Document document = FileDocumentManager.getInstance().getDocument(file);
    int offset = 0;
    if (document != null && myLineNumber >= 0 && myLineNumber < document.getLineCount()) {
      offset = document.getLineStartOffset(myLineNumber);
    }
    OpenFileDescriptorImpl descriptor = new OpenFileDescriptorImpl(myProject, file, offset);
    Editor editor = FileEditorManager.getInstance(myProject).openTextEditor(descriptor, true);
    if (myAction != null && editor != null) {
      if (editor instanceof EditorEx) {
        ((EditorEx)editor).setCaretEnabled(false);
        try {
          myAction.onLinkFollowed(myProject, file, editor, originalEditor);
        }
        finally {
          ((EditorEx)editor).setCaretEnabled(true);
        }
      }
      else {
        myAction.onLinkFollowed(myProject, file, editor, originalEditor);
      }
    }
  }

  @Nullable
  @Override
  public OpenFileDescriptorImpl getDescriptor() {
    VirtualFile file = getPreferredFile();
    return file != null ? new OpenFileDescriptorImpl(myProject, file, myLineNumber, 0) : null;
  }

  @Nullable
  private VirtualFile getPreferredFile() {
    return ContainerUtil.getFirstItem(myVirtualFiles);
  }
}
