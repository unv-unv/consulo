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
package consulo.colorScheme.setting;

import consulo.annotation.DeprecationInfo;
import consulo.colorScheme.TextAttributesKey;
import consulo.localize.LocalizeValue;

import jakarta.annotation.Nonnull;

/**
 * Describes a text attribute key the attributes for which can be configured in a custom
 * colors and fonts page.
 *
 * @see ColorAndFontDescriptorsProvider#getAttributeDescriptors()
 */
public final class AttributesDescriptor {
    private final TextAttributesKey myKey;
    private final LocalizeValue myDisplayName;

    @Deprecated
    @DeprecationInfo("Use parameter with LocalizeValue")
    public AttributesDescriptor(@Nonnull String displayName, @Nonnull TextAttributesKey key) {
        this(LocalizeValue.of(displayName), key);
    }

    /**
     * Creates an attribute descriptor with the specified name and text attributes key.
     *
     * @param displayName the name of the attribute shown in the colors list.
     * @param key         the attributes key for which the colors are specified.
     */
    public AttributesDescriptor(@Nonnull LocalizeValue displayName, @Nonnull TextAttributesKey key) {
        myKey = key;
        myDisplayName = displayName;
    }

    /**
     * Returns the attributes key for which the colors are specified.
     *
     * @return the attributes key.
     */
    @Nonnull
    public TextAttributesKey getKey() {
        return myKey;
    }

    /**
     * Returns the name of the attribute shown in the colors list.
     *
     * @return the name of the attribute.
     */
    @Nonnull
    public LocalizeValue getDisplayName() {
        return myDisplayName;
    }
}