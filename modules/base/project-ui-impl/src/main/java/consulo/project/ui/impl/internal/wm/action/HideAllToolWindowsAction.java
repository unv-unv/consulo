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
package consulo.project.ui.impl.internal.wm.action;

import consulo.annotation.component.ActionImpl;
import consulo.application.dumb.DumbAware;
import consulo.platform.base.localize.ActionLocalize;
import consulo.project.Project;
import consulo.project.ui.internal.ToolWindowLayout;
import consulo.project.ui.internal.ToolWindowManagerEx;
import consulo.project.ui.localize.ProjectUILocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.toolWindow.ToolWindow;
import jakarta.annotation.Nonnull;

@ActionImpl(id = "HideAllWindows")
public class HideAllToolWindowsAction extends AnAction implements DumbAware {
    public HideAllToolWindowsAction() {
        super(ActionLocalize.actionHideallwindowsText(), ActionLocalize.actionHideallwindowsDescription());
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(@Nonnull AnActionEvent e) {
        Project project = e.getRequiredData(Project.KEY);
        performAction(project);
    }

    @RequiredUIAccess
    public static void performAction(Project project) {
        ToolWindowManagerEx toolWindowManager = ToolWindowManagerEx.getInstanceEx(project);

        ToolWindowLayout layout = new ToolWindowLayout();
        layout.copyFrom(toolWindowManager.getLayout());

        // to clear windows stack
        toolWindowManager.clearSideStack();
        //toolWindowManager.activateEditorComponent();


        String[] ids = toolWindowManager.getToolWindowIds();
        boolean hasVisible = false;
        for (String id : ids) {
            ToolWindow toolWindow = toolWindowManager.getToolWindow(id);
            if (toolWindow.isVisible()) {
                toolWindow.hide(null);
                hasVisible = true;
            }
        }

        if (hasVisible) {
            toolWindowManager.setLayoutToRestoreLater(layout);
            toolWindowManager.activateEditorComponent();
        }
        else {
            ToolWindowLayout restoredLayout = toolWindowManager.getLayoutToRestoreLater();
            if (restoredLayout != null) {
                toolWindowManager.setLayoutToRestoreLater(null);
                toolWindowManager.setLayout(restoredLayout);
            }
        }
    }

    @Override
    public void update(@Nonnull AnActionEvent event) {
        Presentation presentation = event.getPresentation();
        Project project = event.getData(Project.KEY);
        if (project == null) {
            presentation.setEnabled(false);
            return;
        }

        ToolWindowManagerEx toolWindowManager = ToolWindowManagerEx.getInstanceEx(project);
        String[] ids = toolWindowManager.getToolWindowIds();
        for (String id : ids) {
            if (toolWindowManager.getToolWindow(id).isVisible()) {
                presentation.setEnabled(true);
                presentation.setTextValue(ProjectUILocalize.actionHideAllWindows());
                return;
            }
        }

        ToolWindowLayout layout = toolWindowManager.getLayoutToRestoreLater();
        if (layout != null) {
            presentation.setEnabled(true);
            presentation.setTextValue(ProjectUILocalize.actionRestoreWindows());
            return;
        }

        presentation.setEnabled(false);
    }
}
