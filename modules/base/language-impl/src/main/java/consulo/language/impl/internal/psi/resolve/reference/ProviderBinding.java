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

package consulo.language.impl.internal.psi.resolve.reference;

import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReferenceService;
import consulo.language.util.ProcessingContext;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * @author ik
 * @since 2003-04-01
 */
public interface ProviderBinding<T> {
  class ProviderInfo<T, Context> {
    public final T provider;
    public final Context processingContext;
    public final double priority;

    public ProviderInfo(@Nonnull T provider, @Nonnull Context processingContext, double priority) {
      this.provider = provider;
      this.processingContext = processingContext;
      this.priority = priority;
    }
  }
  void addAcceptableReferenceProviders(@Nonnull PsiElement position,
                                       @Nonnull List<ProviderInfo<T, ProcessingContext>> list,
                                       @Nonnull PsiReferenceService.Hints hints);

  void unregisterProvider(@Nonnull T provider);
}
