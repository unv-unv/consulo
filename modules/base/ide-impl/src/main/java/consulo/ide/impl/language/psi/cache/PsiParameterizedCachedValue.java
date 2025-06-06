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

package consulo.ide.impl.language.psi.cache;

import consulo.application.impl.internal.util.CachedValuesFactory;
import consulo.language.psi.PsiManager;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.ParameterizedCachedValue;
import consulo.application.util.ParameterizedCachedValueProvider;
import jakarta.annotation.Nonnull;

public class PsiParameterizedCachedValue<T, P> extends PsiCachedValue<T> implements ParameterizedCachedValue<T, P> {
    private final ParameterizedCachedValueProvider<T, P> myProvider;

    PsiParameterizedCachedValue(
        @Nonnull PsiManager manager,
        @Nonnull ParameterizedCachedValueProvider<T, P> provider,
        boolean trackValue,
        CachedValuesFactory factory
    ) {
        super(manager, trackValue, factory);
        myProvider = provider;
    }

    @Override
    public T getValue(P param) {
        return getValueWithLock(param);
    }

    @Nonnull
    @Override
    public ParameterizedCachedValueProvider<T, P> getValueProvider() {
        return myProvider;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <X> CachedValueProvider.Result<T> doCompute(X param) {
        return myProvider.compute((P)param);
    }
}
