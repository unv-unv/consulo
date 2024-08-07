/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package consulo.diff.internal;

import consulo.diff.DiffContentFactory;
import consulo.diff.DiffFilePath;
import consulo.diff.content.DiffContent;
import consulo.diff.content.DocumentContent;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.IOException;

public abstract class DiffContentFactoryEx extends DiffContentFactory {
  @Nonnull
  public static DiffContentFactoryEx getInstanceEx() {
    return (DiffContentFactoryEx)DiffContentFactory.getInstance();
  }


  @Nonnull
  public abstract DocumentContent create(@Nullable Project project, @Nonnull String text, @Nonnull DiffFilePath filePath);


  @Nonnull
  public abstract DiffContent createFromBytes(@Nullable Project project,
                                              @Nonnull byte[] content,
                                              @Nonnull DiffFilePath filePath) throws IOException;

  @Nonnull
  public abstract DiffContent createFromBytes(@Nullable Project project,
                                              @Nonnull byte[] content,
                                              @Nonnull VirtualFile highlightFile) throws IOException;


  @Nonnull
  public abstract DocumentContent createDocumentFromBytes(@Nullable Project project,
                                                          @Nonnull byte[] content,
                                                          @Nonnull DiffFilePath filePath);

  @Nonnull
  public abstract DocumentContent createDocumentFromBytes(@Nullable Project project,
                                                          @Nonnull byte[] content,
                                                          @Nonnull VirtualFile highlightFile);
}
