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

package consulo.language.copyright.impl.internal.action;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.codeEditor.Editor;
import consulo.configurable.internal.ShowConfigurableService;
import consulo.dataContext.DataContext;
import consulo.language.copyright.UpdateCopyrightsProvider;
import consulo.language.copyright.config.CopyrightManager;
import consulo.language.copyright.impl.internal.ui.CopyrightProjectConfigurable;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.module.Module;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.IdeActions;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ActionImpl(id = "GenerateCopyright", parents = @ActionParentRef(@ActionRef(id = IdeActions.GROUP_GENERATE)))
public class GenerateCopyrightAction extends AnAction {
    public GenerateCopyrightAction() {
        super("Copyright", "Generate/Update the copyright notice.", null);
    }

    @Override
    public void update(@Nonnull AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            e.getPresentation().setEnabled(false);
            return;
        }

        PsiFile file = getFile(e.getDataContext(), project);
        if (file == null || !UpdateCopyrightsProvider.hasExtension(file)) {
            e.getPresentation().setEnabled(false);
        }
    }

    @Nullable
    private static PsiFile getFile(DataContext context, Project project) {
        PsiFile file = context.getData(PsiFile.KEY);
        if (file == null) {
            Editor editor = context.getData(Editor.KEY);
            if (editor != null) {
                file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
            }
        }
        return file;
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(@Nonnull AnActionEvent e) {
        Project project = e.getRequiredData(Project.KEY);
        Module module = e.getData(Module.KEY);
        PsiDocumentManager.getInstance(project).commitAllDocuments();

        PsiFile file = getFile(e.getDataContext(), project);
        assert file != null;
        if (CopyrightManager.getInstance(project).getCopyrightOptions(file) == null) {
            if (Messages.showOkCancelDialog(
                project,
                "No copyright configured for current file. Would you like to edit copyright settings?",
                "No Copyright Available",
                UIUtil.getQuestionIcon()
            ) == DialogWrapper.OK_EXIT_CODE) {
                project.getApplication().getInstance(ShowConfigurableService.class).showAndSelect(project, CopyrightProjectConfigurable.class);
            }
            else {
                return;
            }
        }
        new UpdateCopyrightProcessor(project, module, file).run();
    }
}