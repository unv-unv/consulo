// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ui.ex.awt;

import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.ui.ex.awt.internal.ScrollSettings;
import consulo.ui.ex.awt.scroll.LatchingScroll;
import consulo.util.collection.ArrayUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.reflect.ReflectionUtil;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.plaf.ScrollBarUI;
import javax.swing.plaf.ScrollPaneUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.MouseEvent;

import static consulo.ui.ex.awt.JBUI.emptyInsets;

public class JBScrollPane extends JScrollPane {

    /**
     * Supposed to be used as a client property key for scrollbar and indicates if this scrollbar should be ignored
     * when insets for {@code JScrollPane's} content are being calculated.
     * <p>
     * Without this key scrollbar's width is included to content insets when content is {@code JList}. As a result list items cannot intersect with
     * scrollbar.
     * <p>
     * Please use as a marker for scrollbars, that should be transparent and shown over content.
     *
     * @see UIUtil#putClientProperty(JComponent, Key, Object)
     */
    public static final Key<Boolean> IGNORE_SCROLLBAR_IN_INSETS = Key.create("IGNORE_SCROLLBAR_IN_INSETS");

    /**
     * When set to {@link Boolean#TRUE} for component then latching will be ignored.
     *
     * @see LatchingScroll
     * @see UIUtil#putClientProperty(JComponent, Key, Object)
     */
    public static final Key<Boolean> IGNORE_SCROLL_LATCHING = Key.create("IGNORE_SCROLL_LATCHING");

    private static final Logger LOG = Logger.getInstance(JBScrollPane.class);

    private int myViewportBorderWidth = -1;

    public JBScrollPane(int viewportWidth) {
        init(false);
        myViewportBorderWidth = viewportWidth;
        updateViewportBorder();
    }

    public JBScrollPane() {
        init();
    }

    public JBScrollPane(Component view) {
        super(view);
        init();
    }

    public JBScrollPane(int vsbPolicy, int hsbPolicy) {
        super(vsbPolicy, hsbPolicy);
        init();
    }

    public JBScrollPane(Component view, int vsbPolicy, int hsbPolicy) {
        super(view, vsbPolicy, hsbPolicy);
        init();
    }

    private void init() {
        init(true);
    }

    private void init(boolean setupCorners) {
        setLayout(new Layout());

        if (setupCorners) {
            setupCorners();
        }
    }

    protected void setupCorners() {
        setBorder(IdeBorderFactory.createBorder());
        setCorner(UPPER_RIGHT_CORNER, new Corner());
        setCorner(UPPER_LEFT_CORNER, new Corner());
        setCorner(LOWER_RIGHT_CORNER, new Corner());
        setCorner(LOWER_LEFT_CORNER, new Corner());
    }

    @Override
    public void setUI(ScrollPaneUI ui) {
        super.setUI(ui);
        updateViewportBorder();
    }

    private void updateViewportBorder() {
        if (getViewportBorder() instanceof ViewportBorder) {
            setViewportBorder(new ViewportBorder(myViewportBorderWidth >= 0 ? myViewportBorderWidth : 1));
        }
    }

    @Override
    public JScrollBar createVerticalScrollBar() {
        return new JBScrollBar(Adjustable.VERTICAL);
    }

    @Nonnull
    @Override
    public JScrollBar createHorizontalScrollBar() {
        return new JBScrollBar(Adjustable.HORIZONTAL);
    }

    @Override
    protected JViewport createViewport() {
        return new JBViewport();
    }

    public static boolean canBePreprocessed(@Nonnull MouseEvent e, @Nonnull JScrollBar bar) {
        if (e.getID() == MouseEvent.MOUSE_MOVED || e.getID() == MouseEvent.MOUSE_PRESSED) {
            ScrollBarUI ui = bar.getUI();
            if (ui instanceof BasicScrollBarUI) {
                BasicScrollBarUI bui = (BasicScrollBarUI) ui;
                try {
                    Rectangle rect = (Rectangle) ReflectionUtil.getDeclaredMethod(BasicScrollBarUI.class, "getThumbBounds", ArrayUtil.EMPTY_CLASS_ARRAY).invoke(bui);
                    Point point = SwingUtilities.convertPoint(e.getComponent(), e.getX(), e.getY(), bar);
                    return !rect.contains(point);
                }
                catch (Exception e1) {
                    return true;
                }
            }
        }
        return true;
    }

    private static class Corner extends JPanel {
        Corner() {
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    private static class ViewportBorder extends LineBorder {
        ViewportBorder(int thickness) {
            super(null, thickness);
        }
    }

    /**
     * These client properties modify a scroll pane layout.
     * Use the class object as a property key.
     *
     * @see #putClientProperty(Object, Object)
     */
    public enum Flip {
        NONE,
        VERTICAL,
        HORIZONTAL,
        BOTH
    }

    /**
     * These client properties show a component position on a scroll pane.
     * It is set by internal layout manager of the scroll pane.
     */
    public enum Alignment {
        TOP,
        LEFT,
        RIGHT,
        BOTTOM;

        public static Alignment get(JComponent component) {
            if (component != null) {
                Object property = component.getClientProperty(Alignment.class);
                if (property instanceof Alignment) {
                    return (Alignment) property;
                }

                Container parent = component.getParent();
                if (parent instanceof JScrollPane) {
                    JScrollPane pane = (JScrollPane) parent;
                    if (component == pane.getColumnHeader()) {
                        return TOP;
                    }
                    if (component == pane.getHorizontalScrollBar()) {
                        return BOTTOM;
                    }
                    boolean ltr = pane.getComponentOrientation().isLeftToRight();
                    if (component == pane.getVerticalScrollBar()) {
                        return ltr ? RIGHT : LEFT;
                    }
                    if (component == pane.getRowHeader()) {
                        return ltr ? LEFT : RIGHT;
                    }
                }
                // assume alignment for a scroll bar,
                // which is not contained in a scroll pane
                if (component instanceof JScrollBar) {
                    JScrollBar bar = (JScrollBar) component;
                    switch (bar.getOrientation()) {
                        case Adjustable.HORIZONTAL:
                            return BOTTOM;
                        case Adjustable.VERTICAL:
                            return bar.getComponentOrientation().isLeftToRight() ? RIGHT : LEFT;
                    }
                }
            }
            return null;
        }
    }

    /**
     * ScrollPaneLayout implementation that supports
     * ScrollBar flipping and non-opaque ScrollBars.
     */
    public static class Layout extends ScrollPaneLayout {
        private static final Insets EMPTY_INSETS = emptyInsets();

        @Override
        public void syncWithScrollPane(JScrollPane sp) {
            super.syncWithScrollPane(sp);
        }

        @Override
        public void layoutContainer(Container parent) {
            JScrollPane pane = (JScrollPane) parent;
            // Calculate inner bounds of the scroll pane
            Rectangle bounds = new Rectangle(pane.getWidth(), pane.getHeight());
            JBInsets.removeFrom(bounds, pane.getInsets());
            // Determine positions of scroll bars on the scroll pane
            Object property = pane.getClientProperty(Flip.class);
            Flip flip = property instanceof Flip ? (Flip) property : Flip.NONE;
            boolean hsbOnTop = flip == Flip.BOTH || flip == Flip.VERTICAL;
            boolean vsbOnLeft = pane.getComponentOrientation().isLeftToRight() ? flip == Flip.BOTH || flip == Flip.HORIZONTAL : flip == Flip.NONE || flip == Flip.VERTICAL;
            // If there's a visible row header remove the space it needs.
            // The row header is treated as if it were fixed width, arbitrary height.
            Rectangle rowHeadBounds = new Rectangle(bounds.x, 0, 0, 0);
            if (rowHead != null && rowHead.isVisible()) {
                rowHeadBounds.width = min(bounds.width, rowHead.getPreferredSize().width);
                bounds.width -= rowHeadBounds.width;
                if (vsbOnLeft) {
                    rowHeadBounds.x += bounds.width;
                }
                else {
                    bounds.x += rowHeadBounds.width;
                }
            }
            // If there's a visible column header remove the space it needs.
            // The column header is treated as if it were fixed height, arbitrary width.
            Rectangle colHeadBounds = new Rectangle(0, bounds.y, 0, 0);
            if (colHead != null && colHead.isVisible()) {
                colHeadBounds.height = min(bounds.height, colHead.getPreferredSize().height);
                bounds.height -= colHeadBounds.height;
                if (hsbOnTop) {
                    colHeadBounds.y += bounds.height;
                }
                else {
                    bounds.y += colHeadBounds.height;
                }
            }
            // If there's a JScrollPane.viewportBorder, remove the space it occupies
            Border border = pane.getViewportBorder();
            Insets insets = border == null ? null : border.getBorderInsets(parent);
            JBInsets.removeFrom(bounds, insets);
            if (insets == null) {
                insets = EMPTY_INSETS;
            }
            // At this point:
            // colHeadBounds is correct except for its width and x
            // rowHeadBounds is correct except for its height and y
            // bounds - the space available for the viewport and scroll bars
            // Once we're through computing the dimensions of these three parts
            // we can go back and set the bounds for the corners and the dimensions of
            // colHeadBounds.x, colHeadBounds.width, rowHeadBounds.y, rowHeadBounds.height.

            // Don't bother checking the Scrollable methods if there is no room for the viewport,
            // we aren't going to show any scroll bars in this case anyway.
            boolean isEmpty = bounds.width < 0 || bounds.height < 0;

            Component view = viewport == null ? null : viewport.getView();
            Dimension viewPreferredSize = view == null ? new Dimension() : view.getPreferredSize();
            if (view instanceof JComponent && !view.isPreferredSizeSet()) {
                JBInsets.removeFrom(viewPreferredSize, JBViewport.getViewInsets((JComponent) view));
            }
            Dimension viewportExtentSize = viewport == null ? new Dimension() : viewport.toViewCoordinates(bounds.getSize());

            // workaround for installed JBViewport.ViewBorder:
            // do not hide scroll bars if view is not aligned
            Point viewLocation = new Point();
            if (view != null) {
                viewLocation = view.getLocation(viewLocation);
            }
            // If there's a vertical scroll bar and we need one, allocate space for it.
            // A vertical scroll bar is considered to be fixed width, arbitrary height.
            boolean vsbOpaque = false;
            boolean vsbNeeded = false;
            int vsbPolicy = pane.getVerticalScrollBarPolicy();
            if (!isEmpty && vsbPolicy != VERTICAL_SCROLLBAR_NEVER) {
                vsbNeeded = vsbPolicy == VERTICAL_SCROLLBAR_ALWAYS || isVerticalScrollBarNeeded(view, viewLocation, viewPreferredSize, viewportExtentSize);
            }
            Rectangle vsbBounds = new Rectangle(0, bounds.y - insets.top, 0, 0);
            if (vsb != null) {
                if (isAlwaysOpaque(view)) {
                    vsb.setOpaque(true);
                }
                vsbOpaque = vsb.isOpaque();
                if (vsbNeeded) {
                    adjustForVSB(bounds, insets, vsbBounds, vsbOpaque, vsbOnLeft);
                    if (vsbOpaque && viewport != null) {
                        viewportExtentSize = viewport.toViewCoordinates(bounds.getSize());
                    }
                }
            }
            // If there's a horizontal scroll bar and we need one, allocate space for it.
            // A horizontal scroll bar is considered to be fixed height, arbitrary width.
            boolean hsbOpaque = false;
            boolean hsbNeeded = false;
            int hsbPolicy = pane.getHorizontalScrollBarPolicy();
            if (!isEmpty && hsbPolicy != HORIZONTAL_SCROLLBAR_NEVER) {
                hsbNeeded = hsbPolicy == HORIZONTAL_SCROLLBAR_ALWAYS || isHorizontalScrollBarNeeded(view, viewLocation, viewPreferredSize, viewportExtentSize);
            }
            Rectangle hsbBounds = new Rectangle(bounds.x - insets.left, 0, 0, 0);
            if (hsb != null) {
                if (isAlwaysOpaque(view)) {
                    hsb.setOpaque(true);
                }
                hsbOpaque = hsb.isOpaque();
                if (hsbNeeded) {
                    adjustForHSB(bounds, insets, hsbBounds, hsbOpaque, hsbOnTop);
                    // If we added the horizontal scrollbar and reduced the vertical space
                    // we may have to add the vertical scrollbar, if that hasn't been done so already.
                    if (vsb != null && !vsbNeeded && vsbPolicy != VERTICAL_SCROLLBAR_NEVER) {
                        if (!hsbOpaque) {
                            viewPreferredSize.height += hsbBounds.height;
                        }
                        else if (viewport != null) {
                            viewportExtentSize = viewport.toViewCoordinates(bounds.getSize());
                        }
                        vsbNeeded = isScrollBarNeeded(viewLocation.y, viewPreferredSize.height, viewportExtentSize.height);
                        if (vsbNeeded) {
                            adjustForVSB(bounds, insets, vsbBounds, vsbOpaque, vsbOnLeft);
                        }
                    }
                }
            }
            // Set the size of the viewport first, and then recheck the Scrollable methods.
            // Some components base their return values for the Scrollable methods on the size of the viewport,
            // so that if we don't ask after resetting the bounds we may have gotten the wrong answer.
            if (viewport != null) {
                viewport.setBounds(bounds);
                if (!isEmpty && view instanceof Scrollable) {
                    viewportExtentSize = viewport.toViewCoordinates(bounds.getSize());

                    boolean vsbNeededOld = vsbNeeded;
                    if (vsb != null && vsbPolicy == VERTICAL_SCROLLBAR_AS_NEEDED) {
                        boolean vsbNeededNew = isVerticalScrollBarNeeded(view, viewLocation, viewPreferredSize, viewportExtentSize);
                        if (vsbNeeded != vsbNeededNew) {
                            vsbNeeded = vsbNeededNew;
                            if (vsbNeeded) {
                                adjustForVSB(bounds, insets, vsbBounds, vsbOpaque, vsbOnLeft);
                            }
                            else if (vsbOpaque) {
                                bounds.width += vsbBounds.width;
                            }
                            if (vsbOpaque) {
                                viewportExtentSize = viewport.toViewCoordinates(bounds.getSize());
                            }
                        }
                    }
                    boolean hsbNeededOld = hsbNeeded;
                    if (hsb != null && hsbPolicy == HORIZONTAL_SCROLLBAR_AS_NEEDED) {
                        boolean hsbNeededNew = isHorizontalScrollBarNeeded(view, viewLocation, viewPreferredSize, viewportExtentSize);
                        if (hsbNeeded != hsbNeededNew) {
                            hsbNeeded = hsbNeededNew;
                            if (hsbNeeded) {
                                adjustForHSB(bounds, insets, hsbBounds, hsbOpaque, hsbOnTop);
                            }
                            else if (hsbOpaque) {
                                bounds.height += hsbBounds.height;
                            }
                            if (hsbOpaque && vsb != null && !vsbNeeded && vsbPolicy != VERTICAL_SCROLLBAR_NEVER) {
                                viewportExtentSize = viewport.toViewCoordinates(bounds.getSize());
                                vsbNeeded = isScrollBarNeeded(viewLocation.y, viewPreferredSize.height, viewportExtentSize.height);
                                if (vsbNeeded) {
                                    adjustForVSB(bounds, insets, vsbBounds, vsbOpaque, vsbOnLeft);
                                }
                            }
                        }
                    }
                    if (hsbNeededOld != hsbNeeded || vsbNeededOld != vsbNeeded) {
                        viewport.setBounds(bounds);
                        // You could argue that we should recheck the Scrollable methods again until they stop changing,
                        // but they might never stop changing, so we stop here and don't do any additional checks.
                    }
                }
            }
            // Set the bounds of the row header.
            rowHeadBounds.y = bounds.y - insets.top;
            rowHeadBounds.height = bounds.height + insets.top + insets.bottom;
            boolean fillLowerCorner = false;
            if (rowHead != null) {
                if (hsbOpaque) {
                    Component corner = hsbOnTop ? (vsbOnLeft ? upperRight : upperLeft) : (vsbOnLeft ? lowerRight : lowerLeft);
                    fillLowerCorner = corner == null && UIManager.getBoolean("ScrollPane.fillLowerCorner");
                    if (!fillLowerCorner && ScrollSettings.isHeaderOverCorner(viewport)) {
                        if (hsbOnTop) {
                            rowHeadBounds.y -= hsbBounds.height;
                        }
                        rowHeadBounds.height += hsbBounds.height;
                    }
                }
                rowHead.setBounds(rowHeadBounds);
                rowHead.putClientProperty(Alignment.class, vsbOnLeft ? Alignment.RIGHT : Alignment.LEFT);
            }
            // Set the bounds of the column header.
            colHeadBounds.x = bounds.x - insets.left;
            colHeadBounds.width = bounds.width + insets.left + insets.right;
            boolean fillUpperCorner = false;
            boolean hasStatusComponent = false;
            if (colHead != null) {
                if (vsbOpaque) {
                    Component corner = vsbOnLeft ? (hsbOnTop ? lowerLeft : upperLeft) : (hsbOnTop ? lowerRight : upperRight);
                    fillUpperCorner = corner == null && UIManager.getBoolean("ScrollPane.fillUpperCorner");
                    if (!fillUpperCorner && ScrollSettings.isHeaderOverCorner(viewport)) {
                        if (vsbOnLeft) {
                            colHeadBounds.x -= vsbBounds.width;
                        }
                        colHeadBounds.width += vsbBounds.width;
                    }
                }
                colHead.setBounds(colHeadBounds);
                colHead.putClientProperty(Alignment.class, hsbOnTop ? Alignment.BOTTOM : Alignment.TOP);
            }
            // Calculate overlaps for translucent scroll bars
            int overlapWidth = 0;
            int overlapHeight = 0;
            if (vsbNeeded && !vsbOpaque && hsbNeeded && !hsbOpaque) {
                overlapWidth = vsbBounds.width; // shrink horizontally
                //overlapHeight = hsbBounds.height; // shrink vertically
            }
            // Set the bounds of the vertical scroll bar.
            vsbBounds.y = bounds.y - insets.top;
            vsbBounds.height = bounds.height + insets.top + insets.bottom;

            // Forked bounds that are actually used for setting vertical scroll bar bounds
            // after possible modification with statusComponent bounds.
            Rectangle actualVsbBounds = new Rectangle(vsbBounds);
            if (vsb != null) {
                vsb.setVisible(vsbNeeded);
                if (vsbNeeded) {
                    if (fillUpperCorner) {
                        // This is used primarily for GTK L&F, which needs to extend
                        // the vertical scrollbar to fill the upper corner near the column header.
                        // Note that we skip this step (and use the default behavior)
                        // if the user has set a custom corner component.
                        if (!hsbOnTop) {
                            vsbBounds.y -= colHeadBounds.height;
                        }
                        vsbBounds.height += colHeadBounds.height;
                    }
                    int overlapY = !hsbOnTop ? 0 : overlapHeight;
                    actualVsbBounds.y += overlapY;
                    actualVsbBounds.height -= overlapHeight;
                    vsb.putClientProperty(Alignment.class, vsbOnLeft ? Alignment.LEFT : Alignment.RIGHT);
                }
                // Modify the bounds of the translucent scroll bar.
                if (!vsbOpaque) {
                    if (!vsbOnLeft) {
                        vsbBounds.x += vsbBounds.width;
                    }
                    vsbBounds.width = 0;
                }
            }
            // Set the bounds of the horizontal scroll bar.
            hsbBounds.x = bounds.x - insets.left;
            hsbBounds.width = bounds.width + insets.left + insets.right;
            if (hsb != null) {
                hsb.setVisible(hsbNeeded);
                if (hsbNeeded) {
                    if (fillLowerCorner) {
                        // This is used primarily for GTK L&F, which needs to extend
                        // the horizontal scrollbar to fill the lower corner near the row header.
                        // Note that we skip this step (and use the default behavior)
                        // if the user has set a custom corner component.
                        if (!vsbOnLeft) {
                            hsbBounds.x -= rowHeadBounds.width;
                        }
                        hsbBounds.width += rowHeadBounds.width;
                    }
                    int overlapX = !vsbOnLeft ? 0 : overlapWidth;
                    hsb.setBounds(hsbBounds.x + overlapX, hsbBounds.y, hsbBounds.width - overlapWidth, hsbBounds.height);
                    hsb.putClientProperty(Alignment.class, hsbOnTop ? Alignment.TOP : Alignment.BOTTOM);
                }
                // Modify the bounds of the translucent scroll bar.
                if (!hsbOpaque) {
                    if (!hsbOnTop) {
                        hsbBounds.y += hsbBounds.height;
                    }
                    hsbBounds.height = 0;
                }
            }

            if (vsb != null && vsbNeeded) {
                vsb.setBounds(actualVsbBounds);
            }

            // Set the bounds of the corners.
            Rectangle left = vsbOnLeft ? vsbBounds : rowHeadBounds;
            Rectangle right = vsbOnLeft ? rowHeadBounds : vsbBounds;
            Rectangle upper = hsbOnTop ? hsbBounds : colHeadBounds;
            Rectangle lower = hsbOnTop ? colHeadBounds : hsbBounds;
            if (lowerLeft != null) {
                Rectangle lowerLeftBounds = new Rectangle(left.x, left.y + left.height, 0, 0);
                if (left.width > 0 && lower.height > 0) {
                    updateCornerBounds(lowerLeftBounds, lower.x, lower.y + lower.height);
                }
                lowerLeft.setBounds(lowerLeftBounds);
            }
            if (lowerRight != null) {
                Rectangle lowerRightBounds = new Rectangle(lower.x + lower.width, right.y + right.height, 0, 0);
                if (right.width > 0 && lower.height > 0) {
                    updateCornerBounds(lowerRightBounds, right.x + right.width, lower.y + lower.height);
                }
                lowerRight.setBounds(lowerRightBounds);
            }
            if (upperLeft != null) {
                Rectangle upperLeftBounds = new Rectangle(left.x, upper.y, 0, 0);
                if (left.width > 0 && upper.height > 0) {
                    updateCornerBounds(upperLeftBounds, upper.x, left.y);
                }
                upperLeft.setBounds(upperLeftBounds);
            }
            if (upperRight != null) {
                Rectangle upperRightBounds = new Rectangle(upper.x + upper.width, upper.y, 0, 0);
                if (right.width > 0 && upper.height > 0) {
                    updateCornerBounds(upperRightBounds, right.x + right.width, right.y);
                }
                upperRight.setBounds(upperRightBounds);
            }
            if (!vsbOpaque && vsbNeeded || !hsbOpaque && hsbNeeded) {
                fixComponentZOrder(vsb, 0);
                fixComponentZOrder(viewport, -1);
            }
        }

        private static boolean tracksViewportWidth(Component view) {
            return view instanceof Scrollable && ((Scrollable) view).getScrollableTracksViewportWidth();
        }

        private static boolean tracksViewportHeight(Component view) {
            return view instanceof Scrollable && ((Scrollable) view).getScrollableTracksViewportHeight();
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Dimension result = new Dimension();

            JScrollPane pane = (JScrollPane) parent;
            JBInsets.addTo(result, pane.getInsets());

            Border border = pane.getViewportBorder();
            if (border != null) {
                JBInsets.addTo(result, border.getBorderInsets(parent));
            }

            int vsbPolicy = pane.getVerticalScrollBarPolicy();
            int hsbPolicy = pane.getHorizontalScrollBarPolicy();
            if (viewport != null) {
                Component view = viewport.getView();
                if (view != null) {
                    Point viewLocation = view.getLocation();
                    Dimension viewportExtentSize = viewport.getPreferredSize();
                    if (viewportExtentSize == null) {
                        viewportExtentSize = new Dimension();
                    }
                    Dimension viewPreferredSize = view.getPreferredSize();
                    if (viewPreferredSize == null) {
                        viewPreferredSize = new Dimension();
                    }
                    if (view instanceof JComponent && !view.isPreferredSizeSet()) {
                        JBInsets.removeFrom(viewPreferredSize, JBViewport.getViewInsets((JComponent) view));
                    }
                    result.width += viewportExtentSize.width;
                    result.height += viewportExtentSize.height;
                    if (vsbPolicy == VERTICAL_SCROLLBAR_AS_NEEDED) {
                        if (isVerticalScrollBarNeeded(view, viewLocation, viewPreferredSize, viewportExtentSize)) {
                            vsbPolicy = VERTICAL_SCROLLBAR_ALWAYS;
                        }
                    }
                    if (hsbPolicy == HORIZONTAL_SCROLLBAR_AS_NEEDED) {
                        if (isHorizontalScrollBarNeeded(view, viewLocation, viewPreferredSize, viewportExtentSize)) {
                            hsbPolicy = HORIZONTAL_SCROLLBAR_ALWAYS;
                        }
                    }
                }
            }
            // disabled scroll bars should be minimized (see #adjustForVSB and #adjustForHSB)
            if (vsb != null && vsbPolicy == VERTICAL_SCROLLBAR_ALWAYS && vsb.isEnabled()) {
                result.width += vsb.getPreferredSize().width;
            }
            if (hsb != null && hsbPolicy == HORIZONTAL_SCROLLBAR_ALWAYS && hsb.isEnabled()) {
                result.height += hsb.getPreferredSize().height;
            }

            if (rowHead != null && rowHead.isVisible()) {
                result.width += rowHead.getPreferredSize().width;
            }
            if (colHead != null && colHead.isVisible()) {
                result.height += colHead.getPreferredSize().height;
            }

            return result;
        }

        private static boolean isAlwaysOpaque(Component view) {
            return !Platform.current().os().isMac() && ScrollSettings.isNotSupportedYet(view);
        }

        private static void updateCornerBounds(Rectangle bounds, int x, int y) {
            bounds.width = Math.abs(bounds.x - x);
            bounds.height = Math.abs(bounds.y - y);
            bounds.x = Math.min(bounds.x, x);
            bounds.y = Math.min(bounds.y, y);
        }

        private static void fixComponentZOrder(Component component, int index) {
            if (component != null) {
                Container parent = component.getParent();
                synchronized (parent.getTreeLock()) {
                    if (index < 0) {
                        index += parent.getComponentCount();
                    }
                    parent.setComponentZOrder(component, index);
                }
            }
        }

        private void adjustForVSB(Rectangle bounds, Insets insets, Rectangle vsbBounds, boolean vsbOpaque, boolean vsbOnLeft) {
            vsbBounds.width = !vsb.isEnabled() ? 0 : min(bounds.width, vsb.getPreferredSize().width);
            if (vsbOnLeft) {
                vsbBounds.x = bounds.x - insets.left/* + vsbBounds.width*/;
                if (vsbOpaque) {
                    bounds.x += vsbBounds.width;
                }
            }
            else {
                vsbBounds.x = bounds.x + bounds.width + insets.right - vsbBounds.width;
            }
            if (vsbOpaque) {
                bounds.width -= vsbBounds.width;
            }
        }

        private void adjustForHSB(Rectangle bounds, Insets insets, Rectangle hsbBounds, boolean hsbOpaque, boolean hsbOnTop) {
            hsbBounds.height = !hsb.isEnabled() ? 0 : min(bounds.height, hsb.getPreferredSize().height);
            if (hsbOnTop) {
                hsbBounds.y = bounds.y - insets.top/* + hsbBounds.height*/;
                if (hsbOpaque) {
                    bounds.y += hsbBounds.height;
                }
            }
            else {
                hsbBounds.y = bounds.y + bounds.height + insets.bottom - hsbBounds.height;
            }
            if (hsbOpaque) {
                bounds.height -= hsbBounds.height;
            }
        }

        private static int min(int one, int two) {
            return Math.max(0, Math.min(one, two));
        }

        /**
         * @param location      a horizontal (or vertical) position of a component
         * @param preferredSize a preferred width (or height) of a component
         * @param extentSize    an extent size of a viewport
         * @return {@code true} if a preferred size exceeds an extent size or if a component is not aligned
         */
        private static boolean isScrollBarNeeded(int location, int preferredSize, int extentSize) {
            return preferredSize > extentSize || location != 0;
        }

        private static boolean isHorizontalScrollBarNeeded(Component view, Point location, Dimension preferredSize, Dimension extentSize) {
            // don't bother Scrollable.getScrollableTracksViewportWidth if a horizontal scroll bar is not needed
            return isScrollBarNeeded(location.x, preferredSize.width, extentSize.width) && !tracksViewportWidth(view);
        }

        private static boolean isVerticalScrollBarNeeded(Component view, Point location, Dimension preferredSize, Dimension extentSize) {
            // don't bother Scrollable.getScrollableTracksViewportHeight if a vertical scroll bar is not needed
            return isScrollBarNeeded(location.y, preferredSize.height, extentSize.height) && !tracksViewportHeight(view);
        }
    }
}