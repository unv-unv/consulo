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
package consulo.language.editor.navigation;

import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class GotoDeclarationHandlerBase implements GotoDeclarationHandler {
  @Nullable
  @Override
  public PsiElement[] getGotoDeclarationTargets(PsiElement sourceElement, int offset, Editor editor) {
    final PsiElement target = getGotoDeclarationTarget(sourceElement, editor);
    return target != null ? new PsiElement[] {target} : null;
  }

  @Nullable
  public abstract PsiElement getGotoDeclarationTarget(PsiElement sourceElement, Editor editor);
}
