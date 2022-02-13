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
package com.intellij.codeInsight.intention;

import consulo.editor.Editor;
import consulo.language.editor.intention.IntentionAction;
import consulo.project.Project;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import javax.annotation.Nonnull;

public abstract class AbstractEmptyIntentionAction implements IntentionAction {
  @Override
  public final void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {}

  @Override
  public final boolean startInWriteAction() {
    return false;
  }
}
