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
package consulo.ide.impl.idea.openapi.editor.actions;

import consulo.annotation.component.ActionImpl;
import consulo.codeEditor.Editor;
import consulo.platform.base.localize.ActionLocalize;

/**
 * @author max
 * @since 2002-05-14
 */
@ActionImpl(id = "EditorToggleShowWhitespaces")
public class ToggleShowWhitespacesAction extends EditorToggleDecorationAction {
    public ToggleShowWhitespacesAction() {
        super(ActionLocalize.actionEditortoggleshowwhitespacesText(), ActionLocalize.actionEditortoggleshowwhitespacesText());
    }

    @Override
    protected void setOption(Editor editor, boolean state) {
        editor.getSettings().setWhitespacesShown(state);
    }

    @Override
    protected boolean getOption(Editor editor) {
        return editor.getSettings().isWhitespacesShown();
    }
}
