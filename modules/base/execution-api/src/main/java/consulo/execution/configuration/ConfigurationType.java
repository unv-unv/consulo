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
package consulo.execution.configuration;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.dumb.PossiblyDumbAware;
import consulo.localize.LocalizeValue;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * The type of a run configuration.
 *
 * @see ConfigurationTypeBase
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface ConfigurationType extends PossiblyDumbAware {
    /**
     * Returns the display name of the configuration type. This is used, for example, to represent the configuration type in the run
     * configurations tree, and also as the name of the action used to create the configuration.
     *
     * @return the display name of the configuration type.
     */
    @Nonnull
    LocalizeValue getDisplayName();

    /**
     * Returns the description of the configuration type. You may return the same text as the display name of the configuration type.
     *
     * @return the description of the configuration type.
     */
    @Nonnull
    default LocalizeValue getConfigurationTypeDescription() {
        return getDisplayName();
    }

    /**
     * Returns the 16x16 icon used to represent the configuration type.
     *
     * @return the icon
     */
    @Nullable
    Image getIcon();

    /**
     * Returns the ID of the configuration type. The ID is used to store run configuration settings in a project or workspace file and
     * must not change between plugin versions.
     *
     * @return the configuration type ID.
     */
    @Nonnull
    String getId();

    /**
     * Returns the configuration factories used by this configuration type. Normally each configuration type provides just a single factory.
     * You can return multiple factories if your configurations can be created in multiple variants (for example, local and remote for an
     * application server).
     *
     * @return the run configuration factories.
     */
    ConfigurationFactory[] getConfigurationFactories();
}