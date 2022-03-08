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
package com.intellij.codeInsight.completion;

import consulo.language.editor.completion.CompletionLocation;
import consulo.language.editor.completion.CompletionWeigher;
import consulo.language.editor.completion.lookup.LookupElement;
import com.intellij.openapi.util.NullableComputable;
import consulo.language.psi.PsiElement;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import javax.annotation.Nonnull;

/**
 * @author peter
 */
public class LookupElementProximityWeigher extends CompletionWeigher {

  @Override
  public Comparable weigh(@Nonnull final LookupElement item, @Nonnull final CompletionLocation location) {
    if (item.getObject() instanceof PsiElement) {
      return PsiProximityComparator.getProximity((NullableComputable<PsiElement>)() -> item.getPsiElement(), location.getCompletionParameters().getPosition(), location.getProcessingContext());
    }
    return null;
  }
}
