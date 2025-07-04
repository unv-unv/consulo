/*
 * Copyright 2013-2025 consulo.io
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
package consulo.ide.impl.idea.codeInsight.hint;

import consulo.annotation.component.ServiceImpl;
import consulo.language.psi.PsiElement;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.internal.ImplementationViewComponent;
import consulo.usage.internal.ImplementationViewComponentFactory;
import jakarta.annotation.Nonnull;
import jakarta.inject.Singleton;

/**
 * @author VISTALL
 * @since 2025-06-29
 */
@ServiceImpl
@Singleton
public class ImplementationViewComponentFactoryImpl implements ImplementationViewComponentFactory {
    @RequiredUIAccess
    @Nonnull
    @Override
    public ImplementationViewComponent create(PsiElement[] elements, int index) {
        return new ImplementationViewComponentImpl(elements, index);
    }
}
