/*
 * Copyright 2013-2025 consulo.io
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
import consulo.annotation.component.ActionRef;
import consulo.application.dumb.DumbAware;
import consulo.ide.impl.presentationAssistant.action.TogglePresentationAssistantAction;
import consulo.platform.base.localize.ActionLocalize;
import consulo.ui.ex.action.DefaultActionGroup;

/**
 * Mac OS Lion: In native apps toggle full screen mode is the last action in "View" menu.
 *
 * @author UNV
 * @since 2025-07-29
 */
@ActionImpl(
    id = "ToggleFullScreenGroup",
    children = {
        @ActionRef(type = TogglePresentationModeAction.class),
        @ActionRef(type = TogglePresentationAssistantAction.class),
        @ActionRef(type = ToggleFullScreenAction.class)
    }
)
public class ToggleFullScreenGroup extends DefaultActionGroup implements DumbAware {
    public ToggleFullScreenGroup() {
        super(ActionLocalize.groupTogglefullscreengroupText(), false);
    }
}
