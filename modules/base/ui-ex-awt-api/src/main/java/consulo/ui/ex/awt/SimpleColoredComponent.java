/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package consulo.ui.ex.awt;

import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.ui.ex.ColoredTextContainer;
import consulo.ui.ex.Gray;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.util.UISettingsUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.image.Image;
import consulo.util.collection.primitive.ints.IntIntMap;
import consulo.util.collection.primitive.ints.IntMaps;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.intellij.lang.annotations.JdkConstants;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleStateSet;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.CharacterIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * This is high performance Swing component which represents an icon
 * with a colored text. The text consists of fragments. Each
 * text fragment has its own color (foreground) and font style.
 *
 * @author Vladimir Kondratyev
 */
@SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext", "FieldAccessedSynchronizedAndUnsynchronized", "UnusedDeclaration"})
public class SimpleColoredComponent extends JComponent implements Accessible, ColoredTextContainer {
    private static final Logger LOG = Logger.getInstance(SimpleColoredComponent.class);

    public static final Color SHADOW_COLOR = new JBColor(new Color(250, 250, 250, 140), Gray._0.withAlpha(50));
    public static final Color STYLE_SEARCH_MATCH_BACKGROUND = SHADOW_COLOR; //api compatibility
    public static final int FRAGMENT_ICON = -2;

    private final List<String> myFragments;
    private final List<SimpleTextAttributes> myAttributes;
    private List<Object> myFragmentTags = null;
    private IntIntMap myFragmentAlignment;

    /**
     * Component's icon. It can be <code>null</code>.
     */
    private Image myIcon;
    /**
     * Internal padding
     */
    private Insets myIpad;
    /**
     * Gap between icon and text. It is used only if icon is defined.
     */
    protected int myIconTextGap;

    /**
     * Defines whether the focus border around the text extends to icon or not
     */
    private boolean myFocusBorderAroundIcon;

    private int myMainTextLastIndex = -1;

    private final IntIntMap myFragmentPadding;

    @JdkConstants.HorizontalAlignment
    private int myTextAlign = SwingConstants.LEFT;

    private boolean myIconOpaque = false;

    private boolean myAutoInvalidate = !(this instanceof TreeCellRenderer);

    private final AccessibleContext myContext = new MyAccessibleContext();

    private boolean myIconOnTheRight = false;
    private boolean myTransparentIconBackground;

    public SimpleColoredComponent() {
        myFragments = new ArrayList<String>(3);
        myAttributes = new ArrayList<SimpleTextAttributes>(3);
        myIpad = new JBInsets(1, 2, 1, 2);
        myIconTextGap = JBUI.scale(2);
        myFragmentPadding = IntMaps.newIntIntHashMap(10);
        myFragmentAlignment = IntMaps.newIntIntHashMap(10);
        setOpaque(true);

        updateUI();
    }

    @Override
    public void updateUI() {
        UISettingsUtil.setupComponentAntialiasing(this);
    }

    @Override
    @Nonnull
    public ColoredIterator iterator() {
        return new MyIterator();
    }

    public boolean isIconOnTheRight() {
        return myIconOnTheRight;
    }

    public void setIconOnTheRight(boolean iconOnTheRight) {
        myIconOnTheRight = iconOnTheRight;
    }

    /**
     * Appends string fragments to existing ones. Appended string
     * will have specified <code>attributes</code>.
     *
     * @param fragment   text fragment
     * @param attributes text attributes
     */
    @Override
    public final void append(@Nonnull final String fragment, @Nonnull final SimpleTextAttributes attributes) {
        append(fragment, attributes, myMainTextLastIndex < 0);
    }

    /**
     * Appends text fragment and sets it's end offset and alignment.
     * See SimpleColoredComponent#appendTextPadding for details
     *
     * @param fragment   text fragment
     * @param attributes text attributes
     * @param padding    end offset of the text
     * @param align      alignment between current offset and padding
     */
    public final void append(@Nonnull final String fragment, @Nonnull final SimpleTextAttributes attributes, int padding, @JdkConstants.HorizontalAlignment int align) {
        append(fragment, attributes, myMainTextLastIndex < 0);
        appendTextPadding(padding, align);
    }

    /**
     * Appends string fragments to existing ones. Appended string
     * will have specified <code>attributes</code>.
     *
     * @param fragment   text fragment
     * @param attributes text attributes
     * @param isMainText main text of not
     */
    public void append(@Nonnull final String fragment, @Nonnull final SimpleTextAttributes attributes, boolean isMainText) {
        _append(fragment, attributes, isMainText);
        revalidateAndRepaint();
    }

    private synchronized void _append(@Nonnull final String fragment, @Nonnull final SimpleTextAttributes attributes, boolean isMainText) {
        myFragments.add(fragment);
        myAttributes.add(attributes);
        if (isMainText) {
            myMainTextLastIndex = myFragments.size() - 1;
        }
    }

    protected void revalidateAndRepaint() {
        if (myAutoInvalidate) {
            revalidate();
        }

        repaint();
    }

    @Override
    public void append(@Nonnull final String fragment, @Nonnull final SimpleTextAttributes attributes, Object tag) {
        _append(fragment, attributes, tag);
        revalidateAndRepaint();
    }

    private synchronized void _append(String fragment, SimpleTextAttributes attributes, Object tag) {
        append(fragment, attributes);
        if (myFragmentTags == null) {
            myFragmentTags = new ArrayList<Object>();
        }
        while (myFragmentTags.size() < myFragments.size() - 1) {
            myFragmentTags.add(null);
        }
        myFragmentTags.add(tag);
    }

    @Deprecated
    /**
     * fragment width isn't a right name, it is actually a padding
     * @deprecated remove in IDEA 16
     */ public synchronized void appendFixedTextFragmentWidth(int width) {
        appendTextPadding(width);
    }

    public synchronized void appendTextPadding(int padding) {
        appendTextPadding(padding, SwingConstants.LEFT);
    }

    /**
     * @param padding end offset that will be set after drawing current text fragment
     * @param align   alignment of the current text fragment, if it is SwingConstants.RIGHT
     *                or SwingConstants.TRAILING then the text fragment will be aligned to the right at
     *                the padding, otherwise it will be aligned to the left
     */
    public synchronized void appendTextPadding(int padding, @JdkConstants.HorizontalAlignment int align) {
        final int alignIndex = myFragments.size() - 1;
        myFragmentPadding.putInt(alignIndex, padding);
        myFragmentAlignment.putInt(alignIndex, align);
    }

    public void setTextAlign(@JdkConstants.HorizontalAlignment int align) {
        myTextAlign = align;
    }

    /**
     * Clear all special attributes of <code>SimpleColoredComponent</code>.
     * They are icon, text fragments and their attributes, "paint focus border".
     */
    public void clear() {
        _clear();
        revalidateAndRepaint();
    }

    private synchronized void _clear() {
        myIcon = null;
        myFragments.clear();
        myAttributes.clear();
        myFragmentTags = null;
        myMainTextLastIndex = -1;
        myFragmentPadding.clear();
    }

    /**
     * @return component's icon. This method returns <code>null</code>
     * if there is no icon.
     */
    public final Image getIcon() {
        return myIcon;
    }

    /**
     * Sets a new component icon
     *
     * @param icon icon
     */
    @Override
    public final void setIcon(@Nullable final Image icon) {
        myIcon = icon;
        revalidateAndRepaint();
    }

    /**
     * @return "leave" (internal) internal paddings of the component
     */
    @Nonnull
    public Insets getIpad() {
        return myIpad;
    }

    /**
     * Sets specified internal paddings
     *
     * @param ipad insets
     */
    public void setIpad(@Nonnull Insets ipad) {
        myIpad = ipad;

        revalidateAndRepaint();
    }

    /**
     * @return gap between icon and text
     */
    public int getIconTextGap() {
        return myIconTextGap;
    }

    /**
     * Sets a new gap between icon and text
     *
     * @param iconTextGap the gap between text and icon
     * @throws java.lang.IllegalArgumentException if the <code>iconTextGap</code>
     *                                            has a negative value
     */
    public void setIconTextGap(final int iconTextGap) {
        if (iconTextGap < 0) {
            throw new IllegalArgumentException("wrong iconTextGap: " + iconTextGap);
        }
        myIconTextGap = iconTextGap;

        revalidateAndRepaint();
    }

    @Deprecated
    public Border getMyBorder() {
        return null;
    }

    @Deprecated
    public void setMyBorder(@Nullable Border border) {
    }

    /**
     * Sets whether focus border is painted or not
     *
     * @param paintFocusBorder <code>true</code> or <code>false</code>
     */
    @Deprecated
    protected final void setPaintFocusBorder(final boolean paintFocusBorder) {
    }

    /**
     * Sets whether focus border extends to icon or not. If so then
     * component also extends the selection.
     *
     * @param focusBorderAroundIcon <code>true</code> or <code>false</code>
     */
    protected final void setFocusBorderAroundIcon(final boolean focusBorderAroundIcon) {
        myFocusBorderAroundIcon = focusBorderAroundIcon;

        repaint();
    }

    public boolean isIconOpaque() {
        return myIconOpaque;
    }

    public void setIconOpaque(final boolean iconOpaque) {
        myIconOpaque = iconOpaque;

        repaint();
    }

    @Override
    @Nonnull
    public Dimension getPreferredSize() {
        return computePreferredSize(false);
    }

    @Override
    @Nonnull
    public Dimension getMinimumSize() {
        return computePreferredSize(false);
    }

    @Nullable
    public synchronized Object getFragmentTag(int index) {
        if (myFragmentTags != null && index < myFragmentTags.size()) {
            return myFragmentTags.get(index);
        }
        return null;
    }

    @Nonnull
    public final synchronized Dimension computePreferredSize(final boolean mainTextOnly) {
        // Calculate width
        int width = myIpad.left;

        if (myIcon != null) {
            width += myIcon.getWidth() + myIconTextGap;
        }

        final Insets borderInsets = JBUI.emptyInsets();
        width += borderInsets.left;

        Font font = getFont();
        if (font == null) {
            font = UIUtil.getLabelFont();
        }

        LOG.assertTrue(font != null);

        width += computeTextWidth(font, mainTextOnly);
        width += myIpad.right + borderInsets.right;

        // Calculate height
        int height = myIpad.top + myIpad.bottom;

        final FontMetrics metrics = getFontMetrics(font);
        int textHeight = metrics.getHeight();
        textHeight += borderInsets.top + borderInsets.bottom;

        if (myIcon != null) {
            height += Math.max(myIcon.getHeight(), textHeight);
        }
        else {
            height += textHeight;
        }

        // Take into account that the component itself can have a border
        final Insets insets = getInsets();
        if (insets != null) {
            width += insets.left + insets.right;
            height += insets.top + insets.bottom;
        }

        return new Dimension(width, height);
    }

    private int computeTextWidth(@Nonnull Font font, final boolean mainTextOnly) {
        int result = 0;
        int baseSize = font.getSize();
        boolean wasSmaller = false;
        for (int i = 0; i < myAttributes.size(); i++) {
            SimpleTextAttributes attributes = myAttributes.get(i);
            boolean isSmaller = attributes.isSmaller();
            if (font.getStyle() != attributes.getFontStyle() || isSmaller != wasSmaller) { // derive font only if it is necessary
                font = font.deriveFont(attributes.getFontStyle(), isSmaller ? UIUtil.getFontSize(UIUtil.FontSize.SMALL) : baseSize);
            }
            wasSmaller = isSmaller;

            result += computeStringWidth(myFragments.get(i), font);

            final int fixedWidth = myFragmentPadding.getInt(i);
            if (fixedWidth > 0 && result < fixedWidth) {
                result = fixedWidth;
            }
            if (mainTextOnly && myMainTextLastIndex >= 0 && i == myMainTextLastIndex) {
                break;
            }
        }
        return result;
    }

    private void doDrawString(Graphics2D g, String text, int x, int y) {
        Font font = g.getFont();
        if (needFontFallback(font, text)) {
            TextLayout layout = createTextLayout(text, font, g.getFontRenderContext());
            if (layout != null) {
                layout.draw(g, x, y);
            }
        }
        else {
            g.drawString(text, x, y);
        }
    }

    private int computeStringWidth(String text, Font font) {
        if (needFontFallback(font, text)) {
            TextLayout layout = createTextLayout(text, font, getFontMetrics(font).getFontRenderContext());
            return layout != null ? (int) layout.getAdvance() : 0;
        }
        else {
            return getFontMetrics(font).stringWidth(text);
        }
    }

    @Nullable
    private TextLayout createTextLayout(String text, Font basefont, FontRenderContext fontRenderContext) {
        if (StringUtil.isEmpty(text)) {
            return null;
        }
        AttributedString string = new AttributedString(text);
        int start = 0;
        int end = text.length();
        AttributedCharacterIterator it = string.getIterator(new AttributedCharacterIterator.Attribute[0], start, end);
        Font currentFont = basefont;
        int currentIndex = start;
        for (char c = it.first(); c != CharacterIterator.DONE; c = it.next()) {
            Font font = basefont;
            if (!font.canDisplay(c)) {
                font = SuitableFontProvider.getInstance().getFontAbleToDisplay(c, basefont.getSize(), basefont.getStyle(), basefont.getFamily());
            }
            int i = it.getIndex();
            if (!Comparing.equal(currentFont, font)) {
                if (i > currentIndex) {
                    string.addAttribute(TextAttribute.FONT, currentFont, currentIndex, i);
                }
                currentFont = font;
                currentIndex = i;
            }
        }
        if (currentIndex < end) {
            string.addAttribute(TextAttribute.FONT, currentFont, currentIndex, end);
        }
        return new TextLayout(string.getIterator(), fontRenderContext);
    }

    private boolean needFontFallback(Font font, String text) {
        return font.canDisplayUpTo(text) != -1 && text.indexOf(CharacterIterator.DONE) == -1; // see IDEA-137517, TextLayout does not support this character
    }

    /**
     * Returns the index of text fragment at the specified X offset.
     *
     * @param x the offset
     * @return the index of the fragment, {@link #FRAGMENT_ICON} if the icon is at the offset, or -1 if nothing is there.
     */
    public int findFragmentAt(int x) {
        int curX = myIpad.left;
        if (myIcon != null && !myIconOnTheRight) {
            final int iconRight = myIcon.getWidth() + myIconTextGap;
            if (x < iconRight) {
                return FRAGMENT_ICON;
            }
            curX += iconRight;
        }

        Font font = getFont();
        if (font == null) {
            font = UIUtil.getLabelFont();
        }

        int baseSize = font.getSize();
        boolean wasSmaller = false;
        for (int i = 0; i < myAttributes.size(); i++) {
            SimpleTextAttributes attributes = myAttributes.get(i);
            boolean isSmaller = attributes.isSmaller();
            if (font.getStyle() != attributes.getFontStyle() || isSmaller != wasSmaller) { // derive font only if it is necessary
                font = font.deriveFont(attributes.getFontStyle(), isSmaller ? UIUtil.getFontSize(UIUtil.FontSize.SMALL) : baseSize);
            }
            wasSmaller = isSmaller;

            final int curWidth = computeStringWidth(myFragments.get(i), font);
            if (x >= curX && x < curX + curWidth) {
                return i;
            }
            curX += curWidth;
            final int fragmentPadding = myFragmentPadding.getInt(i);
            if (fragmentPadding > 0 && curX < fragmentPadding) {
                curX = fragmentPadding;
            }
        }

        if (myIcon != null && myIconOnTheRight) {
            curX += myIconTextGap;
            if (x >= curX && x < curX + myIcon.getWidth()) {
                return FRAGMENT_ICON;
            }
        }
        return -1;
    }

    @Nullable
    public Object getFragmentTagAt(int x) {
        int index = findFragmentAt(x);
        return index < 0 ? null : getFragmentTag(index);
    }

    @Nonnull
    protected JLabel formatToLabel(@Nonnull JLabel label) {
        label.setIcon(TargetAWT.to(myIcon));

        if (!myFragments.isEmpty()) {
            final StringBuilder text = new StringBuilder();
            text.append("<html><body style=\"white-space:nowrap\">");

            for (int i = 0; i < myFragments.size(); i++) {
                final String fragment = myFragments.get(i);
                final SimpleTextAttributes attributes = myAttributes.get(i);
                final Object tag = getFragmentTag(i);
                if (tag instanceof BrowserLauncherTag) {
                    formatLink(text, fragment, attributes, ((BrowserLauncherTag) tag).myUrl);
                }
                else {
                    formatText(text, fragment, attributes);
                }
            }

            text.append("</body></html>");
            label.setText(text.toString());
        }

        return label;
    }

    static void formatText(@Nonnull StringBuilder builder, @Nonnull String fragment, @Nonnull SimpleTextAttributes attributes) {
        if (!fragment.isEmpty()) {
            builder.append("<span");
            formatStyle(builder, attributes);
            builder.append('>').append(convertFragment(fragment)).append("</span>");
        }
    }

    static void formatLink(@Nonnull StringBuilder builder, @Nonnull String fragment, @Nonnull SimpleTextAttributes attributes, @Nonnull String url) {
        if (!fragment.isEmpty()) {
            builder.append("<a href=\"").append(StringUtil.replace(url, "\"", "%22")).append("\"");
            formatStyle(builder, attributes);
            builder.append('>').append(convertFragment(fragment)).append("</a>");
        }
    }

    private static String convertFragment(String fragment) {
        return StringUtil.escapeXml(fragment).replaceAll("\\\\n", "<br>");
    }

    private static void formatStyle(final StringBuilder builder, final SimpleTextAttributes attributes) {
        final Color fgColor = attributes.getFgColor();
        final Color bgColor = attributes.getBgColor();
        final int style = attributes.getStyle();

        final int pos = builder.length();
        if (fgColor != null) {
            builder.append("color:#").append(Integer.toString(fgColor.getRGB() & 0xFFFFFF, 16)).append(';');
        }
        if (bgColor != null) {
            builder.append("background-color:#").append(Integer.toString(bgColor.getRGB() & 0xFFFFFF, 16)).append(';');
        }
        if ((style & SimpleTextAttributes.STYLE_BOLD) != 0) {
            builder.append("font-weight:bold;");
        }
        if ((style & SimpleTextAttributes.STYLE_ITALIC) != 0) {
            builder.append("font-style:italic;");
        }
        if ((style & SimpleTextAttributes.STYLE_UNDERLINE) != 0) {
            builder.append("text-decoration:underline;");
        }
        else if ((style & SimpleTextAttributes.STYLE_STRIKEOUT) != 0) {
            builder.append("text-decoration:line-through;");
        }
        if (builder.length() > pos) {
            builder.insert(pos, " style=\"");
            builder.append('"');
        }
    }

    @Override
    protected void paintComponent(final Graphics g) {
        try {
            doPaint((Graphics2D) g);
        }
        catch (RuntimeException e) {
            LOG.error(logSwingPath(), e);
            throw e;
        }
    }

    protected void doPaint(final Graphics2D g) {
        int offset = 0;
        final Image icon = myIcon; // guard against concurrent modification (IDEADEV-12635)
        if (icon != null && !myIconOnTheRight) {
            doPaintIcon(g, icon, 0);
            offset += myIpad.left + icon.getWidth() + myIconTextGap;
        }

        doPaintTextBackground(g, offset);
        offset = doPaintText(g, offset, myFocusBorderAroundIcon || icon == null);
        if (icon != null && myIconOnTheRight) {
            doPaintIcon(g, icon, offset);
        }
    }

    private void doPaintTextBackground(Graphics2D g, int offset) {
        if (isOpaque() || shouldDrawBackground()) {
            paintBackground(g, offset, getWidth() - offset, getHeight());
        }
    }

    protected void paintBackground(Graphics2D g, int x, int width, int height) {
        g.setColor(getBackground());
        g.fillRect(x, 0, width, height);
    }

    protected void doPaintIcon(@Nonnull Graphics2D g, @Nonnull Image icon, int offset) {
        final Container parent = getParent();
        Color iconBackgroundColor = null;
        if ((isOpaque() || isIconOpaque()) && !isTransparentIconBackground()) {
            iconBackgroundColor = getBackground();
        }

        if (iconBackgroundColor != null) {
            g.setColor(iconBackgroundColor);
            g.fillRect(offset, 0, icon.getWidth() + myIpad.left + myIconTextGap, getHeight());
        }

        paintIcon(g, icon, offset + myIpad.left);
    }

    protected int doPaintText(Graphics2D g, int offset, boolean focusAroundIcon) {
        // If there is no icon, then we have to add left internal padding
        if (offset == 0) {
            offset = myIpad.left;
        }

        int textStart = offset;

        final List<Object[]> searchMatches = new ArrayList<Object[]>();

        applyAdditionalHints(g);
        final Font ownFont = getFont();
        if (ownFont != null) {
            offset += computeTextAlignShift(ownFont);
        }
        int baseSize = ownFont != null ? ownFont.getSize() : g.getFont().getSize();
        boolean wasSmaller = false;
        for (int i = 0; i < myFragments.size(); i++) {
            final SimpleTextAttributes attributes = myAttributes.get(i);

            Font font = g.getFont();
            boolean isSmaller = attributes.isSmaller();
            if (font.getStyle() != attributes.getFontStyle() || isSmaller != wasSmaller) { // derive font only if it is necessary
                font = font.deriveFont(attributes.getFontStyle(), isSmaller ? UIUtil.getFontSize(UIUtil.FontSize.SMALL) : baseSize);
            }
            wasSmaller = isSmaller;

            g.setFont(font);
            final FontMetrics metrics = g.getFontMetrics(font);

            final String fragment = myFragments.get(i);
            final int fragmentWidth = computeStringWidth(fragment, font);

            final int fragmentPadding = myFragmentPadding.getInt(i);

            final Color bgColor = attributes.isSearchMatch() ? null : attributes.getBgColor();
            if ((attributes.isOpaque() || isOpaque()) && bgColor != null) {
                g.setColor(bgColor);
                g.fillRect(offset, 0, fragmentWidth, getHeight());
            }

            Color color = attributes.getFgColor();
            if (color == null) { // in case if color is not defined we have to get foreground color from Swing hierarchy
                color = getForeground();
            }
            if (!isEnabled()) {
                color = UIUtil.getInactiveTextColor();
            }
            g.setColor(color);

            final int textBaseline = getTextBaseLine(metrics, getHeight()) + getTextOffsetY();

            final int fragmentAlignment = myFragmentAlignment.getInt(i);

            final int endOffset;
            if (fragmentPadding > 0 && fragmentPadding > fragmentWidth) {
                endOffset = fragmentPadding;
                if (fragmentAlignment == SwingConstants.RIGHT || fragmentAlignment == SwingConstants.TRAILING) {
                    offset = (fragmentPadding - fragmentWidth);
                }
            }
            else {
                endOffset = offset + fragmentWidth;
            }

            if (!attributes.isSearchMatch()) {
                g.setColor(color);
                doDrawString(g, fragment, offset, textBaseline);
            }

            // for some reason strokeState here may be incorrect, resetting the stroke helps
            g.setStroke(g.getStroke());

            // 1. Strikeout effect
            if (attributes.isStrikeout()) {
                final int strikeOutAt = textBaseline + (metrics.getDescent() - metrics.getAscent()) / 2;
                UIUtil.drawLine(g, offset, strikeOutAt, offset + fragmentWidth, strikeOutAt);
            }
            // 2. Waved effect
            if (attributes.isWaved()) {
                if (attributes.getWaveColor() != null) {
                    g.setColor(attributes.getWaveColor());
                }
                UIUtil.drawWave(g, new Rectangle(offset, textBaseline + 1, fragmentWidth, Math.max(2, metrics.getDescent())));
            }
            // 3. Underline
            if (attributes.isUnderline()) {
                final int underlineAt = textBaseline + 1;
                UIUtil.drawLine(g, offset, underlineAt, offset + fragmentWidth, underlineAt);
            }
            // 4. Bold Dotted Line
            if (attributes.isBoldDottedLine()) {
                final int dottedAt = Platform.current().os().isMac() ? textBaseline : textBaseline + 1;
                final Color lineColor = attributes.getWaveColor();
                UIUtil.drawBoldDottedLine(g, offset, offset + fragmentWidth, dottedAt, bgColor, lineColor, isOpaque());
            }

            if (attributes.isSearchMatch()) {
                searchMatches.add(new Object[]{offset, offset + fragmentWidth, textBaseline, fragment, g.getFont()});
            }

            offset = endOffset;
        }

        // draw search matches after all
        for (final Object[] info : searchMatches) {
            UIUtil.drawSearchMatch(g, (Integer) info[0], (Integer) info[1], getHeight());
            g.setFont((Font) info[4]);

            g.setColor(new JBColor(Gray._50, Gray._0));
            g.drawString((String) info[3], (Integer) info[0], (Integer) info[2]);
        }
        return offset;
    }

    protected int getTextOffsetY() {
        return 0;
    }

    private int computeTextAlignShift(@Nonnull Font font) {
        if (myTextAlign == SwingConstants.LEFT || myTextAlign == SwingConstants.LEADING) {
            return 0;
        }

        int componentWidth = getSize().width;
        int excessiveWidth = componentWidth - computePreferredSize(false).width;
        if (excessiveWidth <= 0) {
            return 0;
        }

        int textWidth = computeTextWidth(font, false);
        if (myTextAlign == SwingConstants.CENTER) {
            return excessiveWidth / 2;
        }
        else if (myTextAlign == SwingConstants.RIGHT || myTextAlign == SwingConstants.TRAILING) {
            return excessiveWidth;
        }
        return 0;
    }

    protected boolean shouldDrawBackground() {
        return false;
    }

    protected void paintIcon(@Nonnull Graphics g, @Nonnull Image icon, int offsetX) {
        TargetAWT.to(icon).paintIcon(this, g, offsetX, (getHeight() - icon.getHeight()) / 2);
    }

    protected void applyAdditionalHints(@Nonnull Graphics2D g) {
        UISettingsUtil.setupAntialiasing(g);
    }

    @Override
    public int getBaseline(int width, int height) {
        super.getBaseline(width, height);
        return getTextBaseLine(getFontMetrics(getFont()), height);
    }

    public boolean isTransparentIconBackground() {
        return myTransparentIconBackground;
    }

    public void setTransparentIconBackground(boolean transparentIconBackground) {
        myTransparentIconBackground = transparentIconBackground;
    }

    public static int getTextBaseLine(@Nonnull FontMetrics metrics, final int height) {
        return (height - metrics.getHeight()) / 2 + metrics.getAscent();
    }

    @Nonnull
    private String logSwingPath() {
        //noinspection HardCodedStringLiteral
        final StringBuilder buffer = new StringBuilder("Components hierarchy:\n");
        for (Container c = this; c != null; c = c.getParent()) {
            buffer.append('\n');
            buffer.append(c);
        }
        return buffer.toString();
    }

    @Override
    @Nonnull
    public CharSequence getCharSequence(boolean mainOnly) {
        List<String> fragments = mainOnly && myMainTextLastIndex > -1 && myMainTextLastIndex + 1 < myFragments.size() ? myFragments.subList(0, myMainTextLastIndex + 1) : myFragments;
        return StringUtil.join(fragments, "");
    }

    @Override
    public String toString() {
        return getCharSequence(false).toString();
    }

    public void change(@Nonnull Runnable runnable, boolean autoInvalidate) {
        boolean old = myAutoInvalidate;
        myAutoInvalidate = autoInvalidate;
        try {
            runnable.run();
        }
        finally {
            myAutoInvalidate = old;
        }
    }

    @Override
    public AccessibleContext getAccessibleContext() {
        return myContext;
    }

    private static class MyAccessibleContext extends AccessibleContext {
        @Override
        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.AWT_COMPONENT;
        }

        @Override
        public AccessibleStateSet getAccessibleStateSet() {
            return new AccessibleStateSet();
        }

        @Override
        public int getAccessibleIndexInParent() {
            return 0;
        }

        @Override
        public int getAccessibleChildrenCount() {
            return 0;
        }

        @Nullable
        @Override
        public Accessible getAccessibleChild(int i) {
            return null;
        }

        @Override
        public Locale getLocale() throws IllegalComponentStateException {
            return Locale.getDefault();
        }
    }

    public static class BrowserLauncherTag implements Runnable {
        private final String myUrl;

        public BrowserLauncherTag(@Nonnull String url) {
            myUrl = url;
        }

        @Override
        public void run() {
            Platform.current().openInBrowser(myUrl);
        }
    }

    private class MyIterator implements ColoredIterator {
        int myIndex = -1;
        int myOffset;
        int myEndOffset;

        @Override
        public int getOffset() {
            return myOffset;
        }

        @Override
        public int getEndOffset() {
            return myEndOffset;
        }

        @Nonnull
        @Override
        public String getFragment() {
            return myFragments.get(myIndex);
        }

        @Nonnull
        @Override
        public SimpleTextAttributes getTextAttributes() {
            return myAttributes.get(myIndex);
        }

        @Override
        public int split(int offset, @Nonnull SimpleTextAttributes attributes) {
            if (offset < 0 || offset > myEndOffset - myOffset) {
                throw new IllegalArgumentException(offset + " is not within [0, " + (myEndOffset - myOffset) + "]");
            }
            if (offset == myEndOffset - myOffset) {   // replace
                myAttributes.set(myIndex, attributes);
            }
            else if (offset > 0) {   // split
                String text = getFragment();
                myFragments.set(myIndex, text.substring(0, offset));
                myAttributes.add(myIndex, attributes);
                myFragments.add(myIndex + 1, text.substring(offset));
                if (myFragmentTags != null && myFragmentTags.size() > myIndex) {
                    myFragmentTags.add(myIndex, myFragments.get(myIndex));
                }
                myIndex++;
            }
            myOffset += offset;
            return myOffset;
        }

        @Override
        public boolean hasNext() {
            return myIndex + 1 < myFragments.size();
        }

        @Override
        public String next() {
            myIndex++;
            myOffset = myEndOffset;
            String text = getFragment();
            myEndOffset += text.length();
            return text;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    protected class AccessibleSimpleColoredComponent extends JComponent.AccessibleJComponent {
        @Override
        public String getAccessibleName() {
            return getCharSequence(false).toString();
        }

        @Override
        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.LABEL;
        }
    }
}
