/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package consulo.desktop.awt.execution.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.TerminalPanel;
import consulo.codeEditor.impl.ComplementaryFontsRegistry;
import consulo.codeEditor.impl.FontInfo;
import consulo.disposer.Disposable;
import consulo.document.FileDocumentManager;
import consulo.ide.impl.idea.ide.GeneralSettings;
import consulo.desktop.awt.ui.IdeEventQueue;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.CopyPasteManager;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.internal.JBHiDPIScaledImage;
import consulo.ui.ex.awt.util.UISettingsUtil;
import org.intellij.lang.annotations.JdkConstants;

import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/* -*-mode:java; c-basic-offset:2; -*- */
public class JBTerminalPanel extends TerminalPanel implements FocusListener, TerminalSettingsListener, Disposable, Predicate<AWTEvent> {
    private static final String[] ACTIONS_TO_SKIP = new String[]{
        "ActivateTerminalToolWindow",
        "ActivateMessagesToolWindow",
        "ActivateFavoritesToolWindow",
        "ActivateFindToolWindow",
        "ActivateRunToolWindow",
        "ActivateDebugToolWindow",
        "ActivateTODOToolWindow",
        "ActivateStructureToolWindow",
        "ActivateHierarchyToolWindow",
        "ActivateChangesToolWindow",

        "ShowBookmarks",
        "GotoBookmark0",
        "GotoBookmark1",
        "GotoBookmark2",
        "GotoBookmark3",
        "GotoBookmark4",
        "GotoBookmark5",
        "GotoBookmark6",
        "GotoBookmark7",
        "GotoBookmark8",
        "GotoBookmark9",

        "GotoAction",
        "GotoFile",
        "GotoClass",
        "GotoSymbol",

        "ShowSettings"
    };

    private List<AnAction> myActionsToSkip;

    public JBTerminalPanel(
        @Nonnull JBTerminalSystemSettingsProvider settingsProvider,
        @Nonnull TerminalTextBuffer backBuffer,
        @Nonnull StyleState styleState
    ) {
        super(settingsProvider, backBuffer, styleState);

        JBTerminalWidget.convertActions(this, getActions(), input ->
        {
            JBTerminalPanel.this.handleKeyEvent(input);
            return true;
        });

        registerKeymapActions(this);

        addFocusListener(this);

        ((JBTerminalSystemSettingsProvider) mySettingsProvider).addListener(this);
    }

    private static void registerKeymapActions(final TerminalPanel terminalPanel) {

        ActionManager actionManager = ActionManager.getInstance();
        for (String actionId : ACTIONS_TO_SKIP) {
            final AnAction action = actionManager.getAction(actionId);
            if (action != null) {
                AnAction a = new DumbAwareAction() {
                    @Override
                    public void actionPerformed(AnActionEvent e) {
                        if (e.getInputEvent() instanceof KeyEvent) {
                            AnActionEvent event = new AnActionEvent(e.getInputEvent(), e.getDataContext(),
                                e.getPlace(), new Presentation(), e.getActionManager(), e.getModifiers()
                            );
                            action.update(event);
                            if (event.getPresentation().isEnabled()) {
                                action.actionPerformed(event);
                            }
                            else {
                                terminalPanel.handleKeyEvent((KeyEvent) event.getInputEvent());
                            }

                            event.getInputEvent().consume();
                        }
                    }
                };
                for (Shortcut sc : action.getShortcutSet().getShortcuts()) {
                    if (sc.isKeyboard() && sc instanceof KeyboardShortcut) {
                        KeyboardShortcut ksc = (KeyboardShortcut) sc;
                        a.registerCustomShortcutSet(ksc.getFirstKeyStroke().getKeyCode(),
                            ksc.getFirstKeyStroke().getModifiers(), terminalPanel
                        );
                    }
                }
            }
        }
    }

    @Override
    public boolean test(AWTEvent e) {
        if (e instanceof KeyEvent && !skipKeyEvent((KeyEvent) e)) {
            dispatchEvent(e);
            return true;
        }
        return false;
    }

    private boolean skipKeyEvent(KeyEvent e) {
        if (myActionsToSkip == null) {
            return false;
        }
        int kc = e.getKeyCode();
        return kc == KeyEvent.VK_ESCAPE || skipAction(e, myActionsToSkip);
    }

    private static boolean skipAction(KeyEvent e, List<AnAction> actionsToSkip) {
        if (actionsToSkip != null) {
            final KeyboardShortcut eventShortcut = new KeyboardShortcut(KeyStroke.getKeyStrokeForEvent(e), null);
            for (AnAction action : actionsToSkip) {
                for (Shortcut sc : action.getShortcutSet().getShortcuts()) {
                    if (sc.isKeyboard() && sc.startsWith(eventShortcut)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    @Override
    protected void setupAntialiasing(Graphics graphics) {
        UIUtil.setupComposite((Graphics2D) graphics);
        UISettingsUtil.setupAntialiasing(graphics);
    }

    @Override
    protected void setCopyContents(StringSelection selection) {
        CopyPasteManager.getInstance().setContents(selection);
    }

    @Override
    protected void drawImage(Graphics2D gfx, BufferedImage image, int x, int y, ImageObserver observer) {
        UIUtil.drawImage(gfx, image, x, y, observer);
    }

    @Override
    protected void drawImage(
        Graphics2D g,
        BufferedImage image,
        int dx1,
        int dy1,
        int dx2,
        int dy2,
        int sx1,
        int sy1,
        int sx2,
        int sy2
    ) {
        drawImage(g, image, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);
    }

    public static void drawImage(
        Graphics g,
        Image image,
        int dx1,
        int dy1,
        int dx2,
        int dy2,
        int sx1,
        int sy1,
        int sx2,
        int sy2,
        ImageObserver observer
    ) {
        if (image instanceof JBHiDPIScaledImage) {
            final Graphics2D newG = (Graphics2D) g.create(0, 0, image.getWidth(observer), image.getHeight(observer));
            newG.scale(0.5, 0.5);
            Image img = ((JBHiDPIScaledImage) image).getDelegate();
            if (img == null) {
                img = image;
            }
            newG.drawImage(img, 2 * dx1, 2 * dy1, 2 * dx2, 2 * dy2, sx1 * 2, sy1 * 2, sx2 * 2, sy2 * 2, observer);
            newG.scale(1, 1);
            newG.dispose();
        }
        else {
            g.drawImage(image, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer);
        }
    }

    @Override
    protected boolean isRetina() {
        return UIUtil.isRetina();
    }

    @Override
    protected String getClipboardContent(Clipboard clipboard) throws IOException, UnsupportedFlavorException {
        return CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
    }

    @Override
    protected BufferedImage createBufferedImage(int width, int height) {
        return UIUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    @Override
    public void focusGained(FocusEvent event) {
        installKeyDispatcher();

        if (GeneralSettings.getInstance().isSaveOnFrameDeactivation()) {
            FileDocumentManager.getInstance().saveAllDocuments();
        }
    }

    private void installKeyDispatcher() {
        JBTerminalSystemSettingsProvider settingsProvider = (JBTerminalSystemSettingsProvider) mySettingsProvider;
        if (settingsProvider.getTerminalConsoleSettings().isOverrideIdeShortcuts()) {
            myActionsToSkip = setupActionsToSkip();
            IdeEventQueue.getInstance().addDispatcher(this, this);
        }
        else {
            myActionsToSkip = null;
        }
    }

    private static List<AnAction> setupActionsToSkip() {
        List<AnAction> res = new ArrayList<>();
        ActionManager actionManager = ActionManager.getInstance();
        for (String actionId : ACTIONS_TO_SKIP) {
            AnAction action = actionManager.getAction(actionId);
            if (action != null) {
                res.add(action);
            }
        }
        return res;
    }

    @Override
    public void focusLost(FocusEvent event) {
        if (myActionsToSkip != null) {
            myActionsToSkip = null;
            IdeEventQueue.getInstance().removeDispatcher(this);
        }

        JBTerminalStarter.refreshAfterExecution();
    }

    @Override
    protected Font getFontToDisplay(char c, TextStyle style) {
        FontInfo fontInfo = fontForChar(c, style.hasOption(TextStyle.Option.BOLD) ? Font.BOLD : Font.PLAIN);
        return fontInfo.getFont();
    }

    public FontInfo fontForChar(final char c, @JdkConstants.FontStyle int style) {
        return ComplementaryFontsRegistry.getFontAbleToDisplay(
            c,
            style,
            ((JBTerminalSystemSettingsProvider) mySettingsProvider).getColorScheme()
                .getConsoleFontPreferences()
        );
    }

    @Override
    public void fontChanged() {
        reinitFontAndResize();
    }

    @Override
    public void dispose() {
        ((JBTerminalSystemSettingsProvider) mySettingsProvider).removeListener(this);
    }
}
