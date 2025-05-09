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

package consulo.language.editor.template;

import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import jakarta.annotation.Nonnull;

/**
 * Shows a live template-like chooser UI over a PSI element and offers the user to replace certain sub-elements of the
 * specified element with values of his/her choice.
 *
 * @author yole
 * @see TemplateBuilderFactory
 * @since 9.0
 */
public interface TemplateBuilder {
  /**
   * Creates a replacement box for the specified element with the specified initial value.
   *
   * @param element         the element to replace.
   * @param replacementText the initial value for the replacement.
   */
  void replaceElement(@Nonnull PsiElement element, String replacementText);

  void replaceElement(@Nonnull PsiElement element, TextRange rangeWithinElement, String replacementText);

  void replaceElement(PsiElement element, Expression expression, boolean alwaysStopAt);

  void replaceElement(PsiElement element, String varName, Expression expression, boolean alwaysStopAt);

  void replaceElement(PsiReference ref, String varName, Expression expression, boolean alwaysStopAt);

  void replaceElement(PsiElement element, String varName, String dependantVariableName, boolean alwaysStopAt);

  void replaceElement(PsiReference ref, String varName, String dependantVariableName, boolean alwaysStopAt);

  void replaceElement(PsiElement element, TextRange textRange, String primaryVariableName, String otherVariableName, boolean alwaysStopAt);

  void replaceElement(@Nonnull PsiElement element, @Nonnull TextRange textRange, String varName, Expression expression, boolean alwaysStopAt);

  /**
   * Creates a replacement box for the specified element with the specified expression.
   *
   * @param element    the element to replace.
   * @param expression the replacement expression.
   */
  void replaceElement(@Nonnull PsiElement element, Expression expression);

  void replaceElement(@Nonnull PsiElement element, TextRange rangeWithinElement, Expression expression);

  /**
   * Creates a replacement box for the specified text range within the container element.
   *
   * @param rangeWithinElement range within the container element.
   * @param replacementText    the initial value for the replacement.
   */
  void replaceRange(TextRange rangeWithinElement, String replacementText);

  /**
   * Creates a replacement box for the specified text range within the container element.
   *
   * @param rangeWithinElement range within the container element.
   * @param expression         the replacement expression.
   */
  void replaceRange(TextRange rangeWithinElement, Expression expression);

  /**
   * Shows the live template and initiates editing process.
   *
   * @deprecated doesn't work correctly for files with multiple editors use #run(Editor, boolean) instead
   */
  @Deprecated
  void run();

  /**
   * Shows the live template and initiates editing process.
   *
   * @param editor editor to use to start editing process.
   * @param inline if true then inline template will be created, regular otherwise
   */
  void run(@Nonnull Editor editor, boolean inline);

  /**
   * Adds end variable after the specified element
   */
  void setEndVariableAfter(PsiElement element);

  void setEndVariableBefore(PsiElement element);

  void replaceElement(PsiElement element, String varName, Expression expression, boolean alwaysStopAt, boolean skipOnStart);

  @Nonnull
  Template buildInlineTemplate();

  @Nonnull
  Template buildTemplate();
}
