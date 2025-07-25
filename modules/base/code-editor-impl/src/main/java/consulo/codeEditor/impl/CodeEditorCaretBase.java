// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.codeEditor.impl;

import consulo.application.ApplicationManager;
import consulo.application.util.Dumpable;
import consulo.application.util.diff.FilesTooBigForDiffException;
import consulo.codeEditor.*;
import consulo.codeEditor.action.EditorActionHandler;
import consulo.codeEditor.action.EditorActionManager;
import consulo.codeEditor.action.EditorActionUtil;
import consulo.codeEditor.event.CaretEvent;
import consulo.codeEditor.event.SelectionEvent;
import consulo.codeEditor.impl.softwrap.SoftWrapHelper;
import consulo.codeEditor.impl.util.EditorImplUtil;
import consulo.codeEditor.internal.CodeEditorInternalHelper;
import consulo.codeEditor.util.EditorUtil;
import consulo.dataContext.DataContext;
import consulo.disposer.Disposer;
import consulo.document.Document;
import consulo.document.RangeMarker;
import consulo.document.event.DocumentEvent;
import consulo.document.impl.RangeMarkerImpl;
import consulo.document.impl.RangeMarkerTree;
import consulo.document.impl.event.DocumentEventImpl;
import consulo.document.internal.DocumentEx;
import consulo.document.internal.RangeMarkerEx;
import consulo.document.util.DocumentUtil;
import consulo.document.util.TextRangeScalarUtil;
import consulo.language.util.AttachmentFactoryUtil;
import consulo.logging.Logger;
import consulo.logging.attachment.AttachmentFactory;
import consulo.ui.UIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.IdeActions;
import consulo.ui.ex.awt.CopyPasteManager;
import consulo.util.dataholder.Key;
import consulo.util.dataholder.UserDataHolderBase;
import consulo.util.lang.CharArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;

import static consulo.codeEditor.impl.CodeEditorSelectionModelBase.doSelectLineAtCaret;

/**
 * Common part from desktop caret impl
 */
public class CodeEditorCaretBase extends UserDataHolderBase implements Caret, Dumpable {
    private static final Logger LOG = Logger.getInstance(CodeEditorCaretBase.class);
    private static final Key<CaretVisualAttributes> VISUAL_ATTRIBUTES_KEY = Key.create("CaretAttributes");

    protected final CodeEditorBase myEditor;

    @Nonnull
    protected final CodeEditorCaretModelBase<CodeEditorCaretBase> myCaretModel;
    private boolean isValid = true;

    private LogicalPosition myLogicalCaret;
    protected VerticalInfo myVerticalInfo;
    private VisualPosition myVisibleCaret;
    private volatile PositionMarker myPositionMarker;
    private boolean myLeansTowardsLargerOffsets;
    private int myLogicalColumnAdjustment;
    int myVisualColumnAdjustment;
    private int myVisualLineStart;
    private int myVisualLineEnd;
    private boolean mySkipChangeRequests;
    /**
     * Initial horizontal caret position during vertical navigation.
     * Similar to {@link #myDesiredX}, but represents logical caret position ({@code getLogicalPosition().column}) rather than visual.
     */
    private int myLastColumnNumber;
    private int myDesiredSelectionStartColumn = -1;
    private int myDesiredSelectionEndColumn = -1;
    /**
     * We check that caret is located at the target offset at the end of {@link #moveToOffset(int, boolean)} method. However,
     * it's possible that the following situation occurs:
     * <p/>
     * <pre>
     * <ol>
     *   <li>Some client subscribes to caret change events;</li>
     *   <li>{@link #moveToLogicalPosition(LogicalPosition)} is called;</li>
     *   <li>Caret position is changed during {@link #moveToLogicalPosition(LogicalPosition)} processing;</li>
     *   <li>The client receives caret position change event and adjusts the position;</li>
     *   <li>{@link #moveToLogicalPosition(LogicalPosition)} processing is finished;</li>
     *   <li>{@link #moveToLogicalPosition(LogicalPosition)} reports an error because the caret is not located at the target offset;</li>
     * </ol>
     * </pre>
     * <p/>
     * This field serves as a flag that reports unexpected caret position change requests nested from {@link #moveToOffset(int, boolean)}.
     */
    private boolean myReportCaretMoves;
    /**
     * This field holds initial horizontal caret position during vertical navigation. It's used to determine target position when
     * moving to the new line. It is stored in pixels, not in columns, to account for non-monospaced fonts as well.
     * <p/>
     * Negative value means no coordinate should be preserved.
     */
    private int myDesiredX = -1;

    private volatile SelectionMarker mySelectionMarker;
    private volatile VisualPosition myRangeMarkerStartPosition;
    private volatile VisualPosition myRangeMarkerEndPosition;
    private volatile boolean myRangeMarkerEndPositionIsLead;
    private boolean myUnknownDirection;

    private int myDocumentUpdateCounter;

    public CodeEditorCaretBase(@Nonnull CodeEditorBase editor, @Nonnull CodeEditorCaretModelBase caretModel) {
        myEditor = editor;
        myCaretModel = caretModel;

        myLogicalCaret = new LogicalPosition(0, 0);
        myVisibleCaret = new VisualPosition(0, 0);
        myPositionMarker = new PositionMarker(0);
        myVisualLineStart = 0;
        Document doc = myEditor.getDocument();
        myVisualLineEnd = doc.getLineCount() > 1 ? doc.getLineStartOffset(1) : doc.getLineCount() == 0 ? 0 : doc.getLineEndOffset(0);
        myDocumentUpdateCounter = myCaretModel.myDocumentUpdateCounter;
    }

    @Override
    public void moveToOffset(int offset) {
        moveToOffset(offset, false);
    }

    @Override
    public void moveToOffset(final int offset, final boolean locateBeforeSoftWrap) {
        assertIsDispatchThread();
        validateCallContext();
        if (mySkipChangeRequests) {
            return;
        }
        myCaretModel.doWithCaretMerging(() -> {
            LogicalPosition logicalPosition = myEditor.offsetToLogicalPosition(offset);
            CaretEvent event = moveToLogicalPosition(logicalPosition, locateBeforeSoftWrap, null, true, false);
            final LogicalPosition positionByOffsetAfterMove = myEditor.offsetToLogicalPosition(getOffset());
            if (!positionByOffsetAfterMove.equals(logicalPosition)) {
                StringBuilder debugBuffer = new StringBuilder();
                moveToLogicalPosition(logicalPosition, locateBeforeSoftWrap, debugBuffer, true, true);
                int actualOffset = getOffset();
                int textStart = Math.max(0, Math.min(offset, actualOffset) - 1);
                final DocumentEx document = myEditor.getDocument();
                int textEnd = Math.min(document.getTextLength() - 1, Math.max(offset, actualOffset) + 1);
                CharSequence text = document.getCharsSequence().subSequence(textStart, textEnd);
                int inverseOffset = myEditor.logicalPositionToOffset(logicalPosition);
                LOG.error("caret moved to wrong offset. Please submit a dedicated ticket and attach current editor's text to it.", new Throwable(), AttachmentFactory.get().create("context.txt", "Requested:" +
                    " " +
                    "offset=" +
                    offset +
                    ", logical position='" +
                    logicalPosition +
                    "' but actual: offset=" +
                    actualOffset +
                    ", logical position='" +
                    myLogicalCaret +
                    "' (" +
                    positionByOffsetAfterMove +
                    "). " +
                    myEditor.dumpState() +
                    "\ninterested text [" +
                    textStart +
                    ";" +
                    textEnd +
                    "): '" +
                    text +
                    "'\n debug trace: " +
                    debugBuffer +
                    "\nLogical position -> offset ('" +
                    logicalPosition +
                    "'->'" +
                    inverseOffset +
                    "')"));
            }
            if (event != null) {
                myCaretModel.fireCaretPositionChanged(event);
                EditorActionUtil.selectNonexpandableFold(myEditor);
            }
        });
    }

    @Nonnull
    @Override
    public CaretModel getCaretModel() {
        return myCaretModel;
    }

    @Override
    public boolean isValid() {
        return isValid;
    }

    private void stopKillRings() {
        if (!myEditor.isStickySelection() && !myEditor.getDocument().isInEventsHandling()) {
            CopyPasteManager.getInstance().stopKillRings();
        }
    }

    @Override
    public void moveCaretRelatively(final int _columnShift, final int lineShift, final boolean withSelection, final boolean scrollToCaret) {
        assertIsDispatchThread();
        if (mySkipChangeRequests) {
            return;
        }
        stopKillRings();
        myCaretModel.doWithCaretMerging(() -> {
            updateCachedStateIfNeeded();

            int oldOffset = getOffset();
            int columnShift = _columnShift;
            if (withSelection && lineShift == 0) {
                if (columnShift == -1) {
                    int column;
                    while ((column = myVisibleCaret.column + columnShift - (hasSelection() && oldOffset == getSelectionEnd() ? 1 : 0)) >= 0 &&
                        myEditor.getInlayModel().hasInlineElementAt(new VisualPosition(myVisibleCaret.line, column))) {
                        columnShift--;
                    }
                }
                else if (columnShift == 1) {
                    while (myEditor.getInlayModel().hasInlineElementAt(
                        new VisualPosition(myVisibleCaret.line,
                            myVisibleCaret.column + columnShift - (hasSelection() && oldOffset == getSelectionStart() ? 0 : 1)))) {
                        columnShift++;
                    }
                }
            }
            final int leadSelectionOffset = getLeadSelectionOffset();
            final VisualPosition leadSelectionPosition = getLeadSelectionPosition();
            EditorSettings editorSettings = myEditor.getSettings();
            VisualPosition visualCaret = getVisualPosition();

            int desiredX = myDesiredX;
            if (columnShift == 0) {
                if (myDesiredX < 0) {
                    desiredX = getCurrentX();
                }
            }
            else {
                myDesiredX = desiredX = -1;
            }

            int newLineNumber = visualCaret.line + lineShift;
            int newColumnNumber = visualCaret.column + columnShift;
            boolean newLeansRight = lineShift == 0 && columnShift != 0 ? columnShift < 0 : visualCaret.leansRight;

            if (desiredX >= 0) {
                newColumnNumber = myEditor.xyToVisualPosition(new Point(desiredX, myEditor.visualLineToY(newLineNumber))).column;
            }

            Document document = myEditor.getDocument();
            if (!editorSettings.isVirtualSpace() && lineShift == 0 && columnShift == 1) {
                int lastLine = document.getLineCount() - 1;
                if (lastLine < 0) {
                    lastLine = 0;
                }
                if (newColumnNumber > EditorImplUtil.getLastVisualLineColumnNumber(myEditor, newLineNumber) &&
                    newLineNumber < myEditor.logicalToVisualPosition(new LogicalPosition(lastLine, 0)).line) {
                    newColumnNumber = 0;
                    newLineNumber++;
                }
            }
            else if (lineShift == 0 && columnShift == -1) {
                if (newColumnNumber < 0 && newLineNumber > 0) {
                    newLineNumber--;
                    if (editorSettings.isVirtualSpace()) {
                        newColumnNumber = myEditor.offsetToVisualPosition(Math.max(0, oldOffset - 1)).column;
                    }
                    else {
                        newColumnNumber = EditorImplUtil.getLastVisualLineColumnNumber(myEditor, newLineNumber);
                    }
                }
            }

            if (newColumnNumber < 0) {
                newColumnNumber = 0;
            }

            // There is a possible case that caret is located at the first line and user presses 'Shift+Up'. We want to select all text
            // from the document start to the current caret position then. So, we have a dedicated flag for tracking that.
            boolean selectToDocumentStart = false;
            if (newLineNumber < 0) {
                selectToDocumentStart = true;
                newLineNumber = 0;

                // We want to move caret to the first column if it's already located at the first line and 'Up' is pressed.
                newColumnNumber = 0;
            }

            VisualPosition pos = new VisualPosition(newLineNumber, newColumnNumber);
            if (!myEditor.getSoftWrapModel().isInsideSoftWrap(pos)) {
                int offset = myEditor.visualPositionToOffset(new VisualPosition(newLineNumber, newColumnNumber, newLeansRight));
                if (offset >= document.getTextLength() && columnShift == 0) {
                    int lastOffsetColumn = myEditor.offsetToVisualPosition(document.getTextLength(), true, false).column;
                    // We want to move caret to the last column if it's located at the last line and 'Down' is pressed.
                    if (lastOffsetColumn > newColumnNumber) {
                        newColumnNumber = lastOffsetColumn;
                        newLeansRight = true;
                    }
                }
                if (!editorSettings.isCaretInsideTabs()) {
                    CharSequence text = document.getCharsSequence();
                    if (offset >= 0 && offset < document.getTextLength()) {
                        if (text.charAt(offset) == '\t' && (columnShift <= 0 || offset == oldOffset) && !isAtRtlLocation()) {
                            if (columnShift <= 0) {
                                newColumnNumber = myEditor.offsetToVisualPosition(offset, true, false).column;
                            }
                            else {
                                SoftWrap softWrap = myEditor.getSoftWrapModel().getSoftWrap(offset + 1);
                                // There is a possible case that tabulation symbol is the last document symbol represented on a visual line before
                                // soft wrap. We can't just use column from 'offset + 1' because it would point on a next visual line.
                                if (softWrap == null) {
                                    newColumnNumber = myEditor.offsetToVisualPosition(offset + 1).column;
                                }
                                else {
                                    newColumnNumber = EditorImplUtil.getLastVisualLineColumnNumber(myEditor, newLineNumber);
                                }
                            }
                        }
                    }
                }
            }

            pos = new VisualPosition(newLineNumber, newColumnNumber, newLeansRight);
            if (columnShift != 0 && lineShift == 0 && myEditor.getSoftWrapModel().isInsideSoftWrap(pos)) {
                int softWrapOffset = myEditor.visualPositionToOffset(pos);
                if (columnShift >= 0) {
                    moveToOffset(softWrapOffset);
                }
                else {
                    int line = myEditor.offsetToVisualLine(softWrapOffset - 1);
                    moveToVisualPosition(new VisualPosition(line, EditorImplUtil.getLastVisualLineColumnNumber(myEditor, line)));
                }
            }
            else {
                moveToVisualPosition(pos);
            }

            if (withSelection) {
                if (selectToDocumentStart) {
                    setSelection(leadSelectionPosition, leadSelectionOffset, myEditor.offsetToVisualPosition(0), 0);
                }
                else if (pos.line >= myEditor.getVisibleLineCount()) {
                    int endOffset = document.getTextLength();
                    if (leadSelectionOffset < endOffset) {
                        setSelection(leadSelectionPosition, leadSelectionOffset, myEditor.offsetToVisualPosition(endOffset), endOffset);
                    }
                }
                else {
                    int selectionStartToUse = leadSelectionOffset;
                    VisualPosition selectionStartPositionToUse = leadSelectionPosition;
                    if (isUnknownDirection() || oldOffset > getSelectionStart() && oldOffset < getSelectionEnd()) {
                        if (getOffset() > leadSelectionOffset ^ getSelectionStart() < getSelectionEnd()) {
                            selectionStartToUse = getSelectionEnd();
                            selectionStartPositionToUse = getSelectionEndPosition();
                        }
                        else {
                            selectionStartToUse = getSelectionStart();
                            selectionStartPositionToUse = getSelectionStartPosition();
                        }
                    }
                    setSelection(selectionStartPositionToUse, selectionStartToUse, getVisualPosition(), getOffset());
                }
            }
            else {
                removeSelection();
            }

            if (scrollToCaret) {
                myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
            }

            if (desiredX >= 0) {
                myDesiredX = desiredX;
            }

            EditorActionUtil.selectNonexpandableFold(myEditor);
        });
    }

    @Override
    public void moveToLogicalPosition(@Nonnull final LogicalPosition pos) {
        myCaretModel.doWithCaretMerging(() -> moveToLogicalPosition(pos, false, null, false, true));
    }


    private CaretEvent doMoveToLogicalPosition(@Nonnull LogicalPosition pos, boolean locateBeforeSoftWrap, @NonNls @Nullable StringBuilder debugBuffer, boolean adjustForInlays, boolean fireListeners) {
        assertIsDispatchThread();
        checkDisposal();
        updateCachedStateIfNeeded();
        if (debugBuffer != null) {
            debugBuffer.append("Start moveToLogicalPosition(). Locate before soft wrap: ").append(locateBeforeSoftWrap).append(", position: ").append(pos).append("\n");
        }
        myDesiredX = -1;
        validateCallContext();
        int column = pos.column;
        int line = pos.line;
        boolean leansForward = pos.leansForward;

        Document doc = myEditor.getDocument();

        int lineCount = doc.getLineCount();
        if (lineCount == 0) {
            if (debugBuffer != null) {
                debugBuffer.append("Resetting target logical line to zero as the document is empty\n");
            }
            line = 0;
        }
        else if (line > lineCount - 1) {
            if (debugBuffer != null) {
                debugBuffer.append("Resetting target logical line (").append(line).append(") to ").append(lineCount - 1).append(" as it is greater than total document lines number\n");
            }
            line = lineCount - 1;
        }

        EditorSettings editorSettings = myEditor.getSettings();

        if (!editorSettings.isVirtualSpace() && line < lineCount) {
            int lineEndOffset = doc.getLineEndOffset(line);
            final LogicalPosition endLinePosition = myEditor.offsetToLogicalPosition(lineEndOffset);
            int lineEndColumnNumber = endLinePosition.column;
            if (column > lineEndColumnNumber) {
                int oldColumn = column;
                column = lineEndColumnNumber;
                leansForward = true;
                if (debugBuffer != null) {
                    debugBuffer.append("Resetting target logical column (").append(oldColumn).append(") to ").append(lineEndColumnNumber)
                        .append(" because caret is not allowed to be located after line end (offset: ").append(lineEndOffset).append(", ").append("logical position: ").append(endLinePosition)
                        .append(").\n");
                }
            }
        }

        myEditor.getFoldingModel().flushCaretPosition(this);

        VerticalInfo oldVerticalInfo = myVerticalInfo;
        LogicalPosition oldCaretPosition = myLogicalCaret;
        VisualPosition oldVisualPosition = myVisibleCaret;
        boolean oldInVirtualSpace = isInVirtualSpace();

        LogicalPosition logicalPositionToUse = new LogicalPosition(line, column, leansForward);
        final int offset = myEditor.logicalPositionToOffset(logicalPositionToUse);
        if (debugBuffer != null) {
            debugBuffer.append("Resulting logical position to use: ").append(logicalPositionToUse).append(". It's mapped to offset ").append(offset).append("\n");
        }

        FoldRegion collapsedAt = myEditor.getFoldingModel().getCollapsedRegionAtOffset(offset);

        if (collapsedAt != null && offset > collapsedAt.getStartOffset()) {
            if (debugBuffer != null) {
                debugBuffer.append("Scheduling expansion of fold region ").append(collapsedAt).append("\n");
            }
            Runnable runnable = () -> {
                FoldRegion[] allCollapsedAt = myEditor.getFoldingModel().fetchCollapsedAt(offset);
                for (FoldRegion foldRange : allCollapsedAt) {
                    foldRange.setExpanded(true);
                }
            };

            mySkipChangeRequests = true;
            try {
                myEditor.getFoldingModel().runBatchFoldingOperation(runnable, false);
            }
            finally {
                mySkipChangeRequests = false;
            }
        }

        myLogicalCaret = logicalPositionToUse;
        setLastColumnNumber(myLogicalCaret.column);
        myDesiredSelectionStartColumn = myDesiredSelectionEndColumn = -1;
        myVisibleCaret = myEditor.logicalToVisualPosition(myLogicalCaret);
        myVisualColumnAdjustment = 0;

        updateOffsetsFromLogicalPosition();
        int newOffset = getOffset();
        if (debugBuffer != null) {
            debugBuffer.append("Storing offset ").append(newOffset).append(" (mapped from logical position ").append(myLogicalCaret).append(")\n");
        }

        if (adjustForInlays) {
            VisualPosition correctPosition = EditorUtil.inlayAwareOffsetToVisualPosition(myEditor, newOffset);
            assert correctPosition.line == myVisibleCaret.line;
            myVisualColumnAdjustment = correctPosition.column - myVisibleCaret.column;
            myVisibleCaret = correctPosition;
        }

        updateVisualLineInfo();

        myEditor.updateCaretCursor();
        requestRepaint(oldVerticalInfo);

        if (locateBeforeSoftWrap && SoftWrapHelper.isCaretAfterSoftWrap(this)) {
            int lineToUse = myVisibleCaret.line - 1;
            if (lineToUse >= 0) {
                final VisualPosition visualPosition = new VisualPosition(lineToUse, EditorImplUtil.getLastVisualLineColumnNumber(myEditor, lineToUse));
                if (debugBuffer != null) {
                    debugBuffer.append("Adjusting caret position by moving it before soft wrap. Moving to visual position ").append(visualPosition).append("\n");
                }
                final LogicalPosition logicalPosition = myEditor.visualToLogicalPosition(visualPosition);
                final int tmpOffset = myEditor.logicalPositionToOffset(logicalPosition);
                if (tmpOffset == newOffset) {
                    boolean restore = myReportCaretMoves;
                    myReportCaretMoves = false;
                    try {
                        moveToVisualPosition(visualPosition);
                        return null;
                    }
                    finally {
                        myReportCaretMoves = restore;
                    }
                }
                else {
                    LOG.error("Invalid editor dimension mapping", new Throwable(), AttachmentFactoryUtil.createContext("Expected to map visual position '" +
                        visualPosition +
                        "' to offset " +
                        newOffset +
                        " but got the following: -> logical position '" +
                        logicalPosition +
                        "'; -> offset " +
                        tmpOffset +
                        ". State: " +
                        myEditor.dumpState()));
                }
            }
        }

        if (!oldVisualPosition.equals(myVisibleCaret) || !oldCaretPosition.equals(myLogicalCaret)) {
            if (oldInVirtualSpace || isInVirtualSpace()) {
                myCaretModel.validateEditorSize();
            }
            CaretEvent event = new CaretEvent(this, oldCaretPosition, myLogicalCaret);
            if (fireListeners) {
                myCaretModel.fireCaretPositionChanged(event);
            }
            else {
                return event;
            }
        }
        return null;
    }

    private void updateOffsetsFromLogicalPosition() {
        int offset = myEditor.logicalPositionToOffset(myLogicalCaret);
        PositionMarker oldMarker = myPositionMarker;
        if (!oldMarker.isValid() || oldMarker.getStartOffset() != offset || oldMarker.getEndOffset() != offset) {
            myPositionMarker = new PositionMarker(offset);
            oldMarker.dispose();
        }
        myLeansTowardsLargerOffsets = myLogicalCaret.leansForward;
        myLogicalColumnAdjustment = myLogicalCaret.column - myEditor.offsetToLogicalPosition(offset).column;
    }

    private void setLastColumnNumber(int lastColumnNumber) {
        myLastColumnNumber = lastColumnNumber;
    }

    protected void requestRepaint(VerticalInfo oldVerticalInfo) {
        if (oldVerticalInfo == null) {
            oldVerticalInfo = new VerticalInfo(0, 0, myEditor.getLineHeight());
        }
        if (myVerticalInfo == null) {
            myVerticalInfo = new VerticalInfo(0, 0, myEditor.getLineHeight());
        }

        int oldY, oldHeight, newY, newHeight;
        if (oldVerticalInfo.logicalLineY == myVerticalInfo.logicalLineY && oldVerticalInfo.logicalLineHeight == myVerticalInfo.logicalLineHeight) {
            // caret moved within the same soft-wrapped line, repaint only original and target visual lines
            oldY = oldVerticalInfo.y;
            newY = myVerticalInfo.y;
            oldHeight = newHeight = myEditor.getLineHeight();
        }
        else {
            // caret moved between different (possible soft-wrapped) lines, repaint whole lines
            // (to repaint soft-wrap markers and line numbers in gutter)
            oldY = oldVerticalInfo.logicalLineY;
            oldHeight = oldVerticalInfo.logicalLineHeight;
            newY = myVerticalInfo.logicalLineY;
            newHeight = myVerticalInfo.logicalLineHeight;
        }

        //Rectangle visibleArea = myEditor.getScrollingModel().getVisibleArea();
        //final EditorGutterComponentEx gutter = myEditor.getGutterComponentEx();
        //final EditorComponentImpl content = myEditor.getContentComponent();
        //int editorUpdateWidth = myEditor.getScrollPane().getHorizontalScrollBar().getValue() + visibleArea.width;
        //int gutterUpdateWidth = gutter.getWidth();
        //int additionalRepaintHeight = this == myCaretModel.getPrimaryCaret() && Registry.is("editor.adjust.right.margin") && EditorPainter.isMarginShown(myEditor) ? 1 : 0;
        //if ((oldY <= newY + newHeight) && (oldY + oldHeight >= newY)) { // repaint regions overlap
        //  int y = Math.min(oldY, newY);
        //  int height = Math.max(oldY + oldHeight, newY + newHeight) - y;
        //  content.repaintEditorComponent(0, y - additionalRepaintHeight, editorUpdateWidth, height + additionalRepaintHeight);
        //  gutter.repaint(0, y, gutterUpdateWidth, height);
        //}
        //else {
        //  content.repaintEditorComponent(0, oldY - additionalRepaintHeight, editorUpdateWidth, oldHeight + additionalRepaintHeight);
        //  gutter.repaint(0, oldY, gutterUpdateWidth, oldHeight);
        //  content.repaintEditorComponent(0, newY - additionalRepaintHeight, editorUpdateWidth, newHeight + additionalRepaintHeight);
        //  gutter.repaint(0, newY, gutterUpdateWidth, newHeight);
        //}
    }

    @Override
    public void moveToVisualPosition(@Nonnull final VisualPosition pos) {
        moveToVisualPosition(pos, true);
    }

    private void moveToVisualPosition(@Nonnull final VisualPosition pos, boolean fireListeners) {
        myCaretModel.doWithCaretMerging(() -> doMoveToVisualPosition(pos, fireListeners));
    }

    void doMoveToVisualPosition(@Nonnull VisualPosition pos, boolean fireListeners) {
        assertIsDispatchThread();
        checkDisposal();
        validateCallContext();
        if (mySkipChangeRequests) {
            return;
        }
        if (myReportCaretMoves) {
            LOG.error("Unexpected caret move request");
        }
        if (!myEditor.isStickySelection() && !myEditor.getDocument().isInEventsHandling() && !pos.equals(myVisibleCaret)) {
            CopyPasteManager.getInstance().stopKillRings();
        }
        updateCachedStateIfNeeded();

        myDesiredX = -1;
        int column = pos.column;
        int line = pos.line;
        boolean leanRight = pos.leansRight;

        int lastLine = myEditor.getVisibleLineCount() - 1;
        if (lastLine <= 0) {
            lastLine = 0;
        }

        if (line > lastLine) {
            line = lastLine;
        }

        EditorSettings editorSettings = myEditor.getSettings();

        if (!editorSettings.isVirtualSpace()) {
            int lineEndColumn = EditorImplUtil.getLastVisualLineColumnNumber(myEditor, line);
            if (column > lineEndColumn && !myEditor.getSoftWrapModel().isInsideSoftWrap(pos)) {
                column = lineEndColumn;
                leanRight = true;
            }
        }

        VisualPosition oldVisualPosition = myVisibleCaret;
        myVisibleCaret = new VisualPosition(line, column, leanRight);

        VerticalInfo oldVerticalInfo = myVerticalInfo;
        LogicalPosition oldPosition = myLogicalCaret;
        boolean oldInVirtualSpace = isInVirtualSpace();

        myLogicalCaret = myEditor.visualToLogicalPosition(myVisibleCaret);
        VisualPosition mappedPosition = myEditor.logicalToVisualPosition(myLogicalCaret);
        myVisualColumnAdjustment = mappedPosition.line == myVisibleCaret.line && myVisibleCaret.column > mappedPosition.column ? myVisibleCaret.column - mappedPosition.column : 0;
        updateOffsetsFromLogicalPosition();

        updateVisualLineInfo();

        myEditor.getFoldingModel().flushCaretPosition(this);

        setLastColumnNumber(myLogicalCaret.column);
        myDesiredSelectionStartColumn = myDesiredSelectionEndColumn = -1;
        myEditor.updateCaretCursor();
        requestRepaint(oldVerticalInfo);

        if (!oldPosition.equals(myLogicalCaret) || !oldVisualPosition.equals(myVisibleCaret)) {
            if (oldInVirtualSpace || isInVirtualSpace()) {
                myCaretModel.validateEditorSize();
            }
            if (fireListeners) {
                CaretEvent event = new CaretEvent(this, oldPosition, myLogicalCaret);
                myCaretModel.fireCaretPositionChanged(event);
            }
        }
    }

    @Nullable
    CaretEvent moveToLogicalPosition(@Nonnull LogicalPosition pos, boolean locateBeforeSoftWrap, @Nullable StringBuilder debugBuffer, boolean adjustForInlays, boolean fireListeners) {
        if (mySkipChangeRequests) {
            return null;
        }
        if (myReportCaretMoves) {
            LOG.error("Unexpected caret move request");
        }
        if (!myEditor.isStickySelection() && !myEditor.getDocument().isInEventsHandling() && !pos.equals(myLogicalCaret)) {
            CopyPasteManager.getInstance().stopKillRings();
        }

        myReportCaretMoves = true;
        try {
            return doMoveToLogicalPosition(pos, locateBeforeSoftWrap, debugBuffer, adjustForInlays, fireListeners);
        }
        finally {
            myReportCaretMoves = false;
        }
    }

    private static void assertIsDispatchThread() {
        CodeEditorBase.assertIsDispatchThread();
    }

    private void validateCallContext() {
        LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread() || !myCaretModel.myIsInUpdate, "Caret model is in its update process. All requests are illegal at this point.");
    }

    @Override
    public void dispose() {
        PositionMarker positionMarker = myPositionMarker;
        if (positionMarker != null) {
            // null it first to avoid accessing invalid marker from other threads
            myPositionMarker = null;
            positionMarker.dispose();
        }
        if (mySelectionMarker != null) {
            mySelectionMarker = null;
        }
        isValid = false;
    }

    @Override
    public boolean isUpToDate() {
        return !myCaretModel.myIsInUpdate && !myReportCaretMoves;
    }

    @Nonnull
    @Override
    public LogicalPosition getLogicalPosition() {
        validateCallContext();
        updateCachedStateIfNeeded();
        return myLogicalCaret;
    }

    @Nonnull
    @Override
    public VisualPosition getVisualPosition() {
        validateCallContext();
        updateCachedStateIfNeeded();
        return myVisibleCaret;
    }

    @Override
    public int getOffset() {
        validateCallContext();
        validateContext(false);
        while (true) {
            PositionMarker marker = myPositionMarker;
            if (marker == null) {
                return 0; // caret was disposed
            }
            int startOffset = marker.getStartOffset();
            // double checking to avoid "concurrent dispose and return -1 from already disposed marker" race
            if (marker.isValid() && marker == myPositionMarker) {
                return startOffset;
            }
        }
    }

    @Override
    public int getVisualLineStart() {
        updateCachedStateIfNeeded();
        return myVisualLineStart;
    }

    @Override
    public int getVisualLineEnd() {
        updateCachedStateIfNeeded();
        return myVisualLineEnd;
    }

    /**
     * Recalculates caret visual position without changing its logical position (called when soft wraps are changing)
     */
    public void updateVisualPosition() {
        updateCachedStateIfNeeded();
        VerticalInfo oldVerticalInfo = myVerticalInfo;
        myLogicalCaret = new LogicalPosition(myLogicalCaret.line, myLogicalCaret.column, myLogicalCaret.leansForward);
        VisualPosition visualPosition = myEditor.logicalToVisualPosition(myLogicalCaret);
        myVisibleCaret = new VisualPosition(visualPosition.line, visualPosition.column + myVisualColumnAdjustment, visualPosition.leansRight);
        updateVisualLineInfo();

        myEditor.updateCaretCursor();
        requestRepaint(oldVerticalInfo);
    }

    private void updateVisualLineInfo() {
        myVisualLineStart = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line, 0)));
        myVisualLineEnd = myEditor.logicalPositionToOffset(myEditor.visualToLogicalPosition(new VisualPosition(myVisibleCaret.line + 1, 0)));

        int y = myEditor.visualLineToY(myVisibleCaret.line);

        int logicalLineStartY;
        if (myEditor.getSoftWrapModel().getSoftWrap(myVisualLineStart) == null) {
            logicalLineStartY = y;
        }
        else {
            int startVisualLine = myEditor.offsetToVisualLine(EditorUtil.getNotFoldedLineStartOffset(myEditor, getOffset()), false);
            logicalLineStartY = myEditor.visualLineToY(startVisualLine);
        }

        int logicalLineEndY;
        if (myEditor.getSoftWrapModel().getSoftWrap(myVisualLineEnd) == null) {
            logicalLineEndY = y;
        }
        else {
            int endVisualLine = myEditor.offsetToVisualLine(EditorUtil.getNotFoldedLineEndOffset(myEditor, getOffset()), true);
            logicalLineEndY = myEditor.visualLineToY(endVisualLine);
        }

        myVerticalInfo = new VerticalInfo(y, logicalLineStartY, logicalLineEndY - logicalLineStartY + myEditor.getLineHeight());
    }

    void onInlayAdded(int offset) {
        updateCachedStateIfNeeded();
        int currentOffset = getOffset();
        if (offset == currentOffset) {
            VisualPosition pos = EditorUtil.inlayAwareOffsetToVisualPosition(myEditor, offset);
            moveToVisualPosition(pos, false);
        }
        else {
            updateVisualPosition();
        }
    }

    void onInlayRemoved(int offset, int order) {
        int currentOffset = getOffset();
        if (offset == currentOffset && myVisualColumnAdjustment > 0 && myVisualColumnAdjustment > order) {
            myVisualColumnAdjustment--;
        }
        updateVisualPosition();
    }

    int getWordAtCaretStart() {
        Document document = myEditor.getDocument();
        int offset = getOffset();
        if (offset == 0) {
            return 0;
        }
        int lineNumber = getLogicalPosition().line;
        int newOffset = offset - 1;
        int minOffset = lineNumber > 0 ? document.getLineEndOffset(lineNumber - 1) : 0;
        boolean camel = myEditor.getSettings().isCamelWords();
        for (; newOffset > minOffset; newOffset--) {
            if (EditorActionUtil.isWordOrLexemeStart(myEditor, newOffset, camel)) {
                break;
            }
        }

        return newOffset;
    }

    int getWordAtCaretEnd() {
        Document document = myEditor.getDocument();
        int offset = getOffset();

        if (offset >= document.getTextLength() - 1 || document.getLineCount() == 0) {
            return offset;
        }

        int newOffset = offset + 1;

        int lineNumber = getLogicalPosition().line;
        int maxOffset = document.getLineEndOffset(lineNumber);
        if (newOffset > maxOffset) {
            if (lineNumber + 1 >= document.getLineCount()) {
                return offset;
            }
            maxOffset = document.getLineEndOffset(lineNumber + 1);
        }
        boolean camel = myEditor.getSettings().isCamelWords();
        for (; newOffset < maxOffset; newOffset++) {
            if (EditorActionUtil.isWordOrLexemeEnd(myEditor, newOffset, camel)) {
                break;
            }
        }

        return newOffset;
    }

    private CodeEditorCaretBase cloneWithoutSelection() {
        updateCachedStateIfNeeded();
        CodeEditorCaretBase clone = myCaretModel.createCaret(myEditor, myCaretModel);
        clone.myLogicalCaret = myLogicalCaret;
        clone.myVerticalInfo = myVerticalInfo;
        clone.myVisibleCaret = myVisibleCaret;
        clone.myPositionMarker.dispose();
        clone.myPositionMarker = new PositionMarker(getOffset());
        clone.myLeansTowardsLargerOffsets = myLeansTowardsLargerOffsets;
        clone.myLogicalColumnAdjustment = myLogicalColumnAdjustment;
        clone.myVisualColumnAdjustment = myVisualColumnAdjustment;
        clone.myVisualLineStart = myVisualLineStart;
        clone.myVisualLineEnd = myVisualLineEnd;
        clone.mySkipChangeRequests = mySkipChangeRequests;
        clone.myLastColumnNumber = myLastColumnNumber;
        clone.myReportCaretMoves = myReportCaretMoves;
        clone.myDesiredX = myDesiredX;
        clone.myDesiredSelectionStartColumn = -1;
        clone.myDesiredSelectionEndColumn = -1;
        return clone;
    }

    @Nullable
    @Override
    public Caret clone(boolean above) {
        assertIsDispatchThread();
        int lineShift = above ? -1 : 1;
        LogicalPosition oldPosition = getLogicalPosition();
        int newLine = oldPosition.line + lineShift;
        if (newLine < 0 || newLine >= myEditor.getDocument().getLineCount()) {
            return null;
        }
        final CodeEditorCaretBase clone = cloneWithoutSelection();
        final int newSelectionStartOffset;
        final int newSelectionEndOffset;
        final int newSelectionStartColumn;
        final int newSelectionEndColumn;
        final VisualPosition newSelectionStartPosition;
        final VisualPosition newSelectionEndPosition;
        final boolean hasNewSelection;
        if (hasSelection() || myDesiredSelectionStartColumn >= 0 || myDesiredSelectionEndColumn >= 0) {
            VisualPosition startPosition = getSelectionStartPosition();
            VisualPosition endPosition = getSelectionEndPosition();
            VisualPosition leadPosition = getLeadSelectionPosition();
            boolean leadIsStart = leadPosition.equals(startPosition);
            boolean leadIsEnd = leadPosition.equals(endPosition);
            LogicalPosition selectionStart = myEditor.visualToLogicalPosition(leadIsStart || leadIsEnd ? leadPosition : startPosition);
            LogicalPosition selectionEnd = myEditor.visualToLogicalPosition(leadIsEnd ? startPosition : endPosition);
            newSelectionStartColumn = myDesiredSelectionStartColumn < 0 ? selectionStart.column : myDesiredSelectionStartColumn;
            newSelectionEndColumn = myDesiredSelectionEndColumn < 0 ? selectionEnd.column : myDesiredSelectionEndColumn;
            LogicalPosition newSelectionStart = truncate(selectionStart.line + lineShift, newSelectionStartColumn);
            LogicalPosition newSelectionEnd = truncate(selectionEnd.line + lineShift, newSelectionEndColumn);
            newSelectionStartOffset = myEditor.logicalPositionToOffset(newSelectionStart);
            newSelectionEndOffset = myEditor.logicalPositionToOffset(newSelectionEnd);
            newSelectionStartPosition = myEditor.logicalToVisualPosition(newSelectionStart);
            newSelectionEndPosition = myEditor.logicalToVisualPosition(newSelectionEnd);
            hasNewSelection = !newSelectionStart.equals(newSelectionEnd);
        }
        else {
            newSelectionStartOffset = 0;
            newSelectionEndOffset = 0;
            newSelectionStartPosition = null;
            newSelectionEndPosition = null;
            hasNewSelection = false;
            newSelectionStartColumn = -1;
            newSelectionEndColumn = -1;
        }
        clone.moveToLogicalPosition(new LogicalPosition(newLine, myLastColumnNumber, myLeansTowardsLargerOffsets), false, null, false, false);
        clone.myLastColumnNumber = myLastColumnNumber;
        clone.myDesiredX = myDesiredX >= 0 ? myDesiredX : getCurrentX();
        clone.myDesiredSelectionStartColumn = newSelectionStartColumn;
        clone.myDesiredSelectionEndColumn = newSelectionEndColumn;

        if (myCaretModel.addCaret(clone, true)) {
            if (hasNewSelection) {
                myCaretModel.doWithCaretMerging(() -> clone.setSelection(newSelectionStartPosition, newSelectionStartOffset, newSelectionEndPosition, newSelectionEndOffset));
                if (!clone.isValid()) {
                    return null;
                }
            }
            myEditor.getScrollingModel().scrollTo(clone.getLogicalPosition(), ScrollType.RELATIVE);
            return clone;
        }
        else {
            Disposer.dispose(clone);
            return null;
        }
    }

    private LogicalPosition truncate(int line, int column) {
        if (line < 0) {
            return new LogicalPosition(0, 0);
        }
        else if (line >= myEditor.getDocument().getLineCount()) {
            return myEditor.offsetToLogicalPosition(myEditor.getDocument().getTextLength());
        }
        else {
            return new LogicalPosition(line, column);
        }
    }

    /**
     * @return information on whether current selection's direction in known
     * @see #setUnknownDirection(boolean)
     */
    public boolean isUnknownDirection() {
        return myUnknownDirection;
    }

    /**
     * There is a possible case that we don't know selection's direction. For example, a user might triple-click editor (select the
     * whole line). We can't say what selection end is a {@link #getLeadSelectionOffset() leading end} then. However, that matters
     * in a situation when a user clicks before or after that line holding Shift key. It's expected that the selection is expanded
     * up to that point than.
     * <p/>
     * That's why we allow to specify that the direction is unknown and {@link #isUnknownDirection() expose this information}
     * later.
     * <p/>
     * <b>Note:</b> when this method is called with {@code 'true'}, subsequent calls are guaranteed to return {@code true}
     * until selection is changed. 'Unknown direction' flag is automatically reset then.
     */
    public void setUnknownDirection(boolean unknownDirection) {
        myUnknownDirection = unknownDirection;
    }

    @Override
    public int getSelectionStart() {
        validateContext(false);
        if (hasSelection()) {
            RangeMarker marker = mySelectionMarker;
            if (marker != null) {
                return marker.getStartOffset();
            }
        }
        return getOffset();
    }

    @Nonnull
    @Override
    public VisualPosition getSelectionStartPosition() {
        validateContext(true);
        VisualPosition position;
        SelectionMarker marker = mySelectionMarker;
        if (hasSelection()) {
            position = getRangeMarkerStartPosition();
            if (position == null) {
                VisualPosition startPosition = myEditor.offsetToVisualPosition(marker.getStartOffset(), true, false);
                VisualPosition endPosition = myEditor.offsetToVisualPosition(marker.getEndOffset(), false, true);
                position = startPosition.after(endPosition) ? endPosition : startPosition;
            }
        }
        else {
            position = isVirtualSelectionEnabled() ? getVisualPosition() : myEditor.offsetToVisualPosition(getOffset(), getLogicalPosition().leansForward, false);
        }
        if (hasVirtualSelection()) {
            position = new VisualPosition(position.line, position.column + marker.startVirtualOffset);
        }
        return position;
    }

    LogicalPosition getSelectionStartLogicalPosition() {
        validateContext(true);
        LogicalPosition position;
        SelectionMarker marker = mySelectionMarker;
        if (hasSelection()) {
            VisualPosition visualPosition = getRangeMarkerStartPosition();
            position = visualPosition == null ? myEditor.offsetToLogicalPosition(marker.getStartOffset()).leanForward(true) : myEditor.visualToLogicalPosition(visualPosition);
        }
        else {
            position = getLogicalPosition();
        }
        if (hasVirtualSelection()) {
            position = new LogicalPosition(position.line, position.column + marker.startVirtualOffset);
        }
        return position;
    }

    @Override
    public int getSelectionEnd() {
        validateContext(false);
        if (hasSelection()) {
            RangeMarker marker = mySelectionMarker;
            if (marker != null) {
                return marker.getEndOffset();
            }
        }
        return getOffset();
    }

    @Nonnull
    @Override
    public VisualPosition getSelectionEndPosition() {
        validateContext(true);
        VisualPosition position;
        SelectionMarker marker = mySelectionMarker;
        if (hasSelection()) {
            position = getRangeMarkerEndPosition();
            if (position == null) {
                VisualPosition startPosition = myEditor.offsetToVisualPosition(marker.getStartOffset(), true, false);
                VisualPosition endPosition = myEditor.offsetToVisualPosition(marker.getEndOffset(), false, true);
                position = startPosition.after(endPosition) ? startPosition : endPosition;
            }
        }
        else {
            position = isVirtualSelectionEnabled() ? getVisualPosition() : myEditor.offsetToVisualPosition(getOffset(), getLogicalPosition().leansForward, false);
        }
        if (hasVirtualSelection()) {
            position = new VisualPosition(position.line, position.column + marker.endVirtualOffset);
        }
        return position;
    }

    LogicalPosition getSelectionEndLogicalPosition() {
        validateContext(true);
        LogicalPosition position;
        SelectionMarker marker = mySelectionMarker;
        if (hasSelection()) {
            VisualPosition visualPosition = getRangeMarkerEndPosition();
            position = visualPosition == null ? myEditor.offsetToLogicalPosition(marker.getEndOffset()) : myEditor.visualToLogicalPosition(visualPosition);
        }
        else {
            position = getLogicalPosition();
        }
        if (hasVirtualSelection()) {
            position = new LogicalPosition(position.line, position.column + marker.endVirtualOffset);
        }
        return position;
    }

    @Override
    public boolean hasSelection() {
        validateContext(false);
        SelectionMarker marker = mySelectionMarker;
        return marker != null && marker.isValid() && (marker.getEndOffset() > marker.getStartOffset() || isVirtualSelectionEnabled() && marker.hasVirtualSelection());
    }

    @Override
    public void setSelection(int startOffset, int endOffset) {
        setSelection(startOffset, endOffset, true);
    }

    @Override
    public void setSelection(int startOffset, int endOffset, boolean updateSystemSelection) {
        doSetSelection(myEditor.offsetToVisualPosition(startOffset, true, false), startOffset, myEditor.offsetToVisualPosition(endOffset, false, true), endOffset, false, updateSystemSelection, true);
    }

    @Override
    public void setSelection(int startOffset, @Nullable VisualPosition endPosition, int endOffset) {
        VisualPosition startPosition;
        if (hasSelection()) {
            startPosition = getLeadSelectionPosition();
        }
        else {
            startPosition = myEditor.offsetToVisualPosition(startOffset, true, false);
        }
        setSelection(startPosition, startOffset, endPosition, endOffset);
    }

    @Override
    public void setSelection(@Nullable VisualPosition startPosition, int startOffset, @Nullable VisualPosition endPosition, int endOffset) {
        setSelection(startPosition, startOffset, endPosition, endOffset, true);
    }

    @Override
    public void setSelection(@Nullable VisualPosition startPosition, int startOffset, @Nullable VisualPosition endPosition, int endOffset, boolean updateSystemSelection) {
        VisualPosition startPositionToUse = startPosition == null ? myEditor.offsetToVisualPosition(startOffset, true, false) : startPosition;
        VisualPosition endPositionToUse = endPosition == null ? myEditor.offsetToVisualPosition(endOffset, false, true) : endPosition;
        doSetSelection(startPositionToUse, startOffset, endPositionToUse, endOffset, true, updateSystemSelection, true);
    }

    void doSetSelection(@Nonnull final VisualPosition startPosition,
                        final int _startOffset,
                        @Nonnull final VisualPosition endPosition,
                        final int _endOffset,
                        final boolean visualPositionAware,
                        final boolean updateSystemSelection,
                        final boolean fireListeners) {
        myCaretModel.doWithCaretMerging(() -> {
            int startOffset = DocumentUtil.alignToCodePointBoundary(myEditor.getDocument(), _startOffset);
            int endOffset = DocumentUtil.alignToCodePointBoundary(myEditor.getDocument(), _endOffset);
            myUnknownDirection = false;
            final Document doc = myEditor.getDocument();

            validateContext(true);

            int textLength = doc.getTextLength();
            if (startOffset < 0 || startOffset > textLength) {
                LOG.error("Wrong startOffset: " + startOffset + ", textLength=" + textLength);
            }
            if (endOffset < 0 || endOffset > textLength) {
                LOG.error("Wrong endOffset: " + endOffset + ", textLength=" + textLength);
            }

            if (!visualPositionAware && startOffset == endOffset) {
                removeSelection();
                return;
            }

            /* Normalize selection */
            boolean switchedOffsets = false;
            if (startOffset > endOffset) {
                int tmp = startOffset;
                startOffset = endOffset;
                endOffset = tmp;
                switchedOffsets = true;
            }

            FoldingModelEx foldingModel = myEditor.getFoldingModel();
            FoldRegion startFold = foldingModel.getCollapsedRegionAtOffset(startOffset);
            if (startFold != null && startFold.getStartOffset() < startOffset) {
                startOffset = startFold.getStartOffset();
            }

            FoldRegion endFold = foldingModel.getCollapsedRegionAtOffset(endOffset);
            if (endFold != null && endFold.getStartOffset() < endOffset) {
                // All visual positions that lay at collapsed fold region placeholder are mapped to the same offset. Hence, there are
                // at least two distinct situations - selection end is located inside collapsed fold region placeholder and just before it.
                // We want to expand selection to the fold region end at the former case and keep selection as-is at the latest one.
                endOffset = endFold.getEndOffset();
            }

            int oldSelectionStart;
            int oldSelectionEnd;

            if (hasSelection()) {
                oldSelectionStart = getSelectionStart();
                oldSelectionEnd = getSelectionEnd();
                if (oldSelectionStart == startOffset && oldSelectionEnd == endOffset && !visualPositionAware) {
                    return;
                }
            }
            else {
                oldSelectionStart = oldSelectionEnd = getOffset();
            }

            SelectionMarker marker = new SelectionMarker(startOffset, endOffset);
            if (visualPositionAware) {
                if (endPosition.after(startPosition)) {
                    setRangeMarkerStartPosition(startPosition);
                    setRangeMarkerEndPosition(endPosition);
                    setRangeMarkerEndPositionIsLead(false);
                }
                else {
                    setRangeMarkerStartPosition(endPosition);
                    setRangeMarkerEndPosition(startPosition);
                    setRangeMarkerEndPositionIsLead(true);
                }

                if (isVirtualSelectionEnabled() && myEditor.getDocument().getLineNumber(startOffset) == myEditor.getDocument().getLineNumber(endOffset)) {
                    int endLineColumn = myEditor.offsetToVisualPosition(endOffset).column;
                    int startDiff = EditorUtil.isAtLineEnd(myEditor, switchedOffsets ? endOffset : startOffset) ? startPosition.column - endLineColumn : 0;
                    int endDiff = EditorUtil.isAtLineEnd(myEditor, switchedOffsets ? startOffset : endOffset) ? endPosition.column - endLineColumn : 0;
                    marker.startVirtualOffset = Math.max(0, Math.min(startDiff, endDiff));
                    marker.endVirtualOffset = Math.max(0, Math.max(startDiff, endDiff));
                }
            }
            mySelectionMarker = marker;

            if (fireListeners) {
                myEditor.getSelectionModel().fireSelectionChanged(new SelectionEvent(myEditor, oldSelectionStart, oldSelectionEnd, startOffset, endOffset));
            }

            if (updateSystemSelection) {
                myCaretModel.updateSystemSelection();
            }
        });
    }

    @Override
    public void removeSelection() {
        if (myEditor.isStickySelection()) {
            // Most of our 'change caret position' actions (like move caret to word start/end etc) remove active selection.
            // However, we don't want to do that for 'sticky selection'.
            return;
        }
        myCaretModel.doWithCaretMerging(() -> {
            validateContext(true);
            myUnknownDirection = false;
            RangeMarker marker = mySelectionMarker;
            if (marker != null && marker.isValid()) {
                int startOffset = marker.getStartOffset();
                int endOffset = marker.getEndOffset();
                int caretOffset = getOffset();
                mySelectionMarker = null;
                myEditor.getSelectionModel().fireSelectionChanged(new SelectionEvent(myEditor, startOffset, endOffset, caretOffset, caretOffset));
            }
        });
    }

    @Override
    public int getLeadSelectionOffset() {
        validateContext(false);
        int caretOffset = getOffset();
        if (hasSelection()) {
            RangeMarker marker = mySelectionMarker;
            if (marker != null && marker.isValid()) {
                int startOffset = marker.getStartOffset();
                int endOffset = marker.getEndOffset();
                if (caretOffset != startOffset && caretOffset != endOffset) {
                    // Try to check if current selection is tweaked by fold region.
                    FoldingModelEx foldingModel = myEditor.getFoldingModel();
                    FoldRegion foldRegion = foldingModel.getCollapsedRegionAtOffset(caretOffset);
                    if (foldRegion != null) {
                        if (foldRegion.getStartOffset() == startOffset) {
                            return endOffset;
                        }
                        else if (foldRegion.getEndOffset() == endOffset) {
                            return startOffset;
                        }
                    }
                }

                return caretOffset == endOffset ? startOffset : endOffset;
            }
        }
        return caretOffset;
    }

    @Nonnull
    @Override
    public VisualPosition getLeadSelectionPosition() {
        SelectionMarker marker = mySelectionMarker;
        VisualPosition caretPosition = getVisualPosition();
        if (isVirtualSelectionEnabled() && !hasSelection()) {
            return caretPosition;
        }
        if (marker == null || !marker.isValid()) {
            return caretPosition;
        }

        if (isRangeMarkerEndPositionIsLead()) {
            VisualPosition result = getRangeMarkerEndPosition();
            if (result == null) {
                return getSelectionEndPosition();
            }
            else {
                if (hasVirtualSelection()) {
                    result = new VisualPosition(result.line, result.column + marker.endVirtualOffset);
                }
                return result;
            }
        }
        else {
            VisualPosition result = getRangeMarkerStartPosition();
            if (result == null) {
                return getSelectionStartPosition();
            }
            else {
                if (hasVirtualSelection()) {
                    result = new VisualPosition(result.line, result.column + marker.startVirtualOffset);
                }
                return result;
            }
        }
    }

    @Override
    public void selectLineAtCaret() {
        validateContext(true);
        myCaretModel.doWithCaretMerging(() -> doSelectLineAtCaret(this));
    }

    @Override
    public void selectWordAtCaret(final boolean honorCamelWordsSettings) {
        validateContext(true);
        myCaretModel.doWithCaretMerging(() -> {
            removeSelection();
            final EditorSettings settings = myEditor.getSettings();
            boolean camelTemp = settings.isCamelWords();

            final boolean needOverrideSetting = camelTemp && !honorCamelWordsSettings;
            if (needOverrideSetting) {
                settings.setCamelWords(false);
            }

            try {
                EditorActionHandler handler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
                DataContext context = AnActionEvent.getInjectedDataContext(CodeEditorInternalHelper.getInstance().createCaretDataContext(myEditor.getDataContext(), this));
                Caret caret = context.getRequiredData(Caret.KEY);
                handler.execute(caret.getEditor(), caret, context);
            }
            finally {
                if (needOverrideSetting) {
                    settings.resetCamelWords();
                }
            }
        });
    }

    @Nullable
    @Override
    public String getSelectedText() {
        if (!hasSelection()) {
            return null;
        }
        SelectionMarker selectionMarker = mySelectionMarker;
        CharSequence text = myEditor.getDocument().getCharsSequence();
        int selectionStart = getSelectionStart();
        int selectionEnd = getSelectionEnd();
        String selectedText = text.subSequence(selectionStart, selectionEnd).toString();
        if (isVirtualSelectionEnabled() && selectionMarker.hasVirtualSelection()) {
            int padding = selectionMarker.endVirtualOffset - selectionMarker.startVirtualOffset;
            StringBuilder builder = new StringBuilder(selectedText.length() + padding);
            builder.append(selectedText);
            for (int i = 0; i < padding; i++) {
                builder.append(' ');
            }
            return builder.toString();
        }
        else {
            return selectedText;
        }
    }

    private static void validateContext(boolean requireEdt) {
        if (requireEdt) {
            UIAccess.assertIsUIThread();
        }
        else {
            ApplicationManager.getApplication().assertReadAccessAllowed();
        }
    }

    private boolean isVirtualSelectionEnabled() {
        return myEditor.isColumnMode();
    }

    boolean hasVirtualSelection() {
        validateContext(false);
        SelectionMarker marker = mySelectionMarker;
        return marker != null && marker.isValid() && isVirtualSelectionEnabled() && marker.hasVirtualSelection();
    }

    void resetVirtualSelection() {
        SelectionMarker marker = mySelectionMarker;
        if (marker != null) {
            marker.resetVirtualSelection();
        }
    }

    private int getCurrentX() {
        return myEditor.visualPositionToXY(myVisibleCaret).x;
    }

    @Override
    @Nonnull
    public CodeEditorBase getEditor() {
        return myEditor;
    }

    @Override
    public String toString() {
        return "Caret at " +
            (myDocumentUpdateCounter == myCaretModel.myDocumentUpdateCounter ? myVisibleCaret : getOffset()) +
            (mySelectionMarker == null ? "" : ", selection marker: " + mySelectionMarker);
    }

    @Override
    public boolean isAtRtlLocation() {
        return myEditor.isRtlLocation(getVisualPosition());
    }

    @Override
    public boolean isAtBidiRunBoundary() {
        return myEditor.isAtBidiRunBoundary(getVisualPosition());
    }

    @Nonnull
    @Override
    public CaretVisualAttributes getVisualAttributes() {
        CaretVisualAttributes attrs = getUserData(VISUAL_ATTRIBUTES_KEY);
        return attrs == null ? CaretVisualAttributes.DEFAULT : attrs;
    }

    @Override
    public void setVisualAttributes(@Nonnull CaretVisualAttributes attributes) {
        putUserData(VISUAL_ATTRIBUTES_KEY, attributes == CaretVisualAttributes.DEFAULT ? null : attributes);
        requestRepaint(myVerticalInfo);
    }

    @Nonnull
    @Override
    public String dumpState() {
        return "{valid: " +
            isValid +
            ", update counter: " +
            myDocumentUpdateCounter +
            ", position: " +
            myPositionMarker +
            ", logical pos: " +
            myLogicalCaret +
            ", visual pos: " +
            myVisibleCaret +
            ", visual line start: " +
            myVisualLineStart +
            ", visual line end: " +
            myVisualLineEnd +
            ", skip change requests: " +
            mySkipChangeRequests +
            ", desired selection start column: " +
            myDesiredSelectionStartColumn +
            ", desired selection end column: " +
            myDesiredSelectionEndColumn +
            ", report caret moves: " +
            myReportCaretMoves +
            ", desired x: " +
            myDesiredX +
            ", selection marker: " +
            mySelectionMarker +
            ", rangeMarker start position: " +
            myRangeMarkerStartPosition +
            ", rangeMarker end position: " +
            myRangeMarkerEndPosition +
            ", rangeMarker end position is lead: " +
            myRangeMarkerEndPositionIsLead +
            ", unknown direction: " +
            myUnknownDirection +
            ", logical column adjustment: " +
            myLogicalColumnAdjustment +
            ", visual column adjustment: " +
            myVisualColumnAdjustment +
            '}';
    }

    @Nullable
    private VisualPosition getRangeMarkerStartPosition() {
        invalidateRangeMarkerVisualPositions(mySelectionMarker);
        return myRangeMarkerStartPosition;
    }

    private void setRangeMarkerStartPosition(@Nonnull VisualPosition startPosition) {
        myRangeMarkerStartPosition = startPosition;
    }

    @Nullable
    private VisualPosition getRangeMarkerEndPosition() {
        invalidateRangeMarkerVisualPositions(mySelectionMarker);
        return myRangeMarkerEndPosition;
    }

    private void setRangeMarkerEndPosition(@Nonnull VisualPosition endPosition) {
        myRangeMarkerEndPosition = endPosition;
    }

    private boolean isRangeMarkerEndPositionIsLead() {
        return myRangeMarkerEndPositionIsLead;
    }

    private void setRangeMarkerEndPositionIsLead(boolean endPositionIsLead) {
        myRangeMarkerEndPositionIsLead = endPositionIsLead;
    }

    private void invalidateRangeMarkerVisualPositions(RangeMarker marker) {
        CodeEditorSoftWrapModelBase model = myEditor.getSoftWrapModel();
        CodeEditorInlayModelBase inlayModel = myEditor.getInlayModel();
        int startOffset = marker.getStartOffset();
        int endOffset = marker.getEndOffset();
        if ((myRangeMarkerStartPosition == null || !myEditor.offsetToVisualPosition(startOffset, true, false).equals(myRangeMarkerStartPosition)) &&
            model.getSoftWrap(startOffset) == null &&
            !inlayModel.hasInlineElementAt(startOffset) ||
            (myRangeMarkerEndPosition == null || !myEditor.offsetToVisualPosition(endOffset, false, true).equals(myRangeMarkerEndPosition)) &&
                model.getSoftWrap(endOffset) == null &&
                !inlayModel.hasInlineElementAt(endOffset)) {
            myRangeMarkerStartPosition = null;
            myRangeMarkerEndPosition = null;
        }
    }

    void updateCachedStateIfNeeded() {
        if (!ApplicationManager.getApplication().isDispatchThread()) {
            return;
        }
        int modelCounter = myCaretModel.myDocumentUpdateCounter;
        if (myDocumentUpdateCounter != modelCounter) {
            updateCachedState();
            myDocumentUpdateCounter = modelCounter;
        }
    }

    private void updateCachedState() {
        LogicalPosition lp = myEditor.offsetToLogicalPosition(getOffset());
        myLogicalCaret = new LogicalPosition(lp.line, lp.column + myLogicalColumnAdjustment, myLeansTowardsLargerOffsets);
        VisualPosition visualPosition = myEditor.logicalToVisualPosition(myLogicalCaret);
        myVisibleCaret = new VisualPosition(visualPosition.line, visualPosition.column + myVisualColumnAdjustment, visualPosition.leansRight);
        updateVisualLineInfo();
        setLastColumnNumber(myLogicalCaret.column);
        myDesiredSelectionStartColumn = myDesiredSelectionEndColumn = -1;
        myDesiredX = -1;
    }

    public boolean isInVirtualSpace() {
        return myLogicalColumnAdjustment > 0;
    }

    private void checkDisposal() {
        if (myEditor.isDisposed()) {
            myEditor.throwDisposalError("Editor is already disposed");
        }
        if (!isValid) {
            throw new IllegalStateException("Caret is invalid");
        }
    }

    @TestOnly
    public void validateState() {
        LOG.assertTrue(!DocumentUtil.isInsideSurrogatePair(myEditor.getDocument(), getOffset()));
        LOG.assertTrue(!DocumentUtil.isInsideSurrogatePair(myEditor.getDocument(), getSelectionStart()));
        LOG.assertTrue(!DocumentUtil.isInsideSurrogatePair(myEditor.getDocument(), getSelectionEnd()));
    }

    public static class VerticalInfo {
        public final int y; // y coordinate of caret
        public final int logicalLineY; // y coordinate of caret's logical line start
        public final int logicalLineHeight; // height of caret's logical line
        // (if there are soft wraps, it's larger than a visual line's height)

        public VerticalInfo(int y, int logicalLineY, int logicalLineHeight) {
            this.y = y;
            this.logicalLineY = logicalLineY;
            this.logicalLineHeight = logicalLineHeight;
        }
    }

    class PositionMarker extends RangeMarkerImpl {
        private PositionMarker(int offset) {
            super(myEditor.getDocument(), offset, offset, false, true);
            myCaretModel.myPositionMarkerTree.addInterval(this, offset, offset, false, false, false, 0);
        }

        @Override
        public void dispose() {
            if (isValid()) {
                myCaretModel.myPositionMarkerTree.removeInterval(this);
            }
        }

        @Override
        protected void changedUpdateImpl(@Nonnull DocumentEvent e) {
            int oldOffset = intervalStart();
            RangeMarkerTree.RMNode<RangeMarkerEx> node = myNode;
            long newRange = isValid() && node != null ? applyChange(e, node.toScalarRange(), isGreedyToLeft(), isGreedyToRight(), isStickingToRight()) : -1;

            if (newRange != -1) {
                setRange(newRange);
                // Under certain conditions, when text is inserted at caret position, we position caret at the end of inserted text.
                // Ideally, client code should be responsible for positioning caret after document modification, but in case of
                // postponed formatting (after PSI modifications), this is hard to implement, so a heuristic below is used.
                if (e.getOldLength() == 0 && oldOffset == e.getOffset() &&
                    !Boolean.TRUE.equals(myEditor.getUserData(EditorEx.DISABLE_CARET_SHIFT_ON_WHITESPACE_INSERTION)) &&
                    needToShiftWhiteSpaces(e)) {
                    int afterInserted = e.getOffset() + e.getNewLength();
                    setRange(TextRangeScalarUtil.toScalarRange(afterInserted, afterInserted));
                }
                int offset = intervalStart();
                if (DocumentUtil.isInsideSurrogatePair(getDocument(), offset)) {
                    setRange(TextRangeScalarUtil.toScalarRange(offset - 1, offset - 1));
                }
            }
            else {
                setValid(true);
                int newOffset = Math.min(getStartOffset(), e.getOffset() + e.getNewLength());
                if (!e.getDocument().isInBulkUpdate() && e.isWholeTextReplaced()) {
                    try {
                        int line = ((DocumentEventImpl) e).translateLineViaDiff(myLogicalCaret.line);
                        newOffset = myEditor.logicalPositionToOffset(new LogicalPosition(line, myLogicalCaret.column));
                    }
                    catch (FilesTooBigForDiffException ex) {
                        LOG.info(ex);
                    }
                }
                newOffset = DocumentUtil.alignToCodePointBoundary(getDocument(), newOffset);
                setRange(TextRangeScalarUtil.toScalarRange(newOffset, newOffset));
            }
            myLogicalColumnAdjustment = 0;
            myVisualColumnAdjustment = 0;
            if (oldOffset >= e.getOffset() && oldOffset <= e.getOffset() + e.getOldLength() && e.getNewLength() == 0 && e.getOldLength() > 0) {
                int inlaysToTheLeft = myEditor.getInlayModel().getInlineElementsInRange(e.getOffset(), e.getOffset()).size();
                boolean hasInlaysToTheRight = myEditor.getInlayModel().hasInlineElementAt(e.getOffset() + e.getOldLength());
                if (inlaysToTheLeft > 0 || hasInlaysToTheRight) {
                    myLeansTowardsLargerOffsets = !hasInlaysToTheRight;
                    myVisualColumnAdjustment = hasInlaysToTheRight ? inlaysToTheLeft : 0;
                }
                else if (oldOffset == e.getOffset()) {
                    myLeansTowardsLargerOffsets = false;
                }
            }
        }

        private static boolean needToShiftWhiteSpaces(final DocumentEvent e) {
            return e.getOffset() > 0
                && Character.isWhitespace(e.getDocument().getImmutableCharSequence().charAt(e.getOffset() - 1))
                && CharArrayUtil.containsOnlyWhiteSpaces(e.getNewFragment())
                && !CharArrayUtil.containLineBreaks(e.getNewFragment());
        }

        @Override
        protected void onReTarget(@Nonnull DocumentEvent e) {
            int offset = intervalStart();
            if (DocumentUtil.isInsideSurrogatePair(getDocument(), offset)) {
                setRange(TextRangeScalarUtil.toScalarRange(offset - 1, offset - 1));
            }
        }
    }

    class SelectionMarker extends RangeMarkerImpl {
        // offsets of selection start/end position relative to end of line - can be non-zero in column selection mode
        // these are non-negative values, myStartVirtualOffset is always less or equal to myEndVirtualOffset
        private int startVirtualOffset;
        private int endVirtualOffset;

        private SelectionMarker(int start, int end) {
            super(myEditor.getDocument(), start, end, false, true);
            myCaretModel.mySelectionMarkerTree.addInterval(this, start, end, false, false, false, 0);
        }

        private void resetVirtualSelection() {
            startVirtualOffset = 0;
            endVirtualOffset = 0;
        }

        private boolean hasVirtualSelection() {
            return endVirtualOffset > startVirtualOffset;
        }

        @Override
        public void dispose() {
            if (isValid()) {
                myCaretModel.mySelectionMarkerTree.removeInterval(this);
            }
        }

        @Override
        protected void changedUpdateImpl(@Nonnull DocumentEvent e) {
            super.changedUpdateImpl(e);
            if (isValid()) {
                alignToSurrogatePairBoundaries();
            }

            if (endVirtualOffset > 0 && isValid()) {
                Document document = e.getDocument();
                int startAfter = intervalStart();
                int endAfter = intervalEnd();
                if (!DocumentUtil.isAtLineEnd(endAfter, document) || document.getLineNumber(startAfter) != document.getLineNumber(endAfter)) {
                    resetVirtualSelection();
                }
            }
        }

        private void alignToSurrogatePairBoundaries() {
            long alignedRange = TextRangeScalarUtil.shift(toScalarRange(),
                DocumentUtil.isInsideSurrogatePair(getDocument(), getStartOffset()) ? -1 : 0,
                DocumentUtil.isInsideSurrogatePair(getDocument(), getEndOffset()) ? -1 : 0);
            setRange(alignedRange);
        }

        @Override
        protected void onReTarget(@Nonnull DocumentEvent e) {
            alignToSurrogatePairBoundaries();
        }

        @Override
        public String toString() {
            return super.toString() + (hasVirtualSelection() ? " virtual selection: " + startVirtualOffset + "-" + endVirtualOffset : "");
        }
    }
}
