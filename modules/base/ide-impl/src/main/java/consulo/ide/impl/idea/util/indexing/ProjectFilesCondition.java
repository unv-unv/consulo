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
package consulo.ide.impl.idea.util.indexing;

import consulo.content.scope.SearchScope;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileWithId;

import java.util.function.Predicate;

class ProjectFilesCondition implements Predicate<VirtualFile> {
    private static final int MAX_FILES_TO_UPDATE_FROM_OTHER_PROJECT = 2;
    private final VirtualFile myRestrictedTo;
    private final SearchScope myFilter;
    private int myFilesFromOtherProjects;
    private final FileBasedIndexImpl.ProjectIndexableFilesFilter myIndexableFilesFilter;

    public ProjectFilesCondition(
        FileBasedIndexImpl.ProjectIndexableFilesFilter indexableFilesFilter,
        SearchScope filter,
        VirtualFile restrictedTo,
        boolean includeFilesFromOtherProjects
    ) {
        myRestrictedTo = restrictedTo;
        myFilter = filter;
        myIndexableFilesFilter = indexableFilesFilter;
        if (!includeFilesFromOtherProjects) {
            myFilesFromOtherProjects = MAX_FILES_TO_UPDATE_FROM_OTHER_PROJECT;
        }
    }

    @Override
    public boolean test(VirtualFile file) {
        int fileId = ((VirtualFileWithId)file).getId();
        if (myIndexableFilesFilter != null && fileId > 0 && !myIndexableFilesFilter.containsFileId(fileId)) {
            if (myFilesFromOtherProjects >= MAX_FILES_TO_UPDATE_FROM_OTHER_PROJECT) {
                return false;
            }
            ++myFilesFromOtherProjects;
            return true;
        }

        if (fileId < 0 && file instanceof DeletedVirtualFileStub) {
            //file = ((FileBasedIndexImpl.MyLightVirtualFile)file).getOriginalFile();
            return true;
        }
        if (FileBasedIndexImpl.belongsToScope(file, myRestrictedTo, myFilter)) {
            return true;
        }

        if (myFilesFromOtherProjects < MAX_FILES_TO_UPDATE_FROM_OTHER_PROJECT) {
            ++myFilesFromOtherProjects;
            return true;
        }
        return false;
    }
}
