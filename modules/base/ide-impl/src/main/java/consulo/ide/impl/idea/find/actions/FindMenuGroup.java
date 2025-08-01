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
package consulo.ide.impl.idea.find.actions;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionRef;
import consulo.application.dumb.DumbAware;
import consulo.ide.impl.idea.codeInsight.highlighting.actions.HighlightUsagesAction;
import consulo.ide.impl.idea.find.impl.ShowRecentFindUsagesGroup;
import consulo.ide.impl.idea.ide.actions.SearchAgainAction;
import consulo.ide.impl.idea.ide.actions.SearchBackAction;
import consulo.ide.impl.idea.openapi.editor.actions.*;
import consulo.platform.base.localize.ActionLocalize;
import consulo.ui.ex.action.AnSeparator;
import consulo.ui.ex.action.DefaultActionGroup;

/**
 * @author UNV
 * @since 2025-07-27
 */
@ActionImpl(
    id = "FindMenuGroup",
    children = {
        @ActionRef(type = IncrementalFindAction.class),
        @ActionRef(type = ReplaceAction.class),
        @ActionRef(type = SearchAgainAction.class),
        @ActionRef(type = SearchBackAction.class),
        @ActionRef(type = FindWordAtCaretAction.class),
        @ActionRef(type = SelectAllOccurrencesAction.class),
        @ActionRef(type = SelectNextOccurrenceAction.class),
        @ActionRef(type = UnselectPreviousOccurrenceAction.class),
        @ActionRef(type = AnSeparator.class),
        @ActionRef(type = FindInPathAction.class),
        @ActionRef(type = ReplaceInPathAction.class),
        @ActionRef(type = AnSeparator.class),
        @ActionRef(type = FindUsagesAction.class),
        @ActionRef(type = ShowSettingsAndFindUsagesAction.class),
        @ActionRef(type = ShowUsagesAction.class),
        @ActionRef(type = FindUsagesInFileAction.class),
        @ActionRef(type = HighlightUsagesAction.class),
        @ActionRef(type = ShowRecentFindUsagesGroup.class),
    }
)
public class FindMenuGroup extends DefaultActionGroup implements DumbAware {
    public FindMenuGroup() {
        super(ActionLocalize.groupFindmenugroupText(), true);
    }
}
