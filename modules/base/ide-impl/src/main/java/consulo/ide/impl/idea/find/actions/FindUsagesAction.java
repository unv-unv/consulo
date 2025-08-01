/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package consulo.ide.impl.idea.find.actions;

import consulo.annotation.component.ActionImpl;
import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.find.FindManager;
import consulo.find.localize.FindLocalize;
import consulo.ide.impl.idea.codeInsight.navigation.actions.GotoDeclarationAction;
import consulo.language.editor.hint.HintManager;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.localize.LocalizeValue;
import consulo.platform.base.localize.ActionLocalize;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.usage.PsiElementUsageTarget;
import consulo.usage.UsageTarget;
import consulo.usage.UsageView;
import jakarta.annotation.Nonnull;import jakarta.inject.Inject;

@ActionImpl(id = "FindUsages")
public class FindUsagesAction extends AnAction {
    @Inject
    public FindUsagesAction() {
        this(ActionLocalize.actionFindusagesText(), ActionLocalize.actionFindusagesDescription());
    }

    protected FindUsagesAction(@Nonnull LocalizeValue text, @Nonnull LocalizeValue description) {
        super(text, description);
        setInjectedContext(true);
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(@Nonnull AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return;
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments();

        UsageTarget[] usageTargets = e.getData(UsageView.USAGE_TARGETS_KEY);
        if (usageTargets == null) {
            Editor editor = e.getData(Editor.KEY);
            chooseAmbiguousTargetAndPerform(
                project,
                editor,
                element -> {
                    startFindUsages(element);
                    return false;
                }
            );
        }
        else if (usageTargets[0] instanceof PsiElementUsageTarget elementUsageTarget) {
            PsiElement element = elementUsageTarget.getElement();
            if (element != null) {
                startFindUsages(element);
            }
        }
    }

    protected void startFindUsages(@Nonnull PsiElement element) {
        FindManager.getInstance(element.getProject()).findUsages(element);
    }

    @Override
    public void update(@Nonnull AnActionEvent event) {
        FindUsagesInFileAction.updateFindUsagesAction(event);
    }

    @RequiredUIAccess
    static void chooseAmbiguousTargetAndPerform(
        @Nonnull Project project,
        Editor editor,
        @Nonnull PsiElementProcessor<PsiElement> processor
    ) {
        if (editor == null) {
            Messages.showMessageDialog(
                project,
                FindLocalize.findNoUsagesAtCursorError().get(),
                CommonLocalize.titleError().get(),
                UIUtil.getErrorIcon()
            );
        }
        else {
            int offset = editor.getCaretModel().getOffset();
            boolean chosen = GotoDeclarationAction.chooseAmbiguousTarget(
                editor,
                offset,
                processor,
                FindLocalize.findUsagesAmbiguousTitle().get(),
                null
            );
            if (!chosen) {
                Application.get().invokeLater(
                    () -> {
                        if (editor.isDisposed() || !editor.getComponent().isShowing()) {
                            return;
                        }
                        HintManager.getInstance().showErrorHint(editor, FindLocalize.findNoUsagesAtCursorError());
                    },
                    project.getDisposed()
                );
            }
        }
    }
}
