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

package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import consulo.ui.RequiredUIAccess;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import javax.swing.*;
import java.util.Map;

/**
 * @author peter
 */
public class CreateFileFromTemplateDialog extends DialogWrapper {
  private JTextField myNameField;
  private TemplateKindCombo myKindCombo;
  private JPanel myPanel;
  private JLabel myUpDownHint;
  private JLabel myKindLabel;
  private JLabel myNameLabel;

  private ElementCreator myCreator;
  private InputValidator myInputValidator;

  protected CreateFileFromTemplateDialog(@Nonnull Project project) {
    super(project, true);

    myKindLabel.setLabelFor(myKindCombo);
    myKindCombo.registerUpDownHint(myNameField);
    myUpDownHint.setIcon(AllIcons.Ide.UpDown);
    setTemplateKindComponentsVisible(false);
    init();
  }

  @RequiredUIAccess
  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    if (myInputValidator != null) {
      final String text = myNameField.getText().trim();
      final boolean canClose = myInputValidator.canClose(text);
      if (!canClose) {
        String errorText = LangBundle.message("incorrect.name");
        if (myInputValidator instanceof InputValidatorEx) {
          String message = ((InputValidatorEx)myInputValidator).getErrorText(text);
          if (message != null) {
            errorText = message;
          }
        }
        return new ValidationInfo(errorText, myNameField);
      }
    }
    return super.doValidate();
  }

  protected JTextField getNameField() {
    return myNameField;
  }

  protected TemplateKindCombo getKindCombo() {
    return myKindCombo;
  }

  protected JLabel getKindLabel() {
    return myKindLabel;
  }

  protected JLabel getNameLabel() {
    return myNameLabel;
  }

  private String getEnteredName() {
    final JTextField nameField = getNameField();
    final String text = nameField.getText().trim();
    nameField.setText(text);
    return text;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  protected void doOKAction() {
    if (myCreator != null && myCreator.tryCreate(getEnteredName()).length == 0) {
      return;
    }
    super.doOKAction();
  }

  @RequiredUIAccess
  @Override
  public JComponent getPreferredFocusedComponent() {
    return getNameField();
  }

  public void setTemplateKindComponentsVisible(boolean flag) {
    myKindCombo.setVisible(flag);
    myKindLabel.setVisible(flag);
    myUpDownHint.setVisible(flag);
  }

  public static Builder createDialog(@Nonnull final Project project) {
    final CreateFileFromTemplateDialog dialog = new CreateFileFromTemplateDialog(project);
    return new BuilderImpl(dialog, project);
  }

  private static class BuilderImpl implements Builder {
    private final CreateFileFromTemplateDialog myDialog;
    private final Project myProject;

    public BuilderImpl(CreateFileFromTemplateDialog dialog, Project project) {
      myDialog = dialog;
      myProject = project;
    }

    @Override
    public Builder setTitle(String title) {
      myDialog.setTitle(title);
      return this;
    }

    @Override
    public Builder addKind(@Nonnull String name, @Nullable Icon icon, @Nonnull String templateName) {
      myDialog.getKindCombo().addItem(name, icon, templateName);
      if (myDialog.getKindCombo().getComboBox().getItemCount() > 1) {
        myDialog.setTemplateKindComponentsVisible(true);
      }
      return this;
    }

    @Override
    public Builder setValidator(InputValidator validator) {
      myDialog.myInputValidator = validator;
      return this;
    }

    @Override
    public <T extends PsiElement> T show(@Nonnull String errorTitle, @Nullable String selectedTemplateName,
                                         @Nonnull final FileCreator<T> creator) {
      final Ref<T> created = Ref.create(null);
      myDialog.getKindCombo().setSelectedName(selectedTemplateName);
      myDialog.myCreator = new ElementCreator(myProject, errorTitle) {

        @Override
        protected PsiElement[] create(String newName) throws Exception {
          final T element = creator.createFile(myDialog.getEnteredName(), myDialog.getKindCombo().getSelectedName());
          created.set(element);
          if (element != null) {
            return new PsiElement[]{element};
          }
          return PsiElement.EMPTY_ARRAY;
        }

        @Override
        protected String getActionName(String newName) {
          return creator.getActionName(newName, myDialog.getKindCombo().getSelectedName());
        }
      };

      myDialog.show();
      if (myDialog.getExitCode() == OK_EXIT_CODE) {
        return created.get();
      }
      return null;
    }

    @Nullable
    @Override
    public Map<String,String> getCustomProperties() {
      return null;
    }
  }

  public interface Builder {
    Builder setTitle(String title);
    Builder setValidator(InputValidator validator);
    Builder addKind(@Nonnull String kind, @Nullable Icon icon, @Nonnull String templateName);
    @Nullable
    <T extends PsiElement> T show(@Nonnull String errorTitle, @Nullable String selectedItem, @Nonnull FileCreator<T> creator);
    @Nullable
    Map<String,String> getCustomProperties();
  }

  public interface FileCreator<T> {
    @Nullable
    T createFile(@Nonnull String name, @Nonnull String templateName);

    @Nonnull
    String getActionName(@Nonnull String name, @Nonnull String templateName);
  }
}
