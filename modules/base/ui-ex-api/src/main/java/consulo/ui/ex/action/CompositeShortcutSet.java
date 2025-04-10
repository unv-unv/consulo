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
package consulo.ui.ex.action;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CompositeShortcutSet implements ShortcutSet {
  private final ShortcutSet[] mySets;

  public CompositeShortcutSet(ShortcutSet... sets) {
    mySets = sets;
  }

  @Override
  @Nonnull
  public Shortcut[] getShortcuts() {
    List<Shortcut> result = new ArrayList<>();
    for (ShortcutSet each : mySets) {
      Collections.addAll(result, each.getShortcuts());
    }
    return result.toArray(new Shortcut[result.size()]);
  }
}
