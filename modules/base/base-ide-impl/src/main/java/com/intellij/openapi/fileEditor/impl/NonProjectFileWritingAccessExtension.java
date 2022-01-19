/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl;

import consulo.component.extension.ExtensionPointName;
import consulo.virtualFileSystem.VirtualFile;
import javax.annotation.Nonnull;

public interface NonProjectFileWritingAccessExtension {
  ExtensionPointName<NonProjectFileWritingAccessExtension> EP_NAME =
          ExtensionPointName.create("consulo.nonProjectFileWritingAccessExtension");

  /**
   * @return true if the file should not be protected from accidental writing. false to use default logic.
   */
  default boolean isWritable(@Nonnull VirtualFile file) {
    return false;
  }

  /**
   * @return true if the file should be protected from accidental writing. false to use default logic.
   */
  default boolean isNotWritable(@Nonnull VirtualFile file) {
    return false;
  }
}
