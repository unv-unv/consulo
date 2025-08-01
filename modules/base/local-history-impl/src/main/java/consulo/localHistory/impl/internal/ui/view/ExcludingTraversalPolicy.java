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
package consulo.localHistory.impl.internal.ui.view;

import consulo.util.lang.Pair;

import jakarta.annotation.Nonnull;
import java.awt.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public class ExcludingTraversalPolicy extends FocusTraversalPolicy {
  private final FocusTraversalPolicy myWrappee;
  private final Set<Component> myExcludes = new HashSet<>();
  private final Set<String> myRecursionGuard = new HashSet<>();

  public ExcludingTraversalPolicy(Component... excludes) {
    this(KeyboardFocusManager.getCurrentKeyboardFocusManager().getDefaultFocusTraversalPolicy(), excludes);
  }

  public ExcludingTraversalPolicy(@Nonnull FocusTraversalPolicy wrappee, Component... excludes) {
    myWrappee = wrappee;
    Collections.addAll(myExcludes, excludes);
  }

  public void exclude(Component c) {
    myExcludes.add(c);
  }

  @Override
  public Component getComponentAfter(Container aContainer, Component aComponent) {
    try {
      if (!myRecursionGuard.add("getComponentAfter")) return null;

      return traverse(aContainer, aComponent, param -> myWrappee.getComponentAfter(param.first, param.second));
    }
    finally {
      myRecursionGuard.clear();
    }
  }

  @Override
  public Component getComponentBefore(Container aContainer, Component aComponent) {
    try {
      if (!myRecursionGuard.add("getComponentBefore")) return null;

      return traverse(aContainer, aComponent, param -> myWrappee.getComponentBefore(param.first, param.second));
    }
    finally {
      myRecursionGuard.clear();
    }
  }

  private Component traverse(Container aContainer, Component aComponent, Function<Pair<Container, Component>, Component> func) {
    Set<Component> loopGuard = new HashSet<>();
    do {
      if (!loopGuard.add(aComponent)) return null;
      aComponent = func.apply(Pair.create(aContainer, aComponent));
    }
    while (aComponent != null && myExcludes.contains(aComponent));
    return aComponent;
  }

  @Override
  public Component getFirstComponent(Container aContainer) {
    try {
      if (!myRecursionGuard.add("getFirstComponent")) return null;

      Component result = myWrappee.getFirstComponent(aContainer);
      if (result == null) return null;
      return myExcludes.contains(result) ? getComponentAfter(aContainer, result) : result;
    }
    finally {
      myRecursionGuard.clear();
    }
  }

  @Override
  public Component getLastComponent(Container aContainer) {
    try {
      if (!myRecursionGuard.add("getLastComponent")) return null;

      Component result = myWrappee.getLastComponent(aContainer);
      if (result == null) return null;
      return myExcludes.contains(result) ? getComponentBefore(aContainer, result) : result;
    }
    finally {
      myRecursionGuard.clear();
    }
  }

  @Override
  public Component getDefaultComponent(Container aContainer) {
    try {
      if (!myRecursionGuard.add("getDefaultComponent")) return null;

      return getFirstComponent(aContainer);
    }
    finally {
      myRecursionGuard.clear();
    }
  }
}
