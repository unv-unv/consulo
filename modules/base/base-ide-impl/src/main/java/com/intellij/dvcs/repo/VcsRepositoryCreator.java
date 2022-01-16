/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.dvcs.repo;

import consulo.component.extension.ExtensionPointName;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;

/**
 * Creates {@link Repository} instance for appropriate vcs if root is valid
 */
public abstract class VcsRepositoryCreator {
  @NonNls public static final ExtensionPointName<VcsRepositoryCreator> EXTENSION_POINT_NAME =
          ExtensionPointName.create("com.intellij.vcsRepositoryCreator");

  @javax.annotation.Nullable
  public abstract Repository createRepositoryIfValid(@Nonnull VirtualFile root);

  @Nonnull
  public abstract VcsKey getVcsKey();
}
