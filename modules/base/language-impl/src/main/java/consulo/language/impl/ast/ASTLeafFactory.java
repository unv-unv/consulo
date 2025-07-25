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
package consulo.language.impl.ast;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.language.ast.IElementType;
import consulo.language.psi.ElementTypeEntryExtensionCollector;
import consulo.language.version.LanguageVersion;

import jakarta.annotation.Nonnull;
import java.util.function.Predicate;

/**
 * @author VISTALL
 * @since 2013-04-02
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface ASTLeafFactory extends Predicate<IElementType> {
  ElementTypeEntryExtensionCollector<ASTLeafFactory> EP = ElementTypeEntryExtensionCollector.create(ASTLeafFactory.class);

  @Nonnull
  LeafElement createLeaf(@Nonnull IElementType type, @Nonnull LanguageVersion languageVersion, @Nonnull CharSequence text);

  @Override
  boolean test(IElementType elementType);
}
