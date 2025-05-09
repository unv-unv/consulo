/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package consulo.language.psi;

import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
public class ElementDescriptionUtil {
    private ElementDescriptionUtil() {
    }

    @Nonnull
    public static String getElementDescription(@Nonnull PsiElement element, @Nonnull ElementDescriptionLocation location) {
        for (ElementDescriptionProvider provider : ElementDescriptionProvider.EP_NAME.getExtensionList()) {
            String result = provider.getElementDescription(element, location);
            if (result != null) {
                return result;
            }
        }

        ElementDescriptionProvider defaultProvider = location.getDefaultProvider();
        if (defaultProvider != null) {
            String result = defaultProvider.getElementDescription(element, location);
            if (result != null) {
                return result;
            }
        }

        return element.toString();
    }
}
