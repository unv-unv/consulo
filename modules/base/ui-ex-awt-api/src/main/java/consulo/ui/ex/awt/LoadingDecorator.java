// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ui.ex.awt;

import consulo.annotation.DeprecationInfo;
import consulo.application.ui.RemoteDesktopService;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.localize.LocalizeValue;
import consulo.platform.base.localize.CommonLocalize;
import consulo.ui.ex.awt.util.Alarm;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class LoadingDecorator {
    JLayeredPane myPane;

    LoadingLayer myLoadingLayer;
    Animator myFadeOutAnimator;

    int myDelay;
    Alarm myStartAlarm;
    boolean myStartRequest;

    public LoadingDecorator(JComponent content, @Nonnull Disposable parent, int startDelayMs) {
        this(content, parent, startDelayMs, false);
    }

    public LoadingDecorator(JComponent content, @Nonnull Disposable parent, int startDelayMs, boolean useMinimumSize) {
        this(content, parent, startDelayMs, useMinimumSize, new AsyncProcessIcon.Big("Loading"));
    }

    public LoadingDecorator(
        JComponent content,
        @Nonnull Disposable parent,
        int startDelayMs,
        boolean useMinimumSize,
        @Nonnull AsyncProcessIcon icon
    ) {
        myPane = new MyLayeredPane(useMinimumSize ? content : null);
        myLoadingLayer = new LoadingLayer(icon);
        myDelay = startDelayMs;
        myStartAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, parent);

        setLoadingText(CommonLocalize.treeNodeLoading());

        myFadeOutAnimator = new Animator("Loading", 10, RemoteDesktopService.isRemoteSession() ? 2500 : 500, false) {
            @Override
            public void paintNow(int frame, int totalFrames, int cycle) {
                myLoadingLayer.setAlpha(1f - ((float) frame) / totalFrames);
            }

            @Override
            protected void paintCycleEnd() {
                myLoadingLayer.setAlpha(0); // paint with zero alpha before hiding completely
                hideLoadingLayer();
                myLoadingLayer.setAlpha(-1);
            }
        };
        Disposer.register(parent, myFadeOutAnimator);

        myPane.add(content, JLayeredPane.DEFAULT_LAYER, 0);

        Disposer.register(parent, myLoadingLayer.myProgress);
    }

    /**
     * Removes a loading layer to restore a blit-accelerated scrolling.
     */
    private void hideLoadingLayer() {
        myPane.remove(myLoadingLayer);
        myLoadingLayer.setVisible(false);
    }

    /**
     * Placing the invisible layer on top of JViewport suppresses blit-accelerated scrolling
     * as JViewport.canUseWindowBlitter() doesn't take component's visibility into account.
     *
     * We need to add / remove the loading layer on demand to preserve the blit-based scrolling.
     *
     * Blit-acceleration copies as much of the rendered area as possible and then repaints only newly exposed region.
     * This helps to improve scrolling performance and to reduce CPU usage (especially if drawing is compute-intensive).
     */
    private void addLoadingLayerOnDemand() {
        if (myPane != myLoadingLayer.getParent()) {
            myPane.add(myLoadingLayer, JLayeredPane.DRAG_LAYER, 1);
        }
    }

    protected NonOpaquePanel customizeLoadingLayer(JPanel parent, JLabel text, AsyncProcessIcon icon) {
        parent.setLayout(new GridBagLayout());

        Font font = text.getFont();
        text.setFont(font.deriveFont(font.getStyle(), font.getSize() + 8));
        //text.setForeground(Color.black);

        int gap = new JLabel().getIconTextGap();
        NonOpaquePanel result = new NonOpaquePanel(new FlowLayout(FlowLayout.CENTER, gap * 3, 0));
        result.add(icon);
        result.add(text);
        parent.add(result);

        return result;
    }

    public JComponent getComponent() {
        return myPane;
    }

    public void startLoading(boolean takeSnapshot) {
        if (isLoading() || myStartRequest || myStartAlarm.isDisposed()) {
            return;
        }

        myStartRequest = true;
        if (myDelay > 0) {
            myStartAlarm.addRequest(() -> UIUtil.invokeLaterIfNeeded(() -> {
                if (!myStartRequest) {
                    return;
                }
                _startLoading(takeSnapshot);
            }), myDelay);
        }
        else {
            _startLoading(takeSnapshot);
        }
    }

    protected void _startLoading(boolean takeSnapshot) {
        addLoadingLayerOnDemand();
        myLoadingLayer.setVisible(true, takeSnapshot);
    }

    public void stopLoading() {
        myStartRequest = false;
        myStartAlarm.cancelAllRequests();

        if (!isLoading()) {
            return;
        }

        myLoadingLayer.setVisible(false, false);
    }

    public String getLoadingText() {
        return myLoadingLayer.myText.getText();
    }

    public void setLoadingText(LocalizeValue loadingText) {
        myLoadingLayer.myText.setText(loadingText.get());
    }

    @Deprecated
    @DeprecationInfo("Use variant with LocalizeValue")
    public void setLoadingText(String loadingText) {
        myLoadingLayer.myText.setText(loadingText);
    }

    public boolean isLoading() {
        return myLoadingLayer.isLoading();
    }

    private final class LoadingLayer extends JPanel {
        private final JLabel myText = new JLabel("", SwingConstants.CENTER);

        private BufferedImage mySnapshot;
        private Color mySnapshotBg;

        private final AsyncProcessIcon myProgress;

        private boolean myVisible;

        private float myCurrentAlpha;
        private final NonOpaquePanel myTextComponent;

        private LoadingLayer(@Nonnull AsyncProcessIcon processIcon) {
            setOpaque(false);
            setVisible(false);
            myProgress = processIcon;
            myProgress.setOpaque(false);
            myTextComponent = customizeLoadingLayer(this, myText, myProgress);
            myProgress.suspend();
        }

        public void setVisible(boolean visible, boolean takeSnapshot) {
            if (myVisible == visible) {
                return;
            }

            if (myVisible && myCurrentAlpha != -1) {
                return;
            }

            myVisible = visible;
            myFadeOutAnimator.reset();
            if (myVisible) {
                setVisible(true);
                myCurrentAlpha = -1;

                if (takeSnapshot && getWidth() > 0 && getHeight() > 0) {
                    mySnapshot = ImageUtil.createImage(getGraphics(), getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = mySnapshot.createGraphics();
                    myPane.paint(g);
                    Component opaque = UIUtil.findNearestOpaque(this);
                    mySnapshotBg = opaque != null ? opaque.getBackground() : UIUtil.getPanelBackground();
                    g.dispose();
                }
                myProgress.resume();

                myFadeOutAnimator.suspend();
            }
            else {
                disposeSnapshot();
                myProgress.suspend();

                myFadeOutAnimator.resume();
            }
        }

        public boolean isLoading() {
            return myVisible;
        }

        private void disposeSnapshot() {
            if (mySnapshot != null) {
                mySnapshot.flush();
                mySnapshot = null;
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (mySnapshot != null) {
                if (mySnapshot.getWidth() == getWidth() && mySnapshot.getHeight() == getHeight()) {
                    g.drawImage(mySnapshot, 0, 0, getWidth(), getHeight(), null);
                    g.setColor(new Color(200, 200, 200, 240));
                    g.fillRect(0, 0, getWidth(), getHeight());
                    return;
                }
                else {
                    disposeSnapshot();
                }
            }

            if (mySnapshotBg != null) {
                g.setColor(mySnapshotBg);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        }

        public void setAlpha(float alpha) {
            myCurrentAlpha = alpha;
            paintImmediately(myTextComponent.getBounds());
        }

        @Override
        protected void paintChildren(Graphics g) {
            if (myCurrentAlpha != -1) {
                ((Graphics2D) g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, myCurrentAlpha));
            }
            super.paintChildren(g);
        }
    }

    public interface CursorAware {
    }

    private static final class MyLayeredPane extends JBLayeredPane implements CursorAware {
        private final JComponent myContent;

        private MyLayeredPane(JComponent content) {
            myContent = content;
        }

        @Override
        public Dimension getMinimumSize() {
            return myContent != null && !isMinimumSizeSet() ? myContent.getMinimumSize() : super.getMinimumSize();
        }

        @Override
        public Dimension getPreferredSize() {
            return myContent != null && !isPreferredSizeSet() ? myContent.getPreferredSize() : super.getPreferredSize();
        }

        @Override
        public void doLayout() {
            super.doLayout();
            for (int i = 0; i < getComponentCount(); i++) {
                Component each = getComponent(i);
                if (each instanceof Icon) {
                    each.setBounds(0, 0, each.getWidth(), each.getHeight());
                }
                else {
                    each.setBounds(0, 0, getWidth(), getHeight());
                }
            }
        }
    }
}
