/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package consulo.ide.impl.idea.codeInsight.editorActions.moveLeftRight;

import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Caret;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.document.Document;
import consulo.document.internal.DocumentEx;
import consulo.document.util.TextRange;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.codeEditor.action.EditorLastActionTracker;
import consulo.codeEditor.action.EditorWriteActionHandler;
import consulo.language.editor.moveLeftRight.MoveElementLeftRightHandler;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.IdeActions;
import consulo.util.lang.Range;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

public class MoveElementLeftRightActionHandler extends EditorWriteActionHandler {
  private static final Comparator<PsiElement> BY_OFFSET = (o1, o2) -> o1.getTextOffset() - o2.getTextOffset();

  private static final Set<String> OUR_ACTIONS = new HashSet<String>(Arrays.asList(
          IdeActions.MOVE_ELEMENT_LEFT,
          IdeActions.MOVE_ELEMENT_RIGHT
  ));

  private final boolean myIsLeft;

  public MoveElementLeftRightActionHandler(boolean isLeft) {
    super(true);
    myIsLeft = isLeft;
  }

  @Override
  @RequiredUIAccess
  protected boolean isEnabledForCaret(@Nonnull Editor editor, @Nonnull Caret caret, DataContext dataContext) {
    Project project = editor.getProject();
    if (project == null) return false;
    Document document = editor.getDocument();
    if (!(document instanceof DocumentEx)) return false;
    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    psiDocumentManager.commitDocument(document);
    PsiFile file = psiDocumentManager.getPsiFile(document);
    if (file == null || !file.isValid()) return false;
    PsiElement[] elementList = getElementList(file, caret.getSelectionStart(), caret.getSelectionEnd());
    return elementList != null;
  }

  @Nullable
  @RequiredUIAccess
  private static PsiElement[] getElementList(@Nonnull PsiFile file, int rangeStart, int rangeEnd) {
    PsiElement startElement = file.findElementAt(rangeStart);
    if (startElement == null) return null;
    PsiElement endElement = rangeEnd > rangeStart ? file.findElementAt(rangeEnd - 1) : startElement;
    if (endElement == null) return null;
    PsiElement element = PsiTreeUtil.findCommonParent(startElement, endElement);
    while (element != null) {
      List<MoveElementLeftRightHandler> handlers = MoveElementLeftRightHandler.forLanguage(element.getLanguage());
      for (MoveElementLeftRightHandler handler : handlers) {
        PsiElement[] elementList = handler.getMovableSubElements(element);
        if (elementList.length > 1) {
          Arrays.sort(elementList, BY_OFFSET);
          PsiElement first = elementList[0];
          PsiElement last = elementList[elementList.length - 1];
          if (rangeStart >= first.getTextRange().getStartOffset() && rangeEnd <= last.getTextRange().getEndOffset() &&
              (rangeStart >= first.getTextRange().getEndOffset() || rangeEnd <= last.getTextRange().getStartOffset())) {
            return elementList;
          }
        }
      }
      element = element.getParent();
    }
    return null;
  }

  @RequiredWriteAction
  @Override
  public void executeWriteAction(Editor editor, @Nullable Caret caret, DataContext dataContext) {
    assert caret != null;
    DocumentEx document = (DocumentEx)editor.getDocument();
    Project project = editor.getProject();
    assert project != null;
    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    PsiFile file = psiDocumentManager.getPsiFile(document);
    assert file != null;
    int selectionStart = caret.getSelectionStart();
    int selectionEnd = caret.getSelectionEnd();
    assert selectionStart <= selectionEnd;
    PsiElement[] elementList = getElementList(file, selectionStart, selectionEnd);
    assert elementList != null;

    Range<Integer> elementRange = findRangeOfElementsToMove(elementList, selectionStart, selectionEnd);
    if (elementRange == null) return;

    if (!OUR_ACTIONS.contains(EditorLastActionTracker.getInstance().getLastActionId())) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("move.element.left.right");
    }

    int toMoveStart = elementList[elementRange.getFrom()].getTextRange().getStartOffset();
    int toMoveEnd = elementList[elementRange.getTo()].getTextRange().getEndOffset();
    int otherIndex = myIsLeft ? elementRange.getFrom() - 1 : elementRange.getTo() + 1;
    int otherStart = elementList[otherIndex].getTextRange().getStartOffset();
    int otherEnd = elementList[otherIndex].getTextRange().getEndOffset();

    selectionStart = trim(selectionStart, toMoveStart, toMoveEnd);
    selectionEnd = trim(selectionEnd, toMoveStart, toMoveEnd);
    int caretOffset = trim(caret.getOffset(), toMoveStart, toMoveEnd);

    int caretShift;
    if (toMoveStart < otherStart) {
      document.moveText(toMoveStart, toMoveEnd, otherStart);
      document.moveText(otherStart, otherEnd, toMoveStart);
      caretShift = otherEnd - toMoveEnd;
    }
    else {
      document.moveText(otherStart, otherEnd, toMoveStart);
      document.moveText(toMoveStart, toMoveEnd, otherStart);
      caretShift = otherStart - toMoveStart;
    }
    caret.moveToOffset(caretOffset + caretShift);
    caret.setSelection(selectionStart + caretShift, selectionEnd + caretShift);
  }

  @Nullable
  @RequiredWriteAction
  private Range<Integer> findRangeOfElementsToMove(@Nonnull PsiElement[] elements, int startOffset, int endOffset) {
    int startIndex = elements.length;
    int endIndex = -1;
    if (startOffset == endOffset) {
      for (int i = 0; i < elements.length; i++) {
        if (elements[i].getTextRange().containsOffset(startOffset)) {
          startIndex = endIndex = i;
          break;
        }
      }
    }
    else {
      for (int i = 0; i < elements.length; i++) {
        PsiElement psiElement = elements[i];
        TextRange range = psiElement.getTextRange();
        if (i < startIndex && startOffset < range.getEndOffset()) startIndex = i;
        if (endOffset > range.getStartOffset()) endIndex = i; else break;
      }
    }
    return startIndex > endIndex || (myIsLeft ? startIndex == 0 : endIndex == elements.length - 1)
           ? null
           : new Range<Integer>(startIndex, endIndex);
  }

  private static int trim(int offset, int rangeStart, int rangeEnd) {
    return Math.max(rangeStart, Math.min(rangeEnd, offset));
  }
}
