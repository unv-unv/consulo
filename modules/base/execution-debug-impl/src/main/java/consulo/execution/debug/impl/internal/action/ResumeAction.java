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
package consulo.execution.debug.impl.internal.action;

import consulo.application.dumb.DumbAware;
import consulo.dataContext.DataContext;
import consulo.execution.debug.XDebugSession;
import consulo.execution.debug.XDebuggerManager;
import consulo.execution.debug.impl.internal.action.handler.DebuggerActionHandler;
import consulo.execution.debug.impl.internal.action.handler.XDebuggerActionHandler;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author nik
 */
public class ResumeAction extends XDebuggerActionBase implements DumbAware {
    private final DebuggerActionHandler myHandler = new XDebuggerActionHandler() {
        @Override
        protected boolean isEnabled(@Nonnull final XDebugSession session, final DataContext dataContext) {
            return session.isPaused();
        }

        @Override
        protected void perform(@Nonnull final XDebugSession session, final DataContext dataContext) {
            session.resume();
        }
    };

    @Override
    protected boolean isEnabled(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return false;
        }

        XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
        if (session != null && !session.isStopped()) {
            return session.isPaused();
        }
        return !ActionPlaces.DEBUGGER_TOOLBAR.equals(e.getPlace());
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(@Nonnull AnActionEvent e) {
        if (!performWithHandler(e)) {
            Project project = e.getData(Project.KEY);
            if (project != null && !DumbService.isDumb(project)) {
                new ChooseDebugConfigurationPopupAction().actionPerformed(e);
            }
        }
    }

    @Override
    @Nonnull
    protected DebuggerActionHandler getHandler() {
        return myHandler;
    }

    @Nullable
    @Override
    protected Image getTemplateIcon() {
        return PlatformIconGroup.actionsResume();
    }
}
