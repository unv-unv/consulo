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
package consulo.ide.impl.idea.ide.actions;

import consulo.annotation.component.ActionImpl;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.ide.localize.IdeLocalize;
import consulo.platform.base.localize.ActionLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.action.IdeActions;
import jakarta.annotation.Nonnull;

/**
 * @author max
 * @author Konstantin Bulenkov
 */
@ActionImpl(id = IdeActions.ACTION_RECENT_FILES)
public class ShowRecentFilesAction extends DumbAwareAction {
    public ShowRecentFilesAction() {
        super(ActionLocalize.actionRecentfilesText(), ActionLocalize.actionRecentfilesDescription());
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(@Nonnull AnActionEvent e) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.recent.files");
        Switcher.createAndShowSwitcher(e, IdeLocalize.titlePopupRecentFiles().get(), IdeActions.ACTION_RECENT_FILES, false, true);
    }

    @Override
    public void update(@Nonnull AnActionEvent e) {
        e.getPresentation().setEnabled(e.hasData(Project.KEY));
    }
}
