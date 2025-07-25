/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package consulo.desktop.awt.welcomeScreen;

import consulo.desktop.awt.internal.notification.NotificationBalloonShadowBorderProvider;
import consulo.desktop.awt.internal.notification.NotificationsManagerImpl;
import consulo.desktop.awt.ui.popup.BalloonImpl;
import consulo.desktop.awt.uiOld.BalloonLayoutData;
import consulo.desktop.awt.uiOld.DesktopBalloonLayoutImpl;
import consulo.disposer.Disposer;
import consulo.localize.LocalizeValue;
import consulo.platform.Platform;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.ui.notification.NotificationType;
import consulo.ui.Rectangle2D;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.awt.AbstractLayoutManager;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.NonOpaquePanel;
import consulo.ui.ex.popup.Balloon;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static consulo.desktop.awt.internal.notification.NotificationsManagerImpl.BORDER_COLOR;
import static consulo.desktop.awt.internal.notification.NotificationsManagerImpl.FILL_COLOR;

/**
 * @author Alexander Lobas
 */
public class WelcomeDesktopBalloonLayoutImpl extends DesktopBalloonLayoutImpl {
  private static final String TYPE_KEY = "Type";

  private final Consumer<List<NotificationType>> myListener;
  private final Supplier<Point> myButtonLocation;
  private BalloonImpl myPopupBalloon;
  private final BalloonPanel myBalloonPanel = new BalloonPanel();
  private boolean myVisible;

  public WelcomeDesktopBalloonLayoutImpl(@Nonnull JRootPane parent,
                                         @Nonnull Insets insets,
                                         @Nonnull Consumer<List<NotificationType>> listener,
                                         @Nonnull Supplier<Point> buttonLocation) {
    super(parent, insets);
    myListener = listener;
    myButtonLocation = buttonLocation;
  }

  @Override
  public void dispose() {
    super.dispose();
    if (myPopupBalloon != null) {
      Disposer.dispose(myPopupBalloon);
      myPopupBalloon = null;
    }
  }

  @Override
  public boolean isForWelcomeFrame() {
    return true;
  }

  @Override
  public void add(@Nonnull Balloon balloon, @Nullable Object layoutData) {
    if (layoutData instanceof BalloonLayoutData && ((BalloonLayoutData)layoutData).welcomeScreen) {
      addToPopup((BalloonImpl)balloon, (BalloonLayoutData)layoutData);
    }
    else {
      super.add(balloon, layoutData);
    }
  }

  private void addToPopup(@Nonnull BalloonImpl balloon, @Nonnull BalloonLayoutData layoutData) {
    layoutData.doLayout = this::layoutPopup;
    layoutData.configuration = layoutData.configuration.replace(JBUI.scale(myPopupBalloon == null ? 7 : 5), JBUI.scale(12));

    if (myPopupBalloon == null) {
      final JScrollPane pane = NotificationsManagerImpl.createBalloonScrollPane(myBalloonPanel, true);
      pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      pane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

      pane.getVerticalScrollBar().addComponentListener(new ComponentAdapter() {
        @Override
        public void componentShown(ComponentEvent e) {
          int top = Platform.current().os().isMac() ? 2 : 1;
          pane.setBorder(JBUI.Borders.empty(top, 0, 1, 1));
        }

        @Override
        public void componentHidden(ComponentEvent e) {
          pane.setBorder(JBUI.Borders.empty());
        }
      });

      myPopupBalloon =
              new BalloonImpl(pane, BORDER_COLOR, new Insets(0, 0, 0, 0), FILL_COLOR, true, false, false, true, false, true, 0, false, false, null, false, 0, 0, 0, 0, false, null, null, false, false,
                              true, null, false, null, -1);
      myPopupBalloon.setAnimationEnabled(false);
      myPopupBalloon.setShadowBorderProvider(new NotificationBalloonShadowBorderProvider(FILL_COLOR, BORDER_COLOR));
      myPopupBalloon.setHideListener(() -> myPopupBalloon.getComponent().setVisible(false));
      myPopupBalloon.setActionProvider(new BalloonImpl.ActionProvider() {
        private BalloonImpl.ActionButton myAction;

        @Nonnull
        @Override
        public List<BalloonImpl.ActionButton> createActions() {
          myAction = myPopupBalloon.new ActionButton(PlatformIconGroup.ideNotificationClose(), null, LocalizeValue.empty(), event -> {});
          return Collections.singletonList(myAction);
        }

        @Override
        public void layout(@Nonnull Rectangle2D bounds) {
          myAction.setBounds(0, 0, 0, 0);
        }
      });
    }

    myBalloonPanel.add(balloon.getContent());
    balloon.getContent().putClientProperty(TYPE_KEY, layoutData.type);
    Disposer.register(
      balloon,
      () -> {
        myBalloons.remove(balloon);
        myBalloonPanel.remove(balloon.getContent());
        updatePopup();
      }
    );
    myBalloons.add(balloon);

    updatePopup();
  }

  public void showPopup() {
    layoutPopup();
    if (myVisible) {
      myPopupBalloon.getComponent().setVisible(true);
    }
    else {
      myPopupBalloon.show(myLayeredPane);
      myVisible = true;
    }
  }

  @Override
  public void queueRelayout() {
    if (myVisible) {
      layoutPopup();
    }
  }

  private void layoutPopup() {
    Dimension layeredSize = myLayeredPane.getSize();
    Dimension size = new Dimension(myPopupBalloon.getPreferredSize());
    Point location = myButtonLocation.get();
    int x = layeredSize.width - size.width - 5;
    int fullHeight = location.y;

    if (x > location.x) {
      x = location.x - 20;
    }
    if (size.height > fullHeight) {
      size.height = fullHeight;
    }

    myPopupBalloon.setBounds(new Rectangle(x, fullHeight - size.height, size.width, size.height));
  }

  private void updatePopup() {
    int count = myBalloonPanel.getComponentCount();
    List<NotificationType> types = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      types.add((NotificationType)((JComponent)myBalloonPanel.getComponent(i)).getClientProperty(TYPE_KEY));
    }
    myListener.accept(types);

    if (myVisible) {
      if (count == 0) {
        myPopupBalloon.getComponent().setVisible(false);
      }
      else {
        layoutPopup();
      }
    }
  }

  private static class BalloonPanel extends NonOpaquePanel {
    public BalloonPanel() {
      super(new AbstractLayoutManager() {
        @Override
        public Dimension preferredLayoutSize(Container parent) {
          int count = parent.getComponentCount();
          int width = 0;
          int height = 0;
          for (int i = 0; i < count; i++) {
            Dimension size = parent.getComponent(i).getPreferredSize();
            width = Math.max(width, size.width);
            height += size.height;
          }
          height += count - 1;
          return new Dimension(width + JBUI.scale(32), height);
        }

        @Override
        public void layoutContainer(Container parent) {
          int count = parent.getComponentCount();
          int width = parent.getWidth() - JBUI.scale(32);
          int height = parent.getHeight();
          if (count == 1) {
            parent.getComponent(0).setBounds(JBUI.scale(16), 0, width, height);
          }
          else {
            int y = 0;
            for (int i = 0; i < count; i++) {
              Component component = parent.getComponent(i);
              Dimension size = component.getPreferredSize();
              component.setBounds(JBUI.scale(16), y, width, size.height);
              y += size.height + JBUI.scale(2);
            }
          }
        }
      });
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);

      int count = getComponentCount() - 1;
      if (count > 0) {
        int x2 = getWidth() - JBUI.scale(16);
        int y = 0;

        g.setColor(new JBColor(0xD0D0D0, 0x717375));

        for (int i = 0; i < count; i++) {
          Dimension size = getComponent(i).getPreferredSize();
          y += size.height + 1;
          g.drawLine(JBUI.scale(16), y, x2, y);
        }
      }
    }
  }
}