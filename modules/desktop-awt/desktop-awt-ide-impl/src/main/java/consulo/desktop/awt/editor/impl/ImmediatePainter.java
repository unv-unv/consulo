// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.desktop.awt.editor.impl;

import consulo.application.ApplicationManager;
import consulo.application.util.function.Processor;
import consulo.application.util.registry.Registry;
import consulo.application.util.registry.RegistryValue;
import consulo.awt.hacking.SunVolatileImageHacking;
import consulo.codeEditor.*;
import consulo.codeEditor.impl.FontLayoutService;
import consulo.codeEditor.impl.IterationState;
import consulo.codeEditor.internal.EditorActionPlan;
import consulo.codeEditor.markup.HighlighterLayer;
import consulo.codeEditor.markup.HighlighterTargetArea;
import consulo.codeEditor.markup.RangeHighlighterEx;
import consulo.colorScheme.EditorColorsScheme;
import consulo.colorScheme.EffectType;
import consulo.colorScheme.TextAttributes;
import consulo.colorScheme.TextAttributesEffectsBuilder;
import consulo.disposer.Disposer;
import consulo.document.Document;
import consulo.document.impl.DocumentImpl;
import consulo.ide.impl.idea.openapi.editor.ex.util.EditorUIUtil;
import consulo.ide.impl.idea.openapi.editor.ex.util.EditorUtil;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.language.editor.highlight.LexerEditorHighlighter;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.ui.color.ColorValue;
import consulo.ui.color.RGBColor;
import consulo.ui.ex.awt.ImageUtil;
import consulo.ui.ex.awt.JBUIScale;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.paint.PaintUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.util.LightDarkColorValue;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Pavel Fatin
 */
class ImmediatePainter {
    private static final Logger LOG = Logger.getInstance(ImmediatePainter.class);

    private static final int DEBUG_PAUSE_DURATION = 1000;

    static final RegistryValue ENABLED = Registry.get("editor.zero.latency.rendering");
    static final RegistryValue DOUBLE_BUFFERING = Registry.get("editor.zero.latency.rendering.double.buffering");
    private static final RegistryValue PIPELINE_FLUSH = Registry.get("editor.zero.latency.rendering.pipeline.flush");
    private static final RegistryValue DEBUG = Registry.get("editor.zero.latency.rendering.debug");

    private final DesktopEditorImpl myEditor;
    private Image myImage;

    ImmediatePainter(DesktopEditorImpl editor) {
        myEditor = editor;

        Disposer.register(editor.getDisposable(), () -> {
            if (myImage != null) {
                myImage.flush();
            }
        });
    }

    boolean paint(Graphics g, EditorActionPlan plan) {
        if (ENABLED.asBoolean() && canPaintImmediately(myEditor)) {
            if (plan.getCaretShift() != 1) {
                return false;
            }

            List<EditorActionPlan.Replacement> replacements = plan.getReplacements();
            if (replacements.size() != 1) {
                return false;
            }

            EditorActionPlan.Replacement replacement = replacements.get(0);
            if (replacement.getText().length() != 1) {
                return false;
            }

            int caretOffset = replacement.getBegin();
            char c = replacement.getText().charAt(0);
            try {
                paintImmediately((Graphics2D) g, caretOffset, c);
                return true;
            }
            catch (Exception e) {
                LOG.error(e);
            }
        }
        return false;
    }

    private static boolean canPaintImmediately(DesktopEditorImpl editor) {
        CaretModel caretModel = editor.getCaretModel();
        Caret caret = caretModel.getPrimaryCaret();
        Document document = editor.getDocument();

        return document instanceof DocumentImpl &&
            editor.getHighlighter() instanceof LexerEditorHighlighter &&
            !(editor.getComponent().getParent() instanceof EditorTextField) &&
            editor.myView.getTopOverhang() <= 0 && editor.myView.getBottomOverhang() <= 0 &&
            !editor.getSelectionModel().hasSelection() &&
            caretModel.getCaretCount() == 1 &&
            !isInVirtualSpace(editor, caret) &&
            !isInsertion(document, caret.getOffset()) &&
            !caret.isAtRtlLocation() &&
            !caret.isAtBidiRunBoundary() &&
            noBorderEffectPainted(editor, caret);
    }

    private static boolean noBorderEffectPainted(EditorEx editor, Caret caret) {
        int offset = caret.getOffset();
        EditorColorsScheme colorsScheme = editor.getColorsScheme();
        return editor.getMarkupModel().processRangeHighlightersOverlappingWith(offset, offset, h -> {
            TextAttributes attrs = h.getTextAttributes(colorsScheme);
            return attrs == null || !attrs.hasEffects() ||
                TextAttributesEffectsBuilder.create(attrs).getEffectDescriptor(TextAttributesEffectsBuilder.EffectSlot.FRAME_SLOT) == null;
        });
    }

    private static boolean isInVirtualSpace(Editor editor, Caret caret) {
        return caret.getLogicalPosition().compareTo(editor.offsetToLogicalPosition(caret.getOffset())) != 0;
    }

    private static boolean isInsertion(Document document, int offset) {
        return offset < document.getTextLength() && document.getCharsSequence().charAt(offset) != '\n';
    }

    private void paintImmediately(Graphics2D g, int offset, char c2) {
        DesktopEditorImpl editor = myEditor;
        Document document = editor.getDocument();
        LexerEditorHighlighter highlighter = (LexerEditorHighlighter) myEditor.getHighlighter();

        EditorSettings settings = editor.getSettings();
        boolean isBlockCursor = editor.isInsertMode() == settings.isBlockCursor();
        int lineHeight = editor.getLineHeight();
        int caretHeight = editor.myView.getCaretHeight();
        int ascent = editor.getAscent();
        int topOverhang = settings.isFullLineHeightCursor() ? 0 : editor.myView.getTopOverhang();

        char c1 = offset == 0 ? ' ' : document.getCharsSequence().charAt(offset - 1);

        List<TextAttributes> attributes;
        try {
            attributes = highlighter.getAttributesForPreviousAndTypedChars(document, offset, c2);
        }
        catch (Exception e) {
            throw new RuntimeException("Error calculating attributes, highlighter: " + highlighter + ", offset: " + offset + ", document length" +
                document.getTextLength() + ", highlighter's last offset:" + highlighter.getSegments().getLastValidOffset(),
                e);
        }
        updateAttributes(editor, offset, attributes);

        TextAttributes attributes1 = attributes.get(0);
        TextAttributes attributes2 = attributes.get(1);

        if (!(canRender(attributes1) && canRender(attributes2))) {
            return;
        }

        FontLayoutService fontLayoutService = FontLayoutService.getInstance();
        float width1 = fontLayoutService.charWidth2D(editor.getFontMetrics(attributes1.getFontType()), c1);
        float width2 = fontLayoutService.charWidth2D(editor.getFontMetrics(attributes2.getFontType()), c2);

        Font font1 = EditorUtil.fontForChar(c1, attributes1.getFontType(), editor).getFont();
        Font font2 = EditorUtil.fontForChar(c1, attributes2.getFontType(), editor).getFont();

        Point2D p2 = editor.offsetToPoint2D(offset);
        float p2x = (float) p2.getX();
        int p2y = (int) p2.getY();

        Caret caret = editor.getCaretModel().getPrimaryCaret();
        //noinspection ConstantConditions
        float caretWidth = isBlockCursor ? editor.getCaretLocations(false)[0].myWidth
            : JBUIScale.scale(caret.getVisualAttributes().getWidth(settings.getLineCursorWidth()));
        float caretShift = isBlockCursor ? 0 : caretWidth <= 1 ? 0 : 1 / JBUIScale.sysScale(g);
        Rectangle2D caretRectangle = new Rectangle2D.Float(p2x + width2 - caretShift, p2y - topOverhang,
            caretWidth, caretHeight);

        float rectangle2Start = (float) PaintUtil.alignToInt(p2x, g, PaintUtil.RoundingMode.FLOOR);
        float rectangle2End = (float) PaintUtil.alignToInt(p2x + width2 + caretWidth - caretShift, g, PaintUtil.RoundingMode.CEIL);
        Rectangle2D rectangle1 = new Rectangle2D.Float(p2x - width1, p2y, width1, lineHeight);
        Rectangle2D rectangle2 = new Rectangle2D.Float(rectangle2Start, p2y, rectangle2End - rectangle2Start, lineHeight);

        Consumer<Graphics2D> painter = graphics -> {
            EditorUIUtil.setupAntialiasing(graphics);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, editor.myFractionalMetricsHintValue);

            fillRect(graphics, rectangle2, attributes2.getBackgroundColor());
            drawChar(graphics, c2, p2x, p2y + ascent, font2, attributes2.getForegroundColor());

            fillRect(graphics, caretRectangle, getCaretColor(editor));

            fillRect(graphics, rectangle1, attributes1.getBackgroundColor());
            drawChar(graphics, c1, p2x - width1, p2y + ascent, font1, attributes1.getForegroundColor());
        };

        Shape originalClip = g.getClip();

        float clipStartX = (float) PaintUtil.alignToInt(p2x > editor.getContentComponent().getInsets().left ? p2x - caretShift : p2x,
            g, PaintUtil.RoundingMode.FLOOR);
        float clipEndX = (float) PaintUtil.alignToInt(p2x + width2 - caretShift + caretWidth,
            g, PaintUtil.RoundingMode.CEIL);
        if (clipEndX > editor.getContentComponent().getWidth()) {
            // we cannot paint beyond component bounds (this will go beyond dev clip in graphics anyway)
            return;
        }

        g.setClip(new Rectangle2D.Float(clipStartX, p2y, clipEndX - clipStartX, lineHeight));
        // at the moment, lines in editor are not aligned to dev pixel grid along Y axis, when fractional scale is used,
        // so double buffering is disabled (as it might not produce the same result as direct painting, and will case text jitter)
        if (DOUBLE_BUFFERING.asBoolean() && !PaintUtil.isFractionalScale(g.getTransform())) {
            paintWithDoubleBuffering(g, painter);
        }
        else {
            painter.accept(g);
        }

        g.setClip(originalClip);

        if (PIPELINE_FLUSH.asBoolean()) {
            Toolkit.getDefaultToolkit().sync();
        }

        if (DEBUG.asBoolean()) {
            pause();
        }
    }

    private static boolean canRender(TextAttributes attributes) {
        return attributes.getEffectType() != EffectType.BOXED || attributes.getEffectColor() == null;
    }

    private void paintWithDoubleBuffering(Graphics2D graphics, Consumer<? super Graphics2D> painter) {
        Rectangle bounds = graphics.getClipBounds();

        createOrUpdateImageBuffer(myEditor.getComponent(), graphics, bounds.getSize());

        UIUtil.useSafely(myImage.getGraphics(), imageGraphics -> {
            imageGraphics.translate(-bounds.x, -bounds.y);
            painter.accept(imageGraphics);
        });

        UIUtil.drawImage(graphics, myImage, bounds.x, bounds.y, null);
    }

    private void createOrUpdateImageBuffer(JComponent component, Graphics2D graphics, Dimension size) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
            if (myImage == null || !isLargeEnough(myImage, size)) {
                int width = (int) Math.ceil(PaintUtil.alignToInt(size.width, graphics, PaintUtil.RoundingMode.CEIL));
                int height = (int) Math.ceil(PaintUtil.alignToInt(size.height, graphics, PaintUtil.RoundingMode.CEIL));
                myImage = ImageUtil.createImage(width, height, BufferedImage.TYPE_INT_RGB);
            }
        }
        else {
            if (myImage == null) {
                myImage = component.createVolatileImage(size.width, size.height);
            }
            else if (!isLargeEnough(myImage, size) || !isImageValid((VolatileImage) myImage, component)) {
                myImage.flush();
                myImage = component.createVolatileImage(size.width, size.height);
            }
        }
    }

    private static boolean isLargeEnough(Image image, Dimension size) {
        int width = image.getWidth(null);
        int height = image.getHeight(null);
        if (width == -1 || height == -1) {
            throw new IllegalArgumentException("Image size is undefined");
        }
        return width >= size.width && height >= size.height;
    }

    private static boolean isImageValid(VolatileImage image, Component component) {
        GraphicsConfiguration componentConfig = component.getGraphicsConfiguration();
        // JBR-1540
        if (Platform.current().os().isWindows() && SunVolatileImageHacking.isSunVolatileImage(image)) {
            GraphicsConfiguration imageConfig = SunVolatileImageHacking.getGraphicsConfig(image);
            if (imageConfig != null && componentConfig != null && imageConfig.getDevice() != componentConfig.getDevice()) {
                return false;
            }
        }
        return image.validate(componentConfig) != VolatileImage.IMAGE_INCOMPATIBLE;
    }

    private static void fillRect(Graphics2D g, Rectangle2D r, ColorValue color) {
        g.setColor(TargetAWT.to(color));
        g.fill(r);
    }

    private static void drawChar(Graphics2D g, char c, float x, float y, Font font, ColorValue color) {
        g.setFont(font);
        g.setColor(TargetAWT.to(color));
        g.drawString(String.valueOf(c), x, y);
    }

    private static ColorValue getCaretColor(Editor editor) {
        ColorValue overriddenColor = editor.getCaretModel().getPrimaryCaret().getVisualAttributes().getColor();
        if (overriddenColor != null) {
            return overriddenColor;
        }
        ColorValue caretColor = editor.getColorsScheme().getColor(EditorColors.CARET_COLOR);
        return caretColor == null ? new LightDarkColorValue(new RGBColor(0, 0, 0), new RGBColor(255, 255, 255)) : caretColor;
    }

    private static void updateAttributes(DesktopEditorImpl editor, int offset, List<? extends TextAttributes> attributes) {
        List<RangeHighlighterEx> list1 = new ArrayList<>();
        List<RangeHighlighterEx> list2 = new ArrayList<>();

        Processor<RangeHighlighterEx> processor = highlighter -> {
            if (!highlighter.isValid()) {
                return true;
            }

            boolean isLineHighlighter = highlighter.getTargetArea() == HighlighterTargetArea.LINES_IN_RANGE;

            if (isLineHighlighter || highlighter.getStartOffset() < offset) {
                list1.add(highlighter);
            }

            if (isLineHighlighter || highlighter.getEndOffset() > offset || (highlighter.getEndOffset() == offset && (highlighter.isGreedyToRight()))) {
                list2.add(highlighter);
            }

            return true;
        };

        editor.getFilteredDocumentMarkupModel().processRangeHighlightersOverlappingWith(Math.max(0, offset - 1), offset, processor);
        editor.getMarkupModel().processRangeHighlightersOverlappingWith(Math.max(0, offset - 1), offset, processor);

        updateAttributes(editor, attributes.get(0), list1);
        updateAttributes(editor, attributes.get(1), list2);
    }

    // TODO Unify with consulo.ide.impl.idea.openapi.editor.impl.view.IterationState.setAttributes
    private static void updateAttributes(DesktopEditorImpl editor, TextAttributes attributes, List<? extends RangeHighlighterEx> highlighters) {
        EditorColorsScheme colorsScheme = editor.getColorsScheme();

        if (highlighters.size() > 1) {
            ContainerUtil.quickSort(highlighters, IterationState.createByLayerThenByAttributesComparator(colorsScheme));
        }

        TextAttributes syntax = attributes;
        TextAttributes caretRow = editor.getCaretModel().getTextAttributes();

        int size = highlighters.size();

        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < size; i++) {
            RangeHighlighterEx highlighter = highlighters.get(i);
            if (highlighter.getTextAttributes(colorsScheme) == TextAttributes.ERASE_MARKER) {
                syntax = null;
            }
        }

        List<TextAttributes> cachedAttributes = new ArrayList<>();

        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < size; i++) {
            RangeHighlighterEx highlighter = highlighters.get(i);

            if (caretRow != null && highlighter.getLayer() < HighlighterLayer.CARET_ROW) {
                cachedAttributes.add(caretRow);
                caretRow = null;
            }

            if (syntax != null && highlighter.getLayer() < HighlighterLayer.SYNTAX) {
                cachedAttributes.add(syntax);
                syntax = null;
            }

            TextAttributes textAttributes = highlighter.getTextAttributes(colorsScheme);
            if (textAttributes != null && textAttributes != TextAttributes.ERASE_MARKER) {
                cachedAttributes.add(textAttributes);
            }
        }

        if (caretRow != null) {
            cachedAttributes.add(caretRow);
        }
        if (syntax != null) {
            cachedAttributes.add(syntax);
        }

        ColorValue foreground = null;
        ColorValue background = null;
        ColorValue effect = null;
        EffectType effectType = null;
        int fontType = 0;

        //noinspection ForLoopReplaceableByForEach, Duplicates
        for (int i = 0; i < cachedAttributes.size(); i++) {
            TextAttributes attrs = cachedAttributes.get(i);

            if (foreground == null) {
                foreground = attrs.getForegroundColor();
            }

            if (background == null) {
                background = attrs.getBackgroundColor();
            }

            if (fontType == Font.PLAIN) {
                fontType = attrs.getFontType();
            }

            if (effect == null) {
                effect = attrs.getEffectColor();
                effectType = attrs.getEffectType();
            }
        }

        if (foreground == null) {
            foreground = editor.getForegroundColor();
        }
        if (background == null) {
            background = editor.getBackgroundColor();
        }
        if (effectType == null) {
            effectType = EffectType.BOXED;
        }
        TextAttributes defaultAttributes = colorsScheme.getAttributes(HighlighterColors.TEXT);
        if (fontType == Font.PLAIN) {
            fontType = defaultAttributes == null ? Font.PLAIN : defaultAttributes.getFontType();
        }

        attributes.setAttributes(foreground, background, effect, null, effectType, fontType);
    }

    private static void pause() {
        try {
            Thread.sleep(DEBUG_PAUSE_DURATION);
        }
        catch (InterruptedException e) {
            // ...
        }
    }
}
