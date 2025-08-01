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
package consulo.ide.impl.idea.ide.actions;

import consulo.annotation.component.ActionImpl;
import consulo.application.dumb.DumbAware;
import consulo.fileEditor.FileEditorManager;
import consulo.fileEditor.FileEditorWindow;
import consulo.fileEditor.internal.FileEditorManagerEx;
import consulo.ide.localize.IdeLocalize;
import consulo.localize.LocalizeValue;
import consulo.platform.base.localize.ActionLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.IdeActions;
import consulo.ui.ex.action.Presentation;
import consulo.undoRedo.CommandProcessor;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

@ActionImpl(id = IdeActions.ACTION_CLOSE_ALL_EDITORS)
public class CloseAllEditorsAction extends AnAction implements DumbAware {
    public CloseAllEditorsAction() {
        super(ActionLocalize.actionClosealleditorsText(), ActionLocalize.actionClosealleditorsDescription());
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getRequiredData(Project.KEY);
        CommandProcessor.getInstance().newCommand()
            .project(project)
            .name(IdeLocalize.commandCloseAllEditors())
            .run(() -> {
                FileEditorWindow window = e.getData(FileEditorWindow.DATA_KEY);
                if (window != null) {
                    VirtualFile[] files = window.getFiles();
                    for (VirtualFile file : files) {
                        window.closeFile(file);
                    }
                    return;
                }
                FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
                VirtualFile selectedFile = fileEditorManager.getSelectedFiles()[0];
                VirtualFile[] openFiles = fileEditorManager.getSiblings(selectedFile);
                for (VirtualFile openFile : openFiles) {
                    fileEditorManager.closeFile(openFile);
                }
            });
    }

    @Override
    public void update(@Nonnull AnActionEvent event) {
        Presentation presentation = event.getPresentation();
        FileEditorWindow editorWindow = event.getData(FileEditorWindow.DATA_KEY);
        LocalizeValue text = editorWindow != null && editorWindow.inSplitter()
            ? IdeLocalize.actionCloseAllEditorsInTabGroup()
            : IdeLocalize.actionCloseAllEditors();
        presentation.setTextValue(text);
        Project project = event.getData(Project.KEY);
        if (project == null) {
            presentation.setEnabled(false);
            return;
        }
        presentation.setEnabled(FileEditorManager.getInstance(project).getSelectedFiles().length > 0);
    }
}
