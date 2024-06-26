/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import consulo.application.ApplicationManager;
import consulo.ide.ServiceManager;
import consulo.project.Project;
import consulo.language.content.FileIndexFacade;
import consulo.util.lang.Comparing;
import consulo.application.util.function.Computable;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.versionControlSystem.AbstractVcs;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import consulo.versionControlSystem.VirtualFileFilter;
import consulo.versionControlSystem.base.FilePathImpl;
import consulo.versionControlSystem.root.VcsRoot;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileVisitor;
import consulo.application.util.function.Processor;
import consulo.util.lang.StringLenComparator;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

public class VcsRootIterator {
  // folder path to files to be excluded
  private final Map<String, MyRootFilter> myOtherVcsFolders;
  private final FileIndexFacade myExcludedFileIndex;

  public VcsRootIterator(final Project project, final AbstractVcs vcs) {
    final ProjectLevelVcsManager plVcsManager = ProjectLevelVcsManager.getInstance(project);
    myOtherVcsFolders = new HashMap<String, MyRootFilter>();
    myExcludedFileIndex = ServiceManager.getService(project, FileIndexFacade.class);

    final VcsRoot[] allRoots = plVcsManager.getAllVcsRoots();
    final VirtualFile[] roots = plVcsManager.getRootsUnderVcs(vcs);
    for (VirtualFile root : roots) {
      final MyRootFilter rootPresentFilter = new MyRootFilter(root, vcs.getName());
      rootPresentFilter.init(allRoots);
      myOtherVcsFolders.put(root.getUrl(), rootPresentFilter);
    }
  }

  public boolean acceptFolderUnderVcs(final VirtualFile vcsRoot, final VirtualFile file) {
    final String vcsUrl = vcsRoot.getUrl();
    final MyRootFilter rootFilter = myOtherVcsFolders.get(vcsUrl);
    if ((rootFilter != null) && (! rootFilter.accept(file))) {
      return false;
    }
    final Boolean excluded = isExcluded(myExcludedFileIndex, file);
    if (excluded) return false;
    return true;
  }

  private static boolean isExcluded(final FileIndexFacade indexFacade, final VirtualFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return indexFacade.isExcludedFile(file);
      }
    });
  }

  private static class MyRootFilter {
    private final VirtualFile myRoot;
    private final String myVcsName;

    // virtual file URLs
    private final List<String> myExcludedByOthers;

    private MyRootFilter(final VirtualFile root, final String vcsName) {
      myRoot = root;
      myVcsName = vcsName;

      myExcludedByOthers = new LinkedList<String>();
    }

    private void init(final VcsRoot[] allRoots) {
      final String ourPath = myRoot.getUrl();

      for (VcsRoot root : allRoots) {
        final AbstractVcs vcs = root.getVcs();
        if (vcs == null || Comparing.equal(vcs.getName(), myVcsName)) continue;
        final VirtualFile path = root.getPath();
        if (path != null) {
          final String url = path.getUrl();
          if (url.startsWith(ourPath)) {
            myExcludedByOthers.add(url);
          }
        }
      }

      Collections.sort(myExcludedByOthers, StringLenComparator.getDescendingInstance());
    }

    public boolean accept(final VirtualFile vf) {
      final String url = vf.getUrl();
      for (String excludedByOtherVcs : myExcludedByOthers) {
        // use the fact that they are sorted
        if (url.length() > excludedByOtherVcs.length()) return true;
        if (url.startsWith(excludedByOtherVcs)) return false;
      }
      return true;
    }
  }

  public static void iterateVfUnderVcsRoot(final Project project,
                                           final VirtualFile root,
                                           final Processor<VirtualFile> processor) {
    final MyRootIterator rootIterator = new MyRootIterator(project, root, null, processor, null);
    rootIterator.iterate();
  }

  public static void iterateVcsRoot(final Project project,
                                    final VirtualFile root,
                                    final Processor<FilePath> processor) {
    iterateVcsRoot(project, root, processor, null);
  }

  public static void iterateVcsRoot(final Project project,
                                       final VirtualFile root,
                                       final Processor<FilePath> processor,
                                       @jakarta.annotation.Nullable VirtualFileFilter directoryFilter) {
    final MyRootIterator rootIterator = new MyRootIterator(project, root, processor, null, directoryFilter);
    rootIterator.iterate();
  }

  private static class MyRootIterator {
    private final Project myProject;
    private final Processor<FilePath> myPathProcessor;
    private final Processor<VirtualFile> myFileProcessor;
    @jakarta.annotation.Nullable
    private final VirtualFileFilter myDirectoryFilter;
    private final VirtualFile myRoot;
    private final MyRootFilter myRootPresentFilter;
    private final FileIndexFacade myExcludedFileIndex;

    private MyRootIterator(final Project project,
                           final VirtualFile root,
                           @Nullable final Processor<FilePath> pathProcessor,
                           @jakarta.annotation.Nullable final Processor<VirtualFile> fileProcessor,
                           @Nullable VirtualFileFilter directoryFilter) {
      myProject = project;
      myPathProcessor = pathProcessor;
      myFileProcessor = fileProcessor;
      myDirectoryFilter = directoryFilter;
      myRoot = root;

      final ProjectLevelVcsManager plVcsManager = ProjectLevelVcsManager.getInstance(project);
      final AbstractVcs vcs = plVcsManager.getVcsFor(root);
      myRootPresentFilter = (vcs == null) ? null : new MyRootFilter(root, vcs.getName());
      if (myRootPresentFilter != null) {
        myRootPresentFilter.init(ProjectLevelVcsManager.getInstance(myProject).getAllVcsRoots());
      }
      myExcludedFileIndex = ServiceManager.getService(project, FileIndexFacade.class);
    }

    public void iterate() {
      VfsUtilCore.visitChildrenRecursively(myRoot, new VirtualFileVisitor(VirtualFileVisitor.NO_FOLLOW_SYMLINKS) {
        @Override
        public void afterChildrenVisited(@Nonnull VirtualFile file) {
          if (myDirectoryFilter != null) {
            myDirectoryFilter.afterChildrenVisited(file);
          }
        }

        @Nonnull
        @Override
        public Result visitFileEx(@Nonnull VirtualFile file) {
          if (isExcluded(myExcludedFileIndex, file)) return SKIP_CHILDREN;
          if (myRootPresentFilter != null && ! myRootPresentFilter.accept(file)) return SKIP_CHILDREN;
          if (myProject.isDisposed() || ! process(file)) return skipTo(myRoot);
          if (myDirectoryFilter != null && file.isDirectory() && ! myDirectoryFilter.shouldGoIntoDirectory(file)) return SKIP_CHILDREN;
          return CONTINUE;
        }
      });
    }

    private boolean process(VirtualFile current) {
      if (myPathProcessor != null) {
        return myPathProcessor.process(new FilePathImpl(current));
      }
      else if (myFileProcessor != null) {
        return myFileProcessor.process(current);
      }
      return false;
    }
  }
}
