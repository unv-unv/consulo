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
package consulo.versionControlSystem.action;

import consulo.application.dumb.DumbAware;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.action.Presentation;
import consulo.versionControlSystem.AbstractVcs;
import consulo.versionControlSystem.ProjectLevelVcsManager;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public abstract class StandardVcsGroup extends DefaultActionGroup implements DumbAware {
  public abstract AbstractVcs getVcs(Project project);

  @Override
  public void update(@Nonnull AnActionEvent e) {
    Presentation presentation = e.getPresentation();

    Project project = e.getData(Project.KEY);
    if (project != null) {
      final String vcsName = getVcsName(project);
      presentation.setVisible(vcsName != null && ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(vcsName));
    }
    else {
      presentation.setVisible(false);
    }
    presentation.setEnabled(presentation.isVisible());
  }

  @Nullable
  public String getVcsName(Project project) {
    final AbstractVcs vcs = getVcs(project);
    // if the parent group was customized and then the plugin was disabled, we could have an action group with no VCS
    return vcs != null ? vcs.getDisplayName() : null;
  }
}
