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
import consulo.codeEditor.action.EditorAction;
import consulo.codeEditor.action.EditorActionHandler;
import consulo.codeEditor.action.EditorWriteActionHandler;
import consulo.codeEditor.internal.CodeEditorInternalHelper;
import consulo.codeEditor.localize.CodeEditorLocalize;
import consulo.codeEditor.util.EditorModificationUtil;
import consulo.dataContext.DataContext;
import consulo.document.Document;
import consulo.ui.ex.action.IdeActions;
import consulo.undoRedo.CommandProcessor;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author max
 * @since 2002-05-13
 */
@ActionImpl(id = IdeActions.ACTION_EDITOR_ENTER)
public class EnterAction extends EditorAction {
    public EnterAction() {
        super(new Handler());
        setInjectedContext(true);
    }

    @Override
    public int getExecuteWeight() {
        return Integer.MIN_VALUE;
    }

    private static class Handler extends EditorWriteActionHandler {
        public Handler() {
            super(true);
        }

        @RequiredWriteAction
        @Override
        public void executeWriteAction(Editor editor, @Nullable Caret caret, DataContext dataContext) {
            CommandProcessor.getInstance().setCurrentCommandName(CodeEditorLocalize.typingCommandName());
            insertNewLineAtCaret(editor);
        }

        @Override
        public boolean isEnabledForCaret(@Nonnull Editor editor, @Nonnull Caret caret, DataContext dataContext) {
            return !editor.isOneLineMode();
        }
    }

    public static void insertNewLineAtCaret(Editor editor) {
        CodeEditorInternalHelper.getInstance().hideCursorInEditor(editor);
        Document document = editor.getDocument();
        int caretLine = editor.getCaretModel().getLogicalPosition().line;
        if (!editor.isInsertMode()) {
            int lineCount = document.getLineCount();
            if (caretLine < lineCount) {
                if (caretLine == lineCount - 1) {
                    document.insertString(document.getTextLength(), "\n");
                }
                LogicalPosition pos = new LogicalPosition(caretLine + 1, 0);
                editor.getCaretModel().moveToLogicalPosition(pos);
                editor.getSelectionModel().removeSelection();
                EditorModificationUtil.scrollToCaret(editor);
            }
            return;
        }
        EditorModificationUtil.deleteSelectedText(editor);
        // Smart indenting here:
        CharSequence text = document.getCharsSequence();

        int indentLineNum = caretLine;
        int lineLength = 0;
        if (document.getLineCount() > 0) {
            for (; indentLineNum >= 0; indentLineNum--) {
                lineLength = document.getLineEndOffset(indentLineNum) - document.getLineStartOffset(indentLineNum);
                if (lineLength > 0) {
                    break;
                }
            }
        }
        else {
            indentLineNum = -1;
        }

        int colNumber = editor.getCaretModel().getLogicalPosition().column;
        StringBuilder buf = new StringBuilder();
        if (indentLineNum >= 0) {
            int lineStartOffset = document.getLineStartOffset(indentLineNum);
            for (int i = 0; i < lineLength; i++) {
                char c = text.charAt(lineStartOffset + i);
                if (c != ' ' && c != '\t') {
                    break;
                }
                if (i >= colNumber) {
                    break;
                }
                buf.append(c);
            }
        }
        int caretOffset = editor.getCaretModel().getOffset();
        String s = "\n" + buf;
        document.insertString(caretOffset, s);
        editor.getCaretModel().moveToOffset(caretOffset + s.length());
        EditorModificationUtil.scrollToCaret(editor);
        editor.getSelectionModel().removeSelection();
    }
}
