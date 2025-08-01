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

import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ActionImpl;
import consulo.codeEditor.Caret;
import consulo.codeEditor.Editor;
import consulo.codeEditor.LogicalPosition;
import consulo.codeEditor.ScrollType;
import consulo.codeEditor.action.EditorWriteActionHandler;
import consulo.dataContext.DataContext;
import consulo.document.Document;
import consulo.document.util.DocumentUtil;
import consulo.platform.base.localize.ActionLocalize;

@ActionImpl(id = "EditorJoinLines")
public class JoinLinesAction extends TextComponentEditorAction {
    private static class Handler extends EditorWriteActionHandler {
        public Handler() {
            super(true);
        }

        @Override
        @RequiredWriteAction
        public void executeWriteAction(Editor editor, Caret caret, DataContext dataContext) {
            Document doc = editor.getDocument();

            LogicalPosition caretPosition = caret.getLogicalPosition();
            int startLine = caretPosition.line;
            int endLine = startLine + 1;
            if (caret.hasSelection()) {
                startLine = doc.getLineNumber(caret.getSelectionStart());
                endLine = doc.getLineNumber(caret.getSelectionEnd());
                if (doc.getLineStartOffset(endLine) == caret.getSelectionEnd()) {
                    endLine--;
                }
            }

            int[] caretRestoreOffset = new int[]{-1};
            int lineCount = endLine - startLine;
            int line = startLine;

            DocumentUtil.executeInBulk(
                doc,
                lineCount > 1000,
                () -> {
                    for (int i = 0; i < lineCount; i++) {
                        if (line >= doc.getLineCount() - 1) {
                            break;
                        }
                        CharSequence text = doc.getCharsSequence();
                        int end = doc.getLineEndOffset(line) + doc.getLineSeparatorLength(line);
                        int start = end - doc.getLineSeparatorLength(line);
                        while (start > 0 && (text.charAt(start) == ' ' || text.charAt(start) == '\t')) start--;
                        if (caretRestoreOffset[0] == -1) {
                            caretRestoreOffset[0] = start + 1;
                        }
                        while (end < doc.getTextLength() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) end++;
                        doc.replaceString(start, end, " ");
                    }
                }
            );

            if (caret.hasSelection()) {
                caret.moveToOffset(caret.getSelectionEnd());
            }
            else if (caretRestoreOffset[0] != -1) {
                caret.moveToOffset(caretRestoreOffset[0]);
                editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
                caret.removeSelection();
            }
        }
    }

    public JoinLinesAction() {
        super(ActionLocalize.actionEditorjoinlinesText(), new Handler());
    }
}
