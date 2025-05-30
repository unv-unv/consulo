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

package consulo.usage;

import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.language.psi.ElementDescriptionLocation;
import consulo.language.psi.ElementDescriptionProvider;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.meta.PsiMetaData;
import consulo.language.psi.meta.PsiMetaOwner;

/**
 * @author yole
 */
public class UsageViewShortNameLocation extends ElementDescriptionLocation {
    private UsageViewShortNameLocation() {
    }

    public static final UsageViewShortNameLocation INSTANCE = new UsageViewShortNameLocation();

    @Override
    public ElementDescriptionProvider getDefaultProvider() {
        return DEFAULT_PROVIDER;
    }

    private static final ElementDescriptionProvider DEFAULT_PROVIDER = (element, location) -> {
        if (!(location instanceof UsageViewShortNameLocation)) {
            return null;
        }

        if (element instanceof PsiMetaOwner metaOwner) {
            PsiMetaData metaData = metaOwner.getMetaData();
            if (metaData != null) {
                return DescriptiveNameUtil.getMetaDataName(metaData);
            }
        }

        return element instanceof PsiNamedElement namedElement ? namedElement.getName() : "";
    };
}
