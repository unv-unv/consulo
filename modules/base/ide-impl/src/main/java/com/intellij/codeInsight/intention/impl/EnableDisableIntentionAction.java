// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import consulo.language.editor.CodeInsightBundle;
import consulo.language.editor.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import consulo.editor.Editor;
import consulo.project.Project;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import javax.annotation.Nonnull;

public class EnableDisableIntentionAction extends AbstractEditIntentionSettingsAction {
  private final IntentionAction myAction;

  public EnableDisableIntentionAction(@Nonnull IntentionAction action) {
    super(action);
    myAction = action;
  }

  @Override
  @Nonnull
  public String getText() {
    final IntentionManagerSettings mySettings = IntentionManagerSettings.getInstance();
    return CodeInsightBundle.message(mySettings.isEnabled(myAction) ? "disable.intention.action" : "enable.intention.action", myFamilyName);
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final IntentionManagerSettings mySettings = IntentionManagerSettings.getInstance();
    mySettings.setEnabled(myAction, !mySettings.isEnabled(myAction));
  }

  @Override
  public String toString() {
    return getText();
  }
}
