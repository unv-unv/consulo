/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package consulo.language.pattern.compiler;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.Service;
import consulo.application.Application;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Gregory.Shrago
 */
@Service(ComponentScope.APPLICATION)
public abstract class PatternCompilerFactory {
  public static PatternCompilerFactory getFactory() {
    return Application.get().getInstance(PatternCompilerFactory.class);
  }

  /**
   * Retrieves pattern classes registered via consulo.ide.impl.idea.patterns.patternClass extension.
   * @param alias or null
   * @return pattern classes
   */
  @Nonnull
  public abstract Class[] getPatternClasses(@Nullable final String alias);

  @Nonnull
  public abstract <T> PatternCompiler<T> getPatternCompiler(@Nonnull Class[] patternClasses);

  @Nonnull
  public <T> PatternCompiler<T> getPatternCompiler(@Nullable final String alias) {
    return getPatternCompiler(getPatternClasses(alias));
  }
}
