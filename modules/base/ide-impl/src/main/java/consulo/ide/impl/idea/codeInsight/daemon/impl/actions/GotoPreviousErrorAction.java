
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

package consulo.ide.impl.idea.codeInsight.daemon.impl.actions;

import consulo.annotation.component.ActionImpl;
import consulo.language.editor.action.CodeInsightActionHandler;
import consulo.language.editor.impl.action.BaseCodeInsightAction;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.ide.impl.idea.codeInsight.daemon.impl.GotoNextErrorHandler;
import consulo.codeEditor.Editor;
import consulo.application.dumb.DumbAware;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.platform.base.localize.ActionLocalize;
import consulo.project.Project;
import consulo.language.psi.PsiFile;
import jakarta.annotation.Nonnull;

@ActionImpl(id = "GotoPreviousError")
public class GotoPreviousErrorAction extends BaseCodeInsightAction implements DumbAware {
    public GotoPreviousErrorAction() {
        super(
            ActionLocalize.actionGotopreviouserrorText(),
            ActionLocalize.actionGotopreviouserrorDescription(),
            PlatformIconGroup.actionsPreviousoccurence(),
            false
        );
    }

    @Nonnull
    @Override
    protected CodeInsightActionHandler getHandler() {
        return new GotoNextErrorHandler(false);
    }

    @Override
    protected boolean isValidForFile(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiFile file) {
        return DaemonCodeAnalyzer.getInstance(project).isHighlightingAvailable(file);
    }
}