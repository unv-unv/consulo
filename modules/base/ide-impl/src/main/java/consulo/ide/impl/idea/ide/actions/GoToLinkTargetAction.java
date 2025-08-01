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
package consulo.ide.impl.idea.ide.actions;

import consulo.annotation.component.ActionImpl;
import consulo.platform.base.localize.ActionLocalize;
import consulo.project.ui.view.ProjectView;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.project.Project;
import consulo.virtualFileSystem.VFileProperty;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiManager;
import jakarta.annotation.Nonnull;

@ActionImpl(id = "GoToLinkTarget")
public class GoToLinkTargetAction extends DumbAwareAction {
    public GoToLinkTargetAction() {
        super(ActionLocalize.actionGotolinktargetText(), ActionLocalize.actionGotolinktargetDescription());
    }

    @Override
    public void update(@Nonnull AnActionEvent e) {
        VirtualFile file = e.getData(VirtualFile.KEY);
        e.getPresentation().setEnabledAndVisible(e.hasData(Project.KEY) && file != null && file.is(VFileProperty.SYMLINK));
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(@Nonnull AnActionEvent e) {
        Project project = e.getRequiredData(Project.KEY);
        VirtualFile file = e.getRequiredData(VirtualFile.KEY);
        if (file.is(VFileProperty.SYMLINK)) {
            VirtualFile target = file.getCanonicalFile();
            if (target != null) {
                PsiManager psiManager = PsiManager.getInstance(project);
                PsiFileSystemItem psiFile = target.isDirectory() ? psiManager.findDirectory(target) : psiManager.findFile(target);
                if (psiFile != null) {
                    ProjectView.getInstance(project).select(psiFile, target, false);
                }
            }
        }
    }
}
