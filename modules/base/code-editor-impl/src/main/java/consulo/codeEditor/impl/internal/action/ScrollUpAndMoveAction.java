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
package consulo.codeEditor.impl.internal.action;

import consulo.annotation.component.ActionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.action.EditorActionHandler;
import consulo.codeEditor.action.EditorActionUtil;
import consulo.dataContext.DataContext;
import jakarta.annotation.Nonnull;

/**
 * Moves editor viewport one visual line up. Caret is also moved one line up if it becomes off-screen.
 *
 * @author Denis Zhdanov
 * @since 2012-01-13
 */
@ActionImpl(id = "EditorScrollUpAndMove")
public class ScrollUpAndMoveAction extends InactiveEditorAction {
    public ScrollUpAndMoveAction() {
        super(new Handler());
    }

    private static class Handler extends EditorActionHandler {
        @Override
        public void execute(@Nonnull Editor editor, DataContext dataContext) {
            EditorActionUtil.scrollRelatively(editor, -1, 0, true);
        }
    }
}
