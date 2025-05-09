/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package consulo.desktop.awt.uiOld.messages;

import consulo.application.impl.internal.LaterInvocator;
import consulo.desktop.awt.ui.impl.window.JDialogAsUIWindow;
import consulo.desktop.awt.wm.impl.MacMainFrameDecorator;
import consulo.eawt.wrapper.FullScreenUtilitiesWrapper;
import consulo.logging.Logger;
import consulo.ui.ex.Gray;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.awt.Animator;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.IJSwingUtilities;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static consulo.application.ui.wm.IdeFocusManager.getGlobalInstance;

/**
 * Created by Denis Fokin
 */
public class SheetMessage {
    private static final Logger LOG = Logger.getInstance(SheetMessage.class);

    private final JDialog myWindow;
    private final Window myParent;
    private final SheetController myController;

    private final static int TIME_TO_SHOW_SHEET = 250;

    private Image staticImage;
    private int imageHeight;
    private final boolean restoreFullScreenButton;
    private final ComponentAdapter myPositionListener = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent event) {
            setPositionRelativeToParent();
        }

        @Override
        public void componentMoved(ComponentEvent event) {
            setPositionRelativeToParent();
        }
    };

    public SheetMessage(@Nullable consulo.ui.Window uiOwner,
                        final String title,
                        final String message,
                        final consulo.ui.image.Image icon,
                        final String[] buttons,
                        final DialogWrapper.DoNotAskOption doNotAskOption,
                        final String defaultButton,
                        final String focusedButton) {
        final Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        final Component recentFocusOwner = activeWindow == null ? null : activeWindow.getMostRecentFocusOwner();
        WeakReference<Component> beforeShowFocusOwner = new WeakReference<>(recentFocusOwner);

        Window owner = TargetAWT.to(uiOwner);

        maximizeIfNeeded(owner);

        myWindow = new JDialogAsUIWindow(uiOwner, true);
        myWindow.setTitle("This should not be shown");
        myWindow.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
        myWindow.getRootPane().putClientProperty("apple.awt.draggableWindowBackground", Boolean.FALSE);

        myWindow.addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(@Nonnull WindowEvent e) {
                super.windowActivated(e);
            }
        });

        myParent = owner;

        myWindow.setUndecorated(true);
        myWindow.setBackground(Gray.TRANSPARENT);
        myController = new SheetController(this, title, message, TargetAWT.to(icon), buttons, defaultButton, doNotAskOption, focusedButton);

        imageHeight = 0;
        myParent.addComponentListener(myPositionListener);
        myWindow.setFocusable(true);
        myWindow.setFocusableWindowState(true);
        myWindow.setSize(myController.SHEET_NC_WIDTH, 0);

        setWindowOpacity(0.0f);

        myWindow.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(@Nonnull ComponentEvent e) {
                super.componentShown(e);
                setWindowOpacity(1.0f);
                myWindow.setSize(myController.SHEET_NC_WIDTH, myController.SHEET_NC_HEIGHT);
            }
        });

        startAnimation(true);

        restoreFullScreenButton = couldBeInFullScreen();

        if (restoreFullScreenButton) {
            FullScreenUtilitiesWrapper.setWindowCanFullScreen(myParent, false);
        }

        LaterInvocator.enterModal(myWindow);
        myWindow.setVisible(true);
        LaterInvocator.leaveModal(myWindow);

        Component focusCandidate = beforeShowFocusOwner.get();

        if (focusCandidate == null) {
            focusCandidate = getGlobalInstance().getLastFocusedFor(getGlobalInstance().getLastFocusedFrame());
        }

        // focusCandidate is null if a welcome screen is closed and ide frame is not opened.
        // this is ok. We set focus correctly on our frame activation.
        if (focusCandidate != null) {
            final Component finalFocusCandidate = focusCandidate;
            getGlobalInstance().doWhenFocusSettlesDown(() -> getGlobalInstance().requestFocus(finalFocusCandidate, true));
        }
    }

    private static void maximizeIfNeeded(final Window owner) {
        if (owner == null) {
            return;
        }
        if (owner instanceof Frame) {
            Frame f = (Frame) owner;
            if (f.getState() == Frame.ICONIFIED) {
                f.setState(Frame.NORMAL);
            }
        }
    }

    private void setWindowOpacity(float opacity) {
        try {
            Method setOpacityMethod = myWindow.getClass().getMethod("setOpacity", Float.TYPE);
            setOpacityMethod.invoke(myWindow, opacity);
        }
        catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            LOG.error(e);
        }
    }

    private boolean couldBeInFullScreen() {
        if (myParent instanceof JFrame) {
            JRootPane rootPane = ((JFrame) myParent).getRootPane();
            return rootPane.getClientProperty(MacMainFrameDecorator.FULL_SCREEN) == null;
        }
        return false;
    }

    public boolean toBeShown() {
        return !myController.getDoNotAskResult();
    }

    public String getResult() {
        return myController.getResult();
    }

    void startAnimation(final boolean enlarge) {
        staticImage = myController.getStaticImage();
        JPanel staticPanel = new JPanel() {
            @Override
            public void paint(@Nonnull Graphics g) {
                super.paint(g);
                if (staticImage != null) {
                    Graphics2D g2d = (Graphics2D) g.create();


                    g2d.setBackground(new JBColor(new Color(255, 255, 255, 0), new Color(110, 110, 110, 0)));
                    g2d.clearRect(0, 0, myController.SHEET_NC_WIDTH, myController.SHEET_NC_HEIGHT);


                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.95f));

                    int multiplyFactor = staticImage.getWidth(null) / myController.SHEET_NC_WIDTH;

                    g.drawImage(staticImage, 0, 0, myController.SHEET_NC_WIDTH, imageHeight, 0, staticImage.getHeight(null) - imageHeight * multiplyFactor, staticImage.getWidth(null),
                        staticImage.getHeight(null), null);
                }
            }
        };
        staticPanel.setOpaque(false);
        staticPanel.setSize(myController.SHEET_NC_WIDTH, myController.SHEET_NC_HEIGHT);
        myWindow.setContentPane(staticPanel);

        Animator myAnimator = new Animator("Roll Down Sheet Animator", myController.SHEET_NC_HEIGHT, TIME_TO_SHOW_SHEET, false) {
            @Override
            public void paintNow(int frame, int totalFrames, int cycle) {
                setPositionRelativeToParent();
                float percentage = (float) frame / (float) totalFrames;
                imageHeight = enlarge ? (int) (((float) myController.SHEET_NC_HEIGHT) * percentage) : (int) (myController.SHEET_NC_HEIGHT - percentage * myController.SHEET_HEIGHT);
                myWindow.repaint();
            }

            @Override
            protected void paintCycleEnd() {
                setPositionRelativeToParent();
                if (enlarge) {
                    imageHeight = myController.SHEET_NC_HEIGHT;
                    staticImage = null;
                    myWindow.setContentPane(myController.getPanel(myWindow));

                    IJSwingUtilities.moveMousePointerOn(myWindow.getRootPane().getDefaultButton());
                    myController.requestFocus();
                }
                else {
                    if (restoreFullScreenButton) {
                        FullScreenUtilitiesWrapper.setWindowCanFullScreen(myParent, true);
                    }
                    myParent.removeComponentListener(myPositionListener);
                    myController.dispose();
                    myWindow.dispose();
                    DialogWrapper.cleanupRootPane(myWindow.getRootPane());
                }
            }
        };

        myAnimator.resume();

    }

    private void setPositionRelativeToParent() {
        int width = myParent.getWidth();
        myWindow.setBounds(width / 2 - myController.SHEET_NC_WIDTH / 2 + myParent.getLocation().x, myParent.getInsets().top + myParent.getLocation().y, myController.SHEET_NC_WIDTH,
            myController.SHEET_NC_HEIGHT);

    }
}
