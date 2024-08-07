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

package consulo.versionControlSystem;

import consulo.application.util.function.AsynchConsumer;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.change.commited.DecoratorManager;
import consulo.versionControlSystem.change.commited.VcsCommittedListsZipper;
import consulo.versionControlSystem.change.commited.VcsCommittedViewAuxiliary;
import consulo.versionControlSystem.history.VcsRevisionNumber;
import consulo.versionControlSystem.versionBrowser.ChangeBrowserSettings;
import consulo.versionControlSystem.versionBrowser.ChangesBrowserSettingsEditor;
import consulo.versionControlSystem.versionBrowser.CommittedChangeList;
import consulo.versionControlSystem.versionBrowser.ui.awt.SimpleStandardVersionFilterComponent;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * @author yole
 */
public interface CommittedChangesProvider<T extends CommittedChangeList, U extends ChangeBrowserSettings> extends VcsProviderMarker {
  @Nonnull
  U createDefaultSettings();

  @Nonnull
  @RequiredUIAccess
  @SuppressWarnings("unchecked")
  default ChangesBrowserSettingsEditor<U> createFilterUI(final boolean showDateFilter) {
    return (ChangesBrowserSettingsEditor<U>)new SimpleStandardVersionFilterComponent(showDateFilter);
  }

  @Nullable
  RepositoryLocation getLocationFor(FilePath root);

  @Nullable
  RepositoryLocation getLocationFor(final FilePath root, final String repositoryPath);

  @Nullable
  VcsCommittedListsZipper getZipper();

  List<T> getCommittedChanges(U settings, RepositoryLocation location, final int maxCount) throws VcsException;

  void loadCommittedChanges(U settings, RepositoryLocation location, final int maxCount, final AsynchConsumer<CommittedChangeList> consumer) throws VcsException;

  ChangeListColumn[] getColumns();

  @Nullable
  VcsCommittedViewAuxiliary createActions(final DecoratorManager manager, final RepositoryLocation location);

  /**
   * since may be different for different VCSs
   */
  int getUnlimitedCountValue();

  /**
   * @return required list and path of the target file in that revision (changes when move/rename)
   */
  @Nullable
  Pair<T, FilePath> getOneList(final VirtualFile file, final VcsRevisionNumber number) throws VcsException;

  RepositoryLocation getForNonLocal(final VirtualFile file);

  /**
   * Return true if this committed changes provider can be used to show the incoming changes.
   * If false is returned, the "Incoming" tab won't be shown in the Changes toolwindow.
   */
  boolean supportsIncomingChanges();
}
