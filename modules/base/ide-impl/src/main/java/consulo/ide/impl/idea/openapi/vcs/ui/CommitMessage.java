/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package consulo.ide.impl.idea.openapi.vcs.ui;

import consulo.application.ui.wm.IdeFocusManager;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorEx;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.dataContext.DataSink;
import consulo.disposer.Disposable;
import consulo.ide.ServiceManager;
import consulo.ide.impl.idea.openapi.vcs.AbstractDataProviderPanel;
import consulo.ide.impl.idea.ui.*;
import consulo.language.editor.ui.AdditionalPageAtBottomEditorCustomization;
import consulo.language.editor.ui.RightMarginEditorCustomization;
import consulo.language.editor.ui.SoftWrapsEditorCustomization;
import consulo.language.editor.ui.WrapWhenTypingReachesRightMarginCustomization;
import consulo.language.editor.ui.awt.EditorTextField;
import consulo.language.plain.PlainTextLanguage;
import consulo.language.spellchecker.editor.SpellcheckingEditorCustomizationProvider;
import consulo.project.Project;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.ActionToolbar;
import consulo.ui.ex.awt.SeparatorFactory;
import consulo.ui.ex.awt.TitledSeparator;
import consulo.util.dataholder.Key;
import consulo.versionControlSystem.CommitMessageI;
import consulo.versionControlSystem.VcsBundle;
import consulo.versionControlSystem.VcsConfiguration;
import consulo.versionControlSystem.VcsDataKeys;

import jakarta.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class CommitMessage extends AbstractDataProviderPanel implements Disposable, CommitMessageI {

  public static final Key<DataContext> DATA_CONTEXT_KEY = Key.create("commit message data context");
  private final EditorTextField myEditorField;
  private Consumer<String> myMessageConsumer;
  private TitledSeparator mySeparator;
  private boolean myCheckSpelling;

  public CommitMessage(Project project) {
    this(project, true);
  }

  public CommitMessage(Project project, final boolean withSeparator) {
    super(new BorderLayout());
    myEditorField = createEditorField(project);

    // Note that we assume here that editor used for commit message processing uses font family implied by LAF (in contrast,
    // IJ code editor uses monospaced font). Hence, we don't need any special actions here
    // (myEditorField.setFontInheritedFromLAF(true) should be used instead).
    
    add(myEditorField, BorderLayout.CENTER);

    JPanel labelPanel = new JPanel(new BorderLayout());
    labelPanel.setBorder(BorderFactory.createEmptyBorder());
    if (withSeparator) {
      mySeparator = SeparatorFactory.createSeparator(VcsBundle.message("label.commit.comment"), myEditorField.getComponent(), true, true);
      JPanel separatorPanel = new JPanel(new BorderLayout());
      separatorPanel.add(mySeparator, BorderLayout.SOUTH);
      separatorPanel.add(Box.createVerticalGlue(), BorderLayout.NORTH);
      labelPanel.add(separatorPanel, BorderLayout.CENTER);
    }
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, getToolbarActions(), withSeparator);
    toolbar.setTargetComponent(this);
    toolbar.updateActionsImmediately();
    toolbar.setReservePlaceAutoPopupIcon(false);
    toolbar.getComponent().setBorder(BorderFactory.createEmptyBorder());
    if (withSeparator) {
      labelPanel.add(toolbar.getComponent(), BorderLayout.EAST);
      add(labelPanel, BorderLayout.NORTH);
    } else {
      add(toolbar.getComponent(), BorderLayout.EAST);
    }

    setBorder(BorderFactory.createEmptyBorder());
  }

  @Override
  public void calcData(Key<?> key, DataSink sink) {
    if (VcsDataKeys.COMMIT_MESSAGE_CONTROL == key) {
      sink.put(VcsDataKeys.COMMIT_MESSAGE_CONTROL, this);
    }
  }

  public void setSeparatorText(final String text) {
    if (mySeparator != null) {
      mySeparator.setText(text);
    }
  }

  @Override
  public void setCommitMessage(String currentDescription) {
    setText(currentDescription);
  }

  private static EditorTextField createEditorField(final Project project) {
    EditorTextField editorField = createCommitTextEditor(project, false);
    editorField.getDocument().putUserData(DATA_CONTEXT_KEY, DataManager.getInstance().getDataContext(editorField.getComponent()));
    return editorField;
  }

  /**
   * Creates a text editor appropriate for creating commit messages.
   *
   * @param project project this commit message editor is intended for
   * @param forceSpellCheckOn if false, {@link VcsConfiguration#CHECK_COMMIT_MESSAGE_SPELLING} will control
   *                          whether or not the editor has spell check enabled
   * @return a commit message editor
   */
  public static EditorTextField createCommitTextEditor(final Project project, boolean forceSpellCheckOn) {
    Set<Consumer<EditorEx>> features = new HashSet<>();

    VcsConfiguration configuration = VcsConfiguration.getInstance(project);
    boolean enableSpellChecking = forceSpellCheckOn || configuration.CHECK_COMMIT_MESSAGE_SPELLING;

    SpellcheckingEditorCustomizationProvider.getInstance().getCustomizationOpt(enableSpellChecking).ifPresent(features::add);
    features.add(new RightMarginEditorCustomization(configuration.USE_COMMIT_MESSAGE_MARGIN, configuration.COMMIT_MESSAGE_MARGIN_SIZE));
    features.add(WrapWhenTypingReachesRightMarginCustomization.getInstance(configuration.WRAP_WHEN_TYPING_REACHES_RIGHT_MARGIN));

    features.add(SoftWrapsEditorCustomization.ENABLED);
    features.add(AdditionalPageAtBottomEditorCustomization.DISABLED);

    EditorTextFieldProvider service = ServiceManager.getService(project, EditorTextFieldProvider.class);
    return service.getEditorField(PlainTextLanguage.INSTANCE, project, features);
  }

  @Nullable
  public static ActionGroup getToolbarActions() {
    return (ActionGroup)ActionManager.getInstance().getAction("Vcs.MessageActionGroup");
  }

  public EditorTextField getEditorField() {
    return myEditorField;
  }

  public void setText(final String initialMessage) {
    final String text = initialMessage == null ? "" : initialMessage;
    myEditorField.setText(text);
    if (myMessageConsumer != null) {
      myMessageConsumer.accept(text);
    }
  }

  public String getComment() {
    final String s = myEditorField.getDocument().getCharsSequence().toString();
    int end = s.length();
    while(end > 0 && Character.isSpaceChar(s.charAt(end-1))) {
      end--;
    }
    return s.substring(0, end);
  }

  public void requestFocusInMessage() {
    IdeFocusManager.getGlobalInstance().doForceFocusWhenFocusSettlesDown(myEditorField);
    myEditorField.selectAll();
  }

  @Override
  public boolean isCheckSpelling() {
    return myCheckSpelling;
  }

  @Override
  public void setCheckSpelling(boolean check) {
    myCheckSpelling = check;
    Editor editor = myEditorField.getEditor();
    if (!(editor instanceof EditorEx)) {
      return;
    }
    EditorEx editorEx = (EditorEx)editor;

    SpellcheckingEditorCustomizationProvider.getInstance().getCustomizationOpt(check).ifPresent(customizer -> customizer.accept(editorEx));
  }

  @Override
  public void dispose() {
  }

  public void setMessageConsumer(Consumer<String> messageConsumer) {
    myMessageConsumer = messageConsumer;
  }
}
