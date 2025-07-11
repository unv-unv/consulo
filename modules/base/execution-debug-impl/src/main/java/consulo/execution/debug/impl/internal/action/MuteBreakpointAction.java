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
package consulo.execution.debug.impl.internal.action;

import consulo.execution.debug.icon.ExecutionDebugIconGroup;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.ToggleAction;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author nik
 */
public class MuteBreakpointAction extends ToggleAction {
    private final XDebuggerMuteBreakpointsHandler myHandler = new XDebuggerMuteBreakpointsHandler();

    @Override
    public boolean isSelected(@Nonnull AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project != null) {
            if (myHandler.isEnabled(project, e)) {
                return myHandler.isSelected(project, e);
            }
        }
        return false;
    }

    @Override
    public void setSelected(@Nonnull AnActionEvent e, boolean state) {
        Project project = e.getData(Project.KEY);
        if (project != null) {
            if (myHandler.isEnabled(project, e)) {
                myHandler.setSelected(project, e, state);
            }
        }
    }

    @Override
    public void update(@Nonnull AnActionEvent e) {
        super.update(e);
        Project project = e.getData(Project.KEY);
        if (project != null) {
            if (myHandler.isEnabled(project, e)) {
                e.getPresentation().setEnabled(true);
                return;
            }
        }
        e.getPresentation().setEnabled(false);
    }

    @Nullable
    @Override
    protected Image getTemplateIcon() {
        return ExecutionDebugIconGroup.actionMutebreakpoints();
    }
}
