
/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package consulo.ide.impl.idea.openapi.vfs.ex.dummy;

import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.localize.VirtualFileSystemLocalize;
import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

class VirtualFileDirectoryImpl extends VirtualFileImpl {
  private final ArrayList<VirtualFileImpl> myChildren = new ArrayList<>();

  public VirtualFileDirectoryImpl(DummyFileSystem fileSystem, VirtualFileDirectoryImpl parent, String name) {
    super(fileSystem, parent, name);
  }

  @Override
  public boolean isDirectory() {
    return true;
  }

  @Override
  public long getLength() {
    return 0;
  }

  @Override
  public VirtualFile[] getChildren() {
    return myChildren.size() == 0 ? EMPTY_ARRAY : myChildren.toArray(new VirtualFile[myChildren.size()]);
  }

  @Override
  public InputStream getInputStream() throws IOException {
    throw new IOException(VirtualFileSystemLocalize.fileReadError(getUrl()).get());
  }

  @Override
  @Nonnull
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new IOException(VirtualFileSystemLocalize.fileWriteError(getUrl()).get());
  }

  @Override
  @Nonnull
  public byte[] contentsToByteArray() throws IOException {
    throw new IOException(VirtualFileSystemLocalize.fileReadError(getUrl()).get());
  }

  @Override
  public long getModificationStamp() {
    return -1;
  }

  void addChild(VirtualFileImpl child) {
    myChildren.add(child);
  }

  void removeChild(VirtualFileImpl child) {
    myChildren.remove(child);
    child.myIsValid = false;
  }
}
