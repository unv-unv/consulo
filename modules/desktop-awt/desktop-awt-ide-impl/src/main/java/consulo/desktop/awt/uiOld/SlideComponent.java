/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package consulo.desktop.awt.uiOld;

import consulo.ide.impl.idea.ui.LightweightHintImpl;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.language.editor.ui.awt.HintUtil;
import consulo.ui.ex.Gray;
import consulo.ui.ex.RelativePoint;
import consulo.ui.ex.awt.hint.HintHint;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.popup.Balloon;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Alexey Pegov
 * @author Konstantin Bulenkov
 */
class SlideComponent extends JComponent {
  private int myPointerValue = 0;
  private int myValue = 0;
  private final boolean myVertical;
  private final String myTitle;

  private final List<Consumer<Integer>> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private LightweightHintImpl myTooltipHint;
  private final JLabel myLabel = new JLabel();
  private Unit myUnit = Unit.LEVEL;

  enum Unit {
    PERCENT,
    LEVEL;

    private static final float PERCENT_MAX_VALUE = 100f;
    private static final float LEVEL_MAX_VALUE = 255f;

    private static float getMaxValue(Unit unit) {
      return LEVEL.equals(unit) ? LEVEL_MAX_VALUE : PERCENT_MAX_VALUE;
    }

    private static String formatValue(int value, Unit unit) {
      return String.format("%d%s", (int) (getMaxValue(unit) / LEVEL_MAX_VALUE * value),
          unit.equals(PERCENT) ? "%" : "");
    }
  }

  void setUnits(Unit unit) {
    myUnit = unit;
  }

  SlideComponent(String title, boolean vertical) {
    myTitle = title;
    myVertical = vertical;

    addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseDragged(MouseEvent e) {
        processMouse(e);
      }
    });

    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        processMouse(e);
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        updateBalloonText();
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        updateBalloonText();
      }

      @Override
      public void mouseExited(MouseEvent e) {
        if (myTooltipHint != null) {
          myTooltipHint.hide();
          myTooltipHint = null;
        }
      }
    });

    addMouseWheelListener(new MouseWheelListener() {
      @Override
      public void mouseWheelMoved(MouseWheelEvent e) {
        final int amount = e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL ? e.getUnitsToScroll() * e.getScrollAmount() :
                           e.getWheelRotation() < 0 ? -e.getScrollAmount() : e.getScrollAmount();
        int pointerValue = myPointerValue + amount;
        pointerValue = pointerValue < getPointerOffset() ? getPointerOffset() : pointerValue;
        int size = myVertical ? getHeight() : getWidth();
        pointerValue = pointerValue > (size - JBUI.scale(12)) ? size - JBUI.scale(12) : pointerValue;

        myPointerValue = pointerValue;
        myValue = pointerValueToValue(myPointerValue);

        repaint();
        fireValueChanged();
      }
    });

    addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        setValue(getValue());
        fireValueChanged();
        repaint();
      }
    });
  }

  private void updateBalloonText() {
    final Point point = myVertical ? new Point(0, myPointerValue) : new Point(myPointerValue, 0);
    myLabel.setText(myTitle + ": " + Unit.formatValue(myValue, myUnit));
    if (myTooltipHint == null) {
      myTooltipHint = new LightweightHintImpl(myLabel);
      myTooltipHint.setCancelOnClickOutside(false);
      myTooltipHint.setCancelOnOtherWindowOpen(false);

      final HintHint hint = new HintHint(this, point)
        .setPreferredPosition(myVertical ? Balloon.Position.atLeft : Balloon.Position.above)
        .setBorderColor(Color.BLACK)
        .setAwtTooltip(true)
        .setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD))
        .setTextBg(HintUtil.INFORMATION_COLOR)
        .setShowImmediately(true);

      final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      myTooltipHint.show(this, point.x, point.y, owner instanceof JComponent ? (JComponent)owner : null, hint);
    }
    else {
      myTooltipHint.setLocation(new RelativePoint(this, point));
    }
  }

  @Override
  protected void processMouseMotionEvent(MouseEvent e) {
    super.processMouseMotionEvent(e);
    updateBalloonText();
  }

  private void processMouse(MouseEvent e) {
    int pointerValue = myVertical ? e.getY() : e.getX();
    pointerValue = pointerValue < getPointerOffset() ? getPointerOffset() : pointerValue;
    int size = myVertical ? getHeight() : getWidth();
    pointerValue = pointerValue > (size - JBUI.scale(12)) ? size - JBUI.scale(12) : pointerValue;

    myPointerValue = pointerValue;

    myValue = pointerValueToValue(myPointerValue);

    repaint();
    fireValueChanged();
  }

  public void addListener(Consumer<Integer> listener) {
    myListeners.add(listener);
  }

  private void fireValueChanged() {
    for (Consumer<Integer> listener : myListeners) {
      listener.accept(myValue);
    }
  }

  // 0 - 255
  public void setValue(int value) {
    myPointerValue = valueToPointerValue(value);
    myValue = value;
  }

  public int getValue() {
    return myValue;
  }

  private int pointerValueToValue(int pointerValue) {
    pointerValue -= getPointerOffset();
    final int size = myVertical ? getHeight() : getWidth();
    float proportion = (size - JBUI.scale(23)) / 255f;
    return (int)(pointerValue / proportion);
  }

  private int valueToPointerValue(int value) {
    final int size = myVertical ? getHeight() : getWidth();
    float proportion = (size - JBUI.scale(23)) / 255f;
    return getPointerOffset() + (int)(value * proportion);
  }

  private static int getPointerOffset() {
    return JBUI.scale(11);
  }

  @Override
  public Dimension getPreferredSize() {
    return myVertical ? JBUI.size(22, 100) : JBUI.size(100, 22);
  }

  @Override
  public Dimension getMinimumSize() {
    return myVertical ? JBUI.size(22, 50) : JBUI.size(50, 22);
  }

  @Override
  public final void setToolTipText(String text) {
    //disable tooltips
  }

  @Override
  protected void paintComponent(Graphics g) {
    final Graphics2D g2d = (Graphics2D)g;

    if (myVertical) {
      g2d.setPaint(UIUtil.getGradientPaint(0f, 0f, Color.WHITE, 0f, getHeight(), Color.BLACK));
      g.fillRect(JBUI.scale(7), JBUI.scale(10), JBUI.scale(12), getHeight() - JBUI.scale(20));

      g.setColor(Gray._150);
      g.drawRect(JBUI.scale(7), JBUI.scale(10), JBUI.scale(12), getHeight() - JBUI.scale(20));

      g.setColor(Gray._250);
      g.drawRect(JBUI.scale(8), JBUI.scale(11), JBUI.scale(10), getHeight() - JBUI.scale(22));
    }
    else {
      g2d.setPaint(UIUtil.getGradientPaint(0f, 0f, Color.WHITE, getWidth(), 0f, Color.BLACK));
      g.fillRect(JBUI.scale(10), JBUI.scale(7), getWidth() - JBUI.scale(20), JBUI.scale(12));

      g.setColor(Gray._150);
      g.drawRect(JBUI.scale(10), JBUI.scale(7), getWidth() - JBUI.scale(20), JBUI.scale(12));

      g.setColor(Gray._250);
      g.drawRect(JBUI.scale(11), JBUI.scale(8), getWidth() - JBUI.scale(22), JBUI.scale(10));
    }

    drawKnob(g2d, myVertical ? JBUI.scale(7) : myPointerValue, myVertical ? myPointerValue : JBUI.scale(7), myVertical);
  }

  private static void drawKnob(Graphics2D g2d, int x, int y, boolean vertical) {
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    if (vertical) {
      y -= JBUI.scale(6);

      Polygon arrowShadow = new Polygon();
      arrowShadow.addPoint(x - JBUI.scale(5), y + JBUI.scale(1));
      arrowShadow.addPoint(x + JBUI.scale(7), y + JBUI.scale(7));
      arrowShadow.addPoint(x - JBUI.scale(5), y + JBUI.scale(13));

      g2d.setColor(new Color(0, 0, 0, 70));
      g2d.fill(arrowShadow);

      Polygon arrowHead = new Polygon();
      arrowHead.addPoint(x - JBUI.scale(6), y);
      arrowHead.addPoint(x + JBUI.scale(6), y + JBUI.scale(6));
      arrowHead.addPoint(x - JBUI.scale(6), y + JBUI.scale(12));

      g2d.setColor(new Color(153, 51, 0));
      g2d.fill(arrowHead);
    }
    else {
      x -= JBUI.scale(6);

      Polygon arrowShadow = new Polygon();
      arrowShadow.addPoint(x + JBUI.scale(1), y - JBUI.scale(5));
      arrowShadow.addPoint(x + JBUI.scale(13), y - JBUI.scale(5));
      arrowShadow.addPoint(x + JBUI.scale(7), y + JBUI.scale(7));

      g2d.setColor(new Color(0, 0, 0, 70));
      g2d.fill(arrowShadow);

      Polygon arrowHead = new Polygon();
      arrowHead.addPoint(x, y - JBUI.scale(6));
      arrowHead.addPoint(x + JBUI.scale(12), y - JBUI.scale(6));
      arrowHead.addPoint(x + JBUI.scale(6), y + JBUI.scale(6));

      g2d.setColor(new Color(153, 51, 0));
      g2d.fill(arrowHead);
    }
  }
}
