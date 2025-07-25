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
package consulo.ide.impl.idea.ide.favoritesTreeView.actions;

import consulo.bookmark.icon.BookmarkIconGroup;
import consulo.ide.localize.IdeLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import jakarta.annotation.Nonnull;

/**
 * @author anna
 * @since 2005-02-28
 */
class AddAllOpenFilesToNewFavoritesListAction extends AnAction {
    public AddAllOpenFilesToNewFavoritesListAction() {
        super(
            IdeLocalize.actionAddAllOpenTabsToNewFavoritesList(),
            IdeLocalize.actionAddToNewFavoritesList(),
            BookmarkIconGroup.actionAddbookmarkslist()
        );
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(@Nonnull AnActionEvent e) {
        String newName = AddNewFavoritesListAction.doAddNewFavoritesList(e.getRequiredData(Project.KEY));
        if (newName != null) {
            new AddAllOpenFilesToFavorites(newName).actionPerformed(e);
        }
    }

    @Override
    public void update(@Nonnull AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        e.getPresentation().setEnabled(project != null && !AddAllOpenFilesToFavorites.getFilesToAdd(project).isEmpty());
    }
}
