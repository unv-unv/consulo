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
package consulo.codeEditor.action;

import consulo.codeEditor.*;
import consulo.codeEditor.internal.CodeEditorInternalHelper;
import consulo.codeEditor.RealEditor;
import consulo.codeEditor.util.EditorModificationUtil;
import consulo.codeEditor.util.EditorUtil;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.util.lang.CharArrayUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.awt.*;
import java.util.List;

/**
 * @author max
 * @since 2002-06-06
 */
public class EditorActionUtil {
  public static final Object EDIT_COMMAND_GROUP = Key.create("EditGroup");
  public static final Object DELETE_COMMAND_GROUP = Key.create("DeleteGroup");

  private EditorActionUtil() {
  }

  /**
   * Tries to change given editor's viewport position in vertical dimension by the given number of visual lines.
   *
   * @param editor      target editor which viewport position should be changed
   * @param lineShift   defines viewport position's vertical change length
   * @param columnShift defines viewport position's horizontal change length
   * @param moveCaret   flag that identifies whether caret should be moved if its current position becomes off-screen
   */
  public static void scrollRelatively(@Nonnull Editor editor, int lineShift, int columnShift, boolean moveCaret) {
    if (lineShift != 0) {
      editor.getScrollingModel().scrollVertically(editor.getScrollingModel().getVerticalScrollOffset() + lineShift * editor.getLineHeight());
    }
    if (columnShift != 0) {
      editor.getScrollingModel().scrollHorizontally(editor.getScrollingModel().getHorizontalScrollOffset() + columnShift * CodeEditorInternalHelper.getInstance().getSpaceWidth(editor));
    }

    if (!moveCaret) {
      return;
    }

    Rectangle viewRectangle = editor.getScrollingModel().getVisibleArea();
    int lineNumber = editor.getCaretModel().getVisualPosition().line;
    VisualPosition startPos = editor.xyToVisualPosition(new Point(0, viewRectangle.y));
    int start = startPos.line + 1;
    VisualPosition endPos = editor.xyToVisualPosition(new Point(0, viewRectangle.y + viewRectangle.height));
    int end = endPos.line - 2;
    if (lineNumber < start) {
      editor.getCaretModel().moveCaretRelatively(0, start - lineNumber, false, false, true);
    }
    else if (lineNumber > end) {
      editor.getCaretModel().moveCaretRelatively(0, end - lineNumber, false, false, true);
    }
  }

  public static void moveCaretRelativelyAndScroll(@Nonnull Editor editor, int columnShift, int lineShift, boolean withSelection) {
    Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    VisualPosition pos = editor.getCaretModel().getVisualPosition();
    Point caretLocation = editor.visualPositionToXY(pos);
    int caretVShift = caretLocation.y - visibleArea.y;

    editor.getCaretModel().moveCaretRelatively(columnShift, lineShift, withSelection, false, false);

    VisualPosition caretPos = editor.getCaretModel().getVisualPosition();
    Point caretLocation2 = editor.visualPositionToXY(caretPos);
    final boolean scrollToCaret = !(editor instanceof RealEditor) || ((RealEditor)editor).isScrollToCaret();
    if (scrollToCaret) {
      editor.getScrollingModel().scrollVertically(caretLocation2.y - caretVShift);
    }
  }

  public static void indentLine(Project project, @Nonnull Editor editor, int lineNumber, int indent) {
    int caretOffset = editor.getCaretModel().getOffset();
    int newCaretOffset = indentLine(project, editor, lineNumber, indent, caretOffset);
    editor.getCaretModel().moveToOffset(newCaretOffset);
  }

  // This method avoid moving caret directly, so it's suitable for invocation in bulk mode.
  // It does calculate (and returns) target caret position.
  public static int indentLine(Project project, @Nonnull Editor editor, int lineNumber, int indent, int caretOffset) {
    EditorSettings editorSettings = editor.getSettings();
    int tabSize = editorSettings.getTabSize(project);
    Document document = editor.getDocument();
    CharSequence text = document.getImmutableCharSequence();
    int spacesEnd = 0;
    int lineStart = 0;
    int lineEnd = 0;
    int tabsEnd = 0;
    if (lineNumber < document.getLineCount()) {
      lineStart = document.getLineStartOffset(lineNumber);
      lineEnd = document.getLineEndOffset(lineNumber);
      spacesEnd = lineStart;
      boolean inTabs = true;
      for (; spacesEnd <= lineEnd; spacesEnd++) {
        if (spacesEnd == lineEnd) {
          break;
        }
        char c = text.charAt(spacesEnd);
        if (c != '\t') {
          if (inTabs) {
            inTabs = false;
            tabsEnd = spacesEnd;
          }
          if (c != ' ') break;
        }
      }
      if (inTabs) {
        tabsEnd = lineEnd;
      }
    }
    int newCaretOffset = caretOffset;
    if (newCaretOffset >= lineStart && newCaretOffset < lineEnd && spacesEnd == lineEnd) {
      spacesEnd = newCaretOffset;
      tabsEnd = Math.min(spacesEnd, tabsEnd);
    }
    int oldLength = getSpaceWidthInColumns(text, lineStart, spacesEnd, tabSize);
    tabsEnd = getSpaceWidthInColumns(text, lineStart, tabsEnd, tabSize);

    int newLength = oldLength + indent;
    if (newLength < 0) {
      newLength = 0;
    }
    tabsEnd += indent;
    if (tabsEnd < 0) tabsEnd = 0;
    if (!shouldUseSmartTabs(project, editor)) tabsEnd = newLength;
    StringBuilder buf = new StringBuilder(newLength);
    for (int i = 0; i < newLength; ) {
      if (tabSize > 0 && editorSettings.isUseTabCharacter(project) && i + tabSize <= tabsEnd) {
        buf.append('\t');
        //noinspection AssignmentToForLoopParameter
        i += tabSize;
      }
      else {
        buf.append(' ');
        //noinspection AssignmentToForLoopParameter
        i++;
      }
    }

    int newSpacesEnd = lineStart + buf.length();
    if (newCaretOffset >= spacesEnd) {
      newCaretOffset += buf.length() - (spacesEnd - lineStart);
    }
    else if (newCaretOffset >= lineStart && newCaretOffset < spacesEnd && newCaretOffset > newSpacesEnd) {
      newCaretOffset = newSpacesEnd;
    }

    if (buf.length() > 0) {
      if (spacesEnd > lineStart) {
        document.replaceString(lineStart, spacesEnd, buf.toString());
      }
      else {
        document.insertString(lineStart, buf.toString());
      }
    }
    else {
      if (spacesEnd > lineStart) {
        document.deleteString(lineStart, spacesEnd);
      }
    }

    return newCaretOffset;
  }

  private static int getSpaceWidthInColumns(CharSequence seq, int startOffset, int endOffset, int tabSize) {
    int result = 0;
    for (int i = startOffset; i < endOffset; i++) {
      if (seq.charAt(i) == '\t') {
        result = (result / tabSize + 1) * tabSize;
      }
      else {
        result++;
      }
    }
    return result;
  }

  private static boolean shouldUseSmartTabs(Project project, @Nonnull Editor editor) {
    return CodeEditorInternalHelper.getInstance().shouldUseSmartTabs(project, editor);
  }

  public static boolean isWordOrLexemeStart(@Nonnull Editor editor, int offset, boolean isCamel) {
    CharSequence chars = editor.getDocument().getCharsSequence();
    return isWordStart(chars, offset, isCamel) || !isWordEnd(chars, offset, isCamel) && isLexemeBoundary(editor, offset);
  }

  public static boolean isWordOrLexemeEnd(@Nonnull Editor editor, int offset, boolean isCamel) {
    CharSequence chars = editor.getDocument().getCharsSequence();
    return isWordEnd(chars, offset, isCamel) || !isWordStart(chars, offset, isCamel) && isLexemeBoundary(editor, offset);
  }

  /**
   * Finds out whether there's a boundary between two lexemes of different type at given offset.
   */
  public static boolean isLexemeBoundary(@Nonnull Editor editor, int offset) {
    if (!(editor instanceof EditorEx) || offset <= 0 || offset >= editor.getDocument().getTextLength()) return false;
    if (CharArrayUtil.isEmptyOrSpaces(editor.getDocument().getImmutableCharSequence(), offset - 1, offset + 1)) return false;
    EditorHighlighter highlighter = editor.getHighlighter();
    HighlighterIterator it = highlighter.createIterator(offset);
    if (it.getStart() != offset) {
      return false;
    }
    Object rightToken = it.getTokenType();
    it.retreat();
    Object leftToken = it.getTokenType();
    return !Comparing.equal(leftToken, rightToken);
  }

  private static boolean isLexemeBoundary(@Nullable Object leftTokenType, @Nullable Object rightTokenType) {
    return CodeEditorInternalHelper.getInstance().isLexemeBoundary(leftTokenType, rightTokenType);
  }

  public static boolean isWordStart(@Nonnull CharSequence text, int offset, boolean isCamel) {
    char prev = offset > 0 ? text.charAt(offset - 1) : 0;
    char current = text.charAt(offset);

    final boolean firstIsIdentifierPart = Character.isJavaIdentifierPart(prev);
    final boolean secondIsIdentifierPart = Character.isJavaIdentifierPart(current);
    if (!firstIsIdentifierPart && secondIsIdentifierPart) {
      return true;
    }

    if (isCamel && firstIsIdentifierPart && secondIsIdentifierPart && isHumpBound(text, offset, true)) {
      return true;
    }

    return (Character.isWhitespace(prev) || firstIsIdentifierPart) && !Character.isWhitespace(current) && !secondIsIdentifierPart;
  }

  private static boolean isLowerCaseOrDigit(char c) {
    return Character.isLowerCase(c) || Character.isDigit(c);
  }

  public static boolean isWordEnd(@Nonnull CharSequence text, int offset, boolean isCamel) {
    char prev = offset > 0 ? text.charAt(offset - 1) : 0;
    char current = text.charAt(offset);
    char next = offset + 1 < text.length() ? text.charAt(offset + 1) : 0;

    final boolean firstIsIdentifierPart = Character.isJavaIdentifierPart(prev);
    final boolean secondIsIdentifierPart = Character.isJavaIdentifierPart(current);
    if (firstIsIdentifierPart && !secondIsIdentifierPart) {
      return true;
    }

    if (isCamel) {
      if (firstIsIdentifierPart &&
          (Character.isLowerCase(prev) && Character.isUpperCase(current) ||
           prev != '_' && current == '_' ||
           Character.isUpperCase(prev) && Character.isUpperCase(current) && Character.isLowerCase(next))) {
        return true;
      }
    }

    return !Character.isWhitespace(prev) && !firstIsIdentifierPart && (Character.isWhitespace(current) || secondIsIdentifierPart);
  }

  /**
   * Depending on the current caret position and 'smart Home' editor settings, moves caret to the start of current visual line
   * or to the first non-whitespace character on it.
   *
   * @param isWithSelection if true - sets selection from old caret position to the new one, if false - clears selection
   * @see EditorActionUtil#moveCaretToLineStartIgnoringSoftWraps(Editor)
   */
  public static void moveCaretToLineStart(@Nonnull Editor editor, boolean isWithSelection) {
    Document document = editor.getDocument();
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = caretModel.getLogicalPosition();
    EditorSettings editorSettings = editor.getSettings();

    int logCaretLine = caretModel.getLogicalPosition().line;
    VisualPosition currentVisCaret = caretModel.getVisualPosition();
    VisualPosition caretLogLineStartVis = editor.offsetToVisualPosition(document.getLineStartOffset(logCaretLine));

    if (currentVisCaret.line > caretLogLineStartVis.line) {
      // Caret is located not at the first visual line of soft-wrapped logical line.
      if (editorSettings.isSmartHome()) {
        moveCaretToStartOfSoftWrappedLine(editor, currentVisCaret, currentVisCaret.line - caretLogLineStartVis.line);
      }
      else {
        caretModel.moveToVisualPosition(new VisualPosition(currentVisCaret.line, 0));
      }
      setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
      EditorModificationUtil.scrollToCaret(editor);
      return;
    }

    // Skip folded lines.
    int logLineToUse = logCaretLine - 1;
    while (logLineToUse >= 0 && editor.offsetToVisualPosition(document.getLineEndOffset(logLineToUse)).line == currentVisCaret.line) {
      logLineToUse--;
    }
    logLineToUse++;

    if (logLineToUse >= document.getLineCount() || !editorSettings.isSmartHome()) {
      editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(logLineToUse, 0));
    }
    else if (logLineToUse == logCaretLine) {
      int line = currentVisCaret.line;
      int column;
      if (currentVisCaret.column == 0) {
        column = findSmartIndentColumn(editor, currentVisCaret.line);
      }
      else {
        column = findFirstNonSpaceColumnOnTheLine(editor, currentVisCaret.line);
        if (column >= currentVisCaret.column) {
          column = 0;
        }
      }
      caretModel.moveToVisualPosition(new VisualPosition(line, Math.max(column, 0)));
    }
    else {
      LogicalPosition logLineEndLog = editor.offsetToLogicalPosition(document.getLineEndOffset(logLineToUse));
      VisualPosition logLineEndVis = editor.logicalToVisualPosition(logLineEndLog);
      int softWrapCount = EditorUtil.getSoftWrapCountAfterLineStart(editor, logLineEndLog);
      if (softWrapCount > 0) {
        moveCaretToStartOfSoftWrappedLine(editor, logLineEndVis, softWrapCount);
      }
      else {
        int line = logLineEndVis.line;
        int column = 0;
        if (currentVisCaret.column > 0) {
          int firstNonSpaceColumnOnTheLine = findFirstNonSpaceColumnOnTheLine(editor, currentVisCaret.line);
          if (firstNonSpaceColumnOnTheLine < currentVisCaret.column) {
            column = firstNonSpaceColumnOnTheLine;
          }
        }
        caretModel.moveToVisualPosition(new VisualPosition(line, column));
      }
    }

    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
    EditorModificationUtil.scrollToCaret(editor);
  }

  private static void moveCaretToStartOfSoftWrappedLine(@Nonnull Editor editor, VisualPosition currentVisual, int softWrappedLines) {
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition startLineLogical = editor.visualToLogicalPosition(new VisualPosition(currentVisual.line, 0));
    int startLineOffset = editor.logicalPositionToOffset(startLineLogical);
    SoftWrapModel softWrapModel = editor.getSoftWrapModel();
    SoftWrap softWrap = softWrapModel.getSoftWrap(startLineOffset);
    if (softWrap == null) {
      // Don't expect to be here.
      int column = findFirstNonSpaceColumnOnTheLine(editor, currentVisual.line);
      int columnToMove = column;
      if (column < 0 || currentVisual.column <= column && currentVisual.column > 0) {
        columnToMove = 0;
      }
      caretModel.moveToVisualPosition(new VisualPosition(currentVisual.line, columnToMove));
      return;
    }

    if (currentVisual.column > softWrap.getIndentInColumns()) {
      caretModel.moveToOffset(softWrap.getStart());
    }
    else if (currentVisual.column > 0) {
      caretModel.moveToVisualPosition(new VisualPosition(currentVisual.line, 0));
    }
    else {
      // We assume that caret is already located at zero visual column of soft-wrapped line if control flow reaches this place.
      int newVisualCaretLine = currentVisual.line - 1;
      int newVisualCaretColumn = -1;
      if (softWrappedLines > 1) {
        int offset = editor.logicalPositionToOffset(editor.visualToLogicalPosition(new VisualPosition(newVisualCaretLine, 0)));
        SoftWrap prevLineSoftWrap = softWrapModel.getSoftWrap(offset);
        if (prevLineSoftWrap != null) {
          newVisualCaretColumn = prevLineSoftWrap.getIndentInColumns();
        }
      }
      if (newVisualCaretColumn < 0) {
        newVisualCaretColumn = findFirstNonSpaceColumnOnTheLine(editor, newVisualCaretLine);
      }
      caretModel.moveToVisualPosition(new VisualPosition(newVisualCaretLine, newVisualCaretColumn));
    }
  }

  private static int findSmartIndentColumn(@Nonnull Editor editor, int visualLine) {
    for (int i = visualLine; i >= 0; i--) {
      int column = findFirstNonSpaceColumnOnTheLine(editor, i);
      if (column >= 0) {
        return column;
      }
    }
    return 0;
  }

  /**
   * Tries to find visual column that points to the first non-white space symbol at the visual line at the given editor.
   *
   * @param editor           target editor
   * @param visualLineNumber target visual line
   * @return visual column that points to the first non-white space symbol at the target visual line if the one exists;
   * <code>'-1'</code> otherwise
   */
  public static int findFirstNonSpaceColumnOnTheLine(@Nonnull Editor editor, int visualLineNumber) {
    Document document = editor.getDocument();
    VisualPosition visLine = new VisualPosition(visualLineNumber, 0);
    int logLine = editor.visualToLogicalPosition(visLine).line;
    int logLineStartOffset = document.getLineStartOffset(logLine);
    int logLineEndOffset = document.getLineEndOffset(logLine);
    LogicalPosition logLineStart = editor.offsetToLogicalPosition(logLineStartOffset);
    VisualPosition visLineStart = editor.logicalToVisualPosition(logLineStart);
    boolean newRendering = editor instanceof RealEditor;

    boolean softWrapIntroducedLine = visLineStart.line != visualLineNumber;
    if (!softWrapIntroducedLine) {
      int offset = findFirstNonSpaceOffsetInRange(document.getCharsSequence(), logLineStartOffset, logLineEndOffset);
      if (offset >= 0) {
        return newRendering ? editor.offsetToVisualPosition(offset).column : CodeEditorInternalHelper.getInstance().calcColumnNumber(editor, document.getCharsSequence(), logLineStartOffset, offset);
      }
      else {
        return -1;
      }
    }

    int lineFeedsToSkip = visualLineNumber - visLineStart.line;
    List<? extends SoftWrap> softWraps = editor.getSoftWrapModel().getSoftWrapsForLine(logLine);
    for (SoftWrap softWrap : softWraps) {
      CharSequence softWrapText = softWrap.getText();
      int softWrapLineFeedsNumber = StringUtil.countNewLines(softWrapText);

      if (softWrapLineFeedsNumber < lineFeedsToSkip) {
        lineFeedsToSkip -= softWrapLineFeedsNumber;
        continue;
      }

      // Point to the first non-white space symbol at the target soft wrap visual line or to the first non-white space symbol
      // of document line that follows it if possible.
      int softWrapTextLength = softWrapText.length();
      boolean skip = true;
      for (int j = 0; j < softWrapTextLength; j++) {
        if (softWrapText.charAt(j) == '\n') {
          skip = --lineFeedsToSkip > 0;
          continue;
        }
        if (skip) {
          continue;
        }

        int nextSoftWrapLineFeedOffset = StringUtil.indexOf(softWrapText, '\n', j, softWrapTextLength);

        int end = findFirstNonSpaceOffsetInRange(softWrapText, j, softWrapTextLength);
        if (end >= 0) {
          assert !newRendering : "Unexpected soft wrap text";
          // Non space symbol is contained at soft wrap text after offset that corresponds to the target visual line start.
          if (nextSoftWrapLineFeedOffset < 0 || end < nextSoftWrapLineFeedOffset) {
            return CodeEditorInternalHelper.getInstance().calcColumnNumber(editor, softWrapText, j, end);
          }
          else {
            return -1;
          }
        }

        if (nextSoftWrapLineFeedOffset >= 0) {
          // There are soft wrap-introduced visual lines after the target one
          return -1;
        }
      }
      int end = findFirstNonSpaceOffsetInRange(document.getCharsSequence(), softWrap.getStart(), logLineEndOffset);
      if (end >= 0) {
        return newRendering ? editor.offsetToVisualPosition(end).column : CodeEditorInternalHelper.getInstance().calcColumnNumber(editor, document.getCharsSequence(), softWrap.getStart(), end);
      }
      else {
        return -1;
      }
    }
    return -1;
  }

  public static int findFirstNonSpaceOffsetOnTheLine(@Nonnull Document document, int lineNumber) {
    int lineStart = document.getLineStartOffset(lineNumber);
    int lineEnd = document.getLineEndOffset(lineNumber);
    int result = findFirstNonSpaceOffsetInRange(document.getCharsSequence(), lineStart, lineEnd);
    return result >= 0 ? result : lineEnd;
  }

  /**
   * Tries to find non white space symbol at the given range at the given document.
   *
   * @param text  text to be inspected
   * @param start target start offset (inclusive)
   * @param end   target end offset (exclusive)
   * @return index of the first non-white space character at the given document at the given range if the one is found;
   * <code>'-1'</code> otherwise
   */
  public static int findFirstNonSpaceOffsetInRange(@Nonnull CharSequence text, int start, int end) {
    for (; start < end; start++) {
      char c = text.charAt(start);
      if (c != ' ' && c != '\t') {
        return start;
      }
    }
    return -1;
  }

  public static void moveCaretToLineEnd(@Nonnull Editor editor, boolean isWithSelection) {
    moveCaretToLineEnd(editor, isWithSelection, true);
  }

  /**
   * Moves caret to visual line end.
   *
   * @param editor                   target editor
   * @param isWithSelection          whether selection should be set from original caret position to its target position
   * @param ignoreTrailingWhitespace if <code>true</code>, line end will be determined while ignoring trailing whitespace, unless caret is
   *                                 already at so-determined target position, in which case trailing whitespace will be taken into account
   */
  public static void moveCaretToLineEnd(@Nonnull Editor editor, boolean isWithSelection, boolean ignoreTrailingWhitespace) {
    Document document = editor.getDocument();
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = caretModel.getLogicalPosition();
    SoftWrapModel softWrapModel = editor.getSoftWrapModel();

    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    if (lineNumber >= document.getLineCount()) {
      LogicalPosition pos = new LogicalPosition(lineNumber, 0);
      editor.getCaretModel().moveToLogicalPosition(pos);
      setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
      EditorModificationUtil.scrollToCaret(editor);
      return;
    }
    VisualPosition currentVisualCaret = editor.getCaretModel().getVisualPosition();
    VisualPosition visualEndOfLineWithCaret = new VisualPosition(currentVisualCaret.line, CodeEditorInternalHelper.getInstance().getLastVisualLineColumnNumber(editor, currentVisualCaret.line), true);

    // There is a possible case that the caret is already located at the visual end of line and the line is soft wrapped.
    // We want to move the caret to the end of the next visual line then.
    if (currentVisualCaret.equals(visualEndOfLineWithCaret)) {
      LogicalPosition logical = editor.visualToLogicalPosition(visualEndOfLineWithCaret);
      int offset = editor.logicalPositionToOffset(logical);
      if (offset < editor.getDocument().getTextLength()) {

        SoftWrap softWrap = softWrapModel.getSoftWrap(offset);
        if (softWrap == null) {
          // Same offset may correspond to positions on different visual lines in case of soft wraps presence
          // (all soft-wrap introduced virtual text is mapped to the same offset as the first document symbol after soft wrap).
          // Hence, we check for soft wraps presence at two offsets.
          softWrap = softWrapModel.getSoftWrap(offset + 1);
        }
        int line = currentVisualCaret.line;
        int column = currentVisualCaret.column;
        if (softWrap != null) {
          line++;
          column = CodeEditorInternalHelper.getInstance().getLastVisualLineColumnNumber(editor, line);
        }
        visualEndOfLineWithCaret = new VisualPosition(line, column, true);
      }
    }

    LogicalPosition logLineEnd = editor.visualToLogicalPosition(visualEndOfLineWithCaret);
    int offset = editor.logicalPositionToOffset(logLineEnd);
    lineNumber = logLineEnd.line;
    int newOffset = offset;

    CharSequence text = document.getCharsSequence();
    for (int i = newOffset - 1; i >= document.getLineStartOffset(lineNumber); i--) {
      if (softWrapModel.getSoftWrap(i) != null) {
        newOffset = offset;
        break;
      }
      if (text.charAt(i) != ' ' && text.charAt(i) != '\t') {
        break;
      }
      newOffset = i;
    }

    // Move to the calculated end of visual line if caret is located on a last non-white space symbols on a line and there are
    // remaining white space symbols.
    if (newOffset == offset || newOffset == caretModel.getOffset() || !ignoreTrailingWhitespace) {
      caretModel.moveToVisualPosition(visualEndOfLineWithCaret);
    }
    else {
      if (editor instanceof RealEditor) {
        caretModel.moveToLogicalPosition(editor.offsetToLogicalPosition(newOffset).leanForward(true));
      }
      else {
        caretModel.moveToOffset(newOffset);
      }
    }

    EditorModificationUtil.scrollToCaret(editor);

    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  public static void moveCaretToNextWord(@Nonnull Editor editor, boolean isWithSelection, boolean camel) {
    Document document = editor.getDocument();
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = caretModel.getLogicalPosition();

    int offset = caretModel.getOffset();
    if (offset == document.getTextLength()) {
      return;
    }

    int newOffset;

    FoldRegion currentFoldRegion = editor.getFoldingModel().getCollapsedRegionAtOffset(offset);
    if (currentFoldRegion != null) {
      newOffset = currentFoldRegion.getEndOffset();
    }
    else {
      newOffset = offset + 1;
      int lineNumber = caretModel.getLogicalPosition().line;
      if (lineNumber >= document.getLineCount()) return;
      int maxOffset = document.getLineEndOffset(lineNumber);
      if (newOffset > maxOffset) {
        if (lineNumber + 1 >= document.getLineCount()) {
          return;
        }
        maxOffset = document.getLineEndOffset(lineNumber + 1);
      }
      for (; newOffset < maxOffset; newOffset++) {
        if (isWordOrLexemeStart(editor, newOffset, camel)) {
          break;
        }
      }
      FoldRegion foldRegion = editor.getFoldingModel().getCollapsedRegionAtOffset(newOffset);
      if (foldRegion != null) {
        newOffset = foldRegion.getStartOffset();
      }
    }
    if (editor instanceof RealEditor) {
      int boundaryOffset = ((RealEditor)editor).findNearestDirectionBoundary(offset, true);
      if (boundaryOffset >= 0) {
        newOffset = Math.min(boundaryOffset, newOffset);
      }
    }
    caretModel.moveToOffset(newOffset);
    EditorModificationUtil.scrollToCaret(editor);

    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  private static void setupSelection(@Nonnull Editor editor, boolean isWithSelection, int selectionStart, @Nonnull LogicalPosition blockSelectionStart) {
    SelectionModel selectionModel = editor.getSelectionModel();
    CaretModel caretModel = editor.getCaretModel();
    if (isWithSelection) {
      if (editor.isColumnMode() && !caretModel.supportsMultipleCarets()) {
        selectionModel.setBlockSelection(blockSelectionStart, caretModel.getLogicalPosition());
      }
      else {
        selectionModel.setSelection(selectionStart, caretModel.getVisualPosition(), caretModel.getOffset());
      }
    }
    else {
      selectionModel.removeSelection();
    }

    selectNonexpandableFold(editor);
  }

  private static final Key<VisualPosition> PREV_POS = Key.create("PREV_POS");

  public static void selectNonexpandableFold(@Nonnull Editor editor) {
    final CaretModel caretModel = editor.getCaretModel();
    final VisualPosition pos = caretModel.getVisualPosition();

    VisualPosition prevPos = editor.getUserData(PREV_POS);

    if (prevPos != null) {
      int columnShift = pos.line == prevPos.line ? pos.column - prevPos.column : 0;

      int caret = caretModel.getOffset();
      final FoldRegion collapsedUnderCaret = editor.getFoldingModel().getCollapsedRegionAtOffset(caret);
      if (collapsedUnderCaret != null && collapsedUnderCaret.shouldNeverExpand()) {
        if (caret > collapsedUnderCaret.getStartOffset() && columnShift > 0) {
          caretModel.moveToOffset(collapsedUnderCaret.getEndOffset());
        }
        else if (caret + 1 < collapsedUnderCaret.getEndOffset() && columnShift < 0) {
          caretModel.moveToOffset(collapsedUnderCaret.getStartOffset());
        }
        editor.getSelectionModel().setSelection(collapsedUnderCaret.getStartOffset(), collapsedUnderCaret.getEndOffset());
      }
    }

    editor.putUserData(PREV_POS, pos);
  }

  public static void moveCaretToPreviousWord(@Nonnull Editor editor, boolean isWithSelection, boolean camel) {
    Document document = editor.getDocument();
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = caretModel.getLogicalPosition();

    int offset = editor.getCaretModel().getOffset();
    if (offset == 0) return;

    int newOffset;

    FoldRegion currentFoldRegion = editor.getFoldingModel().getCollapsedRegionAtOffset(offset - 1);
    if (currentFoldRegion != null) {
      newOffset = currentFoldRegion.getStartOffset();
    }
    else {
      int lineNumber = editor.getCaretModel().getLogicalPosition().line;
      newOffset = offset - 1;
      int minOffset = lineNumber > 0 ? document.getLineEndOffset(lineNumber - 1) : 0;
      for (; newOffset > minOffset; newOffset--) {
        if (isWordOrLexemeStart(editor, newOffset, camel)) break;
      }
      FoldRegion foldRegion = editor.getFoldingModel().getCollapsedRegionAtOffset(newOffset);
      if (foldRegion != null && newOffset > foldRegion.getStartOffset()) {
        newOffset = foldRegion.getEndOffset();
      }
    }

    if (editor instanceof RealEditor) {
      int boundaryOffset = ((RealEditor)editor).findNearestDirectionBoundary(offset, false);
      if (boundaryOffset >= 0) {
        newOffset = Math.max(boundaryOffset, newOffset);
      }
      caretModel.moveToLogicalPosition(editor.offsetToLogicalPosition(newOffset).leanForward(true));
    }
    else {
      editor.getCaretModel().moveToOffset(newOffset);
    }
    EditorModificationUtil.scrollToCaret(editor);

    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  public static void moveCaretPageUp(@Nonnull Editor editor, boolean isWithSelection) {
    int lineHeight = editor.getLineHeight();
    Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    int linesIncrement = visibleArea.height / lineHeight;
    editor.getScrollingModel().scrollVertically(visibleArea.y - visibleArea.y % lineHeight - linesIncrement * lineHeight);
    int lineShift = -linesIncrement;
    editor.getCaretModel().moveCaretRelatively(0, lineShift, isWithSelection, editor.isColumnMode(), true);
  }

  public static void moveCaretPageDown(@Nonnull Editor editor, boolean isWithSelection) {
    int lineHeight = editor.getLineHeight();
    Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    int linesIncrement = visibleArea.height / lineHeight;
    int allowedBottom = ((EditorEx)editor).getContentSize().height - visibleArea.height;
    editor.getScrollingModel().scrollVertically(Math.min(allowedBottom, visibleArea.y - visibleArea.y % lineHeight + linesIncrement * lineHeight));
    editor.getCaretModel().moveCaretRelatively(0, linesIncrement, isWithSelection, editor.isColumnMode(), true);
  }

  public static void moveCaretPageTop(@Nonnull Editor editor, boolean isWithSelection) {
    int lineHeight = editor.getLineHeight();
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = caretModel.getLogicalPosition();
    Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    int lineNumber = visibleArea.y / lineHeight;
    if (visibleArea.y % lineHeight > 0) {
      lineNumber++;
    }
    VisualPosition pos = new VisualPosition(lineNumber, editor.getCaretModel().getVisualPosition().column);
    editor.getCaretModel().moveToVisualPosition(pos);
    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  public static void moveCaretPageBottom(@Nonnull Editor editor, boolean isWithSelection) {
    int lineHeight = editor.getLineHeight();
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getLeadSelectionOffset();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition blockSelectionStart = caretModel.getLogicalPosition();
    Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    int lineNumber = Math.max(0, (visibleArea.y + visibleArea.height) / lineHeight - 1);
    VisualPosition pos = new VisualPosition(lineNumber, editor.getCaretModel().getVisualPosition().column);
    editor.getCaretModel().moveToVisualPosition(pos);
    setupSelection(editor, isWithSelection, selectionStart, blockSelectionStart);
  }

  public static boolean isHumpBound(@Nonnull CharSequence editorText, int offset, boolean start) {
    if (offset <= 0 || offset >= editorText.length()) return false;
    final char prevChar = editorText.charAt(offset - 1);
    final char curChar = editorText.charAt(offset);
    final char nextChar = offset + 1 < editorText.length() ? editorText.charAt(offset + 1) : 0; // 0x00 is not lowercase.

    return isLowerCaseOrDigit(prevChar) && Character.isUpperCase(curChar) ||
           start && prevChar == '_' && curChar != '_' ||
           !start && prevChar != '_' && curChar == '_' ||
           start && prevChar == '$' && Character.isLetterOrDigit(curChar) ||
           !start && Character.isLetterOrDigit(prevChar) && curChar == '$' ||
           Character.isUpperCase(prevChar) && Character.isUpperCase(curChar) && Character.isLowerCase(nextChar);
  }

  /**
   * This method moves caret to the nearest preceding visual line start, which is not a soft line wrap
   *
   * @see EditorUtil#calcCaretLineRange(Editor)
   * @see EditorActionUtil#moveCaretToLineStart(Editor, boolean)
   */
  public static void moveCaretToLineStartIgnoringSoftWraps(@Nonnull Editor editor) {
    editor.getCaretModel().moveToLogicalPosition(EditorUtil.calcCaretLineRange(editor).first);
  }

  /**
   * This method will make required expansions of collapsed region to make given offset 'visible'.
   */
  public static void makePositionVisible(@Nonnull final Editor editor, final int offset) {
    FoldingModel foldingModel = editor.getFoldingModel();
    FoldRegion collapsedRegionAtOffset;
    while ((collapsedRegionAtOffset = foldingModel.getCollapsedRegionAtOffset(offset)) != null) {
      final FoldRegion region = collapsedRegionAtOffset;
      foldingModel.runBatchFoldingOperation(() -> region.setExpanded(true));
    }
  }

  /**
   * Clones caret in a given direction if it's possible. If there already exists a caret at the given direction, removes the current caret.
   *
   * @param editor editor to perform operation in
   * @param caret  caret to work on
   * @param above  whether to clone the caret above or below
   * @return <code>false</code> if the operation cannot be performed due to current caret being at the edge (top or bottom) of the document,
   * and <code>true</code> otherwise
   */
  public static boolean cloneOrRemoveCaret(Editor editor, Caret caret, boolean above) {
    if (above && caret.getLogicalPosition().line == 0) {
      return false;
    }
    if (!above && caret.getLogicalPosition().line == editor.getDocument().getLineCount() - 1) {
      return false;
    }
    if (caret.clone(above) == null) {
      editor.getCaretModel().removeCaret(caret);
    }
    return true;
  }

  @Nonnull
  public static TextRange getRangeToWordStart(@Nonnull Editor editor, boolean isCamel, boolean handleQuoted) {
    int endOffset = editor.getCaretModel().getOffset();
    int startOffset = getPreviousCaretStopOffset(editor, CaretStopPolicy.WORD_START, isCamel, handleQuoted);
    return TextRange.create(startOffset, endOffset);
  }

  @SuppressWarnings("Duplicates")
  public static int getPreviousCaretStopOffset(@Nonnull Editor editor, @Nonnull CaretStopPolicy caretStopPolicy, boolean isCamel, boolean handleQuoted) {
    int minOffset = getPreviousLineStopOffset(editor, caretStopPolicy.getLineStop());

    final CaretStop wordStop = caretStopPolicy.getWordStop();
    if (wordStop.equals(CaretStop.NONE)) return minOffset;

    final int offset = editor.getCaretModel().getOffset();
    if (offset == minOffset) return minOffset;

    final CharSequence text = editor.getDocument().getCharsSequence();
    final HighlighterIterator tokenIterator = createHighlighterIteratorAtOffset(editor, offset - 1);

    final int newOffset = getPreviousWordStopOffset(text, wordStop, tokenIterator, offset, minOffset, isCamel);
    if (newOffset > minOffset && handleQuoted && tokenIterator != null && isTokenEnd(tokenIterator, newOffset + 1) && isQuotedToken(tokenIterator, text)) {
      // at the start of a closing quote:  "word|" <- "word" |
      // find the end of an opening quote: "|word" <- "word|"  (must be only a single step away)
      final int newOffsetAfterQuote = getPreviousWordStopOffset(text, CaretStop.BOTH, tokenIterator, newOffset, minOffset, isCamel);
      if (isTokenStart(tokenIterator, newOffsetAfterQuote - 1)) {
        return getPreviousWordStopOffset(text, wordStop, tokenIterator, newOffsetAfterQuote, minOffset, isCamel); // |"word"
      }
    }
    return newOffset;
  }

  private static int getPreviousWordStopOffset(@Nonnull CharSequence text, @Nonnull CaretStop wordStop, @Nullable HighlighterIterator tokenIterator, int offset, int minOffset, boolean isCamel) {
    int newOffset = offset - 1;
    for (; newOffset > minOffset; newOffset--) {
      final boolean isTokenBoundary = tokenIterator != null && retreatTokenOnBoundary(tokenIterator, text, newOffset);
      if (isWordStopOffset(text, wordStop, newOffset, isCamel, isTokenBoundary)) break;
    }
    return newOffset;
  }

  private static boolean retreatTokenOnBoundary(@Nonnull HighlighterIterator tokenIterator, @Nonnull CharSequence text, int offset) {
    if (isTokenStart(tokenIterator, offset)) {
      final Object rightToken = tokenIterator.getTokenType();
      final boolean wasQuotedToken = isQuotedToken(tokenIterator, text);
      tokenIterator.retreat();
      return wasQuotedToken || isQuotedToken(tokenIterator, text) || !isBetweenWhitespaces(text, offset) && isLexemeBoundary(tokenIterator.getTokenType(), rightToken);
    }
    return isQuotedTokenInnardsBoundary(tokenIterator, text, offset);
  }

  public static int getPreviousLineStopOffset(@Nonnull Editor editor, @Nonnull CaretStop lineStop) {
    final Document document = editor.getDocument();
    final CaretModel caretModel = editor.getCaretModel();

    final int lineNumber = caretModel.getLogicalPosition().line;
    final boolean isAtLineStart = (caretModel.getOffset() == document.getLineStartOffset(lineNumber));

    return getPreviousLineStopOffset(document, lineStop, lineNumber, isAtLineStart);
  }

  private static int getPreviousLineStopOffset(@Nonnull Document document, @Nonnull CaretStop lineStop, int lineNumber, boolean isAtLineStart) {
    if (lineNumber - 1 < 0) {
      return 0;
    }
    else if (!isAtLineStart) {
      return lineStop.isAtStart() ? document.getLineStartOffset(lineNumber) : lineStop.isAtEnd() ? document.getLineEndOffset(lineNumber - 1) : 0;
    }
    else {
      return lineStop.isAtEnd() ? document.getLineEndOffset(lineNumber - 1) : lineStop.isAtStart() ? document.getLineStartOffset(lineNumber - 1) : 0;
    }
  }

  @Nonnull
  public static TextRange getRangeToWordEnd(@Nonnull Editor editor, boolean isCamel, boolean handleQuoted) {
    int startOffset = editor.getCaretModel().getOffset();
    // IDEA-211756 "Delete to word end" is extremely inconvenient on whitespaces
    int endOffset = getNextCaretStopOffset(editor, CaretStopPolicy.BOTH, isCamel, handleQuoted);
    return TextRange.create(startOffset, endOffset);
  }

  @SuppressWarnings("Duplicates")
  public static int getNextCaretStopOffset(@Nonnull Editor editor, @Nonnull CaretStopPolicy caretStopPolicy, boolean isCamel, boolean handleQuoted) {
    int maxOffset = getNextLineStopOffset(editor, caretStopPolicy.getLineStop());

    final CaretStop wordStop = caretStopPolicy.getWordStop();
    if (wordStop.equals(CaretStop.NONE)) return maxOffset;

    final int offset = editor.getCaretModel().getOffset();
    if (offset == maxOffset) return maxOffset;

    final CharSequence text = editor.getDocument().getCharsSequence();
    final HighlighterIterator tokenIterator = createHighlighterIteratorAtOffset(editor, offset);

    final int newOffset = getNextWordStopOffset(text, wordStop, tokenIterator, offset, maxOffset, isCamel);
    if (newOffset < maxOffset && handleQuoted && tokenIterator != null && isTokenStart(tokenIterator, newOffset - 1) && isQuotedToken(tokenIterator, text)) {
      // now at the end of an opening quote: | "word" -> "|word"
      // find the start of a closing quote:   "|word" -> "word|"  (must be only a single step away)
      final int newOffsetBeforeQuote = getNextWordStopOffset(text, CaretStop.BOTH, tokenIterator, newOffset, maxOffset, isCamel);
      if (isTokenEnd(tokenIterator, newOffsetBeforeQuote + 1)) {
        return getNextWordStopOffset(text, wordStop, tokenIterator, newOffsetBeforeQuote, maxOffset, isCamel); // "word"|
      }
    }
    return newOffset;
  }

  private static int getNextWordStopOffset(@Nonnull CharSequence text, @Nonnull CaretStop wordStop, @Nullable HighlighterIterator tokenIterator, int offset, int maxOffset, boolean isCamel) {
    int newOffset = offset + 1;
    for (; newOffset < maxOffset; newOffset++) {
      final boolean isTokenBoundary = tokenIterator != null && advanceTokenOnBoundary(tokenIterator, text, newOffset);
      if (isWordStopOffset(text, wordStop, newOffset, isCamel, isTokenBoundary)) break;
    }
    return newOffset;
  }

  private static boolean advanceTokenOnBoundary(@Nonnull HighlighterIterator tokenIterator, @Nonnull CharSequence text, int offset) {
    if (isTokenEnd(tokenIterator, offset)) {
      final Object leftToken = tokenIterator.getTokenType();
      final boolean wasQuotedToken = isQuotedToken(tokenIterator, text);
      tokenIterator.advance();
      return wasQuotedToken || isQuotedToken(tokenIterator, text) || !isBetweenWhitespaces(text, offset) && isLexemeBoundary(leftToken, tokenIterator.getTokenType());
    }
    return isQuotedTokenInnardsBoundary(tokenIterator, text, offset);
  }

  private static boolean isBetweenWhitespaces(@Nonnull CharSequence text, int offset) {
    return 0 < offset && offset < text.length() && Character.isWhitespace(text.charAt(offset - 1)) && Character.isWhitespace(text.charAt(offset));
  }

  private static boolean isWordStopOffset(@Nonnull CharSequence text, @Nonnull CaretStop wordStop, int offset, boolean isCamel, boolean isLexemeBoundary) {
    if (wordStop.isAtStart() && wordStop.isAtEnd()) {
      return isLexemeBoundary || isWordStart(text, offset, isCamel) || isWordEnd(text, offset, isCamel);
    }
    if (wordStop.isAtStart()) return isLexemeBoundary && !isWordEnd(text, offset, isCamel) || isWordStart(text, offset, isCamel);
    if (wordStop.isAtEnd()) return isLexemeBoundary && !isWordStart(text, offset, isCamel) || isWordEnd(text, offset, isCamel);
    return false;
  }

  private static boolean isQuotedToken(@Nonnull HighlighterIterator tokenIterator, @Nonnull CharSequence text) {
    final int startOffset = tokenIterator.getStart();
    final int endOffset = tokenIterator.getEnd();
    if (endOffset - startOffset < 2) return false;
    final char openingQuote = getQuoteAt(text, startOffset);
    final char closingQuote = getQuoteAt(text, endOffset - 1);
    return openingQuote != 0 && closingQuote == openingQuote;
  }

  private static char getQuoteAt(@Nonnull CharSequence text, int offset) {
    if (offset < 0 || offset >= text.length()) return 0;
    final char ch = text.charAt(offset);
    return (ch == '\'' || ch == '\"') ? ch : 0;
  }

  private static boolean isQuotedTokenInnardsBoundary(@Nonnull HighlighterIterator tokenIterator, @Nonnull CharSequence text, int offset) {
    return (isTokenStart(tokenIterator, offset - 1) || isTokenEnd(tokenIterator, offset + 1)) && isQuotedToken(tokenIterator, text);
  }

  private static boolean isTokenStart(@Nonnull HighlighterIterator tokenIterator, int offset) {
    return offset == tokenIterator.getStart();
  }

  private static boolean isTokenEnd(@Nonnull HighlighterIterator tokenIterator, int offset) {
    return offset == tokenIterator.getEnd();
  }

  public static int getNextLineStopOffset(@Nonnull Editor editor, @Nonnull CaretStop lineStop) {
    final Document document = editor.getDocument();
    final CaretModel caretModel = editor.getCaretModel();

    final int lineNumber = caretModel.getLogicalPosition().line;
    final boolean isAtLineEnd = (caretModel.getOffset() == document.getLineEndOffset(lineNumber));

    return getNextLineStopOffset(document, lineStop, lineNumber, isAtLineEnd);
  }

  private static int getNextLineStopOffset(@Nonnull Document document, @Nonnull CaretStop lineStop, int lineNumber, boolean isAtLineEnd) {
    if (lineNumber + 1 >= document.getLineCount()) {
      return document.getTextLength();
    }
    else if (!isAtLineEnd) {
      return lineStop.isAtEnd() ? document.getLineEndOffset(lineNumber) : lineStop.isAtStart() ? document.getLineStartOffset(lineNumber + 1) : document.getTextLength();
    }
    else {
      return lineStop.isAtStart() ? document.getLineStartOffset(lineNumber + 1) : lineStop.isAtEnd() ? document.getLineEndOffset(lineNumber + 1) : document.getTextLength();
    }
  }

  @Nonnull
  private static HighlighterIterator createHighlighterIteratorAtOffset(@Nonnull Editor editor, int offset) {
    return editor.getHighlighter().createIterator(offset);
  }
}
