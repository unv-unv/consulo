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
package consulo.ui.ex.awt;

import consulo.annotation.DeprecationInfo;
import consulo.application.ApplicationManager;
import consulo.application.ui.wm.IdeFocusManager;
import consulo.component.ComponentManager;
import consulo.disposer.Disposable;
import consulo.fileChooser.FileChooser;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.CustomShortcutSet;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.action.ShortcutSet;
import consulo.ui.ex.awt.accessibility.ScreenReader;
import consulo.ui.ex.awt.internal.GuiUtils;
import consulo.ui.ex.awt.internal.AWTHasSuffixComponent;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.keymap.util.KeymapUtil;
import consulo.ui.ex.localize.UILocalize;
import consulo.ui.image.Image;
import consulo.ui.image.ImageEffects;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

@Deprecated
@DeprecationInfo("Prefer prefix & suffix components")
public class ComponentWithBrowseButton<Comp extends JComponent> extends JPanel implements Disposable {
    private static final Logger LOG = Logger.getInstance(ComponentWithBrowseButton.class);

    private final Comp myComponent;

    @Nonnull
    private final JButton myBrowseButton;
    private boolean myButtonEnabled = true;

    public ComponentWithBrowseButton(Comp component, @Nullable ActionListener browseActionListener) {
        super(new BorderLayout());

        myComponent = component;
        // required! otherwise JPanel will occasionally gain focus instead of the component
        setFocusable(false);
        add(myComponent, BorderLayout.CENTER);


        if (ApplicationManager.getApplication() != null && AWTHasSuffixComponent.isSuffixComponent(myComponent)) {
            setOpaque(false);

            Image icon;
            if (myComponent instanceof JComboBox) {
                icon = PlatformIconGroup.generalGearplain();
            }
            else {
                icon = PlatformIconGroup.generalInlinevariables();
            }

            myBrowseButton = new JButton(TargetAWT.to(icon));

            JToolBar toolBar = new JToolBar();
            toolBar.add(myBrowseButton);

            toolBar.setBorder(JBCurrentTheme.textFieldSubBorder(false));

            AWTHasSuffixComponent.setSuffixComponent(myComponent, toolBar);
        }
        else {
            myBrowseButton = new FixedSizeButton(myComponent);

            add(centerComponentVertically(myBrowseButton), BorderLayout.EAST);
        }
        // FixedSizeButton isn't focusable but it should be selectable via keyboard.
        if (browseActionListener != null) {
            myBrowseButton.addActionListener(browseActionListener);
        }
        myBrowseButton.setToolTipText(UILocalize.componentWithBrowseButtonBrowseButtonTooltipText().get());

        //noinspection deprecation
        if (ApplicationManager.getApplication() != null) {  // avoid crash at design time
            new MyDoClickAction(myBrowseButton).registerShortcut(myComponent);
        }
        if (ScreenReader.isActive()) {
            myBrowseButton.setFocusable(true);
            myBrowseButton.getAccessibleContext().setAccessibleName("Browse");
        }

    }

    @Nonnull
    private static JPanel centerComponentVertically(@Nonnull Component component) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.add(component, new GridBagConstraints());
        return panel;
    }

    public final Comp getChildComponent() {
        return myComponent;
    }

    public void setTextFieldPreferredWidth(final int charCount) {
        final Comp comp = getChildComponent();
        Dimension size = GuiUtils.getSizeByChars(charCount, comp);
        comp.setPreferredSize(size);

        if (getComponentCount() != 1) {
            final Dimension preferredSize = myBrowseButton.getPreferredSize();
            setPreferredSize(new Dimension(
                size.width + preferredSize.width + 2,
                preferredSize.height + 2
            ));
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        myBrowseButton.setEnabled(enabled && myButtonEnabled);
        myComponent.setEnabled(enabled);
    }

    public void setButtonEnabled(boolean buttonEnabled) {
        myButtonEnabled = buttonEnabled;
        setEnabled(isEnabled());
    }

    public void setButtonIcon(@Nonnull Image icon) {
        myBrowseButton.setIcon(TargetAWT.to(icon));
        myBrowseButton.setDisabledIcon(TargetAWT.to(ImageEffects.grayed(icon)));
    }

    /**
     * Adds specified <code>listener</code> to the browse button.
     */
    public void addActionListener(ActionListener listener) {
        if (listener instanceof BrowseFolderActionListener) {
            setButtonIcon(PlatformIconGroup.nodesFolder());
        }

        myBrowseButton.addActionListener(listener);
    }

    public void removeActionListener(ActionListener listener) {
        myBrowseButton.removeActionListener(listener);
    }

    public void addBrowseFolderListener(
        @Nonnull LocalizeValue title,
        @Nonnull LocalizeValue description,
        @Nullable ComponentManager project,
        FileChooserDescriptor fileChooserDescriptor,
        TextComponentAccessor<Comp> accessor
    ) {
        addActionListener(new BrowseFolderActionListener<>(title, description, this, project, fileChooserDescriptor, accessor));
    }

    @Deprecated
    @DeprecationInfo("Use variant with LocalizeValue")
    @SuppressWarnings("deprecation")
    public void addBrowseFolderListener(
        @Nullable @Nls(capitalization = Nls.Capitalization.Title) String title,
        @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String description,
        @Nullable ComponentManager project,
        FileChooserDescriptor fileChooserDescriptor,
        TextComponentAccessor<Comp> accessor
    ) {
        addActionListener(new BrowseFolderActionListener<>(title, description, this, project, fileChooserDescriptor, accessor));
    }

    @Deprecated
    @DeprecationInfo("Use variant with LocalizeValue")
    @SuppressWarnings("deprecation")
    public void addBrowseFolderListener(
        @Nullable @Nls(capitalization = Nls.Capitalization.Title) String title,
        @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String description,
        @Nullable ComponentManager project,
        FileChooserDescriptor fileChooserDescriptor,
        TextComponentAccessor<Comp> accessor,
        boolean autoRemoveOnHide
    ) {
        addBrowseFolderListener(title, description, project, fileChooserDescriptor, accessor);
    }

    /**
     * @deprecated use {@link #addActionListener(ActionListener)} instead
     */
    @Deprecated
    @SuppressWarnings("UnusedParameters")
    public void addBrowseFolderListener(@Nullable ComponentManager project, final BrowseFolderActionListener<Comp> actionListener) {
        addActionListener(actionListener);
    }

    /**
     * @deprecated use {@link #addActionListener(ActionListener)} instead
     */
    @Deprecated
    @SuppressWarnings("UnusedParameters")
    public void addBrowseFolderListener(
        @Nullable ComponentManager project,
        final BrowseFolderActionListener<Comp> actionListener,
        boolean autoRemoveOnHide
    ) {
        addActionListener(actionListener);
    }

    @Override
    public void dispose() {
        ActionListener[] listeners = myBrowseButton.getActionListeners();
        for (ActionListener listener : listeners) {
            myBrowseButton.removeActionListener(listener);
        }
    }

    @Nullable
    public JButton getButton() {
        return myBrowseButton;
    }

    /**
     * Do not use this class directly it is public just to hack other implementation of controls similar to TextFieldWithBrowseButton.
     */
    public static final class MyDoClickAction extends DumbAwareAction {
        private final JButton myBrowseButton;

        public MyDoClickAction(JButton browseButton) {
            myBrowseButton = browseButton;
        }

        @Override
        public void update(@Nonnull AnActionEvent e) {
            e.getPresentation().setEnabled(myBrowseButton.isVisible() && myBrowseButton.isEnabled());
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(@Nonnull AnActionEvent e) {
            myBrowseButton.doClick();
        }

        public void registerShortcut(JComponent textField) {
            ShortcutSet shiftEnter = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK));
            registerCustomShortcutSet(shiftEnter, textField);
            myBrowseButton.setToolTipText(KeymapUtil.getShortcutsText(shiftEnter.getShortcuts()));
        }

        public static void addTo(FixedSizeButton browseButton, JComponent aComponent) {
            new MyDoClickAction(browseButton).registerShortcut(aComponent);
        }
    }

    @SuppressWarnings("deprecation")
    public static class BrowseFolderActionListener<T extends JComponent> implements ActionListener {
        private final LocalizeValue myTitle;
        private final LocalizeValue myDescription;
        protected ComponentWithBrowseButton<T> myTextComponent;
        private final TextComponentAccessor<T> myAccessor;
        private ComponentManager myProject;
        protected final FileChooserDescriptor myFileChooserDescriptor;

        public BrowseFolderActionListener(
            @Nonnull LocalizeValue title,
            @Nonnull LocalizeValue description,
            ComponentWithBrowseButton<T> textField,
            @Nullable ComponentManager project,
            FileChooserDescriptor fileChooserDescriptor,
            TextComponentAccessor<T> accessor
        ) {
            if (fileChooserDescriptor != null && fileChooserDescriptor.isChooseMultiple()) {
                LOG.error("multiple selection not supported");
                fileChooserDescriptor = new FileChooserDescriptor(fileChooserDescriptor) {
                    @Override
                    public boolean isChooseMultiple() {
                        return false;
                    }
                };
            }

            myTitle = title;
            myDescription = description;
            myTextComponent = textField;
            myProject = project;
            myFileChooserDescriptor = fileChooserDescriptor;
            myAccessor = accessor;
        }

        @Deprecated
        @DeprecationInfo("Use variant with LocalizeValue")
        public BrowseFolderActionListener(
            @Nullable @Nls(capitalization = Nls.Capitalization.Title) String title,
            @Nullable @Nls(capitalization = Nls.Capitalization.Sentence) String description,
            ComponentWithBrowseButton<T> textField,
            @Nullable ComponentManager project,
            FileChooserDescriptor fileChooserDescriptor,
            TextComponentAccessor<T> accessor
        ) {
            this(
                title == null ? LocalizeValue.empty() : LocalizeValue.of(title),
                description == null ? LocalizeValue.empty() : LocalizeValue.of(description),
                textField,
                project,
                fileChooserDescriptor,
                accessor
            );
        }

        @Nullable
        protected ComponentManager getProject() {
            return myProject;
        }

        protected void setProject(@Nullable ComponentManager project) {
            myProject = project;
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(ActionEvent e) {
            FileChooserDescriptor fileChooserDescriptor = myFileChooserDescriptor;
            if (myTitle != LocalizeValue.empty() || myDescription != LocalizeValue.empty()) {
                fileChooserDescriptor = (FileChooserDescriptor) myFileChooserDescriptor.clone();
                if (myTitle != LocalizeValue.empty()) {
                    fileChooserDescriptor.withTitleValue(myTitle);
                }
                if (myDescription != LocalizeValue.empty()) {
                    fileChooserDescriptor.withDescriptionValue(myDescription);
                }
            }

            FileChooser.chooseFile(fileChooserDescriptor, getProject(), myTextComponent, getInitialFile()).doWhenDone(this::onFileChosen);
        }

        @Nullable
        protected VirtualFile getInitialFile() {
            String directoryName = getComponentText();
            if (StringUtil.isEmptyOrSpaces(directoryName)) {
                return null;
            }

            directoryName = FileUtil.toSystemIndependentName(directoryName);
            VirtualFile path = LocalFileSystem.getInstance().findFileByPath(expandPath(directoryName));
            while (path == null && directoryName.length() > 0) {
                int pos = directoryName.lastIndexOf('/');
                if (pos <= 0) {
                    break;
                }
                directoryName = directoryName.substring(0, pos);
                path = LocalFileSystem.getInstance().findFileByPath(directoryName);
            }
            return path;
        }

        @Nonnull
        protected String expandPath(@Nonnull String path) {
            return path;
        }

        protected String getComponentText() {
            return myAccessor.getText(myTextComponent.getChildComponent()).trim();
        }

        @Nonnull
        protected String chosenFileToResultingText(@Nonnull VirtualFile chosenFile) {
            return chosenFile.getPresentableUrl();
        }

        protected void onFileChosen(@Nonnull VirtualFile chosenFile) {
            myAccessor.setText(myTextComponent.getChildComponent(), chosenFileToResultingText(chosenFile));
        }
    }

    @Override
    public final void requestFocus() {
        IdeFocusManager.getGlobalInstance().doForceFocusWhenFocusSettlesDown(myComponent);
    }

    @SuppressWarnings("deprecation")
    @Override
    public final void setNextFocusableComponent(Component aComponent) {
        super.setNextFocusableComponent(aComponent);
        myComponent.setNextFocusableComponent(aComponent);
    }

    private KeyEvent myCurrentEvent = null;

    @Override
    protected final boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
        if (condition == WHEN_FOCUSED && myCurrentEvent != e) {
            try {
                myCurrentEvent = e;
                myComponent.dispatchEvent(e);
            }
            finally {
                myCurrentEvent = null;
            }
        }
        return e.isConsumed() || super.processKeyBinding(ks, e, condition, pressed);
    }
}
