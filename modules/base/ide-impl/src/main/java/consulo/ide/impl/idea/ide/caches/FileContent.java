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

package consulo.ide.impl.idea.ide.caches;

import consulo.util.dataholder.UserDataHolderBase;
import consulo.virtualFileSystem.VirtualFile;
import consulo.ide.impl.idea.util.ArrayUtil;

import java.io.IOException;
import java.util.Objects;

/**
 * @author max
 */
public class FileContent extends UserDataHolderBase {
  private final VirtualFile myVirtualFile;
  private byte[] myCachedBytes;
  private long myCachedLength = -1;
  private long myCachedTimeStamp = -1;
  private Boolean myCachedWritable;

  public FileContent(VirtualFile virtualFile) {
    myVirtualFile = virtualFile;
  }

  public void cache() throws IOException {
    getBytes();
    getLength();
    getTimeStamp();
    isWritable();
  }

  public byte[] getBytes() throws IOException {
    if (myCachedBytes == null) {
      myCachedBytes = myVirtualFile.contentsToByteArray(false);
    }

    return myCachedBytes;
  }

  public void setEmptyContent() {
    myCachedBytes = ArrayUtil.EMPTY_BYTE_ARRAY;
    myCachedLength = 0;
  }

  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  public long getLength() {
    if (myCachedLength == -1) {
      myCachedLength = myVirtualFile.getLength();
    }
    return myCachedLength;
  }

  public long getTimeStamp() {
    if (myCachedTimeStamp == -1) {
      myCachedTimeStamp = myVirtualFile.getTimeStamp();
    }
    return myCachedTimeStamp;
  }

  public boolean isWritable() {
    if (myCachedWritable == null) {
      myCachedWritable = myVirtualFile.isWritable();
    }
    return Objects.equals(myCachedWritable, Boolean.TRUE);
  }
}
