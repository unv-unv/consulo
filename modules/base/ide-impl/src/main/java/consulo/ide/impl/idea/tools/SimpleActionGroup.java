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

package consulo.ide.impl.idea.tools;

import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;

public class SimpleActionGroup extends ActionGroup {
  private final ArrayList<AnAction> myChildren = new ArrayList<>();

  public SimpleActionGroup() {
  }

  public void add(AnAction action) {
    myChildren.add(action);
  }

  @Override
  @Nonnull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    return myChildren.toArray(new AnAction[myChildren.size()]);
  }

  public int getChildrenCount() {
    return myChildren.size();
  }

  public void removeAll() {
    myChildren.clear();
  }
}

