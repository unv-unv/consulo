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
package consulo.ide.impl.idea.ide.actions;

import consulo.annotation.component.ActionImpl;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.platform.base.localize.ActionLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.PasteProvider;
import consulo.dataContext.DataContext;
import consulo.application.dumb.DumbAware;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import jakarta.annotation.Nonnull;

@ActionImpl(id = "$Paste")
public class PasteAction extends AnAction implements DumbAware {
    public PasteAction() {
        super(ActionLocalize.action$pasteText(), ActionLocalize.action$pasteDescription(), PlatformIconGroup.actionsMenu_paste());
    }

    @Override
    public void update(@Nonnull AnActionEvent event) {
        Presentation presentation = event.getPresentation();
        DataContext dataContext = event.getDataContext();

        PasteProvider provider = event.getData(PasteProvider.KEY);
        presentation.setEnabled(provider != null && provider.isPastePossible(dataContext));
        if (event.getPlace().equals(ActionPlaces.EDITOR_POPUP) && provider != null) {
            presentation.setVisible(presentation.isEnabled());
        }
        else {
            presentation.setVisible(true);
        }
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(@Nonnull AnActionEvent e) {
        DataContext dataContext = e.getDataContext();
        PasteProvider provider = e.getRequiredData(PasteProvider.KEY);
        if (provider.isPasteEnabled(dataContext)) {
            provider.performPaste(dataContext);
        }
    }
}