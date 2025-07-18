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
package consulo.ide.impl.idea.openapi.vcs.changes.committed;

import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.action.VcsContext;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangeList;
import consulo.versionControlSystem.change.ChangesUtil;
import consulo.ide.impl.idea.openapi.vcs.update.AbstractCommonUpdateAction;
import consulo.versionControlSystem.impl.internal.change.commited.CommittedChangesCache;
import consulo.versionControlSystem.localize.VcsLocalize;
import consulo.versionControlSystem.update.ActionInfo;
import consulo.ide.impl.idea.openapi.vcs.update.ScopeInfo;
import consulo.versionControlSystem.versionBrowser.CommittedChangeList;
import jakarta.annotation.Nonnull;

import java.util.*;

/**
 * @author yole
 */
public class GetCommittedChangelistAction extends AbstractCommonUpdateAction {
  public GetCommittedChangelistAction() {
    super(ActionInfo.UPDATE, CHANGELIST, false);
  }

  @Override
  @RequiredUIAccess
  protected void actionPerformed(@Nonnull VcsContext context) {
    Collection<FilePath> filePaths = getFilePaths(context);
    final List<ChangeList> selectedChangeLists = new ArrayList<>();
    final ChangeList[] selectionFromContext = context.getSelectedChangeLists();
    if (selectionFromContext != null) {
      Collections.addAll(selectedChangeLists, selectionFromContext);
    }
    final List<CommittedChangeList> incomingChanges = CommittedChangesCache.getInstance(context.getProject()).getCachedIncomingChanges();
    final List<CommittedChangeList> intersectingChanges = new ArrayList<>();
    if (incomingChanges != null) {
      for(CommittedChangeList changeList: incomingChanges) {
        if (!selectedChangeLists.contains(changeList)) {
          for(Change change: changeList.getChanges()) {
            if (filePaths.contains(ChangesUtil.getFilePath(change))) {
              intersectingChanges.add(changeList);
              break;
            }
          }
        }
      }
    }
    if (intersectingChanges.size() > 0) {
      int rc = Messages.showOkCancelDialog(
          context.getProject(),
          VcsLocalize.getCommittedChangesIntersectingPrompt(intersectingChanges.size(), selectedChangeLists.size()).get(),
          VcsLocalize.getCommittedChangesTitle().get(),
          UIUtil.getQuestionIcon()
      );
      if (rc != Messages.OK) return;
    }
    super.actionPerformed(context);
  }

  @Override
  protected boolean filterRootsBeforeAction() {
    return false;
  }

  @Override
  protected void update(@Nonnull final VcsContext vcsContext, @Nonnull final Presentation presentation) {
    super.update(vcsContext, presentation);
    final ChangeList[] changeLists = vcsContext.getSelectedChangeLists();
    presentation.setEnabled(presentation.isEnabled() &&
                            CommittedChangesCache.getInstance(vcsContext.getProject()).getCachedIncomingChanges() != null &&
                            changeLists != null &&  changeLists.length > 0);
  }

  private static final ScopeInfo CHANGELIST = new ScopeInfo() {
    @Override
    public FilePath[] getRoots(final VcsContext context, final ActionInfo actionInfo) {
      final Collection<FilePath> filePaths = getFilePaths(context);
      return filePaths.toArray(new FilePath[filePaths.size()]);
    }

    @Override
    public String getScopeName(final VcsContext dataContext, final ActionInfo actionInfo) {
      return "Changelist";
    }

    @Override
    public boolean filterExistsInVcs() {
      return false;
    }
  };

  private static Collection<FilePath> getFilePaths(final VcsContext context) {
    final Set<FilePath> files = new HashSet<>();
    final ChangeList[] selectedChangeLists = context.getSelectedChangeLists();
    if (selectedChangeLists != null) {
      for(ChangeList changelist: selectedChangeLists) {
        for(Change change: changelist.getChanges()) {
          files.add(ChangesUtil.getFilePath(change));
        }
      }
    }
    return files;
  }
}
