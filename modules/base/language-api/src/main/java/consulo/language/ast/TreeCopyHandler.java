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
package consulo.language.ast;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;

import java.util.Map;

/**
 * @author max
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface TreeCopyHandler {
    ExtensionPointName<TreeCopyHandler> EP_NAME = ExtensionPointName.create(TreeCopyHandler.class);

    void encodeInformation(ASTNode element, ASTNode original, Map<Object, Object> encodingState);

    ASTNode decodeInformation(ASTNode element, Map<Object, Object> decodingState);
}