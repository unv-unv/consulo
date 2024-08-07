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
package consulo.ide.impl.idea.codeInsight.hints.settings;

import consulo.codeEditor.EditorFactory;
import consulo.document.Document;
import consulo.document.event.DocumentAdapter;
import consulo.document.event.DocumentEvent;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.codeInsight.hints.filtering.MatcherConstructor;
import consulo.language.Language;
import consulo.language.editor.inlay.InlayParameterHintsProvider;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.language.plain.PlainTextFileType;
import consulo.ui.ex.awt.*;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class ParameterNameHintsConfigurable extends DialogWrapper {
  public JPanel myConfigurable;
  private EditorTextField myEditorTextField;
  private ComboBox<Language> myCurrentLanguageCombo;

  private JBCheckBox myShowWhenMultipleParamsWithSameType;
  private JBCheckBox myDoNotShowIfParameterNameContainedInMethodName;
  private JPanel myOptionsPanel;
  private JPanel myBlacklistPanel;

  private final Language myInitiallySelectedLanguage;
  private final String myNewPreselectedItem;

  private final Map<Language, String> myBlackLists;

  public ParameterNameHintsConfigurable() {
    this(null, null);
  }

  public ParameterNameHintsConfigurable(@Nullable Language selectedLanguage,
                                        @Nullable String newPreselectedPattern) {
    super(null);
    myInitiallySelectedLanguage = selectedLanguage;

    myNewPreselectedItem = newPreselectedPattern;
    myBlackLists = new HashMap<>();

    setTitle("Configure Parameter Name Hints");
    init();

    myOptionsPanel.setVisible(true);
    myOptionsPanel.setBorder(IdeBorderFactory.createTitledBorder("Options"));
    myBlacklistPanel.setBorder(IdeBorderFactory.createTitledBorder("Blacklist"));
  }

  private void updateOkEnabled() {
    String text = myEditorTextField.getText();
    List<String> rules = StringUtil.split(text, "\n");
    boolean hasAnyInvalid = rules
      .stream()
      .filter((e) -> !e.trim().isEmpty())
      .map(MatcherConstructor::createMatcher)
      .anyMatch(Objects::isNull);

    getOKAction().setEnabled(!hasAnyInvalid);
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();

    Language language = (Language)myCurrentLanguageCombo.getModel().getSelectedItem();
    myBlackLists.put(language, myEditorTextField.getText());

    myBlackLists.entrySet().forEach((entry) -> {
      Language lang = entry.getKey();
      String text = entry.getValue();
      storeBlackListDiff(lang, text);
    });

    ParameterNameHintsSettings settings = ParameterNameHintsSettings.getInstance();
    settings.setDoNotShowIfMethodNameContainsParameterName(myDoNotShowIfParameterNameContainedInMethodName.isSelected());
    settings.setShowForParamsWithSameType(myShowWhenMultipleParamsWithSameType.isSelected());
  }

  private static void storeBlackListDiff(@Nonnull Language language, @Nonnull String text) {
    Set<String> updatedBlackList = StringUtil
      .split(text, "\n")
      .stream()
      .filter((e) -> !e.trim().isEmpty())
      .collect(Collectors.toCollection(LinkedHashSet::new));

    InlayParameterHintsProvider provider = InlayParameterHintsProvider.forLanguage(language);
    Set<String> defaultBlackList = provider.getDefaultBlackList();
    Diff diff = Diff.build(defaultBlackList, updatedBlackList);
    ParameterNameHintsSettings.getInstance().setBlackListDiff(language, diff);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myConfigurable;
  }

  private void createUIComponents() {
    List<Language> languages = getBaseLanguagesWithProviders();

    Language selected = myInitiallySelectedLanguage;
    if (selected == null) {
      selected = languages.get(0);
    }

    String text = getLanguageBlackList(selected);
    myEditorTextField = createEditor(text, myNewPreselectedItem);
    myEditorTextField.addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        updateOkEnabled();
      }
    });

    myDoNotShowIfParameterNameContainedInMethodName = new JBCheckBox();
    myShowWhenMultipleParamsWithSameType = new JBCheckBox();

    ParameterNameHintsSettings settings = ParameterNameHintsSettings.getInstance();
    myDoNotShowIfParameterNameContainedInMethodName.setSelected(settings.isDoNotShowIfMethodNameContainsParameterName());
    myShowWhenMultipleParamsWithSameType.setSelected(settings.isShowForParamsWithSameType());

    initLanguageCombo(languages, selected);
  }

  private void initLanguageCombo(List<Language> languages, Language selected) {
    CollectionComboBoxModel<Language> model = new CollectionComboBoxModel<>(languages);

    myCurrentLanguageCombo = new ComboBox<>(model);
    myCurrentLanguageCombo.setSelectedItem(selected);
    myCurrentLanguageCombo.setRenderer(new ListCellRendererWrapper<Language>() {
      @Override
      public void customize(JList list, Language value, int index, boolean selected, boolean hasFocus) {
        setText(value.getDisplayName());
      }
    });

    myCurrentLanguageCombo.addItemListener(e -> {
      Language language = (Language)e.getItem();
      if (e.getStateChange() == ItemEvent.DESELECTED) {
        myBlackLists.put(language, myEditorTextField.getText());
      }
      else if (e.getStateChange() == ItemEvent.SELECTED) {
        String text = myBlackLists.get(language);
        if (text == null) {
          text = getLanguageBlackList(language);
        }
        myEditorTextField.setText(text);
      }
    });
  }

  @Nonnull
  private static String getLanguageBlackList(@Nonnull Language language) {
    InlayParameterHintsProvider hintsProvider = InlayParameterHintsProvider.forLanguage(language);
    if (hintsProvider == null) {
      return "";
    }
    Diff diff = ParameterNameHintsSettings.getInstance().getBlackListDiff(language);
    Set<String> blackList = diff.applyOn(hintsProvider.getDefaultBlackList());
    return StringUtil.join(blackList, "\n");
  }

  @Nonnull
  private static List<Language> getBaseLanguagesWithProviders() {
    return Language.getRegisteredLanguages()
            .stream()
            .filter(lang -> lang.getBaseLanguage() == null)
            .filter(lang -> InlayParameterHintsProvider.forLanguage(lang) != null)
            .sorted(Comparator.comparingInt(l -> l.getDisplayName().length()))
            .collect(Collectors.toList());
  }

  private static EditorTextField createEditor(@Nonnull String text, @Nullable String newPreselectedItem) {
    final TextRange range;
    if (newPreselectedItem != null) {
      text += "\n";

      final int startOffset = text.length();
      text += newPreselectedItem;
      range = new TextRange(startOffset, text.length());
    }
    else {
      range = null;
    }

    return createEditorField(text, range);
  }

  @Nonnull
  private static EditorTextField createEditorField(@Nonnull String text, @Nullable TextRange rangeToSelect) {
    Document document = EditorFactory.getInstance().createDocument(text);
    EditorTextField field = new EditorTextField(document, null, PlainTextFileType.INSTANCE, false, false);
    field.setPreferredSize(new Dimension(200, 350));
    field.addSettingsProvider(editor -> {
      editor.setVerticalScrollbarVisible(true);
      editor.setHorizontalScrollbarVisible(true);
      editor.getSettings().setAdditionalLinesCount(2);
      if (rangeToSelect != null) {
        editor.getCaretModel().moveToOffset(rangeToSelect.getStartOffset());
        editor.getScrollingModel().scrollVertically(document.getTextLength() - 1);
        editor.getSelectionModel().setSelection(rangeToSelect.getStartOffset(), rangeToSelect.getEndOffset());
      }
    });
    return field;
  }
}
