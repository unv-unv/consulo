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

import consulo.codeEditor.action.EditorActionUtil;
import consulo.language.editor.inject.EditorWindow;
import consulo.dataContext.DataContext;
import consulo.undoRedo.CommandProcessor;
import consulo.ide.impl.idea.openapi.editor.*;
import consulo.codeEditor.action.EditorAction;
import consulo.codeEditor.action.EditorWriteActionHandler;
import consulo.ui.ex.awt.util.MacUIUtil;
import jakarta.annotation.Nonnull;
import consulo.annotation.access.RequiredWriteAction;
import consulo.document.Document;
import consulo.codeEditor.Caret;
import consulo.codeEditor.Editor;
import consulo.codeEditor.FoldRegion;

/**
 * @author max
 * @since 2002-05-13
 */
public class BackspaceAction extends EditorAction {
    public BackspaceAction() {
        super(new Handler());
    }

    private static class Handler extends EditorWriteActionHandler {
        private Handler() {
            super(true);
        }

        @RequiredWriteAction
        @Override
        public void executeWriteAction(Editor editor, Caret caret, DataContext dataContext) {
            MacUIUtil.hideCursor();
            CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
            if (editor instanceof EditorWindow editorWindow) {
                // manipulate actual document/editor instead of injected
                // since the latter have trouble finding the right location of caret movement in the case of multi-shred injected fragments
                editor = editorWindow.getDelegate();
            }
            doBackSpaceAtCaret(editor);
        }
    }

    private static void doBackSpaceAtCaret(@Nonnull Editor editor) {
        if (editor.getSelectionModel().hasSelection()) {
            EditorModificationUtil.deleteSelectedText(editor);
            return;
        }

        int lineNumber = editor.getCaretModel().getLogicalPosition().line;
        int colNumber = editor.getCaretModel().getLogicalPosition().column;
        Document document = editor.getDocument();
        int offset = editor.getCaretModel().getOffset();
        if (colNumber > 0) {
            if (EditorModificationUtil.calcAfterLineEnd(editor) > 0) {
                int columnShift = -1;
                editor.getCaretModel().moveCaretRelatively(columnShift, 0, false, false, true);
            }
            else {
                EditorModificationUtil.scrollToCaret(editor);
                editor.getSelectionModel().removeSelection();

                FoldRegion region = editor.getFoldingModel().getCollapsedRegionAtOffset(offset - 1);
                if (region != null && region.shouldNeverExpand()) {
                    document.deleteString(region.getStartOffset(), region.getEndOffset());
                    editor.getCaretModel().moveToOffset(region.getStartOffset());
                }
                else {
                    document.deleteString(offset - 1, offset);
                    editor.getCaretModel().moveToOffset(offset - 1, true);
                }
            }
        }
        else if (lineNumber > 0) {
            int separatorLength = document.getLineSeparatorLength(lineNumber - 1);
            int lineEnd = document.getLineEndOffset(lineNumber - 1) + separatorLength;
            document.deleteString(lineEnd - separatorLength, lineEnd);
            editor.getCaretModel().moveToOffset(lineEnd - separatorLength);
            EditorModificationUtil.scrollToCaret(editor);
            editor.getSelectionModel().removeSelection();
            // Do not group delete newline and other deletions.
            CommandProcessor commandProcessor = CommandProcessor.getInstance();
            commandProcessor.setCurrentCommandGroupId(null);
        }
    }
}
