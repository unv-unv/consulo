/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package consulo.ui.ex.awt;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.application.Application;
import jakarta.annotation.Nonnull;

import java.awt.*;
import java.util.function.Consumer;

/**
 * @author Konstantin Bulenkov
 */
@ServiceAPI(ComponentScope.APPLICATION)
public abstract class ColorChooserService {
  public static ColorChooserService getInstance() {
    return Application.get().getInstance(ColorChooserService.class);
  }

  public void showDialog(Component parent,
                         String caption,
                         Color preselectedColor,
                         boolean enableOpacity,
                         @Nonnull Consumer<Color> colorConsumer) {
    showDialog(parent, caption, preselectedColor, enableOpacity, false, colorConsumer);
  }

  public abstract void showDialog(Component parent,
                                  String caption,
                                  Color preselectedColor,
                                  boolean enableOpacity,
                                  boolean opacityInPercent,
                                  @Nonnull Consumer<Color> colorConsumer);
}
