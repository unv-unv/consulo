/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package consulo.ide.impl.idea.ide.actions;

import consulo.annotation.component.ActionImpl;
import consulo.application.ui.UISettings;
import consulo.platform.base.localize.ActionLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.ToggleAction;
import consulo.application.dumb.DumbAware;
import jakarta.annotation.Nonnull;

@ActionImpl(id ="ViewToolButtons")
public class ViewToolWindowButtonsAction extends ToggleAction implements DumbAware {
    public ViewToolWindowButtonsAction() {
        super(ActionLocalize.actionViewtoolbuttonsText(), ActionLocalize.actionViewtoolbuttonsDescription());
    }

    @Override
    public boolean isSelected(@Nonnull AnActionEvent event) {
        return !UISettings.getInstance().HIDE_TOOL_STRIPES;
    }

    @Override
    @RequiredUIAccess
    public void setSelected(@Nonnull AnActionEvent event, boolean state) {
        UISettings uiSettings = UISettings.getInstance();
        uiSettings.HIDE_TOOL_STRIPES = !state;
        uiSettings.fireUISettingsChanged();
    }
}
