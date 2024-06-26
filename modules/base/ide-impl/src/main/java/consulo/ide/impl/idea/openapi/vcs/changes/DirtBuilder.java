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
package consulo.ide.impl.idea.openapi.vcs.changes;

import consulo.language.file.FileTypeManager;
import consulo.versionControlSystem.AbstractVcs;
import consulo.versionControlSystem.FilePath;
import consulo.util.collection.MultiMap;
import jakarta.annotation.Nonnull;

public class DirtBuilder {
  private final VcsGuess myGuess;
  private final FileTypeManager myFileTypeManager;

  private final MultiMap<AbstractVcs, FilePath> myFiles;
  private final MultiMap<AbstractVcs, FilePath> myDirs;
  private boolean myEverythingDirty;

  public DirtBuilder(final VcsGuess guess) {
    myGuess = guess;
    myDirs = MultiMap.createSet();
    myFiles = MultiMap.createSet();
    myEverythingDirty = false;
    myFileTypeManager = FileTypeManager.getInstance();
  }

  public DirtBuilder(final DirtBuilder builder) {
    this(builder.myGuess);
    myDirs.putAllValues(builder.myDirs);
    myFiles.putAllValues(builder.myFiles);
    myEverythingDirty = builder.myEverythingDirty;
  }

  public void reset() {
    myFiles.clear();
    myDirs.clear();
    myEverythingDirty = false;
  }

  public void everythingDirty() {
    myEverythingDirty = true;
  }

  public void addDirtyFile(@Nonnull AbstractVcs vcs, @Nonnull FilePath file) {
    if (myFileTypeManager.isFileIgnored(file.getName())) return;
    myFiles.putValue(vcs, file);
  }

  public void addDirtyDirRecursively(@Nonnull AbstractVcs vcs, @Nonnull FilePath dir) {
    if (myFileTypeManager.isFileIgnored(dir.getName())) return;
    myDirs.putValue(vcs, dir);
  }

  public boolean isEverythingDirty() {
    return myEverythingDirty;
  }

  @Nonnull
  public MultiMap<AbstractVcs, FilePath> getFilesForVcs() {
    return myFiles;
  }

  @Nonnull
  public MultiMap<AbstractVcs, FilePath> getDirsForVcs() {
    return myDirs;
  }

  public boolean isEmpty() {
    return myFiles.isEmpty() && myDirs.isEmpty();
  }
}
