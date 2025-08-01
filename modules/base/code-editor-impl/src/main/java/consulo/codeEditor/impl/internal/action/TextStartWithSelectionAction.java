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
import consulo.codeEditor.*;
import consulo.codeEditor.action.EditorActionHandler;
import consulo.dataContext.DataContext;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * @author max
 * @since 2002-05-14
 */
@ActionImpl(id = "EditorTextStartWithSelection")
public class TextStartWithSelectionAction extends TextComponentEditorAction {
    public TextStartWithSelectionAction() {
        super(new Handler());
    }

    private static class Handler extends EditorActionHandler {
        @Override
        public void doExecute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
            List<Caret> carets = editor.getCaretModel().getAllCarets();
            if (editor.isColumnMode() && editor.getCaretModel().supportsMultipleCarets()) {
                if (caret == null) { // normally we're always called with null caret
                    caret = carets.get(0) == editor.getCaretModel().getPrimaryCaret() ? carets.get(carets.size() - 1) : carets.get(0);
                }
                LogicalPosition leadSelectionPosition = editor.visualToLogicalPosition(caret.getLeadSelectionPosition());
                LogicalPosition targetPosition = new LogicalPosition(0, 0);
                editor.getSelectionModel().setBlockSelection(leadSelectionPosition, targetPosition);
            }
            else {
                if (caret == null) { // normally we're always called with null caret
                    caret = carets.get(carets.size() - 1);
                }
                int selectionStart = caret.getLeadSelectionOffset();
                caret.moveToOffset(0);
                caret.setSelection(selectionStart, 0);
            }
            ScrollingModel scrollingModel = editor.getScrollingModel();
            scrollingModel.disableAnimation();
            scrollingModel.scrollToCaret(ScrollType.RELATIVE);
            scrollingModel.enableAnimation();
        }
    }
}
