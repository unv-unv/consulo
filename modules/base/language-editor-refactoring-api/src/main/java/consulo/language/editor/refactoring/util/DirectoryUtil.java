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

package consulo.language.editor.refactoring.util;

import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.platform.Platform;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;

import java.io.File;
import java.util.StringTokenizer;

public class DirectoryUtil {
  private DirectoryUtil() {
  }


  /**
   * Creates the directory with the given path via PSI, including any
   * necessary but nonexistent parent directories. Must be run in write action.
   * @param path directory path in the local file system; separators must be '/'
   * @return true if path exists or has been created as the result of this method call; false otherwise
   */
  public static PsiDirectory mkdirs(PsiManager manager, String path) throws IncorrectOperationException{
    if (File.separatorChar != '/') {
      if (path.indexOf(File.separatorChar) != -1) {
        throw new IllegalArgumentException("separators must be '/'; path is " + path);
      }
    }

    String existingPath = path;

    PsiDirectory directory = null;

    // find longest existing path
    while (existingPath.length() > 0) {
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(existingPath);
      if (file != null) {
        directory = manager.findDirectory(file);
        if (directory == null) {
          return null;
        }
        break;
      }

      if (StringUtil.endsWithChar(existingPath, '/')) {
        existingPath = existingPath.substring(0, existingPath.length() - 1);
        if (Platform.current().os().isWindows() && existingPath.length() == 2 && existingPath.charAt(1) == ':') {
          return null;
        }
      }

      int index = existingPath.lastIndexOf('/');
      if (index == -1) {
        // nothing to do more
        return null;
      }

      existingPath = existingPath.substring(0, index);
    }

    if (directory == null) {
      return null;
    }

    if (existingPath.equals(path)) {
      return directory;
    }

    String postfix = path.substring(existingPath.length() + 1, path.length());
    StringTokenizer tokenizer = new StringTokenizer(postfix, "/");
    while (tokenizer.hasMoreTokens()) {
      String name = tokenizer.nextToken();

      PsiDirectory subdirectory = directory.createSubdirectory(name);
      if (subdirectory == null) {
        return null;
      }

      directory = subdirectory;
    }

    return directory;
  }

  public static PsiDirectory createSubdirectories(final String subDirName, PsiDirectory baseDirectory, final String delim) throws IncorrectOperationException {
    StringTokenizer tokenizer = new StringTokenizer(subDirName, delim);
    PsiDirectory dir = baseDirectory;
    while (tokenizer.hasMoreTokens()) {
      String packName = tokenizer.nextToken();
      if (tokenizer.hasMoreTokens()) {
        PsiDirectory existingDir = dir.findSubdirectory(packName);
        if (existingDir != null) {
          dir = existingDir;
          continue;
        }
      }
      dir = dir.createSubdirectory(packName);
    }
    return dir;
  }
}
