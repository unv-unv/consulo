/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package consulo.codeEditor.markup;

import consulo.colorScheme.EditorColorsScheme;
import consulo.colorScheme.TextAttributes;
import consulo.colorScheme.TextAttributesKey;
import consulo.document.RangeMarker;
import consulo.ui.color.ColorValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.awt.*;
import java.util.Comparator;

/**
 * Represents a range of text in the document which has specific markup (special text attributes,
 * line marker, gutter icon, error stripe marker or line separator).
 *
 * @see MarkupModel#addRangeHighlighter(int, int, int, TextAttributes, HighlighterTargetArea)
 * @see consulo.ide.impl.idea.lang.annotation.Annotation
 */
public interface RangeHighlighter extends RangeMarker {
    RangeHighlighter[] EMPTY_ARRAY = new RangeHighlighter[0];

    Comparator<RangeHighlighter> BY_AFFECTED_START_OFFSET = Comparator.comparingInt(RangeHighlighter::getAffectedAreaStartOffset);

    /**
     * Returns the relative priority of the highlighter (higher priority highlighters can override
     * lower priority ones; layer number values for standard IDEA highlighters are given in
     * {@link HighlighterLayer} class).
     *
     * @return the highlighter priority.
     */
    int getLayer();

    /**
     * Returns the value indicating whether the highlighter affects a range of text or a sequence of
     * of entire lines in the specified range.
     *
     * @return the highlighter target area.
     */
    HighlighterTargetArea getTargetArea();

    /**
     * Returns the text attributes key used for highlighting.
     * Having a key is preferred over raw attributes which makes it impossible to update it on a {@link EditorColorsScheme} changes
     *
     * @return the attributes key or {@code null} if the highlighter does not have a key.
     */
    @Nullable
    TextAttributesKey getTextAttributesKey();

    /**
     * Sets the text attributes key used for highlighting.
     * Having a key is preferred over raw attributes which makes it impossible to update it on a {@link EditorColorsScheme} changes
     *
     * @param textAttributesKey a text attributes key.
     */
    void setTextAttributesKey(@Nonnull TextAttributesKey textAttributesKey);

    /**
     * @deprecated Use the overload with {@link EditorColorsScheme} and prefer using {@link #getTextAttributesKey()}
     */
    @Deprecated
    @Nullable
    default TextAttributes getTextAttributes() {
        return getTextAttributes(null);
    }

    /**
     * Returns text attributes used for highlighting.
     *
     * @param scheme color scheme for which text attributes are requested (when null, the global scheme will be used)
     * @return the attributes, or null if highlighter does not modify the text attributes.
     * @see RangeHighlighter#getTextAttributesKey()
     */
    @Nullable
    TextAttributes getTextAttributes(@Nullable EditorColorsScheme scheme);

    /**
     * Returns the renderer used for drawing line markers in the area covered by the
     * highlighter, and optionally for processing mouse events over the markers.
     * Line markers are drawn over the folding area and are used, for example,
     * to highlight modified lines in files under source control.
     *
     * @return the renderer instance, or null if the highlighter does not add any line markers.
     * @see ActiveGutterRenderer
     */
    @Nullable
    LineMarkerRenderer getLineMarkerRenderer();

    /**
     * Sets the renderer used for drawing line markers in the area covered by the
     * highlighter, and optionally for processing mouse events over the markers.
     * Line markers are drawn over the folding area and are used, for example,
     * to highlight modified lines in files under source control.
     *
     * @param renderer the renderer instance, or null if the highlighter does not add any line markers.
     * @see ActiveGutterRenderer
     */
    void setLineMarkerRenderer(@Nullable LineMarkerRenderer renderer);

    @Nullable
    CustomHighlighterRenderer getCustomRenderer();

    void setCustomRenderer(CustomHighlighterRenderer renderer);

    /**
     * Returns the renderer used for drawing gutter icons in the area covered by the
     * highlighter. Gutter icons are drawn to the left of the folding area and can be used,
     * for example, to mark implemented or overridden methods.
     *
     * @return the renderer instance, or null if the highlighter does not add any gutter icons.
     */
    @Nullable
    GutterIconRenderer getGutterIconRenderer();

    /**
     * Sets the renderer used for drawing gutter icons in the area covered by the
     * highlighter. Gutter icons are drawn to the left of the folding area and can be used,
     * for example, to mark implemented or overridden methods.
     *
     * @param renderer the renderer instance, or null if the highlighter does not add any gutter icons.
     */
    void setGutterIconRenderer(@Nullable GutterIconRenderer renderer);

    /**
     * Returns the color of the marker drawn in the error stripe in the area covered by the highlighter.
     *
     * @param scheme - when null, the global scheme will be used
     * @return the error stripe marker color, or {@code null} if the highlighter does not add any
     * error stripe markers.
     */
    @Nullable
    ColorValue getErrorStripeMarkColor(@Nullable EditorColorsScheme scheme);

    /**
     * Sets the color of the marker drawn in the error stripe in the area covered by the highlighter.
     *
     * @param color the error stripe marker color, or null if the highlighter does not add any
     *              error stripe markers.
     */
    void setErrorStripeMarkColor(@Nullable ColorValue color);

    /**
     * Returns the object whose <code>toString()</code> method is called to get the text of the tooltip
     * for the error stripe marker added by the highlighter.
     *
     * @return the error stripe tooltip objects, or null if the highlighter does not add any error
     * stripe markers or the marker has no tooltip.
     */
    @Nullable
    Object getErrorStripeTooltip();

    /**
     * Sets the object whose <code>toString()</code> method is called to get the text of the tooltip
     * for the error stripe marker added by the highlighter.
     *
     * @param tooltipObject the error stripe tooltip objects, or null if the highlighter does not
     *                      add any error stripe markers or the marker has no tooltip.
     */
    void setErrorStripeTooltip(@Nullable Object tooltipObject);

    /**
     * Returns the value indicating whether the error stripe marker has reduced width (like
     * the markers used to highlight changed lines).
     *
     * @return true if the marker has reduced width, false otherwise.
     */
    boolean isThinErrorStripeMark();

    /**
     * Sets the value indicating whether the error stripe marker has reduced width (like
     * the markers used to highlight changed lines).
     *
     * @param value true if the marker has reduced width, false otherwise.
     */
    void setThinErrorStripeMark(boolean value);

    /**
     * Returns the color of the separator drawn above or below the range covered by
     * the highlighter.
     *
     * @return the separator color, or null if the highlighter does not add a line separator.
     */
    @Nullable
    Color getLineSeparatorColor();

    /**
     * Sets the color of the separator drawn above or below the range covered by
     * the highlighter.
     *
     * @param color the separator color, or null if the highlighter does not add a line separator.
     */
    void setLineSeparatorColor(@Nullable Color color);

    void setLineSeparatorRenderer(LineSeparatorRenderer renderer);

    LineSeparatorRenderer getLineSeparatorRenderer();

    /**
     * Returns the placement of the separator drawn by the range highlighter
     * (above or below the range).
     *
     * @return the separator placement, or null if the highlighter does not add a line separator.
     */
    @Nullable
    SeparatorPlacement getLineSeparatorPlacement();

    /**
     * Sets the placement of the separator drawn by the range highlighter
     * (above or below the range).
     *
     * @param placement the separator placement, or null if the highlighter does not add a line separator.
     */
    void setLineSeparatorPlacement(@Nullable SeparatorPlacement placement);

    /**
     * Sets the filter which can disable the highlighter in specific editor instances.
     *
     * @param filter the filter controlling the highlighter availability, or MarkupEditorFilter.EMPTY if
     *               highlighter is available in all editors.
     */
    void setEditorFilter(@Nonnull MarkupEditorFilter filter);

    /**
     * Gets the filter which can disable the highlighter in specific editor instances.
     *
     * @return the filter controlling the highlighter availability. Default availability is controlled by MarkupEditorFilter.EMPTY
     */
    @Nonnull
    MarkupEditorFilter getEditorFilter();

    boolean isAfterEndOfLine();

    void setAfterEndOfLine(boolean value);

    int getAffectedAreaStartOffset();

    int getAffectedAreaEndOffset();

    /**
     * @see #isVisibleIfFolded()
     */
    void setVisibleIfFolded(boolean value);

    /**
     * If {@code true}, there will be a visual indication that this highlighter is present inside a collapsed fold region.
     * By default it won't happen, use {@link #setVisibleIfFolded(boolean)} to change it.
     *
     * @see FoldRegion#setInnerHighlightersMuted(boolean)
     */
    boolean isVisibleIfFolded();

    default boolean isRenderedInGutter() {
        return getGutterIconRenderer() != null || getLineMarkerRenderer() != null;
    }
}
