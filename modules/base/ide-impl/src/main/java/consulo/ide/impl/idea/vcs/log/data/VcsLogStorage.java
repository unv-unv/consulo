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
package consulo.ide.impl.idea.vcs.log.data;

import consulo.versionControlSystem.log.CommitId;
import consulo.versionControlSystem.log.Hash;
import consulo.versionControlSystem.log.VcsRef;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.function.Predicate;

/**
 * Storage for various Log objects like CommitId or VcsRef
 * which quantity is to big to keep them in memory.
 * VcsLogStorage keeps a mapping from integers to those objects
 * allowing to operate with integers, not the objects themselves.
 */
public interface VcsLogStorage {

    /**
     * Returns an integer index that is a unique identifier for a commit with specified hash and root.
     *
     * @param hash commit hash
     * @param root root of the repository for the commit
     * @return a commit index
     */
    int getCommitIndex(@Nonnull Hash hash, @Nonnull VirtualFile root);

    /**
     * Returns a commit for a specified index or null if this index does not correspond to any commit.
     *
     * @param commitIndex index of a commit
     * @return commit identified by this index or null
     */
    @Nullable
    CommitId getCommitId(int commitIndex);

    /**
     * Iterates over known commit ids to find the first one which satisfies given condition.
     *
     * @return matching commit or null if no commit matches the given condition
     */
    @Nullable
    CommitId findCommitId(@Nonnull Predicate<CommitId> condition);

    /**
     * Returns an integer index that is a unique identifier for a reference.
     *
     * @param ref reference
     * @return a reference index
     */
    int getRefIndex(@Nonnull VcsRef ref);

    /**
     * Returns a reference for a specified index or null if this index does not correspond to any reference.
     *
     * @param refIndex index of a reference
     * @return reference identified by this index or null
     */
    @Nullable
    VcsRef getVcsRef(int refIndex);

    /**
     * Forces data in the storage to be written on disk.
     */
    void flush();
}
