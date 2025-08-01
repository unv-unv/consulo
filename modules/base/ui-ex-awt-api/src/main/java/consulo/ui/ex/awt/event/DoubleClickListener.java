/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package consulo.ui.ex.awt.event;

import consulo.ui.ex.awt.ClickListener;

import java.awt.event.MouseEvent;

/**
 * @author max
 */
public abstract class DoubleClickListener extends ClickListener {
  @Override
  public final boolean onClick(MouseEvent event, int clickCount) {
    if (clickCount == 2 && event.getButton() == MouseEvent.BUTTON1) {
      return onDoubleClick(event);
    }
    return false;
  }

  protected abstract boolean onDoubleClick(MouseEvent event);
}
