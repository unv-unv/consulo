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
package consulo.util.lang;

// TODO replace by Couple?
public class BeforeAfter<T> {
  private final T myBefore;
  private final T myAfter;

  public BeforeAfter(final T before, final T after) {
    myAfter = after;
    myBefore = before;
  }

  public T getAfter() {
    return myAfter;
  }

  public T getBefore() {
    return myBefore;
  }
}
