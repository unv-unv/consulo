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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.refactoring.NamesValidator;
import consulo.logging.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.rename.RenameProcessor;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author ven
 */
public class RenameElementFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance(RenameElementFix.class);

  private final String myNewName;
  private final String myText;

  public RenameElementFix(@Nonnull PsiNamedElement element) {
    super(element);
    final VirtualFile vFile = element.getContainingFile().getVirtualFile();
    assert vFile != null : element;
    myNewName = vFile.getNameWithoutExtension();
    myText =  CodeInsightBundle.message("rename.public.class.text", element.getName(), myNewName);
  }

  public RenameElementFix(@Nonnull PsiNamedElement element, @Nonnull String newName) {
    super(element);
    myNewName = newName;
    myText = CodeInsightBundle.message("rename.named.element.text", element.getName(), myNewName);
  }

  @Override
  @Nonnull
  public String getText() {
    return myText;
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return CodeInsightBundle.message("rename.element.family");
  }

  @Override
  public void invoke(@Nonnull final Project project,
                     @Nonnull final PsiFile file,
                     @Nullable Editor editor,
                     @Nonnull final PsiElement startElement,
                     @Nonnull PsiElement endElement) {
    if (isAvailable(project, null, file)) {
      LOG.assertTrue(file == startElement.getContainingFile());
      if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
      RenameProcessor processor = new RenameProcessor(project, startElement, myNewName, false, false);
      processor.run();
    }
  }

  @Override
  public boolean isAvailable(@Nonnull Project project,
                             @Nonnull PsiFile file,
                             @Nonnull PsiElement startElement,
                             @Nonnull PsiElement endElement) {
    if (!startElement.isValid()) {
      return false;
    }
    final NamesValidator namesValidator = LanguageNamesValidation.INSTANCE.forLanguage(file.getLanguage());
    return namesValidator != null && namesValidator.isIdentifier(myNewName, project);
  }


  @Override
  public boolean startInWriteAction() {
    return false;
  }
}