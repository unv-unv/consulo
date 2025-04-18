/*
 * Copyright 2013-2016 consulo.io
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
package consulo.module.impl.internal.layer.orderEntry;

import consulo.annotation.component.ExtensionImpl;
import consulo.content.library.LibraryTablesRegistrar;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.content.layer.orderEntry.DependencyScope;
import consulo.module.content.layer.orderEntry.OrderEntryType;
import consulo.module.impl.internal.layer.ModuleRootLayerImpl;
import consulo.util.xml.serializer.InvalidDataException;
import jakarta.annotation.Nonnull;
import org.jdom.Element;

/**
 * @author VISTALL
 * @since 2014-08-21
 */
@ExtensionImpl
public class LibraryOrderEntryType implements OrderEntryType<LibraryOrderEntryImpl> {
    public static final String ID = "library";

    @Nonnull
    public static LibraryOrderEntryType getInstance() {
        return EP_NAME.findExtensionOrFail(LibraryOrderEntryType.class);
    }

    private static final String NAME_ATTR = "name";
    private static final String LEVEL_ATTR = "level";
    private static final String EXPORTED_ATTR = "exploded";

    @Nonnull
    @Override
    public String getId() {
        return ID;
    }

    @Nonnull
    @Override
    public LibraryOrderEntryImpl loadOrderEntry(
        @Nonnull Element element,
        @Nonnull ModuleRootLayer moduleRootLayer
    ) throws InvalidDataException {
        String name = element.getAttributeValue(NAME_ATTR);
        if (name == null) {
            throw new InvalidDataException();
        }

        String level = element.getAttributeValue(LEVEL_ATTR, LibraryTablesRegistrar.PROJECT_LEVEL);
        DependencyScope dependencyScope = DependencyScope.readExternal(element);
        boolean exported = element.getAttributeValue(EXPORTED_ATTR) != null;
        return new LibraryOrderEntryImpl(name, level, (ModuleRootLayerImpl)moduleRootLayer, dependencyScope, exported, false);
    }

    @Override
    public void storeOrderEntry(@Nonnull Element element, @Nonnull LibraryOrderEntryImpl orderEntry) {
        String libraryLevel = orderEntry.getLibraryLevel();
        if (orderEntry.isExported()) {
            element.setAttribute(EXPORTED_ATTR, "");
        }
        orderEntry.getScope().writeExternal(element);
        element.setAttribute(NAME_ATTR, orderEntry.getLibraryName());
        element.setAttribute(LEVEL_ATTR, libraryLevel);
    }
}
