/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package consulo.application.ui.action;

import consulo.annotation.DeprecationInfo;
import consulo.component.util.config.AbstractProperty;
import consulo.component.util.config.BooleanProperty;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;

public class ToggleInvertedBooleanProperty extends ToggleBooleanProperty {
    public ToggleInvertedBooleanProperty(
        @Nonnull LocalizeValue text,
        @Nonnull LocalizeValue description,
        Image icon,
        AbstractProperty.AbstractPropertyContainer properties, BooleanProperty property
    ) {
        super(text, description, icon, properties, property);
    }

    @Deprecated
    @DeprecationInfo("Use variant with LocalizeValue")
    @SuppressWarnings("deprecation")
    public ToggleInvertedBooleanProperty(
        String text,
        String description,
        Image icon,
        AbstractProperty.AbstractPropertyContainer properties, BooleanProperty property
    ) {
        super(text, description, icon, properties, property);
    }

    @Override
    public boolean isSelected(@Nonnull AnActionEvent e) {
        return !getProperty().get(getProperties());
    }

    @Override
    public void setSelected(@Nonnull AnActionEvent e, boolean state) {
        getProperty().set(getProperties(), !state);
    }
}
