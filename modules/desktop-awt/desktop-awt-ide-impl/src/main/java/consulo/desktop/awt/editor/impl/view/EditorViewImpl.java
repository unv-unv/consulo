/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package consulo.desktop.awt.editor.impl.view;

import consulo.application.ApplicationManager;
import consulo.application.util.Dumpable;
import consulo.application.util.registry.Registry;
import consulo.codeEditor.*;
import consulo.codeEditor.event.VisibleAreaEvent;
import consulo.codeEditor.event.VisibleAreaListener;
import consulo.codeEditor.impl.EditorSettingsExternalizable;
import consulo.codeEditor.impl.FontInfo;
import consulo.codeEditor.impl.FontLayoutService;
import consulo.codeEditor.impl.LogicalPositionCache;
import consulo.codeEditor.impl.internal.RealEditorView;
import consulo.codeEditor.impl.internal.VisualLinesIterator;
import consulo.colorScheme.EditorFontType;
import consulo.colorScheme.TextAttributes;
import consulo.desktop.awt.editor.impl.*;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.document.impl.DocumentImpl;
import consulo.document.internal.DocumentEx;
import consulo.ide.impl.idea.openapi.editor.ex.util.EditorUtil;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.Point2D;
import java.text.Bidi;

/**
 * A facade for components responsible for drawing editor contents, managing editor size
 * and coordinate conversions (offset <-> logical position <-> visual position <-> x,y).
 * <p>
 * Also contains a cache of several font-related quantities (line height, space width, etc).
 */
public class EditorViewImpl implements RealEditorView, TextDrawingCallback, Disposable, Dumpable, HierarchyListener, VisibleAreaListener {
    private static final Key<LineLayout> FOLD_REGION_TEXT_LAYOUT = Key.create("text.layout");

    private final DesktopEditorImpl myEditor;
    private final DocumentEx myDocument;
    private final EditorPainter myPainter;
    private final EditorCoordinateMapper myMapper;
    private final EditorSizeManager mySizeManager;
    private final TextLayoutCache myTextLayoutCache;
    private final LogicalPositionCache myLogicalPositionCache;
    private final CharWidthCache myCharWidthCache;
    private final TabFragment myTabFragment;

    private FontRenderContext myFontRenderContext; // guarded by myLock
    private String myPrefixText; // accessed only in EDT
    private LineLayout myPrefixLayout; // guarded by myLock
    private TextAttributes myPrefixAttributes; // accessed only in EDT
    private int myBidiFlags; // accessed only in EDT

    private float myPlainSpaceWidth; // guarded by myLock
    private int myLineHeight; // guarded by myLock
    private int myDescent; // guarded by myLock
    private int myCharHeight; // guarded by myLock
    private float myMaxCharWidth; // guarded by myLock
    private int myCapHeight; // guarded by myLock
    private int myTabSize; // guarded by myLock
    private int myTopOverhang; //guarded by myLock
    private int myBottomOverhang; //guarded by myLock

    private final Object myLock = new Object();

    public EditorViewImpl(DesktopEditorImpl editor) {
        myEditor = editor;
        myDocument = editor.getDocument();

        myPainter = new EditorPainter(this);
        myMapper = new EditorCoordinateMapper(this);
        mySizeManager = new EditorSizeManager(this);
        myTextLayoutCache = new TextLayoutCache(this);
        myLogicalPositionCache = new LogicalPositionCache(editor, this::getTabSize);
        myCharWidthCache = new CharWidthCache(this);
        myTabFragment = new TabFragment(this);

        myEditor.getContentComponent().addHierarchyListener(this);
        myEditor.getScrollingModel().addVisibleAreaListener(this);

        Disposer.register(this, myLogicalPositionCache);
        Disposer.register(this, myTextLayoutCache);
        Disposer.register(this, mySizeManager);
    }

    DesktopEditorImpl getEditor() {
        return myEditor;
    }

    FontRenderContext getFontRenderContext() {
        synchronized (myLock) {
            return myFontRenderContext;
        }
    }

    EditorSizeManager getSizeManager() {
        return mySizeManager;
    }

    TextLayoutCache getTextLayoutCache() {
        return myTextLayoutCache;
    }

    EditorPainter getPainter() {
        return myPainter;
    }

    TabFragment getTabFragment() {
        return myTabFragment;
    }

    LogicalPositionCache getLogicalPositionCache() {
        return myLogicalPositionCache;
    }

    float getRightAlignmentLineStartX(int visualLine) {
        return myMapper.getRightAlignmentLineStartX(visualLine);
    }

    int getRightAlignmentMarginX() {
        return myMapper.getRightAlignmentMarginX();
    }

    @Override
    public void dispose() {
        myEditor.getScrollingModel().removeVisibleAreaListener(this);
        myEditor.getContentComponent().removeHierarchyListener(this);
    }

    @Override
    public void hierarchyChanged(HierarchyEvent e) {
        if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && e.getComponent().isShowing()) {
            checkFontRenderContext(null);
        }
    }

    @Override
    public void visibleAreaChanged(@Nonnull VisibleAreaEvent e) {
        checkFontRenderContext(null);
    }

    @Override
    public int yToVisualLine(int y) {
        return myMapper.yToVisualLine(y);
    }

    @Override
    public int visualLineToY(int line) {
        return myMapper.visualLineToY(line);
    }

    @Nonnull
    public LogicalPosition offsetToLogicalPosition(int offset) {
        assertIsReadAccess();
        return myMapper.offsetToLogicalPosition(offset);
    }

    public int logicalPositionToOffset(@Nonnull LogicalPosition pos) {
        assertIsReadAccess();
        return myMapper.logicalPositionToOffset(pos);
    }

    @Nonnull
    public VisualPosition logicalToVisualPosition(@Nonnull LogicalPosition pos, boolean beforeSoftWrap) {
        assertIsDispatchThread();
        assertNotInBulkMode();
        myEditor.getSoftWrapModel().prepareToMapping();
        return myMapper.logicalToVisualPosition(pos, beforeSoftWrap);
    }

    @Nonnull
    public LogicalPosition visualToLogicalPosition(@Nonnull VisualPosition pos) {
        assertIsDispatchThread();
        assertNotInBulkMode();
        myEditor.getSoftWrapModel().prepareToMapping();
        return myMapper.visualToLogicalPosition(pos);
    }

    @Nonnull
    public VisualPosition offsetToVisualPosition(int offset, boolean leanTowardsLargerOffsets, boolean beforeSoftWrap) {
        assertIsDispatchThread();
        assertNotInBulkMode();
        myEditor.getSoftWrapModel().prepareToMapping();
        return myMapper.offsetToVisualPosition(offset, leanTowardsLargerOffsets, beforeSoftWrap);
    }

    public int visualPositionToOffset(VisualPosition visualPosition) {
        assertIsDispatchThread();
        assertNotInBulkMode();
        myEditor.getSoftWrapModel().prepareToMapping();
        return myMapper.visualPositionToOffset(visualPosition);
    }

    public int offsetToVisualLine(int offset, boolean beforeSoftWrap) {
        assertIsDispatchThread();
        assertNotInBulkMode();
        myEditor.getSoftWrapModel().prepareToMapping();
        return myMapper.offsetToVisualLine(offset, beforeSoftWrap);
    }

    @Override
    public int visualLineToOffset(int visualLine) {
        assertIsDispatchThread();
        assertNotInBulkMode();
        myEditor.getSoftWrapModel().prepareToMapping();
        return myMapper.visualLineToOffset(visualLine);
    }

    @Nonnull
    public VisualPosition xyToVisualPosition(@Nonnull Point2D p) {
        assertIsDispatchThread();
        assertNotInBulkMode();
        myEditor.getSoftWrapModel().prepareToMapping();
        return myMapper.xyToVisualPosition(p);
    }

    @Nonnull
    public Point2D visualPositionToXY(@Nonnull VisualPosition pos) {
        assertIsDispatchThread();
        assertNotInBulkMode();
        myEditor.getSoftWrapModel().prepareToMapping();
        return myMapper.visualPositionToXY(pos);
    }

    @Nonnull
    public Point2D offsetToXY(int offset, boolean leanTowardsLargerOffsets, boolean beforeSoftWrap) {
        assertIsDispatchThread();
        assertNotInBulkMode();
        myEditor.getSoftWrapModel().prepareToMapping();
        return myMapper.offsetToXY(offset, leanTowardsLargerOffsets, beforeSoftWrap);
    }

    public void setPrefix(String prefixText, TextAttributes attributes) {
        assertIsDispatchThread();
        myPrefixText = prefixText;
        synchronized (myLock) {
            myPrefixLayout = prefixText == null || prefixText.isEmpty() ? null : LineLayout.create(this, prefixText, attributes.getFontType());
        }
        myPrefixAttributes = attributes;
        mySizeManager.invalidateRange(0, 0);
    }

    public float getPrefixTextWidthInPixels() {
        synchronized (myLock) {
            return myPrefixLayout == null ? 0 : myPrefixLayout.getWidth();
        }
    }

    LineLayout getPrefixLayout() {
        synchronized (myLock) {
            return myPrefixLayout;
        }
    }

    TextAttributes getPrefixAttributes() {
        return myPrefixAttributes;
    }

    public void paint(Graphics2D g) {
        assertIsDispatchThread();
        myEditor.getSoftWrapModel().prepareToMapping();
        checkFontRenderContext(g.getFontRenderContext());
        myPainter.paint(g);
    }

    public void repaintCarets() {
        assertIsDispatchThread();
        myPainter.repaintCarets();
    }

    public Dimension getPreferredSize() {
        assertIsDispatchThread();
        assert !myEditor.isPurePaintingMode();
        myEditor.getSoftWrapModel().prepareToMapping();
        return mySizeManager.getPreferredSize();
    }

    /**
     * Returns preferred pixel width of the lines in range.
     * <p>
     * This method is currently used only with "idea.true.smooth.scrolling" experimental option.
     *
     * @param beginLine begin visual line (inclusive)
     * @param endLine   end visual line (exclusive), may be greater than the actual number of lines
     * @return preferred pixel width
     */
    public int getPreferredWidth(int beginLine, int endLine) {
        assertIsDispatchThread();
        assert !myEditor.isPurePaintingMode();
        myEditor.getSoftWrapModel().prepareToMapping();
        return mySizeManager.getPreferredWidth(beginLine, endLine);
    }

    public int getPreferredHeight() {
        assertIsDispatchThread();
        assert !myEditor.isPurePaintingMode();
        myEditor.getSoftWrapModel().prepareToMapping();
        return mySizeManager.getPreferredHeight();
    }

    public int getMaxWidthInRange(int startOffset, int endOffset) {
        assertIsDispatchThread();
        int startVisualLine = offsetToVisualLine(startOffset, false);
        int endVisualLine = offsetToVisualLine(endOffset, true);
        return getMaxTextWidthInLineRange(startVisualLine, endVisualLine) + getInsets().left;
    }

    /**
     * If {@code quickEvaluationListener} is provided, quick approximate size evaluation becomes enabled, listener will be invoked
     * if approximation will in fact be used during width calculation.
     */
    int getMaxTextWidthInLineRange(int startVisualLine, int endVisualLine) {
        myEditor.getSoftWrapModel().prepareToMapping();
        int maxWidth = 0;
        VisualLinesIterator iterator = new VisualLinesIterator(myEditor, startVisualLine);
        while (!iterator.atEnd() && iterator.getVisualLine() <= endVisualLine) {
            int width = mySizeManager.getVisualLineWidth(iterator, false);
            maxWidth = Math.max(maxWidth, width);
            iterator.advance();
        }
        return maxWidth;
    }

    public void reinitSettings() {
        assertIsDispatchThread();
        synchronized (myLock) {
            myPlainSpaceWidth = -1;
            myTabSize = -1;
            setFontRenderContext(null);
        }
        switch (EditorSettingsExternalizable.getInstance().getBidiTextDirection()) {
            case LTR:
                myBidiFlags = Bidi.DIRECTION_LEFT_TO_RIGHT;
                break;
            case RTL:
                myBidiFlags = Bidi.DIRECTION_RIGHT_TO_LEFT;
                break;
            default:
                myBidiFlags = Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT;
        }
        myLogicalPositionCache.reset(false);
        myTextLayoutCache.resetToDocumentSize(false);
        invalidateFoldRegionLayouts();
        myCharWidthCache.clear();
        setPrefix(myPrefixText, myPrefixAttributes); // recreate prefix layout
        mySizeManager.reset();
    }

    public void invalidateRange(int startOffset, int endOffset) {
        assertIsDispatchThread();
        int textLength = myDocument.getTextLength();
        if (startOffset > endOffset || startOffset >= textLength || endOffset < 0) {
            return;
        }
        int startLine = myDocument.getLineNumber(Math.max(0, startOffset));
        int endLine = myDocument.getLineNumber(Math.min(textLength, endOffset));
        myTextLayoutCache.invalidateLines(startLine, endLine);
        mySizeManager.invalidateRange(startOffset, endOffset);
    }

    /**
     * Invoked when document might have changed, but no notifications were sent (for a hacky document in EditorTextFieldCellRenderer)
     */
    public void reset() {
        assertIsDispatchThread();
        myLogicalPositionCache.reset(true);
        myTextLayoutCache.resetToDocumentSize(true);
        mySizeManager.reset();
    }

    public boolean isRtlLocation(@Nonnull VisualPosition visualPosition) {
        assertIsDispatchThread();
        if (myDocument.getTextLength() == 0) {
            return false;
        }
        LogicalPosition logicalPosition = visualToLogicalPosition(visualPosition);
        int offset = logicalPositionToOffset(logicalPosition);
        if (!logicalPosition.equals(offsetToLogicalPosition(offset))) {
            return false; // virtual space
        }
        if (myEditor.getSoftWrapModel().getSoftWrap(offset) != null) {
            VisualPosition beforeWrapPosition = offsetToVisualPosition(offset, true, true);
            if (visualPosition.line == beforeWrapPosition.line && (visualPosition.column > beforeWrapPosition.column || visualPosition.column == beforeWrapPosition.column && visualPosition.leansRight)) {
                return false;
            }
            VisualPosition afterWrapPosition = offsetToVisualPosition(offset, false, false);
            if (visualPosition.line == afterWrapPosition.line && (visualPosition.column < afterWrapPosition.column || visualPosition.column == afterWrapPosition.column && !visualPosition.leansRight)) {
                return false;
            }
        }
        int line = myDocument.getLineNumber(offset);
        LineLayout layout = myTextLayoutCache.getLineLayout(line);
        return layout.isRtlLocation(offset - myDocument.getLineStartOffset(line), logicalPosition.leansForward);
    }

    public boolean isAtBidiRunBoundary(@Nonnull VisualPosition visualPosition) {
        assertIsDispatchThread();
        int offset = visualPositionToOffset(visualPosition);
        int otherSideOffset = visualPositionToOffset(visualPosition.leanRight(!visualPosition.leansRight));
        return offset != otherSideOffset;
    }

    /**
     * Offset of nearest boundary (not equal to {@code offset}) on the same line is returned. {@code -1} is returned if
     * corresponding boundary is not found.
     */
    public int findNearestDirectionBoundary(int offset, boolean lookForward) {
        assertIsDispatchThread();
        int textLength = myDocument.getTextLength();
        if (textLength == 0 || offset < 0 || offset > textLength) {
            return -1;
        }
        int line = myDocument.getLineNumber(offset);
        LineLayout layout = myTextLayoutCache.getLineLayout(line);
        int lineStartOffset = myDocument.getLineStartOffset(line);
        int relativeOffset = layout.findNearestDirectionBoundary(offset - lineStartOffset, lookForward);
        return relativeOffset < 0 ? -1 : lineStartOffset + relativeOffset;
    }

    public float getPlainSpaceWidth() {
        synchronized (myLock) {
            initMetricsIfNeeded();
            return myPlainSpaceWidth;
        }
    }

    public int getNominalLineHeight() {
        synchronized (myLock) {
            initMetricsIfNeeded();
            return myLineHeight + myTopOverhang + myBottomOverhang;
        }
    }

    @Override
    public int getLineHeight() {
        synchronized (myLock) {
            initMetricsIfNeeded();
            return myLineHeight;
        }
    }

    private float getVerticalScalingFactor() {
        if (myEditor.isOneLineMode()) {
            return 1;
        }
        float lineSpacing = myEditor.getColorsScheme().getLineSpacing();
        return lineSpacing > 0 ? lineSpacing : 1;
    }

    public int getDescent() {
        synchronized (myLock) {
            return myDescent;
        }
    }

    public int getCharHeight() {
        synchronized (myLock) {
            initMetricsIfNeeded();
            return myCharHeight;
        }
    }

    float getMaxCharWidth() {
        synchronized (myLock) {
            initMetricsIfNeeded();
            return myMaxCharWidth;
        }
    }

    public int getAscent() {
        synchronized (myLock) {
            initMetricsIfNeeded();
            return myLineHeight - myDescent;
        }
    }

    public int getTopOverhang() {
        synchronized (myLock) {
            initMetricsIfNeeded();
            return myTopOverhang;
        }
    }

    public int getBottomOverhang() {
        synchronized (myLock) {
            initMetricsIfNeeded();
            return myBottomOverhang;
        }
    }

    // guarded by myLock
    private void initMetricsIfNeeded() {
        if (myPlainSpaceWidth >= 0) {
            return;
        }

        Font font = myEditor.getColorsScheme().getFont(EditorFontType.PLAIN);
        FontMetrics fm = FontInfo.getFontMetrics(font, myFontRenderContext);

        float width = FontLayoutService.getInstance().charWidth2D(fm, ' ');
        myPlainSpaceWidth = width > 0 ? width : 1;

        myCharHeight = FontLayoutService.getInstance().charWidth(fm, 'a');

        float verticalScalingFactor = getVerticalScalingFactor();

        int fontMetricsHeight = FontLayoutService.getInstance().getHeight(fm);
        int lineHeight;
        if (Registry.is("editor.text.xcode.vertical.spacing")) {
            //Here we approximate line calculation to the variant used in Xcode 9 editor
            LineMetrics metrics = font.getLineMetrics("", myFontRenderContext);

            double height = Math.ceil(metrics.getHeight()) + metrics.getLeading();
            double delta = verticalScalingFactor - 1;
            int spacing;
            if (Math.round((height * delta) / 2) <= 1) {
                spacing = delta > 0 ? 2 : 0;
            }
            else {
                spacing = ((int) Math.ceil((height * delta) / 2)) * 2;
            }
            lineHeight = (int) Math.ceil(height) + spacing;
        }
        else if (Registry.is("editor.text.vertical.spacing.correct.rounding")) {
            if (verticalScalingFactor == 1f) {
                lineHeight = fontMetricsHeight;
            }
            else {
                Font scaledFont = font.deriveFont(font.getSize() * verticalScalingFactor);
                FontMetrics scaledMetrics = FontInfo.getFontMetrics(scaledFont, myFontRenderContext);
                lineHeight = FontLayoutService.getInstance().getHeight(scaledMetrics);
            }
        }
        else {
            lineHeight = (int) Math.ceil(fontMetricsHeight * verticalScalingFactor);
        }
        myLineHeight = Math.max(1, lineHeight);
        int descent = FontLayoutService.getInstance().getDescent(fm);
        myDescent = descent + (myLineHeight - fontMetricsHeight) / 2;
        myTopOverhang = fontMetricsHeight - myLineHeight + myDescent - descent;
        myBottomOverhang = descent - myDescent;

        // assuming that bold italic 'W' gives a good approximation of font's widest character
        FontMetrics fmBI = FontInfo.getFontMetrics(myEditor.getColorsScheme().getFont(EditorFontType.BOLD_ITALIC), myFontRenderContext);
        myMaxCharWidth = FontLayoutService.getInstance().charWidth2D(fmBI, 'W');

        myCapHeight = (int) font.createGlyphVector(myFontRenderContext, "H").getVisualBounds().getHeight();
    }

    public int getTabSize() {
        synchronized (myLock) {
            if (myTabSize < 0) {
                myTabSize = EditorUtil.getTabSize(myEditor);
            }
            return myTabSize;
        }
    }

    // guarded by myLock
    private boolean setFontRenderContext(FontRenderContext context) {
        FontRenderContext contextToSet = context == null ? FontInfo.getFontRenderContext(myEditor.getContentComponent()) : context;
        if (areEqualContexts(myFontRenderContext, contextToSet)) {
            return false;
        }
        myFontRenderContext = contextToSet.getFractionalMetricsHint() == myEditor.myFractionalMetricsHintValue
            ? contextToSet
            : new FontRenderContext(contextToSet.getTransform(), contextToSet.getAntiAliasingHint(), myEditor.myFractionalMetricsHintValue);
        return true;
    }

    private void checkFontRenderContext(FontRenderContext context) {
        boolean contextUpdated = false;
        synchronized (myLock) {
            if (setFontRenderContext(context)) {
                myPlainSpaceWidth = -1;
                contextUpdated = true;
            }
        }
        if (contextUpdated) {
            myTextLayoutCache.resetToDocumentSize(false);
            invalidateFoldRegionLayouts();
            myCharWidthCache.clear();
        }
    }

    private static boolean areEqualContexts(FontRenderContext c1, FontRenderContext c2) {
        if (c1 == c2) {
            return true;
        }
        if (c1 == null || c2 == null) {
            return false;
        }
        // We ignore fractional metrics aspect of contexts, because we it's not changing during editor's lifecycle.
        // And it has different values for component graphics (ON/OFF) and component's font metrics (DEFAULT), causing
        // unnecessary layout cache resets.
        return c1.getTransform().equals(c2.getTransform()) && c1.getAntiAliasingHint().equals(c2.getAntiAliasingHint());
    }

    public int offsetToVisualColumnInFoldRegion(@Nonnull FoldRegion region, int offset, boolean leanTowardsLargerOffsets) {
        if (offset < 0 || offset == 0 && !leanTowardsLargerOffsets) {
            return 0;
        }
        String text = region.getPlaceholderText();
        if (offset > text.length()) {
            offset = text.length();
            leanTowardsLargerOffsets = true;
        }
        int logicalColumn = LogicalPositionCache.calcColumn(text, 0, 0, offset, getTabSize());
        int maxColumn = 0;
        for (LineLayout.VisualFragment fragment : getFoldRegionLayout(region).getFragmentsInVisualOrder(0)) {
            int startLC = fragment.getStartLogicalColumn();
            int endLC = fragment.getEndLogicalColumn();
            if (logicalColumn > startLC && logicalColumn < endLC || logicalColumn == startLC && leanTowardsLargerOffsets || logicalColumn == endLC && !leanTowardsLargerOffsets) {
                return fragment.logicalToVisualColumn(logicalColumn);
            }
            maxColumn = fragment.getEndVisualColumn();
        }
        return maxColumn;
    }

    public int visualColumnToOffsetInFoldRegion(@Nonnull FoldRegion region, int visualColumn, boolean leansRight) {
        if (visualColumn < 0 || visualColumn == 0 && !leansRight) {
            return 0;
        }
        String text = region.getPlaceholderText();
        for (LineLayout.VisualFragment fragment : getFoldRegionLayout(region).getFragmentsInVisualOrder(0)) {
            int startVC = fragment.getStartVisualColumn();
            int endVC = fragment.getEndVisualColumn();
            if (visualColumn > startVC && visualColumn < endVC || visualColumn == startVC && leansRight || visualColumn == endVC && !leansRight) {
                int logicalColumn = fragment.visualToLogicalColumn(visualColumn);
                return LogicalPositionCache.calcOffset(text, logicalColumn, 0, 0, text.length(), getTabSize());
            }
        }
        return text.length();
    }

    LineLayout getFoldRegionLayout(FoldRegion foldRegion) {
        LineLayout layout = foldRegion.getUserData(FOLD_REGION_TEXT_LAYOUT);
        if (layout == null) {
            TextAttributes placeholderAttributes = myEditor.getFoldingModel().getPlaceholderAttributes();
            layout = LineLayout.create(this, StringUtil.replace(foldRegion.getPlaceholderText(), "\n", " "), placeholderAttributes == null ? Font.PLAIN : placeholderAttributes.getFontType());
            foldRegion.putUserData(FOLD_REGION_TEXT_LAYOUT, layout);
        }
        return layout;
    }

    private void invalidateFoldRegionLayouts() {
        for (FoldRegion region : myEditor.getFoldingModel().getAllFoldRegions()) {
            invalidateFoldRegionLayout(region);
        }
    }

    public void invalidateFoldRegionLayout(FoldRegion region) {
        region.putUserData(FOLD_REGION_TEXT_LAYOUT, null);
    }

    float getCodePointWidth(int codePoint, @JdkConstants.FontStyle int fontStyle) {
        return myCharWidthCache.getCodePointWidth(codePoint, fontStyle);
    }

    Insets getInsets() {
        return myEditor.getContentComponent().getInsets();
    }

    int getBidiFlags() {
        return myBidiFlags;
    }

    private static void assertIsDispatchThread() {
        UIAccess.assertIsUIThread();
    }

    private static void assertIsReadAccess() {
        ApplicationManager.getApplication().assertReadAccessAllowed();
    }

    @Override
    public void drawChars(@Nonnull Graphics g, @Nonnull char[] data, int start, int end, int x, int y, Color color, Object f) {
        FontInfo fontInfo = (FontInfo) f;

        myPainter.drawChars(g, data, start, end, x, y, color, fontInfo);
    }

    @Nonnull
    @Override
    public String dumpState() {
        String prefixText = myPrefixText;
        TextAttributes prefixAttributes = myPrefixAttributes;
        synchronized (myLock) {
            return "[prefix text: " + prefixText +
                ", prefix attributes: " + prefixAttributes +
                ", space width: " + myPlainSpaceWidth +
                ", line height: " + myLineHeight +
                ", descent: " + myDescent +
                ", char height: " + myCharHeight +
                ", max char width: " + myMaxCharWidth +
                ", tab size: " + myTabSize +
                " ,size manager: " + mySizeManager.dumpState() +
                " ,logical position cache: " + myLogicalPositionCache.dumpState() +
                "]";
        }
    }

    @Override
    public int getVisibleLineCount() {
        return Math.max(1, getVisibleLogicalLinesCount() + getSoftWrapModel().getSoftWrapsIntroducedLinesNumber());
    }

    /**
     * @return the number of visible logical lines, which is the number of total logical lines minus the number of folded lines
     */
    private int getVisibleLogicalLinesCount() {
        return getDocument().getLineCount() - getFoldingModel().getTotalNumberOfFoldedLines();
    }

    @Override
    public SoftWrapModelImpl getSoftWrapModel() {
        return myEditor.getSoftWrapModel();
    }

    @Override
    public DocumentEx getDocument() {
        return myDocument;
    }

    @Override
    public DesktopFoldingModelImpl getFoldingModel() {
        return myEditor.getFoldingModel();
    }

    public DesktopCaretModelImpl getCaretModel() {
        return myEditor.getCaretModel();
    }

    public DesktopScrollingModelImpl getScrollingModel() {
        return myEditor.getScrollingModel();
    }

    @RequiredUIAccess
    public EditorHighlighter getHighlighter() {
        return myEditor.getHighlighter();
    }

    @Override
    public InlayModelImpl getInlayModel() {
        return myEditor.getInlayModel();
    }

    @RequiredUIAccess
    public int[] visualLineToYRange(int line) {
        assertNotInBulkMode();
        return myMapper.visualLineToYRange(line);
    }

    @TestOnly
    public void validateState() {
        myLogicalPositionCache.validateState();
        mySizeManager.validateState();
    }

    int getCapHeight() {
        synchronized (myLock) {
            initMetricsIfNeeded();
            return myCapHeight;
        }
    }

    public int getCaretHeight() {
        synchronized (myLock) {
            initMetricsIfNeeded();
            return myEditor.getSettings().isFullLineHeightCursor()
                ? myLineHeight
                : myLineHeight + myTopOverhang + myBottomOverhang;
        }
    }

    private void assertNotInBulkMode() {
        if (myDocument instanceof DocumentImpl) {
            ((DocumentImpl) myDocument).assertNotInBulkUpdate();
        }
        else if (myDocument.isInBulkUpdate()) {
            throw new IllegalStateException("Current operation is not available in bulk mode");
        }
    }

    public boolean isAd() {
        return myEditor.isAd();
    }
}
