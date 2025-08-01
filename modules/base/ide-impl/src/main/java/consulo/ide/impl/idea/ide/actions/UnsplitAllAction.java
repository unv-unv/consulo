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
import consulo.fileEditor.internal.FileEditorManagerEx;
import consulo.platform.base.localize.ActionLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import jakarta.annotation.Nonnull;

/**
 * @author Vladimir Kondratyev
 */
@ActionImpl(id = "UnsplitAll")
public final class UnsplitAllAction extends SplitterActionBase {
    public UnsplitAllAction() {
        super(ActionLocalize.actionUnsplitallText(), ActionLocalize.actionUnsplitallDescription());
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(@Nonnull AnActionEvent event) {
        Project project = event.getRequiredData(Project.KEY);
        FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
        //VirtualFile file = fileEditorManager.getSelectedFiles()[0];
        fileEditorManager.unsplitAllWindow();
    }

    @Override
    protected boolean isActionEnabled(Project project) {
        FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
        return fileEditorManager.getWindowSplitCount() > 2;
    }
}
