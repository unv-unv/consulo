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
package consulo.ide.impl.idea.refactoring.ui;

import consulo.annotation.DeprecationInfo;
import consulo.ide.impl.idea.util.EventDispatcher;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;

@Deprecated
@DeprecationInfo("See consulo.language.editor.ui.VisibilityPanelBase")
public abstract class VisibilityPanelBase<V> extends JPanel {

  public final EventDispatcher<ChangeListener> myEventDispatcher = EventDispatcher.create(ChangeListener.class);

  @Nullable
  public abstract V getVisibility();

  public abstract void setVisibility(V visibility);

  public void addListener(ChangeListener listener) {
    myEventDispatcher.addListener(listener);
  }
}
