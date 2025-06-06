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
package consulo.ide.impl.idea.openapi.editor.impl;

import consulo.annotation.component.ServiceImpl;
import consulo.ide.impl.idea.codeInsight.editorActions.TextBlockTransferable;
import consulo.ide.impl.idea.codeInsight.editorActions.TextBlockTransferableData;
import consulo.codeEditor.*;
import consulo.logging.Logger;
import consulo.ide.impl.idea.openapi.editor.*;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.CopyPasteManager;
import consulo.document.util.TextRange;
import consulo.application.util.LineTokenizer;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

@Singleton
@ServiceImpl
public class EditorCopyPasteHelperImpl extends EditorCopyPasteHelper {
  private static final Logger LOG = Logger.getInstance(EditorCopyPasteHelperImpl.class);

  @Override
  @RequiredUIAccess
  public void copySelectionToClipboard(@Nonnull Editor editor) {
    UIAccess.assertIsUIThread();
    List<TextBlockTransferableData> extraData = new ArrayList<TextBlockTransferableData>();
    String s = editor.getCaretModel().supportsMultipleCarets() ? getSelectedTextForClipboard(editor, extraData)
                                                               : editor.getSelectionModel().getSelectedText();
    if (s == null) return;

    s = TextBlockTransferable.convertLineSeparators(s, "\n", extraData);
    Transferable contents = editor.getCaretModel().supportsMultipleCarets() ? new TextBlockTransferable(s, extraData, null) : new StringSelection(s);
    CopyPasteManager.getInstance().setContents(contents);
  }

  public static String getSelectedTextForClipboard(@Nonnull Editor editor, @Nonnull Collection<TextBlockTransferableData> extraDataCollector) {
    final StringBuilder buf = new StringBuilder();
    String separator = "";
    List<Caret> carets = editor.getCaretModel().getAllCarets();
    int[] startOffsets = new int[carets.size()];
    int[] endOffsets = new int[carets.size()];
    for (int i = 0; i < carets.size(); i++) {
      buf.append(separator);
      String caretSelectedText = carets.get(i).getSelectedText();
      startOffsets[i] = buf.length();
      if (caretSelectedText != null) {
        buf.append(caretSelectedText);
      }
      endOffsets[i] = buf.length();
      separator = "\n";
    }
    extraDataCollector.add(new CaretStateTransferableData(startOffsets, endOffsets));
    return buf.toString();
  }

  @Nullable
  @Override
  public TextRange[] pasteFromClipboard(@Nonnull Editor editor) {
    CopyPasteManager manager = CopyPasteManager.getInstance();
    if (manager.areDataFlavorsAvailable(DataFlavor.stringFlavor)) {
      Transferable clipboardContents = manager.getContents();
      if (clipboardContents != null) {
        return pasteTransferable(editor, clipboardContents);
      }
    }
    return null;
  }

  @Nullable
  @Override
  public TextRange[] pasteTransferable(final @Nonnull Editor editor, @Nonnull Transferable content) {
    String text = getStringContent(content);
    if (text == null) return null;

    if (editor.getCaretModel().supportsMultipleCarets()) {
      int caretCount = editor.getCaretModel().getCaretCount();
      if (caretCount == 1 && editor.isColumnMode()) {
        int pastedLineCount = LineTokenizer.calcLineCount(text, true);
        EditorModificationUtil.deleteSelectedText(editor);
        Caret caret = editor.getCaretModel().getPrimaryCaret();
        for (int i = 0; i < pastedLineCount - 1; i++) {
          caret = caret.clone(false);
          if (caret == null) {
            break;
          }
        }
        caretCount = editor.getCaretModel().getCaretCount();
      }
      CaretStateTransferableData caretData = null;
      try {
        caretData = content.isDataFlavorSupported(CaretStateTransferableData.FLAVOR)
                    ? (CaretStateTransferableData)content.getTransferData(CaretStateTransferableData.FLAVOR) : null;
      }
      catch (Exception e) {
        LOG.error(e);
      }
      final TextRange[] ranges = new TextRange[caretCount];
      final Iterator<String> segments = new ClipboardTextPerCaretSplitter().split(text, caretData, caretCount).iterator();
      final int[] index = {0};
      editor.getCaretModel().runForEachCaret(new CaretAction() {
        @Override
        public void perform(Caret caret) {
          String segment = segments.next();
          int caretOffset = caret.getOffset();
          ranges[index[0]++] = new TextRange(caretOffset, caretOffset + segment.length());
          EditorModificationUtil.insertStringAtCaret(editor, segment, false, true);
        }
      });
      return ranges;
    }
    else {
      int caretOffset = editor.getCaretModel().getOffset();
      EditorModificationUtil.insertStringAtCaret(editor, text, false, true);
      return new TextRange[] { new TextRange(caretOffset, caretOffset + text.length())};
    }
  }

  @Nullable
  private static String getStringContent(@Nonnull Transferable content) {
    RawText raw = RawText.fromTransferable(content);
    if (raw != null) return raw.rawText;

    try {
      return (String)content.getTransferData(DataFlavor.stringFlavor);
    }
    catch (UnsupportedFlavorException ignore) { }
    catch (IOException ignore) { }

    return null;
  }
}
