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
package consulo.externalSystem.impl.internal.service.action;

import consulo.application.AllIcons;
import consulo.application.dumb.DumbAware;
import consulo.externalSystem.ExternalSystemManager;
import consulo.externalSystem.internal.ExternalSystemInternalAWTHelper;
import consulo.externalSystem.localize.ExternalSystemLocalize;
import consulo.externalSystem.model.ExternalSystemDataKeys;
import consulo.externalSystem.model.ProjectSystemId;
import consulo.externalSystem.util.ExternalSystemApiUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;

/**
 * @author Denis Zhdanov
 * @since 6/14/13 1:28 PM
 */
public class AttachExternalProjectAction extends AnAction implements DumbAware {

    private final ExternalSystemInternalAWTHelper myAwtHelper;

    @Inject
    public AttachExternalProjectAction(ExternalSystemInternalAWTHelper awtHelper) {
        super(
            ExternalSystemLocalize.actionAttachExternalProjectText("external"),
            ExternalSystemLocalize.actionAttachExternalProjectDescription("external")
        );
        myAwtHelper = awtHelper;
    }

    @RequiredUIAccess
    @Override
    public void update(@Nonnull AnActionEvent e) {
        ProjectSystemId externalSystemId = e.getDataContext().getData(ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID);
        if (externalSystemId != null) {
            LocalizeValue readableName = externalSystemId.getDisplayName();
            e.getPresentation().setTextValue(ExternalSystemLocalize.actionAttachExternalProjectText(readableName));
            e.getPresentation().setDescriptionValue(ExternalSystemLocalize.actionAttachExternalProjectDescription(readableName));
        }

        e.getPresentation().setIcon(AllIcons.General.Add);
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(@Nonnull AnActionEvent e) {
        ProjectSystemId externalSystemId = e.getDataContext().getData(ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID);
        if (externalSystemId == null) {
            return;
        }

        ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
        if (manager == null) {
            return;
        }

        Project project = e.getDataContext().getData(Project.KEY);
        if (project == null) {
            return;
        }

        myAwtHelper.executeImportAction(project, manager.getExternalProjectDescriptor());
    }
}
