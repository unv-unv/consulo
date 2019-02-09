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
package com.intellij.testFramework.vcs;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.ThreeState;
import com.intellij.vcsUtil.VcsUtil;
import consulo.ui.RequiredUIAccess;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.*;

/**
 * @author Kirill Likhodedov
 */
public class MockChangeListManager extends ChangeListManagerEx {

  public static final String DEFAULT_CHANGE_LIST_NAME = "Default";

  private final Map<String, MockChangeList> myChangeLists = new HashMap<>();
  private LocalChangeList myActiveChangeList;
  private final MockChangeList myDefaultChangeList;

  public MockChangeListManager() {
    myDefaultChangeList = new MockChangeList(DEFAULT_CHANGE_LIST_NAME);
    myChangeLists.put(DEFAULT_CHANGE_LIST_NAME, myDefaultChangeList);
    myActiveChangeList = myDefaultChangeList;
  }

  public void addChanges(Change... changes) {
    MockChangeList changeList = myChangeLists.get(DEFAULT_CHANGE_LIST_NAME);
    for (Change change : changes) {
      changeList.add(change);
    }
  }

  @Override
  public void scheduleUpdate() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void scheduleUpdate(boolean updateUnversionedFiles) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void invokeAfterUpdate(Runnable afterUpdate,
                                InvokeAfterUpdateMode mode,
                                String title,
                                ModalityState state) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void invokeAfterUpdate(Runnable afterUpdate,
                                InvokeAfterUpdateMode mode,
                                String title,
                                Consumer<VcsDirtyScopeManager> dirtyScopeManager,
                                ModalityState state) {
    afterUpdate.run();
  }

  @TestOnly
  @Override
  public boolean ensureUpToDate(boolean canBeCanceled) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getChangeListsNumber() {
    return getChangeListsCopy().size();
  }

  @Override
  public List<LocalChangeList> getChangeListsCopy() {
    return new ArrayList<>(myChangeLists.values());
  }

  @Nonnull
  @Override
  public List<LocalChangeList> getChangeLists() {
    return getChangeListsCopy();
  }

  @Override
  public List<File> getAffectedPaths() {
    throw new UnsupportedOperationException();
  }

  @Nonnull
  @Override
  public List<VirtualFile> getAffectedFiles() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isFileAffected(VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Nonnull
  @Override
  public Collection<Change> getAllChanges() {
    Collection<Change> changes = new ArrayList<>();
    for (MockChangeList list : myChangeLists.values()) {
      changes.addAll(list.getChanges());
    }
    return changes;
  }

  @Override
  public LocalChangeList findChangeList(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public LocalChangeList getChangeList(String id) {
    throw new UnsupportedOperationException();
  }

  @Override
  public LocalChangeList getDefaultChangeList() {
    return myActiveChangeList;
  }

  @Override
  public boolean isDefaultChangeList(ChangeList list) {
    throw new UnsupportedOperationException();
  }

  @Override
  public LocalChangeList getChangeList(@Nonnull Change change) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getChangeListNameIfOnlyOne(Change[] changes) {
    throw new UnsupportedOperationException();
  }

  @Nonnull
  @Override
  public Runnable prepareForChangeDeletion(Collection<Change> changes) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Change getChange(@Nonnull VirtualFile file) {
    return getChange(VcsUtil.getFilePath(file));
  }

  @Override
  public LocalChangeList getChangeList(@Nonnull VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Change getChange(FilePath file) {
    for (Change change : getAllChanges()) {
      ContentRevision before = change.getBeforeRevision();
      ContentRevision after = change.getAfterRevision();
      if (after != null && after.getFile().equals(file) || before != null && before.getFile().equals(file)) {
        return change;
      }
    }
    return null;
  }

  @Override
  public boolean isUnversioned(VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Nonnull
  @Override
  public FileStatus getStatus(VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Nonnull
  @Override
  public Collection<Change> getChangesIn(VirtualFile dir) {
    return getChangesIn(VcsUtil.getFilePath(dir));
  }

  @Nonnull
  @Override
  public Collection<Change> getChangesIn(FilePath path) {
    List<Change> changes = new ArrayList<>();
    for (Change change : getAllChanges()) {
      ContentRevision before = change.getBeforeRevision();
      ContentRevision after = change.getAfterRevision();
      if (before != null && before.getFile().isUnder(path, false) || after != null && after.getFile().isUnder(path, false)) {
        changes.add(change);
      }
    }
    return changes;
  }

  @Nullable
  @Override
  public AbstractVcs getVcsFor(@Nonnull Change change) {
    return null;
  }

  @Nonnull
  @Override
  public ThreeState haveChangesUnder(@Nonnull VirtualFile vf) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addChangeListListener(ChangeListListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeChangeListListener(ChangeListListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void registerCommitExecutor(CommitExecutor executor) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void commitChanges(LocalChangeList changeList, List<Change> changes) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void commitChangesSynchronously(LocalChangeList changeList, List<Change> changes) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean commitChangesSynchronouslyWithResult(LocalChangeList changeList, List<Change> changes) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void reopenFiles(List<FilePath> paths) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<CommitExecutor> getRegisteredExecutors() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addFilesToIgnore(IgnoredFileBean... ignoredFiles) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addDirectoryToIgnoreImplicitly(@Nonnull String path) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setFilesToIgnore(IgnoredFileBean... ignoredFiles) {
    throw new UnsupportedOperationException();
  }

  @Override
  public IgnoredFileBean[] getFilesToIgnore() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isIgnoredFile(@Nonnull VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getSwitchedBranch(VirtualFile file) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getDefaultListName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void letGo() {
  }

  @Override
  public String isFreezed() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isFreezedWithNotification(@javax.annotation.Nullable String modalTitle) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<VirtualFile> getModifiedWithoutEditing() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public LocalChangeList addChangeList(@Nonnull String name, @javax.annotation.Nullable String comment) {
    MockChangeList changeList = new MockChangeList(name);
    myChangeLists.put(name, changeList);
    return changeList;
  }

  @Override
  public void setDefaultChangeList(@Nonnull LocalChangeList list) {
    myActiveChangeList = list;
  }

  @Override
  public void removeChangeList(String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeChangeList(LocalChangeList list) {
    myChangeLists.remove(list.getName());
    if (myActiveChangeList.equals(list)) {
      myActiveChangeList = myDefaultChangeList;
    }
  }

  @Override
  public void moveChangesTo(LocalChangeList list, Change[] changes) {
  }

  @Override
  public boolean setReadOnly(String name, boolean value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean editName(@Nonnull String fromName, @Nonnull String toName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String editComment(@Nonnull String fromName, String newComment) {
    throw new UnsupportedOperationException();
  }

  @javax.annotation.Nullable
  @Override
  public LocalChangeList getIdentityChangeList(Change change) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isInUpdate() {
    throw new UnsupportedOperationException();
  }

  @Nonnull
  @Override
  public Collection<LocalChangeList> getInvolvedListsFilterChanges(Collection<Change> changes, List<Change> validChanges) {
    throw new UnsupportedOperationException();
  }

  @Nonnull
  @Override
  public LocalChangeList addChangeList(@Nonnull String name, @Nullable String comment, @javax.annotation.Nullable Object data) {
    return addChangeList(name, comment);
  }

  @RequiredUIAccess
  @Override
  public void blockModalNotifications() {
    throw new UnsupportedOperationException();
  }

  @RequiredUIAccess
  @Override
  public void unblockModalNotifications() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void freeze(@Nonnull String reason) {

  }

  @Override
  public void unfreeze() {

  }
}
