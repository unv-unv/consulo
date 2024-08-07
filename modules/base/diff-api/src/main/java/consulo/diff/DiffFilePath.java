/*
 * Copyright 2013-2024 consulo.io
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
package consulo.diff;

import consulo.document.Document;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.nio.charset.Charset;

/**
 * Represents a path to a (possibly non-existing) file on disk or in a VCS repository.
 */
public interface DiffFilePath {
  /**
   * @return a virtual file that corresponds to this path, or null if the virtual file is no more valid.
   */
  @Nullable
  VirtualFile getVirtualFile();

  /**
   * @return the virtual file that corresponds to the parent file path, or null if the virtual file is no more valid.
   */
  @Nullable
  VirtualFile getVirtualFileParent();

  /**
   * @return the {@link File} that corresponds to the path. The path might be non-existent or not local.
   * @see #isNonLocal()
   */
  @Nonnull
  File getIOFile();

  /**
   * @return the file name (without directory component)
   */
  String getName();

  String getPresentableUrl();

  @Nullable
  Document getDocument();

  Charset getCharset();

  /**
   * Get character set, considering the project defaults and a virtual file
   *
   * @param project the project which settings will be consulted
   * @return the character set of the file
   */
  Charset getCharset(Project project);

  /**
   * @return the type of the file
   */
  FileType getFileType();

  void refresh();

  void hardRefresh();

  String getPath();

  /**
   * @return true if the path represents the directory
   */
  boolean isDirectory();

  /**
   * @return true if the path does not represents a file in the local file system
   */
  boolean isNonLocal();

  /**
   * Check if the provided file is an ancestor of the current file.
   *
   * @param parent a possible parent
   * @param strict if false, the method also returns true if files are equal
   * @return true if {@code this} file is ancestor of the {@code parent}.
   */
  boolean isUnder(DiffFilePath parent, boolean strict);

  /**
   * @return the parent path or null if there are no parent
   */
  @Nullable
  DiffFilePath getParentPath();
}
