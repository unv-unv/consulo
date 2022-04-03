/*
 * Copyright 2013-2022 consulo.io
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
package consulo.language.editor;

import consulo.component.extension.ExtensionPointName;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface VirtualFileQualifiedNameProvider {
  ExtensionPointName<VirtualFileQualifiedNameProvider> EP_NAME = ExtensionPointName.create("consulo.virtualFileQualifiedNameProvider");

  /**
   * @return {@code virtualFile} fqn (relative path for example) or null if not handled by this provider
   */
  @Nullable
  String getQualifiedName(@Nonnull Project project, @Nonnull VirtualFile virtualFile);
}
