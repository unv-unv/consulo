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
package consulo.ide.impl.idea.openapi.editor.actions;

import consulo.annotation.component.ActionImpl;
import consulo.ide.impl.idea.find.FindUtil;
import consulo.dataContext.DataManager;
import consulo.dataContext.DataContext;
import consulo.codeEditor.Editor;
import consulo.codeEditor.action.EditorAction;
import consulo.codeEditor.action.EditorActionHandler;
import consulo.platform.base.localize.ActionLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;

/**
 * @author max
 * @since 2002-05-18
 */
@ActionImpl(id = "FindWordAtCaret")
public class FindWordAtCaretAction extends EditorAction {
    private static class Handler extends EditorActionHandler {
        @Override
        @RequiredUIAccess
        public void execute(Editor editor, DataContext dataContext) {
            Project project = DataManager.getInstance().getDataContext(editor.getComponent()).getData(Project.KEY);
            FindUtil.findWordAtCaret(project, editor);
        }

        @Override
        public boolean isEnabled(Editor editor, DataContext dataContext) {
            Project project = DataManager.getInstance().getDataContext(editor.getComponent()).getData(Project.KEY);
            return project != null;
        }
    }

    public FindWordAtCaretAction() {
        super(ActionLocalize.actionFindwordatcaretText(), ActionLocalize.actionFindwordatcaretDescription(), new Handler());
    }
}
