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
package consulo.diff.impl.internal.action;

import consulo.application.dumb.DumbAware;
import consulo.diff.DiffManager;
import consulo.diff.request.DiffRequest;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileWithoutContent;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public abstract class BaseShowDiffAction extends AnAction implements DumbAware {
    protected BaseShowDiffAction(@Nonnull LocalizeValue text, @Nonnull LocalizeValue description) {
        this(text, description, null);
    }

    protected BaseShowDiffAction(@Nonnull LocalizeValue text, @Nonnull LocalizeValue description, @Nullable Image icon) {
        super(text, description, icon);
        setEnabledInModalContext(true);
    }

    @Override
    public void update(@Nonnull AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        boolean canShow = isAvailable(e);
        presentation.setEnabled(canShow);
        if (ActionPlaces.isPopupPlace(e.getPlace())) {
            presentation.setVisible(canShow);
        }
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(@Nonnull AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        DiffRequest request = getDiffRequest(e);
        if (request == null) {
            return;
        }

        DiffManager.getInstance().showDiff(project, request);
    }

    protected abstract boolean isAvailable(@Nonnull AnActionEvent e);

    protected static boolean hasContent(VirtualFile file) {
        return !(file instanceof VirtualFileWithoutContent);
    }

    @Nullable
    protected abstract DiffRequest getDiffRequest(@Nonnull AnActionEvent e);
}
