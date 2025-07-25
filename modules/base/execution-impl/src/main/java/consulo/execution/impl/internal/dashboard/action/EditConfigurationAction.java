// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.execution.impl.internal.dashboard.action;

import consulo.annotation.component.ActionImpl;
import consulo.execution.RunConfigurationEditor;
import consulo.execution.RunManager;
import consulo.execution.dashboard.RunDashboardRunConfigurationNode;
import consulo.execution.localize.ExecutionLocalize;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.platform.base.localize.ActionLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.*;
import jakarta.annotation.Nonnull;

@ActionImpl(id = "RunDashboard.EditConfiguration")
public final class EditConfigurationAction extends AnAction {
    public EditConfigurationAction() {
        super(ActionLocalize.actionRundashboardEditconfigurationText(), LocalizeValue.empty(), PlatformIconGroup.actionsEditsource());
    }

    @Nonnull
    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@Nonnull AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        RunDashboardRunConfigurationNode node = project == null ? null : RunDashboardActionUtils.getTarget(e);
        boolean enabled = node != null && RunManager.getInstance(project).hasSettings(node.getConfigurationSettings());
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(enabled);
        boolean popupPlace = ActionPlaces.isPopupPlace(e.getPlace());
        presentation.setVisible(enabled || !popupPlace);
        if (popupPlace) {
            presentation.setText(getTemplatePresentation().getText() + "...");
        }
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(@Nonnull AnActionEvent e) {
        Project project = e.getRequiredData(Project.KEY);
        RunDashboardRunConfigurationNode node = RunDashboardActionUtils.getTarget(e);
        if (node == null) {
            return;
        }

        RunConfigurationEditor.getInstance(project)
            .editConfiguration(project, node.getConfigurationSettings(), ExecutionLocalize.runDashboardEditConfigurationDialogTitle());
    }
}
