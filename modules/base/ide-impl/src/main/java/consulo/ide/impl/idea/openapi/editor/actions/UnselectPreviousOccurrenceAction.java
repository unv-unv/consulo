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
package consulo.ide.impl.idea.openapi.editor.actions;

import consulo.annotation.component.ActionImpl;
import consulo.dataContext.DataContext;
import consulo.codeEditor.Caret;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.codeEditor.action.EditorAction;

import consulo.platform.base.localize.ActionLocalize;
import jakarta.annotation.Nullable;

@ActionImpl(id = "UnselectPreviousOccurrence")
public class UnselectPreviousOccurrenceAction extends EditorAction {
    public UnselectPreviousOccurrenceAction() {
        super(ActionLocalize.actionUnselectpreviousoccurrenceText(), new Handler());
    }

    private static class Handler extends SelectOccurrencesActionHandler {
        @Override
        public boolean isEnabled(Editor editor, DataContext dataContext) {
            return super.isEnabled(editor, dataContext) && editor.getCaretModel().supportsMultipleCarets();
        }

        @Override
        public void doExecute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
            if (editor.getCaretModel().getCaretCount() > 1) {
                editor.getCaretModel().removeCaret(editor.getCaretModel().getPrimaryCaret());
            }
            else {
                editor.getSelectionModel().removeSelection();
            }
            getAndResetNotFoundStatus(editor);
            editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        }
    }
}
