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
package consulo.virtualFileSystem.archive;

import consulo.logging.Logger;
import consulo.util.collection.ArrayUtil;
import consulo.util.io.FileAttributes;
import consulo.util.lang.ByteArrayCharSequence;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.SoftReference;
import consulo.virtualFileSystem.internal.FileSystemUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.ref.Reference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class ArchiveHandler {
  public static final long DEFAULT_LENGTH = 0L;
  public static final long DEFAULT_TIMESTAMP = -1L;

  public static class EntryInfo {
    public final EntryInfo parent;
    public final CharSequence shortName;
    public final boolean isDirectory;
    public final long length;
    public final long timestamp;

    /** @deprecated use {@link EntryInfo#EntryInfo(CharSequence, boolean, long, long, EntryInfo)} instead (to be removed in IDEA 16) */
    @SuppressWarnings("unused")
    public EntryInfo(EntryInfo parent, @Nonnull String shortName, boolean isDirectory, long length, long timestamp) {
      this(shortName, isDirectory, length, timestamp, parent);
    }

    public EntryInfo(@Nonnull CharSequence shortName, boolean isDirectory, long length, long timestamp, @Nullable EntryInfo parent) {
      this.parent = parent;
      this.shortName = shortName;
      this.isDirectory = isDirectory;
      this.length = length;
      this.timestamp = timestamp;
    }
  }

  private final File myPath;
  private final Object myLock = new Object();
  private volatile Reference<Map<String, EntryInfo>> myEntries = new SoftReference<Map<String, EntryInfo>>(null);
  private boolean myCorrupted;

  protected ArchiveHandler(@Nonnull String path) {
    myPath = new File(path);
  }

  @Nonnull
  public File getFile() {
    return myPath;
  }

  @Nullable
  public FileAttributes getAttributes(@Nonnull String relativePath) {
    if (relativePath.isEmpty()) {
      FileAttributes attributes = FileSystemUtil.getAttributes(myPath);
      return attributes != null ? new FileAttributes(true, false, false, false, DEFAULT_LENGTH, DEFAULT_TIMESTAMP, false) : null;
    }
    else {
      EntryInfo entry = getEntryInfo(relativePath);
      return entry != null ? new FileAttributes(entry.isDirectory, false, false, false, entry.length, entry.timestamp, false) : null;
    }
  }

  @Nonnull
  public String[] list(@Nonnull String relativePath) {
    EntryInfo entry = getEntryInfo(relativePath);
    if (entry == null || !entry.isDirectory) return ArrayUtil.EMPTY_STRING_ARRAY;

    Set<String> names = new HashSet<String>();
    for (EntryInfo info : getEntriesMap().values()) {
      if (info.parent == entry) {
        names.add(info.shortName.toString());
      }
    }
    return ArrayUtil.toStringArray(names);
  }

  public void dispose() {
    myEntries.clear();
  }

  @Nullable
  protected EntryInfo getEntryInfo(@Nonnull String relativePath) {
    return getEntriesMap().get(relativePath);
  }

  @Nonnull
  protected Map<String, EntryInfo> getEntriesMap() {
    Map<String, EntryInfo> map = SoftReference.dereference(myEntries);
    if (map == null) {
      synchronized (myLock) {
        map = SoftReference.dereference(myEntries);

        if (map == null) {
          if (myCorrupted) {
            map = Collections.emptyMap();
          }
          else {
            try {
              map = createEntriesMap();
            }
            catch (Exception e) {
              myCorrupted = true;
              Logger.getInstance(getClass()).warn(e.getMessage() + ": " + myPath, e);
              map = Collections.emptyMap();
            }
          }

          myEntries = new SoftReference<Map<String, EntryInfo>>(map);
        }
      }
    }
    return map;
  }

  @Nonnull
  protected abstract Map<String, EntryInfo> createEntriesMap() throws IOException;

  @Nonnull
  protected EntryInfo createRootEntry() {
    return new EntryInfo("", true, DEFAULT_LENGTH, DEFAULT_TIMESTAMP, null);
  }

  @Nonnull
  protected EntryInfo getOrCreate(@Nonnull Map<String, EntryInfo> map, @Nonnull String entryName) {
    EntryInfo entry = map.get(entryName);
    if (entry == null) {
      Pair<String, String> path = splitPath(entryName);
      EntryInfo parentEntry = getOrCreate(map, path.first);
      CharSequence shortName = ByteArrayCharSequence.convertToBytesIfAsciiString(path.second);
      entry = new EntryInfo(shortName, true, DEFAULT_LENGTH, DEFAULT_TIMESTAMP, parentEntry);
      map.put(entryName, entry);
    }
    return entry;
  }

  @Nonnull
  protected Pair<String, String> splitPath(@Nonnull String entryName) {
    int p = entryName.lastIndexOf('/');
    String parentName = p > 0 ? entryName.substring(0, p) : "";
    String shortName = p > 0 ? entryName.substring(p + 1) : entryName;
    return Pair.create(parentName, shortName);
  }

  @Nonnull
  public abstract byte[] contentsToByteArray(@Nonnull String relativePath) throws IOException;
}
