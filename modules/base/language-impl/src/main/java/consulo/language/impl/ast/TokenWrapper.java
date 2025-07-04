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
package consulo.language.impl.ast;

import consulo.language.ast.IElementType;

/**
 * @author max
 */
public class TokenWrapper extends IElementType {
  private final IElementType myDelegate;
  private final CharSequence myValue;

  public TokenWrapper(IElementType delegate, CharSequence value) {
    super("Wrapper", delegate.getLanguage(), false);
    myDelegate = delegate;
    myValue = value;
  }

  public IElementType getDelegate() {
    return myDelegate;
  }

  public CharSequence getValue() {
    return myValue;
  }

  @Override
  public String toString() {
    return "Wrapper (" + myDelegate + ")";
  }
}
