// Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.codeEditor.impl;

import consulo.application.Application;
import consulo.application.util.function.CommonProcessors;
import consulo.codeEditor.*;
import consulo.codeEditor.internal.CodeEditorInternalHelper;
import consulo.codeEditor.markup.HighlighterLayer;
import consulo.codeEditor.markup.HighlighterTargetArea;
import consulo.codeEditor.markup.MarkupModelEx;
import consulo.codeEditor.markup.RangeHighlighterEx;
import consulo.codeEditor.util.EditorUtil;
import consulo.colorScheme.EditorColorsScheme;
import consulo.colorScheme.EffectType;
import consulo.colorScheme.TextAttributes;
import consulo.colorScheme.TextAttributesEffectsBuilder;
import consulo.document.internal.DocumentEx;
import consulo.document.util.DocumentUtil;
import consulo.logging.Logger;
import consulo.ui.UIAccess;
import consulo.ui.color.ColorValue;
import consulo.util.collection.Lists;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.Contract;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Iterator over editor's text contents. Each iteration step corresponds to a text fragment having common graphical attributes
 * (font style, foreground and background color, effect type and color).
 */
public final class IterationState {

    private static final Logger LOG = Logger.getInstance(IterationState.class);
    private static final Comparator<RangeHighlighterEx> BY_AFFECTED_END_OFFSET_REVERSED = (r1, r2) -> r2.getAffectedAreaEndOffset() - r1.getAffectedAreaEndOffset();

    @Contract(pure = true)
    public static @Nonnull Comparator<RangeHighlighterEx> createByLayerThenByAttributesComparator(@Nonnull EditorColorsScheme scheme) {
        CodeEditorInternalHelper helper = CodeEditorInternalHelper.getInstance();
        return (o1, o2) -> {
            int result = LayerComparator.HIGHER_FIRST.compare(o1, o2);
            if (result != 0) {
                return result;
            }

            // There is a possible case when more than one highlighter target the same region (e.g. 'identifier under caret' and 'identifier').
            // We want to prefer the one that defines foreground color to the one that doesn't define (has either fore- or background colors
            // while the other one has only foreground color). See IDEA-85697 for concrete example.
            TextAttributes a1 = o1.getTextAttributes(scheme);
            TextAttributes a2 = o2.getTextAttributes(scheme);
            if (a1 == null ^ a2 == null) {
                return a1 == null ? 1 : -1;
            }

            if (a1 != null) {
                ColorValue fore1 = a1.getForegroundColor();
                ColorValue fore2 = a2.getForegroundColor();
                if (fore1 == null ^ fore2 == null) {
                    return fore1 == null ? 1 : -1;
                }

                ColorValue back1 = a1.getBackgroundColor();
                ColorValue back2 = a2.getBackgroundColor();
                if (back1 == null ^ back2 == null) {
                    return back1 == null ? 1 : -1;
                }
            }
            return helper.compareByHighlightInfoSeverity(o1, o2);
        };
    }

    private final int myInitialStartOffset;
    private final int myEnd;
    private final EditorColorsScheme myColorsScheme;
    private final int myDefaultFontType;
    private final @Nullable HighlighterIterator myHighlighterIterator;
    private final HighlighterSweep myEditorHighlighters;
    private final HighlighterSweep myDocumentHighlighters;
    private final FoldingModelEx myFoldingModel;
    private final SoftWrapModel mySoftWrapModel;
    private final TextAttributes myFoldTextAttributes;
    private final TextAttributes mySelectionAttributes;
    private final TextAttributes myCaretRowAttributes;
    private final TextAttributes myMergedAttributes = new TextAttributes();
    private final ColorValue myDefaultBackground;
    private final ColorValue myDefaultForeground;
    private final ColorValue myReadOnlyColor;
    private final DocumentEx myDocument;
    private final CaretData myCaretData;
    private final boolean myUseOnlyFullLineHighlighters;
    private final boolean myUseOnlyFontOrForegroundAffectingHighlighters;
    private final boolean myStickyLinesPainting;
    private final boolean myEditorRightAligned;
    private final boolean myReverseIteration;
    private final List<RangeHighlighterEx> myCurrentHighlighters = new ArrayList<>();
    private final List<TextAttributes> myCachedAttributesList = new ArrayList<>(5);
    private final GuardedBlocksIndex myGuardedBlocks;

    private int myStartOffset;
    private int myEndOffset;
    private int myCurrentSelectionIndex;
    private ColorValue myCurrentBackgroundColor;
    private ColorValue myLastBackgroundColor;
    private FoldRegion myCurrentFold;
    private boolean myNextIsFoldRegion;

    public IterationState(
        @Nonnull EditorEx editor,
        int start,
        int end,
        @Nullable CaretData caretData,
        boolean useOnlyFullLineHighlighters,
        boolean useOnlyFontOrForegroundAffectingHighlighters,
        boolean useFoldRegions,
        boolean iterateBackwards
    ) {
        if (!UIAccess.isUIThread()) {
            Application.get().assertReadAccessAllowed();
        }
        LOG.assertTrue(iterateBackwards ? start >= end : start <= end);

        myDocument = getDocument(editor);
        assert !DocumentUtil.isInsideSurrogatePair(myDocument, start);
        assert !DocumentUtil.isInsideSurrogatePair(myDocument, end);

        myColorsScheme = editor.getColorsScheme();
        myInitialStartOffset = start;
        myStartOffset = start;
        myEnd = end;
        myUseOnlyFullLineHighlighters = useOnlyFullLineHighlighters;
        myUseOnlyFontOrForegroundAffectingHighlighters = useOnlyFontOrForegroundAffectingHighlighters;
        myStickyLinesPainting = editor instanceof RealEditor impl && impl.isStickyLinePainting();
        myEditorRightAligned = editor instanceof RealEditor impl && impl.isRightAligned();
        myReverseIteration = iterateBackwards;
        myHighlighterIterator = useOnlyFullLineHighlighters ? null : getHighlighter(editor).createIterator(start);
        myCaretData = ObjectUtil.notNull(caretData, CaretData.getNullCaret());
        myFoldingModel = !useFoldRegions ? null : getFoldingModel(editor);
        mySoftWrapModel = getSoftWrapModel(editor);
        myFoldTextAttributes = useFoldRegions ? myFoldingModel.getPlaceholderAttributes() : null;
        mySelectionAttributes = getSelectionModel(editor).getTextAttributes();
        myReadOnlyColor = myColorsScheme.getColor(EditorColors.READONLY_FRAGMENT_BACKGROUND_COLOR);
        myCaretRowAttributes = editor.isRendererMode() ? null : getCaretModel(editor).getTextAttributes();
        myDefaultBackground = myColorsScheme.getDefaultBackground();
        myDefaultForeground = myColorsScheme.getDefaultForeground();
        TextAttributes defaultAttributes = myColorsScheme.getAttributes(HighlighterColors.TEXT);
        myDefaultFontType = defaultAttributes == null ? Font.PLAIN : defaultAttributes.getFontType();
        myEditorHighlighters = createSweep(getEditorMarkupModel(editor));
        myDocumentHighlighters = createSweep(getDocumentMarkupModel(editor));
        myGuardedBlocks = buildGuardedBlocks(start, end);
        myEndOffset = myStartOffset;

        advance();
    }

    public void retreat(int offset) {
        assert !myReverseIteration && // we need only this case at the moment, this can be relaxed if needed
            myCaretData == CaretData.getNullCaret() &&
            offset >= myInitialStartOffset &&
            offset <= myStartOffset &&
            !DocumentUtil.isInsideSurrogatePair(myDocument, offset);
        if (offset == myStartOffset) return;
        if (myHighlighterIterator != null) {
            while (myHighlighterIterator.getStart() > offset) {
                myHighlighterIterator.retreat();
            }
        }
        myCurrentHighlighters.clear();
        myDocumentHighlighters.retreat(offset);
        myEditorHighlighters.retreat(offset);
        myEndOffset = offset;
        advance();
    }

    public void advance() {
        myNextIsFoldRegion = false;
        myStartOffset = myEndOffset;
        advanceSegmentHighlighters();
        advanceCurrentSelectionIndex();

        if (!myUseOnlyFullLineHighlighters) {
            myCurrentFold = myFoldingModel == null
                ? null
                : myFoldingModel.getCollapsedRegionAtOffset(myReverseIteration ? myStartOffset - 1 : myStartOffset);
        }
        if (myCurrentFold != null) {
            myEndOffset = myReverseIteration ? myCurrentFold.getStartOffset() : myCurrentFold.getEndOffset();
            assert !DocumentUtil.isInsideSurrogatePair(myDocument, myEndOffset);
        }
        else {
            int highlighterEnd = getHighlighterEnd(myStartOffset);
            int selectionEnd = getSelectionEnd();
            int minSegmentHighlightersEnd = getMinSegmentHighlightersEnd();
            int foldRangesEnd = getFoldRangesEnd(myStartOffset);
            int caretEnd = getCaretEnd(myStartOffset);
            int guardedBlockEnd = getGuardedBlockEnd(myStartOffset);

            myEndOffset = highlighterEnd;
            setEndOffsetIfCloser(selectionEnd);
            setEndOffsetIfCloser(minSegmentHighlightersEnd);
            setEndOffsetIfCloser(foldRangesEnd);
            setEndOffsetIfCloser(caretEnd);
            setEndOffsetIfCloser(guardedBlockEnd);

            myNextIsFoldRegion = myEndOffset == foldRangesEnd && myEndOffset < myEnd;

            assert !DocumentUtil.isInsideSurrogatePair(myDocument, myEndOffset) :
                "caret: " + DocumentUtil.isInsideSurrogatePair(myDocument, caretEnd) +
                    ", selection: " + DocumentUtil.isInsideSurrogatePair(myDocument, selectionEnd) +
                    ", guarded block: " + DocumentUtil.isInsideSurrogatePair(myDocument, guardedBlockEnd) +
                    ", folding: " + DocumentUtil.isInsideSurrogatePair(myDocument, foldRangesEnd) +
                    ", lexer: " + DocumentUtil.isInsideSurrogatePair(myDocument, highlighterEnd) +
                    ", highlighters: " + DocumentUtil.isInsideSurrogatePair(myDocument, minSegmentHighlightersEnd);
        }

        reinit();
    }

    public @Nonnull TextAttributes getBreakAttributes() {
        return getBreakAttributes(false);
    }

    public @Nonnull TextAttributes getBreakAttributes(boolean beforeBreak) {
        TextAttributes attributes = new TextAttributes();
        setAttributes(attributes, true, beforeBreak);
        return attributes;
    }

    public @Nonnull TextAttributes getMergedAttributes() {
        return myMergedAttributes;
    }

    public boolean atEnd() {
        return myReverseIteration ? myStartOffset <= myEnd : myStartOffset >= myEnd;
    }

    public int getStartOffset() {
        return myStartOffset;
    }

    public int getEndOffset() {
        return myEndOffset;
    }

    public FoldRegion getCurrentFold() {
        return myCurrentFold;
    }

    public boolean nextIsFoldRegion() {
        return myNextIsFoldRegion;
    }

    public @Nonnull TextAttributes getPastLineEndBackgroundAttributes() {
        myMergedAttributes.setBackgroundColor(
            hasSoftWrap()
                ? getBreakBackgroundColor(true)
                : myEditorRightAligned && myLastBackgroundColor != null
                ? myLastBackgroundColor
                : myCurrentBackgroundColor
        );
        return myMergedAttributes;
    }

    public @Nonnull TextAttributes getBeforeLineStartBackgroundAttributes() {
        return myEditorRightAligned && !hasSoftWrap()
            ? getBreakAttributes()
            : new TextAttributes(null, getBreakBackgroundColor(false), null, null, Font.PLAIN);
    }

    private @Nonnull HighlighterSweep createSweep(MarkupModelEx markupModel) {
        return new HighlighterSweep(
            myColorsScheme,
            markupModel,
            myStartOffset,
            myEnd,
            myUseOnlyFullLineHighlighters,
            myUseOnlyFontOrForegroundAffectingHighlighters
        );
    }

    private void setEndOffsetIfCloser(int offset) {
        if (myReverseIteration ? offset > myEndOffset : offset < myEndOffset) {
            myEndOffset = offset;
        }
    }

    private int getHighlighterEnd(int start) {
        if (myHighlighterIterator == null) {
            return myEnd;
        }
        while (!myHighlighterIterator.atEnd()) {
            int end = alignOffset(myReverseIteration ? myHighlighterIterator.getStart() : myHighlighterIterator.getEnd());
            if (myReverseIteration ? end < start : end > start) {
                return end;
            }
            if (myReverseIteration) {
                myHighlighterIterator.retreat();
            }
            else {
                myHighlighterIterator.advance();
            }
        }
        return myEnd;
    }

    private int getCaretEnd(int start) {
        return getNearestValueAhead(start, myCaretData.caretRowStart(), myCaretData.caretRowEnd());
    }

    private int getNearestValueAhead(int offset, int rangeStart, int rangeEnd) {
        if (myReverseIteration) {
            if (rangeEnd < offset) {
                return rangeEnd;
            }
            if (rangeStart < offset) {
                return rangeStart;
            }
        }
        else {
            if (rangeStart > offset) {
                return rangeStart;
            }
            if (rangeEnd > offset) {
                return rangeEnd;
            }
        }

        return myEnd;
    }

    private int getGuardedBlockEnd(int start) {
        int end = myEnd;
        assert myReverseIteration && start >= end || !myReverseIteration && start <= end;

        if (myUseOnlyFullLineHighlighters) {
            return end;
        }

        if (myReverseIteration) {
            int nearest = myGuardedBlocks.nearestLeft(start - 1);
            return (nearest != -1 && nearest > end) ? nearest : end;
        }

        int nearest = myGuardedBlocks.nearestRight(start + 1);
        return (nearest != -1 && nearest < end) ? nearest : end;
    }

    private void advanceCurrentSelectionIndex() {
        while (myCurrentSelectionIndex < myCaretData.selectionsSize() &&
            (myReverseIteration
                ? myStartOffset <= myCaretData.selectionStart(myCurrentSelectionIndex, true)
                : myStartOffset >= myCaretData.selectionEnd(myCurrentSelectionIndex, false))) {
            myCurrentSelectionIndex++;
        }
    }

    private int getSelectionEnd() {
        if (myCurrentSelectionIndex >= myCaretData.selectionsSize()) {
            return myEnd;
        }
        return getNearestValueAhead(
            myStartOffset,
            myCaretData.selectionStart(myCurrentSelectionIndex, myReverseIteration),
            myCaretData.selectionEnd(myCurrentSelectionIndex, myReverseIteration)
        );
    }

    private boolean isInSelection(boolean atBreak) {
        return myCurrentSelectionIndex < myCaretData.selectionsSize() &&
            (myReverseIteration ? lessThan(myStartOffset, myCaretData.selectionEnd(myCurrentSelectionIndex, true), !atBreak)
                : lessThan(myCaretData.selectionStart(myCurrentSelectionIndex, false), myStartOffset, !atBreak));
    }

    private GuardedBlocksIndex buildGuardedBlocks(int start, int end) {
        if (myUseOnlyFullLineHighlighters) {
            return null;
        }
        var guardedBlocks = new GuardedBlocksIndex.DocumentBuilder(myDocument);
        return myReverseIteration
            ? guardedBlocks.build(end, start)
            : guardedBlocks.build(start, end);
    }

    private boolean isInDocumentGuardedBlock(boolean atBreak, boolean beforeBreak) {
        if (myUseOnlyFullLineHighlighters || (atBreak && beforeBreak)) {
            return false;
        }
        if (myReverseIteration) {
            return myGuardedBlocks.isGuarded(myStartOffset - 1);
        }
        return myGuardedBlocks.isGuarded(myStartOffset);
    }

    private static boolean lessThan(int x, int y, boolean orEquals) {
        return x < y || orEquals && x == y;
    }

    private void advanceSegmentHighlighters() {
        myDocumentHighlighters.advance();
        myEditorHighlighters.advance();

        boolean fileEnd = myStartOffset == myDocument.getTextLength();
        for (int i = myCurrentHighlighters.size() - 1; i >= 0; i--) {
            RangeHighlighterEx highlighter = myCurrentHighlighters.get(i);
            if (myReverseIteration ?
                getAlignedStartOffset(highlighter) >= myStartOffset :
                fileEnd && highlighter.getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE ?
                    getAlignedEndOffset(highlighter) < myStartOffset :
                    getAlignedEndOffset(highlighter) <= myStartOffset) {
                myCurrentHighlighters.remove(i);
            }
        }
    }

    private int getFoldRangesEnd(int startOffset) {
        if (myUseOnlyFullLineHighlighters || myFoldingModel == null) {
            return myEnd;
        }
        int end = myEnd;
        FoldRegion[] topLevelCollapsed = myFoldingModel.fetchTopLevel();
        if (topLevelCollapsed != null) {
            if (myReverseIteration) {
                for (int i = myFoldingModel.getLastCollapsedRegionBefore(startOffset);
                     i >= 0 && i < topLevelCollapsed.length;
                     i--) {
                    FoldRegion range = topLevelCollapsed[i];
                    if (!range.isValid()) continue;

                    int rangeEnd = range.getEndOffset();
                    if (rangeEnd < startOffset) {
                        if (rangeEnd > end) {
                            end = rangeEnd;
                        }
                        else {
                            break;
                        }
                    }
                }
            }
            else {
                for (int i = myFoldingModel.getLastCollapsedRegionBefore(startOffset) + 1;
                     i >= 0 && i < topLevelCollapsed.length;
                     i++) {
                    FoldRegion range = topLevelCollapsed[i];
                    if (!range.isValid()) continue;

                    int rangeEnd = range.getStartOffset();
                    if (rangeEnd > startOffset) {
                        if (rangeEnd < end) {
                            end = rangeEnd;
                        }
                        else {
                            break;
                        }
                    }
                }
            }
        }

        return end;
    }

    private int getMinSegmentHighlightersEnd() {
        int end = myEnd;

        for (int i = 0; i < myCurrentHighlighters.size(); i++) {
            RangeHighlighterEx highlighter = myCurrentHighlighters.get(i);
            if (myReverseIteration) {
                end = Math.max(end, getAlignedStartOffset(highlighter));
            }
            else {
                end = Math.min(end, getAlignedEndOffset(highlighter));
            }
        }

        end = myReverseIteration ? Math.max(end, myDocumentHighlighters.getMinSegmentHighlighterEnd()) : Math.min(end, myDocumentHighlighters.getMinSegmentHighlighterEnd());
        end = myReverseIteration ? Math.max(end, myEditorHighlighters.getMinSegmentHighlighterEnd()) : Math.min(end, myEditorHighlighters.getMinSegmentHighlighterEnd());

        return end;
    }

    private void reinit() {
        setAttributes(myMergedAttributes, false, false);
        myLastBackgroundColor = myCurrentBackgroundColor;
        myCurrentBackgroundColor = myMergedAttributes.getBackgroundColor();
    }

    private void setAttributes(TextAttributes attributes, boolean atBreak, boolean beforeBreak) {
        boolean isInSelection = isInSelection(atBreak);
        boolean isInCaretRow = isInCaretRow(
            !myReverseIteration && (!atBreak || !beforeBreak),
            myReverseIteration || (atBreak && beforeBreak)
        );
        boolean isInGuardedBlock = isInDocumentGuardedBlock(atBreak, beforeBreak);

        TextAttributes syntax = myHighlighterIterator == null || myHighlighterIterator.atEnd()
            ? null
            : (atBreak && myStartOffset == (myReverseIteration ? myHighlighterIterator.getEnd() : myHighlighterIterator.getStart()))
            ? null
            : myHighlighterIterator.getTextAttributes();
        TextAttributes selection = getSelectionAttributes(isInSelection);
        TextAttributes caret = getCaretRowAttributes(isInCaretRow);
        TextAttributes fold = myCurrentFold != null ? myFoldTextAttributes : null;
        TextAttributes guard = isInGuardedBlock
            ? new TextAttributes(null, myReadOnlyColor, null, EffectType.BOXED, Font.PLAIN)
            : null;

        int size = myCurrentHighlighters.size();
        if (size > 1) {
            Lists.quickSort(myCurrentHighlighters, createByLayerThenByAttributesComparator(myColorsScheme));
        }

        for (int i = 0; i < size; i++) {
            RangeHighlighterEx highlighter = myCurrentHighlighters.get(i);
            if (highlighter.getTextAttributes(myColorsScheme) == TextAttributes.ERASE_MARKER) {
                syntax = null;
            }
        }

        List<TextAttributes> cachedAttributes = myCachedAttributesList;
        if (!cachedAttributes.isEmpty()) cachedAttributes.clear();

        for (int i = 0; i < size; i++) {
            RangeHighlighterEx highlighter = myCurrentHighlighters.get(i);
            if (atBreak &&
                highlighter.getTargetArea() == HighlighterTargetArea.EXACT_RANGE &&
                myStartOffset == (myReverseIteration ? highlighter.getEndOffset() : highlighter.getStartOffset())) {
                continue;
            }
            if (highlighter.getLayer() < HighlighterLayer.SELECTION) {
                if (selection != null) {
                    cachedAttributes.add(selection);
                    selection = null;
                }
            }

            if (fold != null && highlighter.getLayer() < HighlighterLayer.GUARDED_BLOCKS) {
                cachedAttributes.add(fold);
                fold = null;
            }

            if (guard != null && highlighter.getLayer() < HighlighterLayer.GUARDED_BLOCKS) {
                cachedAttributes.add(guard);
                guard = null;
            }

            if (caret != null && highlighter.getLayer() < HighlighterLayer.CARET_ROW) {
                cachedAttributes.add(caret);
                caret = null;
            }

            if (syntax != null && highlighter.getLayer() < HighlighterLayer.SYNTAX) {
                cachedAttributes.add(syntax);
                syntax = null;
            }

            TextAttributes textAttributes = highlighter.getTextAttributes(myColorsScheme);
            if (textAttributes != null && textAttributes != TextAttributes.ERASE_MARKER) {
                cachedAttributes.add(textAttributes);
            }
        }

        if (selection != null) cachedAttributes.add(selection);
        if (fold != null) cachedAttributes.add(fold);
        if (guard != null) cachedAttributes.add(guard);
        if (caret != null) cachedAttributes.add(caret);
        if (syntax != null) cachedAttributes.add(syntax);

        ColorValue fore = null;
        ColorValue back = isInGuardedBlock ? myReadOnlyColor : null;
        @JdkConstants.FontStyle int fontType = Font.PLAIN;

        TextAttributesEffectsBuilder effectsBuilder = null;
        for (int i = 0; i < cachedAttributes.size(); i++) {
            TextAttributes attrs = cachedAttributes.get(i);

            if (fore == null) {
                fore = attrs.getForegroundColor();
            }

            if (back == null) {
                back = attrs.getBackgroundColor();
            }

            if (fontType == Font.PLAIN) {
                fontType = attrs.getFontType();
            }

            if (attrs.hasEffects()) {
                if (effectsBuilder == null) {
                    effectsBuilder = TextAttributesEffectsBuilder.create();
                }
                effectsBuilder.slipUnder(attrs);
            }
        }

        if (fore == null) fore = myDefaultForeground;
        if (back == null) back = myDefaultBackground;
        if (fontType == Font.PLAIN) fontType = myDefaultFontType;

        attributes.setAttributesNoCache(fore, back, null, null, null, fontType);
        if (effectsBuilder != null) {
            effectsBuilder.applyTo(attributes);
        }
    }

    private boolean isInCaretRow(boolean includeLineStart, boolean includeLineEnd) {
        return myCaretData.caretRowStart() < myStartOffset && myStartOffset < myCaretData.caretRowEnd() ||
            includeLineStart && myStartOffset == myCaretData.caretRowStart() ||
            includeLineEnd && myStartOffset == myCaretData.caretRowEnd();
    }

    private @Nullable TextAttributes getSelectionAttributes(boolean isInSelection) {
        return isInSelection ? mySelectionAttributes : null;
    }

    private @Nullable TextAttributes getCaretRowAttributes(boolean isInCaretRow) {
        if (myStickyLinesPainting) {
            // suppress a caret row background on the sticky lines panel
            return null;
        }
        return isInCaretRow ? myCaretRowAttributes : null;
    }

    @Nullable
    private ColorValue getBreakBackgroundColor(boolean lineEnd) {
        return getBreakAttributes(lineEnd).getBackgroundColor();
    }

    private boolean hasSoftWrap() {
        return mySoftWrapModel.getSoftWrap(myStartOffset) != null;
    }

    private int alignOffset(int offset) {
        return DocumentUtil.alignToCodePointBoundary(myDocument, offset);
    }

    private int getAlignedStartOffset(RangeHighlighterEx highlighter) {
        return alignOffset(highlighter.getAffectedAreaStartOffset());
    }

    private int getAlignedEndOffset(RangeHighlighterEx highlighter) {
        return alignOffset(highlighter.getAffectedAreaEndOffset());
    }

    private final class HighlighterSweep {
        private final MarkupModelEx myMarkupModel;
        private final EditorColorsScheme myColorsScheme;
        private final boolean myOnlyFullLine;
        private final boolean myOnlyFontOrForegroundAffecting;
        private RangeHighlighterEx myNextHighlighter;
        int i;
        private final RangeHighlighterEx[] highlighters;

        private HighlighterSweep(@Nonnull EditorColorsScheme scheme,
                                 @Nonnull MarkupModelEx markupModel,
                                 int start,
                                 int end,
                                 boolean onlyFullLine,
                                 boolean onlyFontOrForegroundAffecting) {
            myColorsScheme = scheme;
            myMarkupModel = markupModel;
            myOnlyFullLine = onlyFullLine;
            myOnlyFontOrForegroundAffecting = onlyFontOrForegroundAffecting;
            highlighters = collectHighlighters(
                myReverseIteration ? end : start,
                myReverseIteration ? start : end,
                myReverseIteration ? BY_AFFECTED_END_OFFSET_REVERSED : RangeHighlighterEx.BY_AFFECTED_START_OFFSET
            );
            myNextHighlighter = firstAdvance();
        }

        private RangeHighlighterEx[] collectHighlighters(int start, int end, Comparator<RangeHighlighterEx> comparator) {
            // we have to get all highlighters in advance and sort them by affected offsets
            // since these can be different from the real offsets the highlighters are sorted by in the tree.
            // (See LINES_IN_RANGE perverts)
            var processor = new CommonProcessors.CollectProcessor<RangeHighlighterEx>() {
                @Override
                public boolean accept(RangeHighlighterEx h) {
                    return acceptHighlighter(h); // TODO: refactor to skipHighlighter here
                }
            };
            myMarkupModel.processRangeHighlightersOverlappingWith(start, end, processor);
            RangeHighlighterEx[] highlights = processor.getResults().isEmpty()
                ? RangeHighlighterEx.EMPTY_ARRAY
                : processor.toArray(RangeHighlighterEx.EMPTY_ARRAY);
            Arrays.sort(highlights, comparator);
            return highlights;
        }

        private RangeHighlighterEx firstAdvance() {
            while (i < highlighters.length) {
                RangeHighlighterEx highlighter = highlighters[i++];
                if (!skipHighlighter(highlighter)) {
                    return highlighter;
                }
            }
            return null;
        }

        private void advance() {
            if (myNextHighlighter != null) {
                if (myReverseIteration ?
                    getAlignedEndOffset(myNextHighlighter) < myStartOffset :
                    getAlignedStartOffset(myNextHighlighter) > myStartOffset) {
                    return;
                }
                myCurrentHighlighters.add(myNextHighlighter);
                myNextHighlighter = null;
            }

            while (i < highlighters.length) {
                RangeHighlighterEx highlighter = highlighters[i++];
                if (!skipHighlighter(highlighter)) {
                    if (myReverseIteration
                        ? getAlignedEndOffset(highlighter) < myStartOffset
                        : getAlignedStartOffset(highlighter) > myStartOffset) {
                        myNextHighlighter = highlighter;
                        break;
                    }
                    else {
                        myCurrentHighlighters.add(highlighter);
                    }
                }
            }
        }

        private void retreat(int offset) {
            for (int j = i - 2; j >= 0; j--) {
                RangeHighlighterEx highlighter = highlighters[j];
                if (skipHighlighter(highlighter)) continue;
                if (getAlignedStartOffset(highlighter) > offset) {
                    myNextHighlighter = highlighter;
                    i = j + 1;
                }
                else {
                    break;
                }
            }
            myMarkupModel.processRangeHighlightersOverlappingWith(
                offset,
                offset,
                h -> {
                    if (acceptHighlighter(h) && !skipHighlighter(h)) {
                        myCurrentHighlighters.add(h);
                    }
                    return true;
                }
            );
        }

        private boolean acceptHighlighter(RangeHighlighterEx highlighter) {
            return (!myOnlyFullLine || highlighter.getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE) &&
                (!myOnlyFontOrForegroundAffecting || EditorUtil.attributesImpactFontStyleOrColor(highlighter.getTextAttributes(myColorsScheme)));
        }

        private boolean skipHighlighter(@Nonnull RangeHighlighterEx highlighter) {
            if (!highlighter.isValid() || highlighter.isAfterEndOfLine() || highlighter.getTextAttributes(myColorsScheme) == null) {
                return true;
            }
            FoldRegion region = myFoldingModel == null ? null : myFoldingModel.getCollapsedRegionAtOffset(highlighter.getAffectedAreaStartOffset());
            return region != null && region == myFoldingModel.getCollapsedRegionAtOffset(highlighter.getAffectedAreaEndOffset());
        }

        private int getMinSegmentHighlighterEnd() {
            if (myNextHighlighter != null) {
                return myReverseIteration ? getAlignedEndOffset(myNextHighlighter) : getAlignedStartOffset(myNextHighlighter);
            }
            return myReverseIteration ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        }
    }

    // region boilerplate

    private static DocumentEx getDocument(@Nonnull EditorEx editor) {
//        if (editor instanceof EditorImpl editorImpl) {
//            return editorImpl.getEditorModel().getDocument();
//        }
        return (DocumentEx) editor.getDocument();
    }

    private static FoldingModelEx getFoldingModel(@Nonnull EditorEx editor) {
//        if (editor instanceof EditorImpl editorImpl) {
//            return editorImpl.getEditorModel().getFoldingModel();
//        }
        return editor.getFoldingModel();
    }

    private static MarkupModelEx getEditorMarkupModel(@Nonnull EditorEx editor) {
//        if (editor instanceof RealEditor editorImpl) {
//            return editorImpl.getEditorModel().getEditorMarkupModel();
//        }
        return editor.getMarkupModel();
    }

    private static MarkupModelEx getDocumentMarkupModel(@Nonnull EditorEx editor) {
//        if (editor instanceof RealEditor editorImpl) {
//            return editorImpl.getEditorModel().getDocumentMarkupModel();
//        }
        return editor.getFilteredDocumentMarkupModel();
    }

    private static CaretModel getCaretModel(@Nonnull EditorEx editor) {
//        if (editor instanceof RealEditor editorImpl) {
//            return editorImpl.getEditorModel().getCaretModel();
//        }
        return editor.getCaretModel();
    }

    private static EditorHighlighter getHighlighter(@Nonnull EditorEx editor) {
//        if (editor instanceof RealEditor editorImpl) {
//            return editorImpl.getEditorModel().getHighlighter();
//        }
        return editor.getHighlighter();
    }

    private static SoftWrapModel getSoftWrapModel(@Nonnull EditorEx editor) {
//        if (editor instanceof EditorImpl editorImpl) {
//            return editorImpl.getEditorModel().getSoftWrapModel();
//        }
        return editor.getSoftWrapModel();
    }

    private static SelectionModel getSelectionModel(@Nonnull EditorEx editor) {
//        if (editor instanceof EditorImpl editorImpl) {
//            return editorImpl.getEditorModel().getSelectionModel();
//        }
        return editor.getSelectionModel();
    }

    // endregion

    private static final class LayerComparator implements Comparator<RangeHighlighterEx> {
        private static final LayerComparator HIGHER_FIRST = new LayerComparator();

        @Override
        public int compare(RangeHighlighterEx o1, RangeHighlighterEx o2) {
            int layerDiff = o2.getLayer() - o1.getLayer();
            if (layerDiff != 0) {
                return layerDiff;
            }
            // prefer more specific region
            int o1Length = o1.getAffectedAreaEndOffset() - o1.getAffectedAreaStartOffset();
            int o2Length = o2.getAffectedAreaEndOffset() - o2.getAffectedAreaStartOffset();
            return o1Length - o2Length;
        }
    }
}
