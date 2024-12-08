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

package consulo.execution.impl.internal.action;

import consulo.execution.RunConfigurationEditor;
import consulo.execution.localize.ExecutionLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.action.Presentation;

public class EditRunConfigurationsAction extends DumbAwareAction {
    @RequiredUIAccess
    @Override
    public void actionPerformed(final AnActionEvent e) {
        Project project = e.getData(Project.KEY);

        if (project == null) {
            //setup template project configurations
            project = ProjectManager.getInstance().getDefaultProject();
        }

        RunConfigurationEditor editor = RunConfigurationEditor.getInstance(project);

        editor.editAll();
    }

    @RequiredUIAccess
    @Override
    public void update(final AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(true);

        if (ActionPlaces.RUN_CONFIGURATIONS_COMBOBOX.equals(e.getPlace())) {
            LocalizeValue textValue = ExecutionLocalize.editConfigurationAction();

            presentation.setTextValue(textValue);
            presentation.setDescriptionValue(textValue.map(Presentation.NO_MNEMONIC));
        }
    }
}
