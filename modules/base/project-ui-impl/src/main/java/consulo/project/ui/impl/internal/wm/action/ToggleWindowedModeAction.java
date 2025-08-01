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
import consulo.project.ui.internal.ToolWindowManagerEx;
import consulo.platform.Platform;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.action.ToggleAction;
import consulo.ui.ex.internal.ToolWindowEx;
import consulo.ui.ex.toolWindow.ToolWindowType;
import jakarta.annotation.Nonnull;

@ActionImpl(id = "ToggleWindowedMode")
public class ToggleWindowedModeAction extends ToggleAction implements DumbAware {
    public ToggleWindowedModeAction() {
        super(ActionLocalize.actionTogglewindowedmodeText(), ActionLocalize.actionTogglewindowedmodeDescription());
    }

    @Override
    @RequiredUIAccess
    public boolean isSelected(AnActionEvent event) {
        Project project = event.getData(Project.KEY);
        if (project == null) {
            return false;
        }
        ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
        String id = windowManager.getActiveToolWindowId();
        return id != null && ToolWindowType.WINDOWED == windowManager.getToolWindow(id).getType();
    }

    @Override
    @RequiredUIAccess
    public void setSelected(AnActionEvent event, boolean flag) {
        Project project = event.getData(Project.KEY);
        if (project == null) {
            return;
        }
        String id = ToolWindowManager.getInstance(project).getActiveToolWindowId();
        if (id == null) {
            return;
        }
        ToolWindowManagerEx mgr = ToolWindowManagerEx.getInstanceEx(project);
        ToolWindowEx toolWindow = (ToolWindowEx) mgr.getToolWindow(id);
        ToolWindowType type = toolWindow.getType();
        if (ToolWindowType.WINDOWED == type) {
            toolWindow.setType(toolWindow.getInternalType(), null);
        }
        else {
            toolWindow.setType(ToolWindowType.WINDOWED, null);
        }
    }

    @Override
    public void update(@Nonnull AnActionEvent event) {
        super.update(event);
        Presentation presentation = event.getPresentation();
        if (Platform.current().os().isMac()) {
            presentation.setEnabledAndVisible(false);
            return;
        }
        Project project = event.getData(Project.KEY);
        if (project == null) {
            presentation.setEnabled(false);
            return;
        }
        ToolWindowManager mgr = ToolWindowManager.getInstance(project);
        String id = mgr.getActiveToolWindowId();
        presentation.setEnabled(id != null && mgr.getToolWindow(id).isAvailable());
    }
}
