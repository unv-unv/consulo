// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ui.ex.awt;

import consulo.application.util.registry.Registry;
import consulo.ui.ex.TypingTarget;
import consulo.ui.ex.awt.JBScrollPane.Alignment;
import consulo.ui.ex.awt.internal.ScrollSettings;
import consulo.ui.ex.awt.table.JBTable;
import consulo.ui.ex.awt.tree.TreeUtil;
import consulo.ui.ex.awt.util.ComponentUtil;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;

@Deprecated
public class JBViewport extends JViewport implements ZoomableViewport {
  public static final Key<Boolean> FORCE_VISIBLE_ROW_COUNT_KEY = Key.create("forceVisibleRowCount");

  private static final int NOTIFICATION_TIMEOUT = 1500;

  //private Notification myPreviousNotification;

  private static final ViewportLayout ourLayoutManager = new ViewportLayout() {
    @Override
    public void layoutContainer(Container parent) {
      if (parent instanceof JViewport) {
        JViewport viewport = (JViewport)parent;
        Component view = viewport.getView();
        if (view != null) {
          Container grand = viewport.getParent();
          if (grand instanceof JScrollPane) {
            doLayout((JScrollPane)grand, viewport, view);
          }
          else {
            super.layoutContainer(parent);
          }
        }
      }
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      if (parent instanceof JViewport) {
        JViewport viewport = (JViewport)parent;
        Dimension size = getPreferredScrollableViewportSize(viewport.getView());
        if (size != null) return size; // may be null for for tables or custom components
      }
      return new Dimension();
    }
  };

  private StatusText myEmptyText;
  private boolean myPaintingNow;

  private ZoomingDelegate myZoomer;

  public JBViewport() {
    addContainerListener(new ContainerListener() {
      @Override
      public void componentAdded(ContainerEvent e) {
        Component child = e.getChild();
        if (child instanceof JBTable) {
          myEmptyText = ((ComponentWithEmptyText)child).getEmptyText();
          myEmptyText.attachTo(JBViewport.this, child);
        }
      }

      @Override
      public void componentRemoved(ContainerEvent e) {
        Component child = e.getChild();
        if (child instanceof JBTable) {
          ((ComponentWithEmptyText)child).getEmptyText().attachTo(child);
          myEmptyText = null;
        }
      }
    });
  }

  @Override
  public void setViewPosition(Point p) {
    super.setViewPosition(p);
  }

  @Override
  public void setView(Component view) {
    super.setView(view);
    updateBorder(view);
  }

  @Override
  protected LayoutManager createLayoutManager() {
    return ourLayoutManager;
  }

  @Override
  public void paint(Graphics g) {
    myPaintingNow = true;
    if (myZoomer != null && myZoomer.isActive()) {
      myZoomer.paint(g);
    }
    else {
      super.paint(g);

      if (myEmptyText != null) {
        myEmptyText.paint(this, g);
      }
    }
    myPaintingNow = false;
  }

  @Nullable
  @Override
  public Magnificator getMagnificator() {
    return UIUtil.getClientProperty(getView(), Magnificator.CLIENT_PROPERTY_KEY);
  }

  @Override
  public void magnificationStarted(Point at) {
    myZoomer = new ZoomingDelegate((JComponent)getView(), this);
    myZoomer.magnificationStarted(at);
  }

  @Override
  public void magnificationFinished(double magnification) {
    myZoomer.magnificationFinished(magnification);
    myZoomer = null;
  }

  @Override
  public void magnify(double magnification) {
    myZoomer.magnify(magnification);
  }

  public boolean isPaintingNow() {
    return myPaintingNow;
  }

  @Override
  public void scrollRectToVisible(Rectangle bounds) {
    Component view = getView();
    if (view instanceof JComponent && !isAutoscroll(bounds)) {
      JBInsets.addTo(bounds, getViewInsets((JComponent)view));
    }
    if (bounds.width > getWidth()) bounds.width = getWidth();
    if (bounds.height > getHeight()) bounds.height = getHeight();
    super.scrollRectToVisible(bounds);
  }

  /**
   * @param bounds a bounds passed to {@link #scrollRectToVisible}
   * @return {@code true} if the specified bounds requested by auto-scrolling
   */
  private boolean isAutoscroll(Rectangle bounds) {
    if (bounds.x == -bounds.width || bounds.x == getWidth()) {
      if (bounds.y + bounds.height + bounds.y == getHeight()) {
        // Horizontal auto-scrolling:
        //          /---   or   ---\
        //          y              y
        //  /-width-!              !-width-\
        //  !       !              !       !
        //  h       !              !       h
        //  e       !              !       e
        //  i       !              !       i
        //  g       !              !       g
        //  h       !              !       h
        //  t       !              !       t
        //  !       !              !       !
        //  \-------!              !-------/
        //          y              y
        //          \---   or   ---/
        return true;
      }
    }
    if (bounds.y == -bounds.height || bounds.y == getHeight()) {
      if (bounds.x + bounds.width + bounds.x == getWidth()) {
        // Vertical auto-scrolling is symmetric to horizontal one
        return true;
      }
    }
    return false;
  }

  private static boolean isAlignmentNeeded(JComponent view, boolean horizontal) {
    return false;
  }

  private static Insets getInnerInsets(JComponent view) {
    Border border = view.getBorder();
    if (border instanceof ViewBorder) {
      ViewBorder vb = (ViewBorder)border;
      border = vb.myBorder;
    }
    return border == null ? null : border.getBorderInsets(view);
  }

  static Insets getViewInsets(JComponent view) {
    Border border = view.getBorder();
    if (border instanceof ViewBorder) {
      ViewBorder vb = (ViewBorder)border;
      Insets insets = JBUI.emptyInsets();
      vb.addViewInsets(view, insets);
      return insets;
    }
    return null;
  }

  private static void doLayout(JScrollPane pane, JViewport viewport, Component view) {
    updateBorder(view);

    Dimension actualSize = viewport.getSize();
    Dimension extentSize = viewport.toViewCoordinates(actualSize);
    Dimension viewPreferredSize = view.getPreferredSize();
    Dimension viewSize = new Dimension(viewPreferredSize);
    Point viewPosition = viewport.getViewPosition();

    Scrollable scrollable = null;
    if (view instanceof Scrollable) {
      scrollable = (Scrollable)view;
      if (scrollable.getScrollableTracksViewportWidth()) viewSize.width = actualSize.width;
      if (scrollable.getScrollableTracksViewportHeight()) viewSize.height = actualSize.height;
    }
    // If the new viewport size would leave empty space to the right of the view,
    // right justify the view or left justify the view
    // when the width of the view is smaller than the container.
    int maxX = viewSize.width - extentSize.width;
    if (scrollable == null || pane.getComponentOrientation().isLeftToRight()) {
      if (viewPosition.x > maxX) {
        viewPosition.x = Math.max(0, maxX);
      }
    }
    else {
      viewPosition.x = maxX < 0 ? maxX : Math.max(0, Math.min(maxX, viewPosition.x));
    }
    // If the new viewport size would leave empty space below the view,
    // bottom justify the view or top justify the view
    // when the height of the view is smaller than the container.
    int maxY = viewSize.height - extentSize.height;
    if (viewPosition.y > maxY) {
      viewPosition.y = Math.max(0, maxY);
    }
    // If we haven't been advised about how the viewports size should change wrt to the viewport,
    // i.e. if the view isn't an instance of Scrollable, then adjust the views size as follows.
    if (scrollable == null) {
      // If the origin of the view is showing and the viewport is bigger than the views preferred size,
      // then make the view the same size as the viewport.
      if (viewPosition.x == 0 && actualSize.width > viewPreferredSize.width) viewSize.width = actualSize.width;
      if (viewPosition.y == 0 && actualSize.height > viewPreferredSize.height) viewSize.height = actualSize.height;
    }
    // do not force viewport size on editor component, e.g. EditorTextField and LanguageConsole
    if (!(view instanceof TypingTarget)) {
      if (ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER == pane.getHorizontalScrollBarPolicy()) {
        viewPosition.x = 0;
        viewSize.width = extentSize.width;
      }
      if (ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER == pane.getVerticalScrollBarPolicy()) {
        viewPosition.y = 0;
        viewSize.height = extentSize.height;
      }
    }
    viewport.setViewPosition(viewPosition);
    viewport.setViewSize(viewSize);
  }

  private static void updateBorder(@Nullable Component view) {
    if (ScrollSettings.isNotSupportedYet(view)) return;
    if (view instanceof JComponent) {
      JComponent component = (JComponent)view;
      Border border = component.getBorder();
      if (border instanceof ViewBorder) return; // already set
      component.setBorder(border == null || border instanceof UIResource ? new ResourceViewBorder(border) : new ViewBorder(border));
    }
  }

  /**
   * This border is used to add additional space for a view
   * and can be changed on UI update.
   */
  private static class ResourceViewBorder extends ViewBorder implements UIResource {
    ResourceViewBorder(Border border) {
      super(border);
    }
  }

  /**
   * This border is used to add additional space for a view.
   */
  private static class ViewBorder extends AbstractBorder {
    private final Insets myInsets = JBUI.emptyInsets();
    private final Border myBorder;

    ViewBorder(Border border) {
      myBorder = border;
    }

    @Override
    public Insets getBorderInsets(Component view, Insets insets) {
      if (insets == null) {
        insets = JBUI.emptyInsets();
      }
      else {
        insets.set(0, 0, 0, 0);
      }
      if (myBorder != null) {
        Insets inner = myBorder.getBorderInsets(view);
        if (inner != null) insets.set(inner.top, inner.left, inner.bottom, inner.right);
      }
      if (view instanceof JComponent) {
        addViewInsets((JComponent)view, insets);
      }
      if (!myInsets.equals(insets)) {
        myInsets.set(insets.top, insets.left, insets.bottom, insets.right);
        if (view instanceof JComponent) {
          JComponent component = (JComponent)view;
          if (component instanceof JTree) {
            JTree tree = (JTree)component;
            TreeUtil.invalidateCacheAndRepaint(tree.getUI());
          }
          component.revalidate();
        }
        else {
          view.invalidate();
          view.repaint();
        }
      }
      return insets;
    }

    @Override
    public void paintBorder(Component view, Graphics g, int x, int y, int width, int height) {
      if (myBorder != null) {
        // additional insets are used inside a custom border
        myBorder.paintBorder(view, g, x, y, width, height);
      }
    }

    private void addViewInsets(JComponent view, Insets insets) {
      if (this == view.getBorder()) {
        JViewport viewport = ComponentUtil.getViewport(view);
        if (viewport != null) {
          JScrollPane pane = ComponentUtil.getScrollPane(viewport);
          if (pane != null) {
            // calculate empty border under vertical scroll bar
            JScrollBar vsb = pane.getVerticalScrollBar();
            if (vsb != null && vsb.isVisible()) {
              boolean opaque = vsb.isOpaque();
              if (viewport == pane.getColumnHeader()
                  ? (!opaque || ScrollSettings.isHeaderOverCorner(pane.getViewport()))
                  : (!opaque && viewport == pane.getViewport() && !UIUtil.isClientPropertyTrue(vsb, JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS))) {
                Alignment va = Alignment.get(vsb);
                if (va == Alignment.LEFT) {
                  insets.left += vsb.getWidth();
                }
                else if (va == Alignment.RIGHT && (opaque || isAlignmentNeeded(view, false))) {
                  insets.right += vsb.getWidth();
                }
              }
            }
            // calculate empty border under horizontal scroll bar
            JScrollBar hsb = pane.getHorizontalScrollBar();
            if (hsb != null && hsb.isVisible()) {
              boolean opaque = hsb.isOpaque();
              if (viewport == pane.getRowHeader()
                  ? (!opaque || ScrollSettings.isHeaderOverCorner(pane.getViewport()))
                  : (!opaque && viewport == pane.getViewport() && !UIUtil.isClientPropertyTrue(hsb, JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS))) {
                Alignment ha = Alignment.get(hsb);
                if (ha == Alignment.TOP) {
                  insets.top += hsb.getHeight();
                }
                else if (ha == Alignment.BOTTOM && (opaque || isAlignmentNeeded(view, true))) {
                  insets.bottom += hsb.getHeight();
                }
              }
            }
          }
        }
      }
    }
  }

  private static Dimension getPreferredScrollableViewportSize(Component view) {
    if (view instanceof JList) return getPreferredScrollableViewportSize((JList)view);
    if (view instanceof JTree) return getPreferredScrollableViewportSize((JTree)view);
    if (view instanceof Scrollable) return ((Scrollable)view).getPreferredScrollableViewportSize();
    if (view instanceof JComponent) return getPreferredSizeWithoutScrollBars((JComponent)view);
    return view == null ? null : view.getPreferredSize();
  }

  private static Dimension getPreferredSizeWithoutScrollBars(@Nonnull JComponent view) {
    Dimension size = view.getPreferredSize();
    if (size == null) return new Dimension();
    JBInsets.removeFrom(size, getViewInsets(view));
    return size;
  }

  private static Class<?> getPreferredScrollableViewportSizeDeclaringClass(@Nonnull Scrollable scrollable) {
    try {
      return scrollable.getClass().getMethod("getPreferredScrollableViewportSize").getDeclaringClass();
    }
    catch (Exception exception) {
      return null;
    }
  }

  private static Dimension getPreferredScrollableViewportSize(@Nonnull JList<?> list) {
    if (JList.class != getPreferredScrollableViewportSizeDeclaringClass(list)) {
      return list.getPreferredScrollableViewportSize(); // may be null
    }
    Dimension size = getPreferredSizeWithoutScrollBars(list);
    if (JList.VERTICAL != list.getLayoutOrientation()) return size;

    ListModel<?> model = list.getModel();
    int modelRows = model == null ? 0 : model.getSize();
    int visibleRows = list.getVisibleRowCount();
    boolean forceVisibleRowCount = Boolean.TRUE.equals(UIUtil.getClientProperty(list, FORCE_VISIBLE_ROW_COUNT_KEY));
    if (!forceVisibleRowCount && visibleRows > 0) {
      visibleRows = Math.min(modelRows, visibleRows);
    }
    if (visibleRows <= 0) {
      visibleRows = Registry.intValue("ide.preferred.scrollable.viewport.visible.rows", 10);
    }

    boolean addExtraSpace = Registry.is("ide.preferred.scrollable.viewport.extra.space", true);
    Insets insets = getInnerInsets(list);
    size.height = insets != null ? insets.top + insets.bottom : 0;
    if (modelRows == 0) {
      int fixedWidth = list.getFixedCellWidth();
      int fixedHeight = list.getFixedCellHeight();
      if (fixedWidth <= 0) {
        fixedWidth = Registry.intValue("ide.preferred.scrollable.viewport.fixed.width", 256);
      }
      if (fixedWidth <= 0) {
        fixedWidth = JBUIScale.scale(256); // scaled value from JDK
      }
      if (fixedHeight <= 0) {
        fixedHeight = Registry.intValue("ide.preferred.scrollable.viewport.fixed.height", 0);
      }
      if (fixedHeight <= 0) {
        fixedHeight = UIManager.getInt("List.rowHeight");
      }
      if (fixedHeight <= 0) {
        fixedHeight = JBUIScale.scale(16); // scaled value from JDK
      }

      size.width = insets != null ? insets.left + insets.right + fixedWidth : fixedWidth;
      size.height += fixedHeight * visibleRows;
      if (addExtraSpace) size.height += fixedHeight / 2;
    }
    else if (visibleRows > 0) {
      int lastRow = Math.min(visibleRows, modelRows) - 1;
      Rectangle bounds = list.getCellBounds(lastRow, lastRow);
      if (bounds == null) return size; // null UI?
      size.height = bounds.y + bounds.height;
      if (insets != null) size.height += insets.bottom;
      if (modelRows < visibleRows) {
        size.height += (visibleRows - modelRows) * bounds.height;
      }
      else if (modelRows > visibleRows) {
        if (addExtraSpace) size.height += bounds.height / 2;
      }
    }
    return size;
  }

  private static Dimension getPreferredScrollableViewportSize(@Nonnull JTree tree) {
    if (JTree.class != getPreferredScrollableViewportSizeDeclaringClass(tree)) {
      return tree.getPreferredScrollableViewportSize(); // may be null
    }
    Dimension size = getPreferredSizeWithoutScrollBars(tree);

    int fixedHeight = tree.getRowHeight();

    int modelRows = tree.getRowCount();
    if (modelRows <= 0) {
      if (fixedHeight <= 0) {
        fixedHeight = Registry.intValue("ide.preferred.scrollable.viewport.fixed.height", 0);
      }
      if (fixedHeight <= 0) {
        fixedHeight = UIManager.getInt("Tree.rowHeight");
      }
      if (fixedHeight <= 0) {
        fixedHeight = JBUIScale.scale(16);
      }
    }
    int visibleRows = tree.getVisibleRowCount();
    if (visibleRows <= 0) {
      visibleRows = Registry.intValue("ide.preferred.scrollable.viewport.visible.rows", 10);
    }

    boolean addExtraSpace = (modelRows != visibleRows) && Registry.is("ide.preferred.scrollable.viewport.extra.space", true);
    Insets insets = getInnerInsets(tree);
    size.height = insets != null ? insets.top + insets.bottom : 0;
    if (0 < fixedHeight) {
      size.height += fixedHeight * visibleRows;
      if (addExtraSpace) size.height += fixedHeight / 2;
    }
    else if (visibleRows > 0) {
      int lastRow = Math.min(visibleRows, modelRows - 1);
      Rectangle bounds = tree.getRowBounds(lastRow);
      if (bounds != null) {
        size.height = bounds.y + bounds.height * (visibleRows - lastRow);
        if (addExtraSpace) {
          size.height += bounds.height / 2;
        }
        else if (insets != null) {
          size.height += insets.bottom;
        }
      }
    }
    return size;
  }
}
