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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 18, 2002
 * Time: 5:49:15 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package consulo.ide.impl.idea.openapi.editor.actions;

import consulo.codeEditor.Editor;
import consulo.codeEditor.action.EditorAction;
import consulo.codeEditor.action.EditorActionHandler;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.ide.impl.idea.find.FindUtil;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.image.Image;
import jakarta.annotation.Nullable;

public class ReplaceAction extends EditorAction {
    private static class Handler extends EditorActionHandler {
        @Override
        public void execute(Editor editor, DataContext dataContext) {
            Project project = DataManager.getInstance().getDataContext(editor.getComponent()).getData(Project.KEY);
            FindUtil.replace(project, editor);
        }

        @Override
        public boolean isEnabled(Editor editor, DataContext dataContext) {
            Project project = DataManager.getInstance().getDataContext(editor.getComponent()).getData(Project.KEY);
            return project != null;
        }
    }

    public ReplaceAction() {
        super(new IncrementalFindAction.Handler(true));
    }

    @Nullable
    @Override
    protected Image getTemplateIcon() {
        return PlatformIconGroup.actionsReplace();
    }
}
