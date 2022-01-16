/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi;

import consulo.component.extension.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.SearchScope;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author peter
 */
public abstract class ResolveScopeEnlarger {
  public static final ExtensionPointName<ResolveScopeEnlarger> EP_NAME = ExtensionPointName.create("com.intellij.resolveScopeEnlarger");

  @Nullable
  public SearchScope getAdditionalResolveScope(@Nonnull VirtualFile file, Project project) {
    return null;
  }

  @Nullable
  public SearchScope getAdditionalUseScope(@Nonnull VirtualFile file, @Nonnull Project project) {
    return null;
  }
}
