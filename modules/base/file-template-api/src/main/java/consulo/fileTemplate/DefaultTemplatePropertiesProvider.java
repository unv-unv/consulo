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

package consulo.fileTemplate;

import consulo.annotation.DeprecationInfo;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiDirectory;

import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;

/**
 * Provides default variables which are available in file templates.
 *
 * @author yole
 * @since 8.0
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface DefaultTemplatePropertiesProvider {
    ExtensionPointName<DefaultTemplatePropertiesProvider> EP_NAME = ExtensionPointName.create(DefaultTemplatePropertiesProvider.class);

    /**
     * Fills the default properties for a file which is created in the specified directory.
     *
     * @param directory the directory in which the file is created.
     * @param props     the map in which the defined properties should be stored.
     */
    @Deprecated
    @DeprecationInfo("Use #fillProperties(PsiDirectory directory, Map<String, Object> props)")
    default void fillProperties(PsiDirectory directory, Properties props) {
    }

    default void fillProperties(PsiDirectory directory, Map<String, Object> props) {
        Properties properties = new Properties();
        fillProperties(directory, properties);

        for (Enumeration<?> e = properties.propertyNames(); e.hasMoreElements(); ) {
            String s = (String)e.nextElement();
            props.put(s, properties.getProperty(s));
        }
    }
}
