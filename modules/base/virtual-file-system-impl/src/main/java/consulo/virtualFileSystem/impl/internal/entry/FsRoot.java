// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.virtualFileSystem.impl.internal.entry;

import consulo.util.io.URLUtil;
import consulo.util.lang.CharArrayUtil;
import consulo.virtualFileSystem.NewVirtualFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

public class FsRoot extends VirtualDirectoryImpl {
  private final String myPathWithOneSlash;

  public FsRoot(int id, int nameId, @Nonnull VfsData vfsData, @Nonnull NewVirtualFileSystem fs, @Nonnull String pathBeforeSlash) throws VfsData.FileAlreadyCreatedException {
    super(id, vfsData.getSegment(id, true), new VfsData.DirectoryData(), null, fs);
    if (!looksCanonical(pathBeforeSlash)) {
      throw new IllegalArgumentException("path must be canonical but got: '" + pathBeforeSlash + "'");
    }
    myPathWithOneSlash = pathBeforeSlash + '/';
    VfsData.initFile(id, mySegment, nameId, myData);
  }

  @Nonnull
  @Override
  protected char[] appendPathOnFileSystem(int pathLength, int[] position) {
    int myLength = myPathWithOneSlash.length() - 1;
    char[] chars = new char[pathLength + myLength];
    CharArrayUtil.getChars(myPathWithOneSlash, chars, 0, position[0], myLength);
    position[0] += myLength;
    return chars;
  }

  @Override
  public void setNewName(@Nonnull String newName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public final void setParent(@Nonnull VirtualFile newParent) {
    throw new UnsupportedOperationException();
  }

  @Nonnull
  @Override
  public String getPath() {
    return myPathWithOneSlash;
  }

  @Nonnull
  @Override
  public String getUrl() {
    return getFileSystem().getProtocol() + URLUtil.SCHEME_SEPARATOR + getPath();
  }

  private static boolean looksCanonical(@Nonnull String pathBeforeSlash) {
    if (pathBeforeSlash.endsWith("/")) {
      return false;
    }
    int start = 0;
    while (true) {
      int i = pathBeforeSlash.indexOf("..", start);
      if (i == -1) break;
      if (i != 0 && pathBeforeSlash.charAt(i - 1) == '/') return false; // /..
      if (i < pathBeforeSlash.length() - 2 && pathBeforeSlash.charAt(i + 2) == '/') return false; // ../
      start = i + 1;
    }
    return true;
  }
}
