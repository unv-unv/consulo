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
import consulo.platform.base.localize.ActionLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.ui.ex.toolWindow.ToolWindowContentUiType;

@ActionImpl(id = "ToggleContentUiTypeMode")
public class ToggleContentUiTypeAction extends BaseToolWindowToggleAction {
    public ToggleContentUiTypeAction() {
        super(ActionLocalize.actionTogglecontentuitypemodeText(), ActionLocalize.actionTogglecontentuitypemodeDescription());
    }

    @Override
    @RequiredUIAccess
    protected boolean isSelected(ToolWindow window) {
        return window.getContentUiType() == ToolWindowContentUiType.TABBED;
    }

    @Override
    @RequiredUIAccess
    protected void setSelected(ToolWindow window, boolean state) {
        window.setContentUiType(state ? ToolWindowContentUiType.TABBED : ToolWindowContentUiType.COMBO, null);
    }

    @Override
    protected void update(ToolWindow window, Presentation presentation) {
        presentation.setEnabled(window.getContentManager().getContentCount() > 1);
    }
}
