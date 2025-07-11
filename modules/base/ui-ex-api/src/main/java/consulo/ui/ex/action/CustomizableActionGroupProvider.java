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
package consulo.ui.ex.action;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

/**
 * This extension allows to add an action group to the list of customizable actions in Settings | Menus and Toolbars.
 * Use {@code CustomActionsSchema.getInstance().getCorrectedAction(id)} to get customized version of a group.
 *
 * @author nik
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class CustomizableActionGroupProvider {
    public abstract void registerGroups(CustomizableActionGroupRegistrar registrar);

    public interface CustomizableActionGroupRegistrar {
        void addCustomizableActionGroup(@Nonnull String groupId, @Nonnull LocalizeValue groupTitle);
    }
}
