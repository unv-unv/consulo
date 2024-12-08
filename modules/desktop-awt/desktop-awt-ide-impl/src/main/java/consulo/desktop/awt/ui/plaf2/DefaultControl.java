// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.desktop.awt.ui.plaf2;

import consulo.ui.ex.awt.UIUtil;

import jakarta.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;

public final class DefaultControl implements Control {
  private final Icon expandedDefault = UIUtil.getTreeExpandedIcon();
  private final Icon collapsedDefault = UIUtil.getTreeCollapsedIcon();

  @Nonnull
  @Override
  public Icon getIcon(boolean expanded, boolean selected) {
    return !selected ? expanded ? expandedDefault : collapsedDefault : expanded ? expandedDefault : collapsedDefault;
  }

  @Override
  public int getWidth() {
    return Math.max(Math.max(expandedDefault.getIconWidth(), collapsedDefault.getIconWidth()), Math.max(expandedDefault.getIconWidth(), collapsedDefault.getIconWidth()));
  }

  @Override
  public int getHeight() {
    return Math.max(Math.max(expandedDefault.getIconHeight(), collapsedDefault.getIconHeight()), Math.max(expandedDefault.getIconHeight(), collapsedDefault.getIconHeight()));
  }

  @Override
  public void paint(@Nonnull Component c, @Nonnull Graphics g, int x, int y, int width, int height, boolean expanded, boolean selected) {
    Icon icon = getIcon(expanded, selected);
    icon.paintIcon(c, g, x + (width - icon.getIconWidth()) / 2, y + (height - icon.getIconHeight()) / 2);
  }
}
