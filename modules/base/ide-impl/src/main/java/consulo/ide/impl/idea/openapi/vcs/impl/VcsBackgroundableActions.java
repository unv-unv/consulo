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
package consulo.ide.impl.idea.openapi.vcs.impl;

import consulo.versionControlSystem.FilePath;
import consulo.virtualFileSystem.VirtualFile;

public enum VcsBackgroundableActions {
  ANNOTATE,
  COMPARE_WITH, // common for compare with (selected/latest/same) revision
  CREATE_HISTORY_SESSION,
  HISTORY_FOR_SELECTION,
  COMMITTED_CHANGES_DETAILS;

  public static Object keyFrom(FilePath filePath) {
    return filePath.getPath();
  }

  public static String keyFrom(VirtualFile vf) {
    return vf.getPath();
  }
}
