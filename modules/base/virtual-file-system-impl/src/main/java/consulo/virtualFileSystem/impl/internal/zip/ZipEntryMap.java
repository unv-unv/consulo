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
package consulo.virtualFileSystem.impl.internal.zip;

import consulo.util.collection.ContainerUtil;
import consulo.util.collection.Iterables;
import consulo.util.collection.Iterators;
import consulo.util.lang.CharArrayUtil;
import consulo.util.lang.function.Predicates;
import consulo.virtualFileSystem.archive.ArchiveHandler;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * Map of relativePath => ArchiveHandler.EntryInfo optimised for memory:
 * - it does not store keys (may be recovered from the ArchiveHandler.EntryInfo)
 * - does not support removal
 */
class ZipEntryMap extends AbstractMap<String, ArchiveHandler.EntryInfo> {
    private ArchiveHandler.EntryInfo[] entries;
    private int size;

    ZipEntryMap(int expectedSize) {
        size = 0;
        // expectedSize is the number of entries in the zip file, and the actual number of entries in the
        // hash map will be larger because we also need to store intermediate directories which aren't
        // represented by ZipEntry instances. Therefore, choose a smaller load factor than the one used in rehash().
        entries = new ArchiveHandler.EntryInfo[Math.max(10, expectedSize * 2)];  // load factor 0.5
    }

    @Override
    public ArchiveHandler.EntryInfo get(@Nonnull Object key) {
        String relativePath = (String) key;
        int index = index(relativePath, entries);
        ArchiveHandler.EntryInfo entry;
        int i = index;
        while (true) {
            entry = entries[i];
            if (entry == null || isTheOne(entry, relativePath)) {
                break;
            }
            if (++i == entries.length) {
                i = 0;
            }
            if (i == index) {
                entry = null;
                break;
            }
        }
        return entry;
    }

    private static int index(@Nonnull String relativePath, @Nonnull ArchiveHandler.EntryInfo[] entries) {
        return (relativePath.hashCode() & 0x7fffffff) % entries.length;
    }

    @Override
    public ArchiveHandler.EntryInfo put(String relativePath, ArchiveHandler.EntryInfo value) {
        if (size >= 5 * entries.length / 8) { // 0.625
            rehash();
        }

        ArchiveHandler.EntryInfo old = put(relativePath, value, entries);
        if (old == null) {
            size++;
        }
        return old;
    }

    @Nullable
    private static ArchiveHandler.EntryInfo put(
        @Nonnull String relativePath,
        @Nonnull ArchiveHandler.EntryInfo value,
        @Nonnull ArchiveHandler.EntryInfo[] entries
    ) {
        int index = index(relativePath, entries);
        ArchiveHandler.EntryInfo entry;
        int i = index;
        while (true) {
            entry = entries[i];
            if (entry == null || isTheOne(entry, relativePath)) {
                entries[i] = value;
                break;
            }
            if (++i == entries.length) {
                i = 0;
            }
        }
        return entry;
    }

    private static boolean isTheOne(@Nonnull ArchiveHandler.EntryInfo entry, @Nonnull CharSequence relativePath) {
        int endIndex = relativePath.length();
        for (ArchiveHandler.EntryInfo e = entry; e != null; e = e.parent) {
            CharSequence shortName = e.shortName;
            if (!CharArrayUtil.regionMatches(relativePath, endIndex - shortName.length(), relativePath.length(), shortName)) {
                return false;
            }

            endIndex -= shortName.length();
            if (e.parent != null && e.parent.shortName.length() != 0 && endIndex != 0) {
                // match "/"
                if (relativePath.charAt(endIndex - 1) == '/') {
                    endIndex -= 1;
                }
                else {
                    return false;
                }
            }

        }
        return endIndex == 0;
    }

    @Nonnull
    private ArchiveHandler.EntryInfo[] rehash() {
        ArchiveHandler.EntryInfo[] newEntries =
            new ArchiveHandler.EntryInfo[entries.length < 1000 ? entries.length * 2 : entries.length * 3 / 2];
        for (ArchiveHandler.EntryInfo entry : entries) {
            if (entry != null) {
                put(getRelativePath(entry), entry, newEntries);
            }
        }
        entries = newEntries;
        return newEntries;
    }

    @Nonnull
    private static String getRelativePath(@Nonnull ArchiveHandler.EntryInfo entry) {
        StringBuilder result = new StringBuilder(entry.shortName.length() + 10);
        for (ArchiveHandler.EntryInfo e = entry; e != null; e = e.parent) {
            if (result.length() != 0 && e.shortName.length() != 0) {
                result.append('/');
            }
            appendReversed(result, e.shortName);
        }
        return result.reverse().toString();
    }

    private static void appendReversed(@Nonnull StringBuilder builder, @Nonnull CharSequence sequence) {
        for (int i = sequence.length() - 1; i >= 0; i--) {
            builder.append(sequence.charAt(i));
        }
    }

    @Override
    public ArchiveHandler.EntryInfo remove(@Nonnull Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void clear() {
        size = 0;
        entries = new ArchiveHandler.EntryInfo[10];
    }

    private EntrySet entrySet;

    @Nonnull
    @Override
    public EntrySet entrySet() {
        EntrySet es;
        return (es = entrySet) == null ? (entrySet = new EntrySet()) : es;
    }

    private final class EntrySet extends AbstractSet<Entry<String, ArchiveHandler.EntryInfo>> {
        @Override
        public final int size() {
            return ZipEntryMap.this.size();
        }

        @Override
        public final void clear() {
            ZipEntryMap.this.clear();
        }

        @Nonnull
        @Override
        public final Iterator<Entry<String, ArchiveHandler.EntryInfo>> iterator() {
            return Iterators.mapIterator(
                Iterables.iterate(entries, Predicates.notNull()).iterator(),
                entry -> new SimpleEntry<>(getRelativePath(entry), entry)
            );
        }

        @Override
        public final boolean contains(Object o) {
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            String key = (String) e.getKey();
            ArchiveHandler.EntryInfo value = (ArchiveHandler.EntryInfo) e.getValue();
            return value.equals(get(key));
        }

        @Override
        public final boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }
    }

    @Nonnull
    @Override
    public Collection<ArchiveHandler.EntryInfo> values() {
        return ContainerUtil.filter(entries, Predicates.notNull());
    }
}
