
/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import consulo.ide.impl.idea.find.replaceInProject.ReplaceInProjectManager;
import consulo.platform.base.localize.ActionLocalize;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationService;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;

@ActionImpl(id = "ReplaceInPath")
public class ReplaceInPathAction extends FindReplaceInPathActionBase {
    @Inject
    public ReplaceInPathAction(@Nonnull NotificationService notificationService) {
        super(ActionLocalize.actionReplaceinpathText(), ActionLocalize.actionReplaceinpathDescription(), notificationService);
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(@Nonnull AnActionEvent e) {
        Project project = e.getRequiredData(Project.KEY);

        ReplaceInProjectManager replaceManager = ReplaceInProjectManager.getInstance(project);
        if (!replaceManager.isEnabled()) {
            showNotAvailableMessage(e, project);
            return;
        }

        replaceManager.replaceInProject(e.getDataContext(), null);
    }

    @Override
    public void update(@Nonnull AnActionEvent event) {
        FindInPathAction.doUpdate(event);
    }
}
