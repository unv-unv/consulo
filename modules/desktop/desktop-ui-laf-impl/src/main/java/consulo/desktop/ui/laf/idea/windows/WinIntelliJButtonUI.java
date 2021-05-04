// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.desktop.ui.laf.idea.windows;

import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;
import consulo.awt.TargetAWT;
import consulo.desktop.ui.laf.idea.darcula.DarculaButtonUI;
import consulo.desktop.ui.laf.idea.darcula.DarculaUIUtil;
import consulo.desktop.ui.laf.windows.icon.WindowsIconGroup;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import static consulo.desktop.ui.laf.idea.windows.WinIntelliJTextBorder.MINIMUM_HEIGHT;


/**
 * @author Konstantin Bulenkov
 */
public class WinIntelliJButtonUI extends DarculaButtonUI {
  static final float DISABLED_ALPHA_LEVEL = 0.47f;

  private final PropertyChangeListener helpButtonListener = new PropertyChangeListener() {
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      final Object source = evt.getSource();
      if (source instanceof AbstractButton) {
        if (UIUtil.isHelpButton((JComponent)source)) {
          ((AbstractButton)source).setOpaque(false);
        }
      }
    }
  };

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    ((AbstractButton)c).setRolloverEnabled(true);
    return new WinIntelliJButtonUI();
  }

  @Override
  protected void installListeners(AbstractButton b) {
    super.installListeners(b);
    b.addPropertyChangeListener("JButton.buttonType", helpButtonListener);
  }

  @Override
  protected void uninstallListeners(AbstractButton b) {
    b.removePropertyChangeListener("JButton.buttonType", helpButtonListener);
    super.uninstallListeners(b);
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    if (UIUtil.isHelpButton(c)) {
      Icon help = TargetAWT.to(WindowsIconGroup.winHelp());
      Insets i = c.getInsets();
      help.paintIcon(c, g, i.left, i.top + (c.getHeight() - help.getIconHeight()) / 2);
    }
    else if (c instanceof AbstractButton) {
      AbstractButton b = (AbstractButton)c;
      Graphics2D g2 = (Graphics2D)g.create();
      try {
        Rectangle r = new Rectangle(c.getSize());
        Container parent = c.getParent();
        if (c.isOpaque() && parent != null) {
          g2.setColor(parent.getBackground());
          g2.fill(r);
        }

        JBInsets.removeFrom(r, JBUI.insets(2));

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

        if (!b.isEnabled()) {
          g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DISABLED_ALPHA_LEVEL));
        }
        if (b.isContentAreaFilled()) {
          g2.setColor(getBackgroundColor(b));
          g2.fill(r);
        }

        paintContents(g2, b);
      }
      finally {
        g2.dispose();
      }
    }
  }

  @Override
  protected void modifyViewRect(AbstractButton b, Rectangle rect) {
    super.modifyViewRect(b, rect);
    rect.y -= JBUIScale.scale(1); // Move one pixel up
  }

  @Override
  protected int getMinimumHeight() {
    return MINIMUM_HEIGHT.get();
  }

  @Override
  protected void setupDefaultButton(JComponent button, Graphics g) {
  }

  @Override
  protected void paintDisabledText(Graphics g, String text, JComponent c, Rectangle textRect, FontMetrics metrics) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setColor(UIManager.getColor("Button.disabledText"));
      BasicGraphicsUtils.drawStringUnderlineCharAt(c, g2, text, -1, textRect.x + getTextShiftOffset(), textRect.y + metrics.getAscent() + getTextShiftOffset());
    }
    finally {
      g2.dispose();
    }
  }

  @Override
  protected Color getButtonTextColor(AbstractButton button) {
    Color focusedColor = (Color)button.getClientProperty("JButton.focusedTextColor");
    Color textColor = (Color)button.getClientProperty("JButton.textColor");

    boolean focusedStyle = button.hasFocus() || button.getModel().isRollover();

    if (focusedStyle && focusedColor != null) {
      return focusedColor;
    }
    else if (!focusedStyle && textColor != null) {
      return textColor;
    }
    else {
      return DarculaUIUtil.getButtonTextColor(button);
    }
  }

  @Override
  protected int getMnemonicIndex(AbstractButton b) {
    return b.getDisplayedMnemonicIndex();
  }

  private static Color getBackgroundColor(AbstractButton b) {
    ButtonModel bm = b.getModel();

    Color focusedColor = (Color)b.getClientProperty("JButton.focusedBackgroundColor");
    if (bm.isPressed()) {
      return focusedColor != null ? focusedColor : UIManager.getColor("Button.intellij.native.pressedBackgroundColor");
    }
    else if (b.hasFocus() || bm.isRollover()) {
      return focusedColor != null ? focusedColor : UIManager.getColor("Button.intellij.native.focusedBackgroundColor");
    }
    else {
      Color backgroundColor = (Color)b.getClientProperty("JButton.backgroundColor");
      return backgroundColor != null ? backgroundColor : b.getBackground();
    }
  }
}
