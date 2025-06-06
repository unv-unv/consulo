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
package consulo.fileEditor.structureView;

import consulo.fileEditor.structureView.tree.TreeElement;
import consulo.navigation.Navigatable;

/**
 * An element in the structure view tree model.
 *
 * @see StructureViewModel#getRoot()
 */

public interface StructureViewTreeElement extends TreeElement, Navigatable {
    StructureViewTreeElement[] EMPTY_ARRAY = new StructureViewTreeElement[0];

    /**
     * Returns the data object (usually a PSI element) corresponding to the
     * structure view element.
     *
     * @return the data object instance.
     */
    Object getValue();
}
