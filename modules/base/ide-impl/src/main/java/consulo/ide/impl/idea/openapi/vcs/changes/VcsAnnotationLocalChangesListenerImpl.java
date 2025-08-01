/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package consulo.ide.impl.idea.openapi.vcs.changes;

import consulo.application.ApplicationManager;
import consulo.project.Project;
import consulo.versionControlSystem.impl.internal.util.ZipperUpdater;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import consulo.versionControlSystem.VcsKey;
import consulo.versionControlSystem.change.BaseRevision;
import consulo.versionControlSystem.change.VcsAnnotationRefresher;
import consulo.versionControlSystem.root.VcsRoot;
import consulo.versionControlSystem.annotate.FileAnnotation;
import consulo.versionControlSystem.change.VcsAnnotationLocalChangesListener;
import consulo.versionControlSystem.diff.DiffProvider;
import consulo.versionControlSystem.history.VcsRevisionNumber;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.virtualFileSystem.VirtualFile;
import consulo.ui.ex.awt.util.Alarm;
import consulo.util.collection.MultiMap;
import consulo.component.messagebus.MessageBusConnection;
import consulo.disposer.Disposable;

import java.io.File;
import java.util.*;

/**
 * @author Irina.Chernushina
 * @since 2012-11-20
 */
public class VcsAnnotationLocalChangesListenerImpl implements Disposable, VcsAnnotationLocalChangesListener {
  private final ZipperUpdater myUpdater;
  private final MessageBusConnection myConnection;

  private final Runnable myUpdateStuff;

  private final Set<String> myDirtyPaths;
  private final Set<VirtualFile> myDirtyFiles;
  private final Map<String, VcsRevisionNumber> myDirtyChanges;
  private final LocalFileSystem myLocalFileSystem;
  private final ProjectLevelVcsManager myVcsManager;
  private final Set<VcsKey> myVcsKeySet;
  private final Object myLock;

  private final MultiMap<VirtualFile, FileAnnotation> myFileAnnotationMap;

  public VcsAnnotationLocalChangesListenerImpl(Project project, final ProjectLevelVcsManager vcsManager) {
    myLock = new Object();
    myUpdateStuff = createUpdateStuff();
    myUpdater = new ZipperUpdater(ApplicationManager.getApplication().isUnitTestMode() ? 10 : 300, Alarm.ThreadToUse.POOLED_THREAD, project);
    myConnection = project.getMessageBus().connect();
    myLocalFileSystem = LocalFileSystem.getInstance();
    VcsAnnotationRefresher handler = createHandler();
    myDirtyPaths = new HashSet<>();
    myDirtyChanges = new HashMap<>();
    myDirtyFiles = new HashSet<>();
    myFileAnnotationMap = MultiMap.createSet();
    myVcsManager = vcsManager;
    myVcsKeySet = new HashSet<>();

    myConnection.subscribe(VcsAnnotationRefresher.class, handler);
  }

  private Runnable createUpdateStuff() {
    return new Runnable() {
      @Override
      public void run() {
        final Set<String> paths = new HashSet<>();
        final Map<String, VcsRevisionNumber> changes = new HashMap<>();
        final Set<VirtualFile> files = new HashSet<>();
        Set<VcsKey> vcsToRefresh;
        synchronized (myLock) {
          vcsToRefresh = new HashSet<>(myVcsKeySet);

          paths.addAll(myDirtyPaths);
          changes.putAll(myDirtyChanges);
          files.addAll(myDirtyFiles);
          myDirtyPaths.clear();
          myDirtyChanges.clear();
          myVcsKeySet.clear();
          myDirtyFiles.clear();
        }

        closeForVcs(vcsToRefresh);
        checkByDirtyScope(paths, changes, files);
      }
    };
  }

  private void checkByDirtyScope(Set<String> removed, Map<String, VcsRevisionNumber> refresh, Set<VirtualFile> files) {
    for (String path : removed) {
      refreshForPath(path, null);
    }
    for (Map.Entry<String, VcsRevisionNumber> entry : refresh.entrySet()) {
      refreshForPath(entry.getKey(), entry.getValue());
    }
    for (VirtualFile file : files) {
      processUnderFile(file);
    }
  }

  private void processUnderFile(VirtualFile file) {
    final MultiMap<VirtualFile, FileAnnotation> annotations = new MultiMap<>();
    synchronized (myLock) {
      for (VirtualFile virtualFile : myFileAnnotationMap.keySet()) {
        if (VfsUtilCore.isAncestor(file, virtualFile, true)) {
          final Collection<FileAnnotation> values = myFileAnnotationMap.get(virtualFile);
          for (FileAnnotation value : values) {
            annotations.putValue(virtualFile, value);
          }
        }
      }
    }
    if (! annotations.isEmpty()) {
      for (Map.Entry<VirtualFile, Collection<FileAnnotation>> entry : annotations.entrySet()) {
        final VirtualFile key = entry.getKey();
        final VcsRevisionNumber number = fromDiffProvider(key);
        if (number == null) continue;
        final Collection<FileAnnotation> fileAnnotations = entry.getValue();
        for (FileAnnotation annotation : fileAnnotations) {
          if (annotation.isBaseRevisionChanged(number)) {
            annotation.close();
          }
        }
      }
    }
  }

  private void refreshForPath(String path, VcsRevisionNumber number) {
    final File file = new File(path);
    VirtualFile vf = myLocalFileSystem.findFileByIoFile(file);
    if (vf == null) {
      vf = myLocalFileSystem.refreshAndFindFileByIoFile(file);
    }
    if (vf == null) return;
    processFile(number, vf);
  }

  private void processFile(VcsRevisionNumber number, VirtualFile vf) {
    final Collection<FileAnnotation> annotations;
    synchronized (myLock) {
      annotations = myFileAnnotationMap.get(vf);
    }
    if (! annotations.isEmpty()) {
      if (number == null) {
        number = fromDiffProvider(vf);
      }
      if (number == null) return;

      for (FileAnnotation annotation : annotations) {
        if (annotation.isBaseRevisionChanged(number)) {
          annotation.close();
        }
      }
    }
  }

  private VcsRevisionNumber fromDiffProvider(final VirtualFile vf) {
    final VcsRoot vcsRoot = myVcsManager.getVcsRootObjectFor(vf);
    DiffProvider diffProvider;
    if (vcsRoot != null && vcsRoot.getVcs() != null && (diffProvider = vcsRoot.getVcs().getDiffProvider()) != null) {
      return diffProvider.getCurrentRevision(vf);
    }
    return null;
  }

  private void closeForVcs(final Set<VcsKey> refresh) {
    if (refresh.isEmpty()) return;
    final Set<FileAnnotation> copy = new HashSet<>();
    synchronized (myLock) {
      for (FileAnnotation annotation : myFileAnnotationMap.values()) {
        final VcsKey key = annotation.getVcsKey();
        if (key != null && refresh.contains(key)) {
          copy.add(annotation);
        }
      }
    }
    for (FileAnnotation annotation : copy) {
      annotation.close();
    }
  }

  // annotations for already committed revisions should not register with this method - they are not subject to refresh
  @Override
  public void registerAnnotation(final VirtualFile file, final FileAnnotation annotation) {
    synchronized (myLock) {
      myFileAnnotationMap.putValue(file, annotation);
    }
  }

  @Override
  public void unregisterAnnotation(final VirtualFile file, final FileAnnotation annotation) {
    synchronized (myLock) {
      final Collection<FileAnnotation> annotations = myFileAnnotationMap.get(file);
      if (!annotations.isEmpty()) {
        annotations.remove(annotation);
      }
      if (annotations.isEmpty()) {
        myFileAnnotationMap.remove(file);
      }
    }
  }

  @Override
  public void dispose() {
    myConnection.disconnect();
    myUpdater.stop();
  }

  private VcsAnnotationRefresher createHandler() {
    return new VcsAnnotationRefresher() {
      @Override
      public void dirtyUnder(VirtualFile file) {
        if (file == null) return;
        synchronized (myLock) {
          myDirtyFiles.add(file);
        }
        myUpdater.queue(myUpdateStuff);
      }

      @Override
      public void dirty(BaseRevision currentRevision) {
        synchronized (myLock) {
          myDirtyChanges.put(currentRevision.getPath().getPath(), currentRevision.getRevision());
        }
        myUpdater.queue(myUpdateStuff);
      }

      @Override
      public void dirty(String path) {
        synchronized (myLock) {
          myDirtyPaths.add(path);
        }
        myUpdater.queue(myUpdateStuff);
      }

      @Override
      public void configurationChanged(VcsKey vcsKey) {
        synchronized (myLock) {
          myVcsKeySet.add(vcsKey);
        }
        myUpdater.queue(myUpdateStuff);
      }
    };
  }
}
