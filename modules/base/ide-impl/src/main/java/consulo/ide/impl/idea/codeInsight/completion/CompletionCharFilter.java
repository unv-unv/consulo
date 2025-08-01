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
package consulo.ide.impl.idea.codeInsight.completion;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.completion.lookup.CharFilter;
import consulo.language.editor.completion.lookup.Lookup;

/**
 * @author mike
 * @since 2002-07-23
 */
@ExtensionImpl(id = "completion", order = "last")
public class CompletionCharFilter extends CharFilter {

  @Override
  public Result acceptChar(char c, final int prefixLength, final Lookup lookup) {
    if (!lookup.isCompletion()) return null;

    if (Character.isJavaIdentifierPart(c)) return Result.ADD_TO_PREFIX;
    switch (c) {
      case '.':
      case ',':
      case ';':
      case '=':
      case ' ':
      case ':':
      case '(':
        return Result.SELECT_ITEM_AND_FINISH_LOOKUP;

      default:
        return Result.HIDE_LOOKUP;
    }
  }

}