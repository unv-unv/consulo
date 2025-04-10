/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package consulo.ide.impl.idea.codeInsight.daemon.impl;

import consulo.application.AllIcons;
import consulo.codeEditor.EditorBundle;
import consulo.configurable.ConfigurationException;
import consulo.disposer.Disposer;
import consulo.language.editor.highlight.HighlightLevelUtil;
import consulo.language.editor.highlight.HighlightingLevelManager;
import consulo.ide.impl.idea.profile.codeInspection.ui.ErrorsConfigurable;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.language.Language;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.editor.FileHighlightingSetting;
import consulo.language.editor.HectorComponentPanel;
import consulo.language.editor.HectorComponentPanelsProvider;
import consulo.language.file.FileViewProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.LanguageUtil;
import consulo.logging.Logger;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.RelativePoint;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.popup.JBPopup;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Function;

/**
 * User: anna
 * Date: Jun 27, 2005
 */
public class HectorComponent extends JPanel {
    private static final Logger LOG = Logger.getInstance(HectorComponent.class);

    private WeakReference<JBPopup> myHectorRef;
    private final ArrayList<HectorComponentPanel> myAdditionalPanels;
    private final Map<Language, JSlider> mySliders;
    private final PsiFile myFile;

    public HectorComponent(@Nonnull PsiFile file) {
        super(new GridBagLayout());
        setBorder(JBUI.Borders.empty(0, 0, 7, 0));
        myFile = file;
        mySliders = new HashMap<Language, JSlider>();

        final Project project = myFile.getProject();
        final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        final VirtualFile virtualFile = myFile.getContainingFile().getVirtualFile();
        LOG.assertTrue(virtualFile != null);
        final boolean notInLibrary =
            !fileIndex.isInLibrarySource(virtualFile) && !fileIndex.isInLibraryClasses(virtualFile) || fileIndex.isInContent(virtualFile);
        final FileViewProvider viewProvider = myFile.getViewProvider();
        final Set<Language> languages = new TreeSet<Language>(LanguageUtil.LANGUAGE_COMPARATOR);
        languages.addAll(viewProvider.getLanguages());
        for (Language language : languages) {
            @SuppressWarnings("UseOfObsoleteCollectionType") final Hashtable<Integer, JLabel> sliderLabels =
                new Hashtable<Integer, JLabel>();
            sliderLabels.put(
                1,
                new JBLabel(EditorBundle.message("hector.none.slider.label"), AllIcons.Ide.HectorOff, SwingConstants.LEADING)
            );
            sliderLabels.put(
                2,
                new JBLabel(EditorBundle.message("hector.syntax.slider.label"), AllIcons.Ide.HectorSyntax, SwingConstants.LEADING)
            );
            if (notInLibrary) {
                sliderLabels.put(
                    3,
                    new JBLabel(EditorBundle.message("hector.inspections.slider.label"), AllIcons.Ide.HectorOn, SwingConstants.LEADING)
                );
            }

            final JSlider slider = new JSlider(SwingConstants.VERTICAL, 1, notInLibrary ? 3 : 2, 1);

            slider.setLabelTable(sliderLabels);
            UIUtil.setSliderIsFilled(slider, true);
            slider.setPaintLabels(true);
            slider.setSnapToTicks(true);
            slider.addChangeListener(new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent e) {
                    int value = slider.getValue();
                    for (Enumeration<Integer> enumeration = sliderLabels.keys(); enumeration.hasMoreElements(); ) {
                        Integer key = enumeration.nextElement();
                        sliderLabels.get(key)
                            .setForeground(key.intValue() <= value ? UIUtil.getLabelForeground() : UIUtil.getLabelDisabledForeground());
                    }
                }
            });

            final PsiFile psiRoot = viewProvider.getPsi(language);
            assert psiRoot != null : "No root in " + viewProvider + " for " + language;
            slider.setValue(getValue(
                HighlightingLevelManager.getInstance(project).shouldHighlight(psiRoot),
                HighlightingLevelManager.getInstance(project).shouldInspect(psiRoot)
            ));
            mySliders.put(language, slider);
        }

        GridBagConstraints gc = new GridBagConstraints(
            0,
            GridBagConstraints.RELATIVE,
            1,
            1,
            0,
            0,
            GridBagConstraints.NORTHWEST,
            GridBagConstraints.NONE,
            JBUI.insets(0, 5, 0, 0),
            0,
            0
        );

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(IdeBorderFactory.createTitledBorder(EditorBundle.message("hector.highlighting.level.title"), false));
        final boolean addLabel = mySliders.size() > 1;
        if (addLabel) {
            layoutVertical(panel);
        }
        else {
            layoutHorizontal(panel);
        }
        gc.gridx = 0;
        gc.gridy = 0;
        gc.weighty = 1.0;
        gc.fill = GridBagConstraints.BOTH;
        add(panel, gc);

        gc.gridy = GridBagConstraints.RELATIVE;
        gc.weighty = 0;

        final HyperlinkLabel configurator = new HyperlinkLabel("Configure inspections");
        gc.insets.right = JBUI.scale(5);
        gc.insets.bottom = JBUI.scale(10);
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        gc.anchor = GridBagConstraints.EAST;
        add(configurator, gc);
        configurator.addHyperlinkListener(new HyperlinkListener() {
            @Override
            @RequiredUIAccess
            public void hyperlinkUpdate(HyperlinkEvent e) {
                final JBPopup hector = getOldHector();
                if (hector != null) {
                    hector.cancel();
                }
                if (!DaemonCodeAnalyzer.getInstance(myFile.getProject()).isHighlightingAvailable(myFile)) {
                    return;
                }
                final Project project = myFile.getProject();

                ShowSettingsUtil.getInstance().showAndSelect(project, ErrorsConfigurable.class, inspectionToolsConfigurable -> {
                    inspectionToolsConfigurable.setFilterLanguages(languages);
                });
            }
        });

        gc.anchor = GridBagConstraints.WEST;
        gc.weightx = 1.0;
        gc.insets.right = 0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        myAdditionalPanels = new ArrayList<HectorComponentPanel>();
        for (HectorComponentPanelsProvider provider : HectorComponentPanelsProvider.EP_NAME.getExtensions(project)) {
            final HectorComponentPanel componentPanel = provider.createConfigurable(file);
            if (componentPanel != null) {
                myAdditionalPanels.add(componentPanel);
                add(componentPanel.createComponent(), gc);
                componentPanel.reset();
            }
        }
    }

    @Override
    public Dimension getPreferredSize() {
        final Dimension preferredSize = super.getPreferredSize();
        final int width = JBUI.scale(300);
        if (preferredSize.width < width) {
            preferredSize.width = width;
        }
        return preferredSize;
    }

    private void layoutHorizontal(final JPanel panel) {
        for (JSlider slider : mySliders.values()) {
            slider.setOrientation(SwingConstants.HORIZONTAL);
            slider.setPreferredSize(JBUI.size(200, 40));
            panel.add(
                slider,
                new GridBagConstraints(
                    0,
                    1,
                    1,
                    1,
                    1,
                    0,
                    GridBagConstraints.WEST,
                    GridBagConstraints.HORIZONTAL,
                    JBUI.insets(5, 0, 5, 0),
                    0,
                    0
                )
            );
        }
    }

    private void layoutVertical(final JPanel panel) {
        for (Map.Entry<Language, JSlider> entry : mySliders.entrySet()) {
            Language language = entry.getKey();
            JSlider slider = entry.getValue();

            JPanel borderPanel = new JPanel(new BorderLayout());
            slider.setPreferredSize(JBUI.size(100, 100));
            borderPanel.add(new JLabel(language.getDisplayName()), BorderLayout.NORTH);
            borderPanel.add(slider, BorderLayout.CENTER);
            panel.add(
                borderPanel,
                new GridBagConstraints(
                    GridBagConstraints.RELATIVE,
                    1,
                    1,
                    1,
                    0,
                    1,
                    GridBagConstraints.CENTER,
                    GridBagConstraints.VERTICAL,
                    JBUI.insets(0, 5, 0, 5),
                    0,
                    0
                )
            );
        }
    }

    public void showComponent(@Nonnull Component component, @Nonnull Function<? super Dimension, ? extends Point> offset) {
        showComponent(new RelativePoint(component, offset.apply(getPreferredSize())));
    }

    public void showComponent(@Nonnull RelativePoint point) {
        final JBPopup hector = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(this, this)
            .setRequestFocus(true)
            .setMovable(true)
            .setCancelCallback(() -> {
                for (HectorComponentPanel additionalPanel : myAdditionalPanels) {
                    if (!additionalPanel.canClose()) {
                        return Boolean.FALSE;
                    }
                }
                onClose();
                return Boolean.TRUE;
            })
            .createPopup();
        Disposer.register(myFile.getProject(), () -> {
            final JBPopup oldHector = getOldHector();
            if (oldHector != null && !oldHector.isDisposed()) {
                Disposer.dispose(oldHector);
            }
            Disposer.dispose(hector);
        });
        final JBPopup oldHector = getOldHector();
        if (oldHector != null) {
            oldHector.cancel();
        }
        else {
            myHectorRef = new WeakReference<>(hector);
            // UIEventLogger.logUIEvent(UIEventId.HectorPopupDisplayed);
            hector.show(point);
        }
    }

    @Nullable
    private JBPopup getOldHector() {
        if (myHectorRef == null) {
            return null;
        }
        final JBPopup hector = myHectorRef.get();
        if (hector == null || !hector.isVisible()) {
            myHectorRef = null;
            return null;
        }
        return hector;
    }

    private void onClose() {
        if (isModified()) {
            for (HectorComponentPanel panel : myAdditionalPanels) {
                try {
                    panel.apply();
                }
                catch (ConfigurationException e) {
                    //shouldn't be
                }
            }
            forceDaemonRestart();
        }
    }

    private void forceDaemonRestart() {
        final FileViewProvider viewProvider = myFile.getViewProvider();
        for (Language language : mySliders.keySet()) {
            JSlider slider = mySliders.get(language);
            PsiElement root = viewProvider.getPsi(language);
            assert root != null : "No root in " + viewProvider + " for " + language;
            int value = slider.getValue();
            if (value == 1) {
                HighlightLevelUtil.forceRootHighlighting(root, FileHighlightingSetting.SKIP_HIGHLIGHTING);
            }
            else if (value == 2) {
                HighlightLevelUtil.forceRootHighlighting(root, FileHighlightingSetting.SKIP_INSPECTION);
            }
            else {
                HighlightLevelUtil.forceRootHighlighting(root, FileHighlightingSetting.FORCE_HIGHLIGHTING);
            }
        }
        final DaemonCodeAnalyzer analyzer = DaemonCodeAnalyzer.getInstance(myFile.getProject());
        analyzer.restart();
    }

    private boolean isModified() {
        final FileViewProvider viewProvider = myFile.getViewProvider();
        for (Language language : mySliders.keySet()) {
            JSlider slider = mySliders.get(language);
            final PsiFile root = viewProvider.getPsi(language);
            HighlightingLevelManager highlightingLevelManager = HighlightingLevelManager.getInstance(myFile.getProject());
            if (root != null && getValue(
                highlightingLevelManager.shouldHighlight(root),
                highlightingLevelManager.shouldInspect(root)
            ) != slider.getValue()) {
                return true;
            }
        }
        for (HectorComponentPanel panel : myAdditionalPanels) {
            if (panel.isModified()) {
                return true;
            }
        }

        return false;
    }

    private static int getValue(boolean isSyntaxHighlightingEnabled, boolean isInspectionsHighlightingEnabled) {
        if (!isSyntaxHighlightingEnabled && !isInspectionsHighlightingEnabled) {
            return 1;
        }
        if (isSyntaxHighlightingEnabled && !isInspectionsHighlightingEnabled) {
            return 2;
        }
        return 3;
    }
}
