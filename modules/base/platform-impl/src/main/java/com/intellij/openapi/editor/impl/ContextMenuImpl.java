// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.TimerUtil;
import consulo.desktop.editor.impl.DesktopEditorPanelLayer;
import consulo.disposer.Disposable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;

/**
 * @author spleaner
 */
public final class ContextMenuImpl extends JPanel implements Disposable, DesktopEditorPanelLayer {
  public static final String ACTION_GROUP = "EditorContextBarMenu";

  private final JComponent myComponent;
  private ActionGroup myActionGroup;
  private boolean myVisible = false;
  private boolean myShow = false;
  private int myCurrentOpacity;
  private Timer myTimer;
  private DesktopEditorImpl myEditor;
  private boolean myDisposed;
  private ActionToolbar myActionToolbar;

  public ContextMenuImpl(@Nonnull final JScrollPane container, @Nonnull final DesktopEditorImpl editor) {
    setLayout(new BorderLayout());
    myEditor = editor;

    final ActionManager actionManager = ActionManager.getInstance();

    editor.addEditorMouseListener(new EditorMouseListener() {
      @Override
      public void mouseExited(@Nonnull final EditorMouseEvent e) {
        if (!isInsideActivationArea(container, e.getMouseEvent().getPoint())) {
          toggleContextToolbar(false);
        }
      }
    });

    editor.addEditorMouseMotionListener(new EditorMouseMotionListener() {
      @Override
      public void mouseMoved(@Nonnull final EditorMouseEvent e) {
        toggleContextToolbar(isInsideActivationArea(container, e.getMouseEvent().getPoint()));
      }
    });

    AnAction action = actionManager.getAction(ACTION_GROUP);
    if (action == null) {
      action = new DefaultActionGroup();
      actionManager.registerAction(ACTION_GROUP, action);
    }

    if (action instanceof ActionGroup) {
      myActionGroup = (ActionGroup)action;
    }

    myComponent = createComponent();
    add(myComponent);

    setVisible(false);
    setOpaque(false);
  }

  private static boolean isInsideActivationArea(JScrollPane container, Point p) {
    final JViewport viewport = container.getViewport();
    final Rectangle r = viewport.getBounds();
    final Point viewPosition = viewport.getViewPosition();

    final Rectangle activationArea = new Rectangle(0, 0, r.width, r.height);
    return activationArea.contains(p.x, p.y - viewPosition.y);
  }

  public static boolean mayShowToolbar(@Nullable final Document document) {
    if (document == null) {
      return false;
    }

    final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    return file != null && file.isValid();
  }

  private void toggleContextToolbar(final boolean show) {
    if (myShow != show) {
      myShow = show;
      restartTimer();
    }
  }

  private void restartTimer() {
    if (myTimer != null && myTimer.isRunning()) {
      myTimer.stop();
    }

    myTimer = TimerUtil.createNamedTimer("Restart context menu", 500, e -> {
      if (myDisposed) return;

      if (myTimer != null && myTimer.isRunning()) myTimer.stop();

      myActionToolbar.updateActionsImmediately();
      if (((Container)myActionToolbar).getComponentCount() == 0) {
        myShow = false;
        return;
      }

      myTimer = TimerUtil.createNamedTimer("Restart context menu now", 50, e1 -> {
        if (myShow) {
          if (myVisible) {
            scheduleHide();
            return;
          }

          ContextMenuImpl.this.setVisible(true);

          myCurrentOpacity += 20;
          if (myCurrentOpacity >= 100) {
            myCurrentOpacity = 100;
            myVisible = true;
            myTimer.stop();

            scheduleHide();
          }
        }
        else {
          if (!myVisible) {
            if (myTimer != null && myTimer.isRunning()) myTimer.stop();
            return;
          }

          myCurrentOpacity -= 20;
          if (myCurrentOpacity <= 0) {
            myCurrentOpacity = 0;
            myVisible = false;
            setVisible(false);
          }
        }
        repaint();
      });

      myTimer.setRepeats(true);
      myTimer.start();
    });

    myTimer.setRepeats(false);
    myTimer.start();
  }

  @Override
  public void dispose() {
    myDisposed = true;
    myEditor = null;

    if (myTimer != null) {
      myTimer.stop();
      myTimer = null;
    }
  }

  private void scheduleHide() {
    if (myTimer != null && myTimer.isRunning()) {
      myTimer.stop();
    }

    myTimer = TimerUtil.createNamedTimer("Hide context menu", 1500, e -> {
      if (myDisposed) return;

      if (myComponent.isVisible()) {
        final PointerInfo pointerInfo = MouseInfo.getPointerInfo();
        if (pointerInfo != null) {
          final Point location = pointerInfo.getLocation();
          SwingUtilities.convertPointFromScreen(location, myComponent);
          if (!myComponent.getBounds().contains(location)) {
            toggleContextToolbar(false);
          }
          else {
            scheduleHide();
          }
        }
      }
    });

    myTimer.setRepeats(false);
    myTimer.start();
  }

  private JComponent createComponent() {
    myActionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.CONTEXT_TOOLBAR, myActionGroup, true);
    myActionToolbar.setTargetComponent(myEditor.getContentComponent());
    myActionToolbar.setMinimumButtonSize(new Dimension(22, 22));
    myActionToolbar.setReservePlaceAutoPopupIcon(false);

    ContextMenuPanel contextMenuPanel = new ContextMenuPanel(this);
    contextMenuPanel.setLayout(new BorderLayout(0, 0));
    JComponent toolbarComponent = myActionToolbar.getComponent();
    toolbarComponent.setOpaque(false);
    contextMenuPanel.add(toolbarComponent);

    return contextMenuPanel;
  }

  @Override
  public int getPositionYInLayer() {
    return 50;
  }

  private static class ContextMenuPanel extends JPanel {
    private final ContextMenuImpl myContextMenu;

    private ContextMenuPanel(final ContextMenuImpl contextMenu) {
      myContextMenu = contextMenu;
      setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
      setOpaque(false);
    }

    @Override
    protected void paintChildren(final Graphics g) {
      Graphics2D graphics = (Graphics2D)g.create();
      try {
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, myContextMenu.myCurrentOpacity / 100.0f));
        super.paintChildren(graphics);
      }
      finally {
        graphics.dispose();
      }
    }

    @Override
    public void paint(Graphics g) {
      paintComponent(g);
      super.paint(g);
    }

    @Override
    public void paintComponent(final Graphics g) {
      Rectangle r = getBounds();
      Graphics2D graphics = (Graphics2D)g.create();
      try {
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, myContextMenu.myCurrentOpacity / 600.0f));
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.GRAY);
        graphics.fillRoundRect(0, 0, r.width - 1, r.height - 1, 6, 6);
      }
      finally {
        graphics.dispose();
      }
    }
  }
}
