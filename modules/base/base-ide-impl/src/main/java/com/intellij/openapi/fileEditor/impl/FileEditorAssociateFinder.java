/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.VirtualFile;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: angus
 * Date: 3/6/12
 * Time: 1:37 PM
 */
public interface FileEditorAssociateFinder {
  ExtensionPointName<FileEditorAssociateFinder> EP_NAME = ExtensionPointName.create("com.intellij.editorFileAssociateFinder");

  @Nullable
  VirtualFile getAssociatedFileToOpen(@Nonnull Project project, @Nonnull VirtualFile original);
}
