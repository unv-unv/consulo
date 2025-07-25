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
package consulo.execution.debug.ui;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.awt.*;

/**
 * @author Eugene Zhuravlev
 * @since 2007-01-27
 */
public class ValueMarkup {
  private final String myText;
  private final Color myColor;
  @Nullable
  private final String myToolTipText;

  public ValueMarkup(final String text, final Color color, @Nullable String toolTipText) {
    myText = text;
    myColor = color;
    myToolTipText = toolTipText;
  }

  @Nonnull
  public String getText() {
    return myText;
  }

  public Color getColor() {
    return myColor;
  }

  @Nullable
  public String getToolTipText() {
    return myToolTipText;
  }
}
