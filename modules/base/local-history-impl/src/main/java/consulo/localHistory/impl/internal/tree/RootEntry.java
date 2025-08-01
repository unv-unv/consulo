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

package consulo.localHistory.impl.internal.tree;

import consulo.localHistory.impl.internal.Paths;
import jakarta.annotation.Nonnull;

import java.io.DataOutput;
import java.io.IOException;

public class RootEntry extends DirectoryEntry {
  public RootEntry() {
    super("");
  }

  @Override
  public void write(DataOutput out) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Nonnull
  @Override
  public RootEntry copy() {
    return (RootEntry)super.copy();
  }

  @Override
  protected DirectoryEntry copyEntry() {
    return new RootEntry();
  }

  public Entry ensureDirectoryExists(String path) {
    Entry parent = this;
    for (String each : Paths.split(path)) {
      Entry nextParent = parent.findChild(each);
      if (nextParent == null) {
        parent.addChild(nextParent = new DirectoryEntry(each));
      }
      parent = nextParent;
    }
    return parent;
  }
}
