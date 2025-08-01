/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package consulo.bookmark.ui.view.impl.internal.action;

import consulo.application.dumb.DumbAware;
import consulo.bookmark.Bookmark;
import consulo.bookmark.BookmarkManager;
import consulo.bookmark.localize.BookmarkLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import jakarta.annotation.Nonnull;

/**
 * @author max
 */
public abstract class ToggleNumberedBookmarkActionBase extends AnAction implements DumbAware {
    private final int myNumber;

    public ToggleNumberedBookmarkActionBase(int n) {
        super(BookmarkLocalize.actionBookmarkToggle0Text(n));
        myNumber = n;
    }

    @Override
    public void update(@Nonnull AnActionEvent e) {
        e.getPresentation().setEnabled(e.hasData(Project.KEY));
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(@Nonnull AnActionEvent e) {
        Project project = e.getRequiredData(Project.KEY);

        BookmarkInContextInfo info = new BookmarkInContextInfo(e.getDataContext(), project).invoke();
        if (info.getFile() == null) {
            return;
        }

        Bookmark oldBookmark = info.getBookmarkAtPlace();

        BookmarkManager manager = BookmarkManager.getInstance(project);
        if (oldBookmark != null) {
            manager.removeBookmark(oldBookmark);
        }

        if (oldBookmark == null || oldBookmark.getMnemonic() != '0' + myNumber) {
            Bookmark bookmark = manager.addTextBookmark(info.getFile(), info.getLine(), "");
            manager.setMnemonic(bookmark, (char) ('0' + myNumber));
        }
    }
}
