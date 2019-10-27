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

package com.intellij.ui;

import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.concurrent.atomic.AtomicReference;

import static java.awt.Cursor.*;
import static javax.swing.SwingUtilities.convertPointFromScreen;

/**
 * @author Sergey Malenkov
 */
public class WindowResizeListener extends WindowMouseListener {
  private final Insets myBorder;
  private final Icon myCorner;

  /**
   * @param border the border insets specify different areas to resize
   * @param corner the corner icon specifies the Mac-specific area to resize
   */
  public WindowResizeListener(Component content, Insets border, Icon corner) {
    super(content);
    myBorder = border;
    myCorner = corner;
  }

  /**
   * @param content the window content to find a window, or {@code null} to use a component from a mouse event
   * @param corner  the corner icon that specifies a Mac-specific area to resize
   */
  public WindowResizeListener(Component content, Icon corner) {
    super(content);
    myBorder = null;
    myCorner = corner;
  }

  /**
   * @param view the component to resize
   * @return an insets indicating inactive outer area
   */
  protected Insets getResizeOffset(Component view) {
    return null;
  }

  /**
   * @param view the component to resize
   * @return an insets indicating active inner area
   */
  protected Insets getResizeBorder(Component view) {
    return myBorder;
  }

  @Override
  protected boolean isDisabled(Component view) {
    if (view instanceof Dialog && !((Dialog)view).isResizable()) return true;
    if (view instanceof Frame && !((Frame)view).isResizable()) return true;
    return super.isDisabled(view);
  }

  @Override
  int getCursorType(Component view, Point location) {
    Component parent = view instanceof Window ? null : view.getParent();
    if (parent != null) {
      convertPointFromScreen(location, parent);
    }
    Rectangle bounds = view.getBounds();
    JBInsets.removeFrom(bounds, getResizeOffset(view));

    int top = location.y - bounds.y;
    if (top < 0) return CUSTOM_CURSOR;

    int left = location.x - bounds.x;
    if (left < 0) return CUSTOM_CURSOR;

    int right = bounds.width - left;
    if (right < 0) return CUSTOM_CURSOR;

    int bottom = bounds.height - top;
    if (bottom < 0) return CUSTOM_CURSOR;

    if (myCorner != null && right < myCorner.getIconWidth() && bottom < myCorner.getIconHeight()) {
      return DEFAULT_CURSOR;
    }
    Insets expected = getResizeBorder(view);
    if (expected != null) {
      if (view instanceof Frame) {
        int state = ((Frame)view).getExtendedState();
        if (isStateSet(Frame.MAXIMIZED_HORIZ, state)) {
          left = Integer.MAX_VALUE;
          right = Integer.MAX_VALUE;
        }
        if (isStateSet(Frame.MAXIMIZED_VERT, state)) {
          top = Integer.MAX_VALUE;
          bottom = Integer.MAX_VALUE;
        }
      }
      if (top < expected.top) {
        if (left < expected.left * 2) return NW_RESIZE_CURSOR;
        if (right < expected.right * 2) return NE_RESIZE_CURSOR;
        return N_RESIZE_CURSOR;
      }
      if (bottom < expected.bottom) {
        if (left < expected.left * 2) return SW_RESIZE_CURSOR;
        if (right < expected.right * 2) return SE_RESIZE_CURSOR;
        return S_RESIZE_CURSOR;
      }
      if (left < expected.left) {
        if (top < expected.top * 2) return NW_RESIZE_CURSOR;
        if (bottom < expected.bottom * 2) return SW_RESIZE_CURSOR;
        return W_RESIZE_CURSOR;
      }
      if (right < expected.right) {
        if (top < expected.top * 2) return NE_RESIZE_CURSOR;
        if (bottom < expected.bottom * 2) return SE_RESIZE_CURSOR;
        return E_RESIZE_CURSOR;
      }
    }
    return CUSTOM_CURSOR;
  }

  @Override
  void updateBounds(Rectangle bounds, Component view, int dx, int dy) {
    Dimension minimum = getMinimumSize(view);
    if (myCursorType == NE_RESIZE_CURSOR || myCursorType == E_RESIZE_CURSOR || myCursorType == SE_RESIZE_CURSOR || myCursorType == DEFAULT_CURSOR) {
      bounds.width += fixMinSize(dx, bounds.width, minimum.width);
    }
    else if (myCursorType == NW_RESIZE_CURSOR || myCursorType == W_RESIZE_CURSOR || myCursorType == SW_RESIZE_CURSOR) {
      dx = fixMinSize(-dx, bounds.width, minimum.width);
      bounds.x -= dx;
      bounds.width += dx;
    }
    if (myCursorType == SW_RESIZE_CURSOR || myCursorType == S_RESIZE_CURSOR || myCursorType == SE_RESIZE_CURSOR || myCursorType == DEFAULT_CURSOR) {
      bounds.height += fixMinSize(dy, bounds.height, minimum.height);
    }
    else if (myCursorType == NW_RESIZE_CURSOR || myCursorType == N_RESIZE_CURSOR || myCursorType == NE_RESIZE_CURSOR) {
      dy = fixMinSize(-dy, bounds.height, minimum.height);
      bounds.y -= dy;
      bounds.height += dy;
    }
  }

  @Override
  protected void setCursorType(int cursorType) {
    super.setCursorType(cursorType);
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    ourIsResizing = myCursorType >= SW_RESIZE_CURSOR && myCursorType <= E_RESIZE_CURSOR;
  }

  /**
   * Note: default implementation takes Component.getTreeLock()
   */
  protected Dimension getMinimumSize(Component comp) {
    return comp.getMinimumSize();
  }

  private static int fixMinSize(int delta, int value, int min) {
    return delta + value < min ? min - value : delta;
  }

  /**
   * @author tav
   */
  //@ApiStatus.Experimental
  public static class ToolkitListener extends WindowResizeListener {
    private final ToolkitListenerHelper myHelper;
    private final AtomicReference<Dimension> myMinSize = new AtomicReference<>();

    public ToolkitListener(Component content, Insets border, Icon corner) {
      super(content, border, corner);
      myHelper = new ToolkitListenerHelper(this);
      myMinSize.set(content.getMinimumSize());
      Window window = UIUtil.getWindow(content);
      if (window != null) window.addHierarchyListener(new HierarchyListener() {
        @Override
        public void hierarchyChanged(HierarchyEvent e) {
          if (e.getID() == HierarchyEvent.HIERARCHY_CHANGED) {
            myMinSize.set(content.getMinimumSize());
          }
          else if (e.getID() == HierarchyEvent.SHOWING_CHANGED && !window.isShowing()) {
            window.removeHierarchyListener(this);
          }
        }
      });
    }

    @Override
    protected void setBounds(Component comp, Rectangle bounds) {
      myHelper.setBounds(comp, bounds, () -> super.setBounds(comp, bounds));
    }

    @Override
    protected void setCursor(Component content, Cursor cursor) {
      myHelper.setCursor(content, cursor, () -> super.setCursor(content, cursor));
    }

    @Override
    protected Dimension getMinimumSize(Component comp) {
      return myMinSize.get();
    }

    public void addTo(Component comp) {
      myHelper.addTo(comp);
    }

    public void removeFrom(Component comp) {
      myHelper.removeFrom(comp);
    }
  }
}
