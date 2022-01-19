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
package com.intellij.openapi.fileEditor.impl;

import consulo.component.extension.ExtensionPointName;
import com.intellij.openapi.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.fileEditor.impl.EditorWindow;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import java.awt.*;

/**
 * @author spleaner
 */
public interface EditorTabColorProvider {
  ExtensionPointName<EditorTabColorProvider> EP_NAME = ExtensionPointName.create("consulo.editorTabColorProvider");

  @Nullable
  Color getEditorTabColor(Project project, VirtualFile file);

  @Nullable
  default Color getEditorTabColor(@Nonnull Project project, @Nonnull VirtualFile file, @Nullable EditorWindow editorWindow) {
    return getEditorTabColor(project, file);
  }

  @Nullable
  default Color getProjectViewColor(@Nonnull Project project, @Nonnull VirtualFile file) {
    return null;
  }
}
