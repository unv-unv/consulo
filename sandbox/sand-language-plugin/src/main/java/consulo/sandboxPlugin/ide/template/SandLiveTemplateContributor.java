/*
 * Copyright 2013-2024 consulo.io
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
package consulo.sandboxPlugin.ide.template;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.template.LiveTemplateContributor;
import consulo.language.editor.template.context.EverywhereContextType;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 2024-09-15
 */
@ExtensionImpl
public class SandLiveTemplateContributor implements LiveTemplateContributor {
    private static final LocalizeValue SAND = LocalizeValue.of("Sand");

    @Override
    public void contribute(@Nonnull Factory factory) {
        try (Builder builder = factory.newBuilder("sandTest", "test", "test($V$)", LocalizeValue.localizeTODO("Sand Test Expression"))) {
            builder.withReformat();
            builder.withVariable("V", "1", "1", true);

            builder.withContext(EverywhereContextType.class);
        }
    }

    @Nonnull
    @Override
    public String groupId() {
        return "SAND";
    }

    @Nonnull
    @Override
    public LocalizeValue groupName() {
        return SAND;
    }
}
