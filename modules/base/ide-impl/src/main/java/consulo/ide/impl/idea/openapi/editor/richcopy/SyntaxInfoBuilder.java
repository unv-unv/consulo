// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ide.impl.idea.openapi.editor.richcopy;

import consulo.application.ApplicationManager;
import consulo.codeEditor.EditorHighlighter;
import consulo.codeEditor.HighlighterIterator;
import consulo.codeEditor.markup.HighlighterLayer;
import consulo.codeEditor.markup.MarkupModel;
import consulo.codeEditor.markup.MarkupModelEx;
import consulo.codeEditor.markup.RangeHighlighterEx;
import consulo.colorScheme.EditorColorsScheme;
import consulo.colorScheme.FontPreferences;
import consulo.colorScheme.TextAttributes;
import consulo.colorScheme.TextAttributesKey;
import consulo.document.MarkupIterator;
import consulo.ide.impl.idea.openapi.editor.impl.FontFallbackIterator;
import consulo.ide.impl.idea.openapi.editor.richcopy.model.SyntaxInfo;
import consulo.ide.impl.idea.util.text.CharArrayUtil;
import consulo.language.ast.TokenType;
import consulo.language.editor.impl.internal.rawHighlight.HighlightInfoImpl;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.platform.Platform;
import consulo.ui.color.ColorValue;
import consulo.ui.ex.awt.util.UISettingsUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.Comparator;

public final class SyntaxInfoBuilder {
  private SyntaxInfoBuilder() {
  }

  @Nonnull
  static MyMarkupIterator createMarkupIterator(@Nonnull EditorHighlighter highlighter,
                                               @Nonnull CharSequence text,
                                               @Nonnull EditorColorsScheme schemeToUse,
                                               @Nonnull MarkupModel markupModel,
                                               int startOffsetToUse,
                                               int endOffset) {

    CompositeRangeIterator iterator = new CompositeRangeIterator(schemeToUse, new HighlighterRangeIterator(highlighter, startOffsetToUse, endOffset),
                                                                 new MarkupModelRangeIterator(markupModel, schemeToUse, startOffsetToUse, endOffset));

    return new MyMarkupIterator(text, iterator, schemeToUse);
  }

  public interface RangeIterator {
    boolean atEnd();

    void advance();

    int getRangeStart();

    int getRangeEnd();

    TextAttributes getTextAttributes();

    void dispose();
  }

  static class MyMarkupIterator {
    private final SegmentIterator mySegmentIterator;
    private final RangeIterator myRangeIterator;
    private int myCurrentFontStyle;
    private ColorValue myCurrentForegroundColor;
    private ColorValue myCurrentBackgroundColor;

    MyMarkupIterator(@Nonnull CharSequence charSequence, @Nonnull RangeIterator rangeIterator, @Nonnull EditorColorsScheme colorsScheme) {
      myRangeIterator = rangeIterator;
      mySegmentIterator = new SegmentIterator(charSequence, colorsScheme.getFontPreferences());
    }

    public boolean atEnd() {
      return myRangeIterator.atEnd() && mySegmentIterator.atEnd();
    }

    public void advance() {
      if (mySegmentIterator.atEnd()) {
        myRangeIterator.advance();
        TextAttributes textAttributes = myRangeIterator.getTextAttributes();
        myCurrentFontStyle = textAttributes == null ? Font.PLAIN : textAttributes.getFontType();
        myCurrentForegroundColor = textAttributes == null ? null : textAttributes.getForegroundColor();
        myCurrentBackgroundColor = textAttributes == null ? null : textAttributes.getBackgroundColor();
        mySegmentIterator.reset(myRangeIterator.getRangeStart(), myRangeIterator.getRangeEnd(), myCurrentFontStyle);
      }
      mySegmentIterator.advance();
    }

    public int getStartOffset() {
      return mySegmentIterator.getCurrentStartOffset();
    }

    public int getEndOffset() {
      return mySegmentIterator.getCurrentEndOffset();
    }

    public int getFontStyle() {
      return myCurrentFontStyle;
    }

    @Nonnull
    public String getFontFamilyName() {
      return mySegmentIterator.getCurrentFontFamilyName();
    }

    @Nullable
    public ColorValue getForegroundColor() {
      return myCurrentForegroundColor;
    }

    @Nullable
    public ColorValue getBackgroundColor() {
      return myCurrentBackgroundColor;
    }

    public void dispose() {
      myRangeIterator.dispose();
    }
  }

  static class CompositeRangeIterator implements RangeIterator {
    @Nonnull
    private final ColorValue myDefaultForeground;
    @Nonnull
    private final ColorValue myDefaultBackground;
    private final IteratorWrapper[] myIterators;
    private final TextAttributes myMergedAttributes = new TextAttributes();
    private int overlappingRangesCount;
    private int myCurrentStart;
    private int myCurrentEnd;

    // iterators have priority corresponding to their order in the parameter list - rightmost having the largest priority
    CompositeRangeIterator(@Nonnull EditorColorsScheme colorsScheme, RangeIterator... iterators) {
      myDefaultForeground = colorsScheme.getDefaultForeground();
      myDefaultBackground = colorsScheme.getDefaultBackground();
      myIterators = new IteratorWrapper[iterators.length];
      for (int i = 0; i < iterators.length; i++) {
        myIterators[i] = new IteratorWrapper(iterators[i], i);
      }
    }

    @Override
    public boolean atEnd() {
      boolean validIteratorExists = false;
      for (int i = 0; i < myIterators.length; i++) {
        IteratorWrapper wrapper = myIterators[i];
        if (wrapper == null) {
          continue;
        }
        RangeIterator iterator = wrapper.iterator;
        if (!iterator.atEnd() || overlappingRangesCount > 0 && (i >= overlappingRangesCount || iterator.getRangeEnd() > myCurrentEnd)) {
          validIteratorExists = true;
        }
      }
      return !validIteratorExists;
    }

    @Override
    public void advance() {
      int max = overlappingRangesCount == 0 ? myIterators.length : overlappingRangesCount;
      for (int i = 0; i < max; i++) {
        IteratorWrapper wrapper = myIterators[i];
        if (wrapper == null) {
          continue;
        }
        RangeIterator iterator = wrapper.iterator;
        if (overlappingRangesCount > 0 && iterator.getRangeEnd() > myCurrentEnd) {
          continue;
        }
        if (iterator.atEnd()) {
          iterator.dispose();
          myIterators[i] = null;
        }
        else {
          iterator.advance();
        }
      }
      Arrays.sort(myIterators, RANGE_SORTER);
      myCurrentStart = Math.max(myIterators[0].iterator.getRangeStart(), myCurrentEnd);
      myCurrentEnd = Integer.MAX_VALUE;
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < myIterators.length; i++) {
        IteratorWrapper wrapper = myIterators[i];
        if (wrapper == null) {
          break;
        }
        RangeIterator iterator = wrapper.iterator;
        int nearestBound;
        if (iterator.getRangeStart() > myCurrentStart) {
          nearestBound = iterator.getRangeStart();
        }
        else {
          nearestBound = iterator.getRangeEnd();
        }
        myCurrentEnd = Math.min(myCurrentEnd, nearestBound);
      }
      for (overlappingRangesCount = 1; overlappingRangesCount < myIterators.length; overlappingRangesCount++) {
        IteratorWrapper wrapper = myIterators[overlappingRangesCount];
        if (wrapper == null || wrapper.iterator.getRangeStart() > myCurrentStart) {
          break;
        }
      }
    }

    private final Comparator<IteratorWrapper> RANGE_SORTER = new Comparator<IteratorWrapper>() {
      @Override
      public int compare(IteratorWrapper o1, IteratorWrapper o2) {
        if (o1 == null) {
          return 1;
        }
        if (o2 == null) {
          return -1;
        }
        int startDiff = Math.max(o1.iterator.getRangeStart(), myCurrentEnd) - Math.max(o2.iterator.getRangeStart(), myCurrentEnd);
        if (startDiff != 0) {
          return startDiff;
        }
        return o2.order - o1.order;
      }
    };

    @Override
    public int getRangeStart() {
      return myCurrentStart;
    }

    @Override
    public int getRangeEnd() {
      return myCurrentEnd;
    }

    @Override
    public TextAttributes getTextAttributes() {
      TextAttributes ta = myIterators[0].iterator.getTextAttributes();
      myMergedAttributes.setAttributes(ta.getForegroundColor(), ta.getBackgroundColor(), null, null, null, ta.getFontType());
      for (int i = 1; i < overlappingRangesCount; i++) {
        merge(myIterators[i].iterator.getTextAttributes());
      }
      return myMergedAttributes;
    }

    private void merge(TextAttributes attributes) {
      ColorValue myBackground = myMergedAttributes.getBackgroundColor();
      if (myBackground == null || myDefaultBackground.equals(myBackground)) {
        myMergedAttributes.setBackgroundColor(attributes.getBackgroundColor());
      }
      ColorValue myForeground = myMergedAttributes.getForegroundColor();
      if (myForeground == null || myDefaultForeground.equals(myForeground)) {
        myMergedAttributes.setForegroundColor(attributes.getForegroundColor());
      }
      if (myMergedAttributes.getFontType() == Font.PLAIN) {
        myMergedAttributes.setFontType(attributes.getFontType());
      }
    }

    @Override
    public void dispose() {
      for (IteratorWrapper wrapper : myIterators) {
        if (wrapper != null) {
          wrapper.iterator.dispose();
        }
      }
    }

    private static class IteratorWrapper {
      private final RangeIterator iterator;
      private final int order;

      private IteratorWrapper(RangeIterator iterator, int order) {
        this.iterator = iterator;
        this.order = order;
      }
    }
  }

  private static class MarkupModelRangeIterator implements RangeIterator {
    private final boolean myUnsupportedModel;
    private final int myStartOffset;
    private final int myEndOffset;
    private final EditorColorsScheme myColorsScheme;
    private final ColorValue myDefaultForeground;
    private final ColorValue myDefaultBackground;
    private final MarkupIterator<RangeHighlighterEx> myIterator;

    private int myCurrentStart;
    private int myCurrentEnd;
    private TextAttributes myCurrentAttributes;
    private int myNextStart;
    private int myNextEnd;
    private TextAttributes myNextAttributes;

    private MarkupModelRangeIterator(@Nullable MarkupModel markupModel, @Nonnull EditorColorsScheme colorsScheme, int startOffset, int endOffset) {
      myStartOffset = startOffset;
      myEndOffset = endOffset;
      myColorsScheme = colorsScheme;
      myDefaultForeground = colorsScheme.getDefaultForeground();
      myDefaultBackground = colorsScheme.getDefaultBackground();
      myUnsupportedModel = !(markupModel instanceof MarkupModelEx);
      if (myUnsupportedModel) {
        myIterator = null;
        return;
      }
      myIterator = ((MarkupModelEx)markupModel).overlappingIterator(startOffset, endOffset);
      try {
        findNextSuitableRange();
      }
      catch (RuntimeException | Error e) {
        myIterator.dispose();
        throw e;
      }
    }

    @Override
    public boolean atEnd() {
      return myUnsupportedModel || myNextAttributes == null;
    }

    @Override
    public void advance() {
      myCurrentStart = myNextStart;
      myCurrentEnd = myNextEnd;
      myCurrentAttributes = myNextAttributes;
      findNextSuitableRange();
    }

    private void findNextSuitableRange() {
      myNextAttributes = null;
      while (myIterator.hasNext()) {
        RangeHighlighterEx highlighter = myIterator.next();
        if (highlighter == null || !highlighter.isValid() || !isInterestedInLayer(highlighter.getLayer())) {
          continue;
        }
        // LINES_IN_RANGE highlighters are not supported currently
        myNextStart = Math.max(highlighter.getStartOffset(), myStartOffset);
        myNextEnd = Math.min(highlighter.getEndOffset(), myEndOffset);
        if (myNextStart >= myEndOffset) {
          break;
        }
        if (myNextStart < myCurrentEnd) {
          continue; // overlapping ranges withing document markup model are not supported currently
        }
        TextAttributes attributes = null;
        HighlightInfoImpl info = HighlightInfoImpl.fromRangeHighlighter(highlighter);
        if (info != null) {
          TextAttributesKey key = info.forcedTextAttributesKey;
          if (key == null) {
            HighlightInfoType type = info.type;
            key = type.getAttributesKey();
          }
          if (key != null) {
            attributes = myColorsScheme.getAttributes(key);
          }
        }
        if (attributes == null) {
          continue;
        }
        ColorValue foreground = attributes.getForegroundColor();
        ColorValue background = attributes.getBackgroundColor();
        if ((foreground == null || myDefaultForeground.equals(foreground)) && (background == null || myDefaultBackground.equals(background)) && attributes.getFontType() == Font.PLAIN) {
          continue;
        }
        myNextAttributes = attributes;
        break;
      }
    }

    private static boolean isInterestedInLayer(int layer) {
      return layer != HighlighterLayer.CARET_ROW &&
             layer != HighlighterLayer.SELECTION &&
             layer != HighlighterLayer.ERROR &&
             layer != HighlighterLayer.WARNING &&
             layer != HighlighterLayer.ELEMENT_UNDER_CARET;
    }

    @Override
    public int getRangeStart() {
      return myCurrentStart;
    }

    @Override
    public int getRangeEnd() {
      return myCurrentEnd;
    }

    @Override
    public TextAttributes getTextAttributes() {
      return myCurrentAttributes;
    }

    @Override
    public void dispose() {
      if (myIterator != null) {
        myIterator.dispose();
      }
    }
  }

  static class HighlighterRangeIterator implements RangeIterator {
    private static final TextAttributes EMPTY_ATTRIBUTES = new TextAttributes();

    private final HighlighterIterator myIterator;
    private final int myStartOffset;
    private final int myEndOffset;

    private int myCurrentStart;
    private int myCurrentEnd;
    private TextAttributes myCurrentAttributes;

    HighlighterRangeIterator(@Nonnull EditorHighlighter highlighter, int startOffset, int endOffset) {
      myStartOffset = startOffset;
      myEndOffset = endOffset;
      myIterator = highlighter.createIterator(startOffset);
    }

    @Override
    public boolean atEnd() {
      return myIterator.atEnd() || getCurrentStart() >= myEndOffset;
    }

    private int getCurrentStart() {
      return Math.max(myIterator.getStart(), myStartOffset);
    }

    private int getCurrentEnd() {
      return Math.min(myIterator.getEnd(), myEndOffset);
    }

    @Override
    public void advance() {
      myCurrentStart = getCurrentStart();
      myCurrentEnd = getCurrentEnd();
      myCurrentAttributes = myIterator.getTokenType() == TokenType.BAD_CHARACTER ? EMPTY_ATTRIBUTES : myIterator.getTextAttributes();
      myIterator.advance();
    }

    @Override
    public int getRangeStart() {
      return myCurrentStart;
    }

    @Override
    public int getRangeEnd() {
      return myCurrentEnd;
    }

    @Override
    public TextAttributes getTextAttributes() {
      return myCurrentAttributes;
    }

    @Override
    public void dispose() {
    }
  }

  private static class SegmentIterator {
    private final FontFallbackIterator myIterator = new FontFallbackIterator();
    private final CharSequence myCharSequence;
    private int myEndOffset;
    private boolean myAdvanceCalled;

    private SegmentIterator(CharSequence charSequence, FontPreferences fontPreferences) {
      myCharSequence = charSequence;
      myIterator.setPreferredFonts(fontPreferences);
    }

    public void reset(int startOffset, int endOffset, int fontStyle) {
      myIterator.setFontStyle(fontStyle);
      myIterator.start(myCharSequence, startOffset, endOffset);
      myEndOffset = endOffset;
      myAdvanceCalled = false;
    }

    public boolean atEnd() {
      return myIterator.atEnd() || myIterator.getEnd() == myEndOffset;
    }

    public void advance() {
      if (!myAdvanceCalled) {
        myAdvanceCalled = true;
        return;
      }
      myIterator.advance();
    }

    public int getCurrentStartOffset() {
      return myIterator.getStart();
    }

    public int getCurrentEndOffset() {
      return myIterator.getEnd();
    }

    public String getCurrentFontFamilyName() {
      return myIterator.getFont().getFamily();
    }
  }

  static class Context {

    private final SyntaxInfo.Builder builder;

    @Nonnull
    private final CharSequence myText;
    @Nonnull
    private final ColorValue myDefaultForeground;
    @Nonnull
    private final ColorValue myDefaultBackground;

    @Nullable
    private ColorValue myBackground;
    @Nullable
    private ColorValue myForeground;
    @Nullable
    private String myFontFamilyName;

    private final int myIndentSymbolsToStrip;

    private int myFontStyle = -1;
    private int myStartOffset = -1;
    private int myOffsetShift = 0;

    private int myIndentSymbolsToStripAtCurrentLine;

    Context(@Nonnull CharSequence charSequence, @Nonnull EditorColorsScheme scheme, int indentSymbolsToStrip) {
      myText = charSequence;
      myDefaultForeground = scheme.getDefaultForeground();
      myDefaultBackground = scheme.getDefaultBackground();

      int javaFontSize = scheme.getEditorFontSize();
      float fontSize = Platform.current().os().isMac() || ApplicationManager.getApplication().isHeadlessEnvironment()
        ? javaFontSize
        : javaFontSize * 0.75f / UISettingsUtil.getDefFontScale(); // matching font size in external apps

      builder = new SyntaxInfo.Builder(myDefaultForeground, myDefaultBackground, fontSize);
      myIndentSymbolsToStrip = indentSymbolsToStrip;
    }

    public void reset(int offsetShiftDelta) {
      myStartOffset = -1;
      myOffsetShift += offsetShiftDelta;
      myIndentSymbolsToStripAtCurrentLine = 0;
    }

    public void iterate(MyMarkupIterator iterator, int endOffset) {
      while (!iterator.atEnd()) {
        iterator.advance();
        int startOffset = iterator.getStartOffset();
        if (startOffset >= endOffset) {
          break;
        }
        if (myStartOffset < 0) {
          myStartOffset = startOffset;
        }

        boolean whiteSpacesOnly = CharArrayUtil.isEmptyOrSpaces(myText, startOffset, iterator.getEndOffset());

        processBackground(startOffset, iterator.getBackgroundColor());
        if (!whiteSpacesOnly) {
          processForeground(startOffset, iterator.getForegroundColor());
          processFontFamilyName(startOffset, iterator.getFontFamilyName());
          processFontStyle(startOffset, iterator.getFontStyle());
        }
      }
      addTextIfPossible(endOffset);
    }

    private void processFontStyle(int startOffset, int fontStyle) {
      if (fontStyle != myFontStyle) {
        addTextIfPossible(startOffset);
        builder.addFontStyle(fontStyle);
        myFontStyle = fontStyle;
      }
    }

    private void processFontFamilyName(int startOffset, String fontName) {
      String fontFamilyName = FontMapper.getPhysicalFontName(fontName);
      if (!fontFamilyName.equals(myFontFamilyName)) {
        addTextIfPossible(startOffset);
        builder.addFontFamilyName(fontFamilyName);
        myFontFamilyName = fontFamilyName;
      }
    }

    private void processForeground(int startOffset, ColorValue foreground) {
      if (myForeground == null && foreground != null) {
        addTextIfPossible(startOffset);
        myForeground = foreground;
        builder.addForeground(foreground);
      }
      else if (myForeground != null) {
        ColorValue c = foreground == null ? myDefaultForeground : foreground;
        if (!myForeground.equals(c)) {
          addTextIfPossible(startOffset);
          builder.addForeground(c);
          myForeground = c;
        }
      }
    }

    private void processBackground(int startOffset, ColorValue background) {
      if (myBackground == null && background != null && !myDefaultBackground.equals(background)) {
        addTextIfPossible(startOffset);
        myBackground = background;
        builder.addBackground(background);
      }
      else if (myBackground != null) {
        ColorValue c = background == null ? myDefaultBackground : background;
        if (!myBackground.equals(c)) {
          addTextIfPossible(startOffset);
          builder.addBackground(c);
          myBackground = c;
        }
      }
    }

    private void addTextIfPossible(int endOffset) {
      if (endOffset <= myStartOffset) {
        return;
      }

      for (int i = myStartOffset; i < endOffset; i++) {
        char c = myText.charAt(i);
        switch (c) {
          case '\r':
            if (i + 1 < myText.length() && myText.charAt(i + 1) == '\n') {
              myIndentSymbolsToStripAtCurrentLine = myIndentSymbolsToStrip;
              builder.addText(myStartOffset + myOffsetShift, i + myOffsetShift + 1);
              myStartOffset = i + 2;
              myOffsetShift--;
              //noinspection AssignmentToForLoopParameter
              i++;
              break;
            }
            // Intended fall-through.
          case '\n':
            myIndentSymbolsToStripAtCurrentLine = myIndentSymbolsToStrip;
            builder.addText(myStartOffset + myOffsetShift, i + myOffsetShift + 1);
            myStartOffset = i + 1;
            break;
          // Intended fall-through.
          case ' ':
          case '\t':
            if (myIndentSymbolsToStripAtCurrentLine > 0) {
              myIndentSymbolsToStripAtCurrentLine--;
              myStartOffset++;
              continue;
            }
          default:
            myIndentSymbolsToStripAtCurrentLine = 0;
        }
      }

      if (myStartOffset < endOffset) {
        builder.addText(myStartOffset + myOffsetShift, endOffset + myOffsetShift);
        myStartOffset = endOffset;
      }
    }

    void addCharacter(int position) {
      builder.addText(position + myOffsetShift, position + myOffsetShift + 1);
    }

    @Nonnull
    public SyntaxInfo finish() {
      return builder.build();
    }
  }
}
