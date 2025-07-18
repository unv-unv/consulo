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

package consulo.project.ui.view.internal.node;

import consulo.module.Module;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;

/**
 * @author Eugene Zhuravlev
 * @since 2003-09-17
 */
public final class LibraryGroupElement {
  public static final Key<LibraryGroupElement[]> ARRAY_DATA_KEY = Key.create("libraryGroup.array");
  
  private final Module myModule;

  public LibraryGroupElement(@Nonnull Module module) {
    myModule = module;
  }

  public Module getModule() {
    return myModule;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LibraryGroupElement)) return false;

    final LibraryGroupElement libraryGroupElement = (LibraryGroupElement)o;

    if (!myModule.equals(libraryGroupElement.myModule)) return false;

    return true;
  }

  public int hashCode() {
    return myModule.hashCode();
  }
}
