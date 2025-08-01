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
import consulo.project.ui.wm.ToolWindowManager;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.action.ToggleAction;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.ui.ex.toolWindow.ToolWindowType;
import jakarta.annotation.Nonnull;

@ActionImpl(id = "ToggleDockMode")
public class ToggleDockModeAction extends ToggleAction implements DumbAware {
    public ToggleDockModeAction() {
        super(ActionLocalize.actionToggledockmodeText(), ActionLocalize.actionToggledockmodeDescription());
    }

    @Override
    @RequiredUIAccess
    public boolean isSelected(@Nonnull AnActionEvent event) {
        Project project = event.getData(Project.KEY);
        if (project == null) {
            return false;
        }
        ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
        String id = windowManager.getActiveToolWindowId();
        return id != null && ToolWindowType.DOCKED == windowManager.getToolWindow(id).getType();
    }

    @Override
    @RequiredUIAccess
    public void setSelected(@Nonnull AnActionEvent event, boolean flag) {
        Project project = event.getData(Project.KEY);
        if (project == null) {
            return;
        }
        ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
        String id = windowManager.getActiveToolWindowId();
        if (id == null) {
            return;
        }
        ToolWindow toolWindow = windowManager.getToolWindow(id);
        ToolWindowType type = toolWindow.getType();
        if (ToolWindowType.DOCKED == type) {
            toolWindow.setType(ToolWindowType.SLIDING, null);
        }
        else if (ToolWindowType.SLIDING == type) {
            toolWindow.setType(ToolWindowType.DOCKED, null);
        }
    }

    @Override
    @RequiredUIAccess
    public void update(@Nonnull AnActionEvent event) {
        super.update(event);
        Presentation presentation = event.getPresentation();
        Project project = event.getData(Project.KEY);
        if (project == null) {
            presentation.setEnabled(false);
            return;
        }
        ToolWindowManager mgr = ToolWindowManager.getInstance(project);
        String id = mgr.getActiveToolWindowId();
        if (id == null) {
            presentation.setEnabled(false);
            return;
        }
        ToolWindow toolWindow = mgr.getToolWindow(id);
        presentation.setEnabled(toolWindow.isAvailable() && ToolWindowType.FLOATING != toolWindow.getType());
    }
}
