/*
 * Copyright 2013-2016 consulo.io
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
package consulo.sandboxPlugin.ide.codeInsight.template.postfix.templates;

import consulo.language.editor.postfixTemplate.PostfixTemplate;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.language.editor.postfixTemplate.PostfixTemplateProvider;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

/**
* @author VISTALL
* @since 16.08.14
*/
public class DDTemplate extends PostfixTemplate {
  public DDTemplate(PostfixTemplateProvider postfixTemplateProvider) {
    super("dd 2", "dd", "ddd  dsadas dsa", postfixTemplateProvider);
  }

  @Override
  public boolean isApplicable(@Nonnull PsiElement context, @Nonnull Document copyDocument, int newOffset) {
    return true;
  }

  @Override
  public void expand(@Nonnull PsiElement context, @Nonnull Editor editor) {

  }
}
