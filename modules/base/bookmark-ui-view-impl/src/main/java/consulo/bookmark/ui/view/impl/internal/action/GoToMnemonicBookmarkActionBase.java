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
package consulo.bookmark.ui.view.impl.internal.action;

import consulo.application.dumb.DumbAware;
import consulo.bookmark.localize.BookmarkLocalize;
import consulo.bookmark.Bookmark;
import consulo.bookmark.BookmarkManager;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;

/**
 * @author max
 */
public abstract class GoToMnemonicBookmarkActionBase extends AnAction implements DumbAware {
    private final int myNumber;

    public GoToMnemonicBookmarkActionBase(int n) {
        super(BookmarkLocalize.actionBookmarkGoto0Text(n));
        myNumber = n;
    }

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(e.hasData(Project.KEY));
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getRequiredData(Project.KEY);

        Bookmark bookmark = BookmarkManager.getInstance(project).findBookmarkForMnemonic((char) ('0' + myNumber));
        if (bookmark != null) {
            bookmark.navigate(true);
        }
    }
}
