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
package consulo.usage;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.codeEditor.Editor;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;

import jakarta.annotation.Nullable;

/**
 * @author max
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface UsageTargetProvider {
    ExtensionPointName<UsageTargetProvider> EP_NAME = ExtensionPointName.create(UsageTargetProvider.class);

    @Nullable
    UsageTarget[] getTargets(Editor editor, PsiFile file);

    @Nullable
    UsageTarget[] getTargets(PsiElement psiElement);
}