/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package consulo.desktop.awt.editor.impl.view;

import consulo.codeEditor.*;
import consulo.codeEditor.impl.CodeEditorFoldingModelBase;
import consulo.desktop.awt.editor.impl.DesktopEditorImpl;
import consulo.desktop.awt.editor.impl.SoftWrapModelImpl;
import consulo.document.Document;
import consulo.document.util.DocumentUtil;
import consulo.ide.impl.idea.openapi.editor.ex.util.EditorUtil;
import consulo.logging.Logger;
import consulo.logging.attachment.AttachmentFactory;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.geom.Point2D;
import java.util.List;

/**
 * Performs transformations between various location representations in editor
 * (offset, logical position, visual position, pixel coordinates).
 *
 * @see LogicalPosition
 * @see VisualPosition
 */
class EditorCoordinateMapper {
  private static final Logger LOG = Logger.getInstance(EditorCoordinateMapper.class);

  private final EditorView myView;
  private final Document myDocument;
  private final CodeEditorFoldingModelBase myFoldingModel;

  EditorCoordinateMapper(EditorView view) {
    myView = view;
    myDocument = myView.getEditor().getDocument();
    myFoldingModel = myView.getEditor().getFoldingModel();
  }

  int visualLineToY(int line) {
    return myView.getInsets().top + Math.max(0, line) * myView.getLineHeight();
  }

  int yToVisualLine(int y) {
    return Math.max(0, y - myView.getInsets().top) / myView.getLineHeight();
  }

  @Nonnull
  LogicalPosition offsetToLogicalPosition(int offset) {
    return myView.getLogicalPositionCache().offsetToLogicalPosition(offset);
  }

  int logicalPositionToOffset(@Nonnull LogicalPosition pos) {
    return myView.getLogicalPositionCache().logicalPositionToOffset(pos);
  }

  @Nonnull
  VisualPosition logicalToVisualPosition(@Nonnull LogicalPosition pos, boolean beforeSoftWrap) {
    int line = pos.line;
    int column = pos.column;
    int logicalLineCount = myDocument.getLineCount();
    if (line >= logicalLineCount) {
      return new VisualPosition(line - logicalLineCount + myView.getEditor().getVisibleLineCount(), column, pos.leansForward);
    }
    int offset = logicalPositionToOffset(pos);
    int visualLine = offsetToVisualLine(offset, beforeSoftWrap);
    int maxVisualColumn = 0;
    int maxLogicalColumn = 0;
    for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(myView, offset, beforeSoftWrap)) {
      if (!pos.leansForward && offset == fragment.getVisualLineStartOffset()) {
        return new VisualPosition(visualLine, fragment.getStartVisualColumn());
      }
      if (fragment.isCollapsedFoldRegion()) {
        int startLogicalLine = fragment.getStartLogicalLine();
        int endLogicalLine = fragment.getEndLogicalLine();
        int startLogicalColumn = fragment.getStartLogicalColumn();
        int endLogicalColumn = fragment.getEndLogicalColumn();
        if ((line > startLogicalLine || line == startLogicalLine && (column > startLogicalColumn || column == startLogicalColumn && pos.leansForward)) &&
            (line < endLogicalLine || line == endLogicalLine && column < endLogicalColumn)) {
          return new VisualPosition(visualLine, fragment.getStartVisualColumn(), true);
        }
        if (line == endLogicalLine && column == endLogicalColumn && !pos.leansForward) {
          return new VisualPosition(visualLine, fragment.getEndVisualColumn());
        }
        maxLogicalColumn = startLogicalLine == endLogicalLine ? Math.max(maxLogicalColumn, endLogicalColumn) : endLogicalColumn;
      }
      else if (fragment.getCurrentInlay() == null) {
        int minColumn = fragment.getMinLogicalColumn();
        int maxColumn = fragment.getMaxLogicalColumn();
        if (line == fragment.getStartLogicalLine() && (column > minColumn && column < maxColumn || column == minColumn && pos.leansForward || column == maxColumn && !pos.leansForward)) {
          return new VisualPosition(visualLine, fragment.logicalToVisualColumn(column), fragment.isRtl() ^ pos.leansForward);
        }
        maxLogicalColumn = Math.max(maxLogicalColumn, maxColumn);
      }
      maxVisualColumn = fragment.getEndVisualColumn();
    }
    int resultColumn = column - maxLogicalColumn + maxVisualColumn;
    if (resultColumn < 0) {
      if (maxVisualColumn > maxLogicalColumn) {
        resultColumn = Integer.MAX_VALUE; // guarding against overflow
      }
      else {
        LOG.error("Error converting " + pos + " to visual position",
                  AttachmentFactory.get().create("details.txt", String.format("offset: %d, visual line: %d, max logical column: %d, max visual column: %d", offset, visualLine, maxLogicalColumn, maxVisualColumn)),
                  AttachmentFactory.get().create("dump.txt", myView.getEditor().dumpState()));
        resultColumn = 0;
      }
    }
    return new VisualPosition(visualLine, resultColumn, pos.leansForward);
  }

  @Nonnull
  LogicalPosition visualToLogicalPosition(@Nonnull VisualPosition pos) {
    int line = pos.line;
    int column = pos.column;
    int visualLineCount = myView.getEditor().getVisibleLineCount();
    if (line >= visualLineCount) {
      return new LogicalPosition(line - visualLineCount + myDocument.getLineCount(), column, pos.leansRight);
    }
    int offset = visualLineToOffset(line);
    int logicalLine = myDocument.getLineNumber(offset);
    int maxVisualColumn = 0;
    int maxLogicalColumn = 0;
    int maxOffset = offset;
    LogicalPosition delayedResult = null;
    for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(myView, offset, false)) {
      if (delayedResult != null) return delayedResult.leanForward(fragment.getCurrentInlay() == null);
      int minColumn = fragment.getStartVisualColumn();
      int maxColumn = fragment.getEndVisualColumn();
      if (column < minColumn || column == minColumn && !pos.leansRight) {
        return offsetToLogicalPosition(offset);
      }
      if (column > minColumn && column < maxColumn || column == minColumn || column == maxColumn && !pos.leansRight) {
        if (column == maxColumn && fragment.getCurrentInlay() != null) {
          // for visual positions between adjacent inlays, we return same result as for visual position before the first one
          delayedResult = new LogicalPosition(fragment.getEndLogicalLine(), fragment.getEndLogicalColumn(), true);
        }
        else {
          return new LogicalPosition(column == maxColumn ? fragment.getEndLogicalLine() : fragment.getStartLogicalLine(), fragment.visualToLogicalColumn(column),
                                     fragment.isCollapsedFoldRegion() ? column < maxColumn : fragment.getCurrentInlay() == null && fragment.isRtl() ^ pos.leansRight);
        }
      }
      maxLogicalColumn = logicalLine == fragment.getEndLogicalLine() ? Math.max(maxLogicalColumn, fragment.getMaxLogicalColumn()) : fragment.getMaxLogicalColumn();
      maxVisualColumn = maxColumn;
      logicalLine = fragment.getEndLogicalLine();
      maxOffset = Math.max(maxOffset, fragment.getMaxOffset());
    }
    if (myView.getEditor().getSoftWrapModel().getSoftWrap(maxOffset) == null) {
      int resultColumn = column - maxVisualColumn + maxLogicalColumn;
      if (resultColumn < 0 && maxLogicalColumn > maxVisualColumn) {
        resultColumn = Integer.MAX_VALUE; // guarding against overflow
      }
      return new LogicalPosition(logicalLine, resultColumn, true);
    }
    else {
      return offsetToLogicalPosition(maxOffset).leanForward(true);
    }
  }

  @Nonnull
  VisualPosition offsetToVisualPosition(int offset, boolean leanTowardsLargerOffsets, boolean beforeSoftWrap) {
    return logicalToVisualPosition(offsetToLogicalPosition(offset).leanForward(leanTowardsLargerOffsets), beforeSoftWrap);
  }

  int visualPositionToOffset(VisualPosition visualPosition) {
    return logicalPositionToOffset(visualToLogicalPosition(visualPosition));
  }

  int offsetToVisualLine(int offset, boolean beforeSoftWrap) {
    int textLength = myDocument.getTextLength();
    if (offset < 0 || textLength == 0) {
      return 0;
    }
    offset = Math.min(offset, textLength);
    offset = DocumentUtil.alignToCodePointBoundary(myDocument, offset);

    FoldRegion outermostCollapsed = myFoldingModel.getCollapsedRegionAtOffset(offset);
    if (outermostCollapsed != null && offset > outermostCollapsed.getStartOffset()) {
      assert outermostCollapsed.isValid();
      offset = outermostCollapsed.getStartOffset();
      beforeSoftWrap = false;
    }

    int wrapIndex = myView.getEditor().getSoftWrapModel().getSoftWrapIndex(offset);
    int softWrapsBeforeOrAtOffset = wrapIndex < 0 ? (-wrapIndex - 1) : wrapIndex + (beforeSoftWrap ? 0 : 1);

    return myDocument.getLineNumber(offset) - myFoldingModel.getFoldedLinesCountBefore(offset) + softWrapsBeforeOrAtOffset;
  }

  int visualLineToOffset(int visualLine) {
    int start = 0;
    int end = myDocument.getTextLength();
    if (visualLine <= 0) return start;
    if (visualLine >= myView.getEditor().getVisibleLineCount()) return end;
    int current = 0;
    while (start <= end) {
      current = (start + end) / 2;
      int line = offsetToVisualLine(current, false);
      if (line < visualLine) {
        start = current + 1;
      }
      else if (line > visualLine) {
        end = current - 1;
      }
      else {
        break;
      }
    }
    return visualLineStartOffset(current, true);
  }

  private int visualLineStartOffset(int offset, boolean leanForward) {
    DesktopEditorImpl editor = myView.getEditor();
    offset = DocumentUtil.alignToCodePointBoundary(myDocument, offset);
    int result = EditorUtil.getNotFoldedLineStartOffset(editor, offset);

    SoftWrapModelImpl softWrapModel = editor.getSoftWrapModel();
    List<? extends SoftWrap> softWraps = softWrapModel.getRegisteredSoftWraps();
    int currentOrPrevWrapIndex = softWrapModel.getSoftWrapIndex(offset);
    SoftWrap currentOrPrevWrap;
    if (currentOrPrevWrapIndex < 0) {
      currentOrPrevWrapIndex = -currentOrPrevWrapIndex - 2;
      currentOrPrevWrap = currentOrPrevWrapIndex < 0 || currentOrPrevWrapIndex >= softWraps.size() ? null : softWraps.get(currentOrPrevWrapIndex);
    }
    else {
      currentOrPrevWrap = leanForward ? softWraps.get(currentOrPrevWrapIndex) : null;
    }
    if (currentOrPrevWrap != null && currentOrPrevWrap.getStart() > result) {
      result = currentOrPrevWrap.getStart();
    }
    return result;
  }

  private float getStartX(int line) {
    return myView.getEditor().isRightAligned() ? getRightAlignmentLineStartX(line) : myView.getInsets().left + (line == 0 ? myView.getPrefixTextWidthInPixels() : 0);
  }

  float getRightAlignmentLineStartX(int visualLine) {
    checkRightAlignment();
    DesktopEditorImpl editor = myView.getEditor();
    int max = getRightAlignmentMarginX();
    float shift = visualLine == 0 ? myView.getPrefixTextWidthInPixels() : 0;
    if (visualLine >= editor.getVisibleLineCount()) return max - shift;
    int lineWidth = myView.getSizeManager().getVisualLineWidth(new VisualLinesIterator(editor, visualLine), false);
    return Math.max(max - lineWidth, 0);
  }

  int getRightAlignmentMarginX() {
    checkRightAlignment();
    DesktopEditorImpl editor = myView.getEditor();
    JScrollBar vsb = editor.getScrollPane().getVerticalScrollBar();
    int vsbWidth = vsb != null && editor.getVerticalScrollbarOrientation() == EditorEx.VERTICAL_SCROLLBAR_RIGHT ? vsb.getWidth() : 0;
    return editor.getContentComponent().getWidth() - myView.getInsets().right - editor.getSettings().getLineCursorWidth() - vsbWidth;
  }

  private void checkRightAlignment() {
    if (!myView.getEditor().isRightAligned()) throw new IllegalStateException("Editor is not right-aligned");
  }

  @Nonnull
  VisualPosition xyToVisualPosition(@Nonnull Point2D p) {
    int visualLine = yToVisualLine((int)p.getY());
    int lastColumn = 0;
    float x = getStartX(visualLine);
    float px = (float)p.getX();
    if (visualLine < myView.getEditor().getVisibleLineCount()) {
      int visualLineStartOffset = visualLineToOffset(visualLine);
      int maxOffset = 0;
      for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(myView, visualLineStartOffset, false, true)) {
        if (px <= fragment.getStartX()) {
          if (fragment.getStartVisualColumn() == 0) {
            return new VisualPosition(visualLine, 0);
          }
          int markerWidth = myView.getEditor().getSoftWrapModel().getMinDrawingWidthInPixels(SoftWrapDrawingType.AFTER_SOFT_WRAP);
          float indent = fragment.getStartX() - markerWidth;
          if (px <= indent) {
            break;
          }
          boolean after = px >= indent + markerWidth / 2;
          return new VisualPosition(visualLine, fragment.getStartVisualColumn() - (after ? 0 : 1), !after);
        }
        float nextX = fragment.getEndX();
        if (px <= nextX) {
          int[] column = fragment.xToVisualColumn(px);
          return new VisualPosition(visualLine, column[0], column[1] > 0);
        }
        x = nextX;
        lastColumn = fragment.getEndVisualColumn();
        maxOffset = Math.max(maxOffset, fragment.getMaxOffset());
      }
      if (myView.getEditor().getSoftWrapModel().getSoftWrap(maxOffset) != null) {
        int markerWidth = myView.getEditor().getSoftWrapModel().getMinDrawingWidthInPixels(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED);
        if (px <= x + markerWidth) {
          boolean after = px >= x + markerWidth / 2;
          return new VisualPosition(visualLine, lastColumn + (after ? 1 : 0), !after);
        }
        px -= markerWidth;
        lastColumn++;
      }
    }
    float plainSpaceWidth = myView.getPlainSpaceWidth();
    float remainingShift = px - x;
    int additionalColumns = remainingShift <= 0 ? 0 : Math.round(remainingShift / plainSpaceWidth);
    return new VisualPosition(visualLine, lastColumn + additionalColumns, remainingShift > additionalColumns * plainSpaceWidth);
  }

  @Nonnull
  Point2D visualPositionToXY(@Nonnull VisualPosition pos) {
    int visualLine = pos.line;
    int column = pos.column;
    int y = visualLineToY(visualLine);
    float x = getStartX(visualLine);
    int lastColumn = 0;
    if (visualLine < myView.getEditor().getVisibleLineCount()) {
      int visualLineStartOffset = visualLineToOffset(visualLine);
      int maxOffset = 0;
      for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(myView, visualLineStartOffset, false, true)) {
        int startVisualColumn = fragment.getStartVisualColumn();
        if (column < startVisualColumn || column == startVisualColumn && !pos.leansRight) {
          break;
        }
        int endColumn = fragment.getEndVisualColumn();
        if (column < endColumn || column == endColumn && !pos.leansRight) {
          return new Point2D.Double(fragment.visualColumnToX(column), y);
        }
        x = fragment.getEndX();
        lastColumn = endColumn;
        maxOffset = Math.max(maxOffset, fragment.getMaxOffset());
      }
      if (column > lastColumn && myView.getEditor().getSoftWrapModel().getSoftWrap(maxOffset) != null) {
        column--;
        x += myView.getEditor().getSoftWrapModel().getMinDrawingWidthInPixels(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED);
      }
    }
    float additionalShift = column <= lastColumn ? 0 : (column - lastColumn) * myView.getPlainSpaceWidth();
    return new Point2D.Double(x + additionalShift, y);
  }

  @Nonnull
  Point2D offsetToXY(int offset, boolean leanTowardsLargerOffsets, boolean beforeSoftWrap) {
    offset = Math.max(0, Math.min(myDocument.getTextLength(), offset));
    offset = DocumentUtil.alignToCodePointBoundary(myDocument, offset);
    int logicalLine = myDocument.getLineNumber(offset);
    int visualLine = offsetToVisualLine(offset, beforeSoftWrap);
    int visualLineStartOffset = visualLineToOffset(visualLine);
    int y = visualLineToY(visualLine);
    float x = getStartX(logicalLine);
    if (myDocument.getTextLength() > 0) {
      boolean firstFragment = true;
      for (VisualLineFragmentsIterator.Fragment fragment : VisualLineFragmentsIterator.create(myView, offset, beforeSoftWrap, true)) {
        if (firstFragment && offset == visualLineStartOffset && !leanTowardsLargerOffsets) {
          x = fragment.getStartX();
          break;
        }
        firstFragment = false;
        int minOffset = fragment.getMinOffset();
        int maxOffset = fragment.getMaxOffset();
        if (fragment.getCurrentInlay() == null && (offset > minOffset && offset < maxOffset || offset == minOffset && leanTowardsLargerOffsets || offset == maxOffset && !leanTowardsLargerOffsets)) {
          x = fragment.offsetToX(offset);
          break;
        }
        else {
          x = fragment.getEndX();
        }
      }
    }
    return new Point2D.Double(x, y);
  }
}
