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
package consulo.codeEditor.impl.internal.action;

import consulo.annotation.component.ActionImpl;
import consulo.codeEditor.action.EditorActionUtil;
import consulo.dataContext.DataContext;
import consulo.codeEditor.action.EditorAction;
import consulo.codeEditor.action.EditorActionHandler;
import consulo.codeEditor.Caret;
import consulo.codeEditor.CaretAction;
import consulo.codeEditor.Editor;

import jakarta.annotation.Nullable;

/**
 * @author max
 * @since 2002-05-13
 */
@ActionImpl(id = "EditorUpWithSelection")
public class MoveCaretUpWithSelectionAction extends EditorAction {
    public MoveCaretUpWithSelectionAction() {
        super(new Handler());
    }

    private static class Handler extends EditorActionHandler {
        @Override
        public void doExecute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
            if (!editor.getCaretModel().supportsMultipleCarets()) {
                editor.getCaretModel().moveCaretRelatively(0, -1, true, editor.isColumnMode(), true);
                return;
            }
            if (editor.isColumnMode()) {
                EditorActionUtil.cloneOrRemoveCaret(editor, caret == null ? editor.getCaretModel().getPrimaryCaret() : caret, true);
            }
            else {
                CaretAction caretAction = new CaretAction() {
                    @Override
                    public void perform(Caret caret) {
                        caret.moveCaretRelatively(0, -1, true, true);
                    }
                };
                if (caret == null) {
                    editor.getCaretModel().runForEachCaret(caretAction);
                }
                else {
                    caretAction.perform(caret);
                }
            }
        }
    }
}
