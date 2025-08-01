/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package consulo.ide.impl.idea.find.impl;

import consulo.annotation.component.ActionImpl;
import consulo.find.FindManager;
import consulo.ide.impl.idea.find.findUsages.FindUsagesManager;
import consulo.localize.LocalizeValue;
import consulo.platform.base.localize.ActionLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.ConfigurableUsageTarget;
import consulo.ide.impl.idea.usages.impl.UsageViewImpl;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author cdr
 */
@ActionImpl(id = "ShowRecentFindUsagesGroup")
public class ShowRecentFindUsagesGroup extends ActionGroup {
    public ShowRecentFindUsagesGroup() {
        super(ActionLocalize.groupShowrecentfindusagesgroupText(), ActionLocalize.groupShowrecentfindusagesgroupDescription());
        setPopup(true);
    }

    @Override
    public void update(@Nonnull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.hasData(Project.KEY));
    }

    @Nonnull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
        if (e == null) {
            return EMPTY_ARRAY;
        }
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return EMPTY_ARRAY;
        }
        final FindUsagesManager findUsagesManager = ((FindManagerImpl) FindManager.getInstance(project)).getFindUsagesManager();
        List<ConfigurableUsageTarget> history = new ArrayList<>(findUsagesManager.getHistory().getAll());
        Collections.reverse(history);

        LocalizeValue description = ActionManager.getInstance()
            .getAction(UsageViewImpl.SHOW_RECENT_FIND_USAGES_ACTION_ID)
            .getTemplatePresentation()
            .getDescriptionValue();

        List<AnAction> children = new ArrayList<>(history.size());
        for (final ConfigurableUsageTarget usageTarget : history) {
            if (!usageTarget.isValid()) {
                continue;
            }
            LocalizeValue text = LocalizeValue.of(usageTarget.getLongDescriptiveName());
            AnAction action = new AnAction(text, description) {
                @Override
                @RequiredUIAccess
                public void actionPerformed(@Nonnull AnActionEvent e) {
                    findUsagesManager.rerunAndRecallFromHistory(usageTarget);
                }
            };
            children.add(action);
        }
        return children.toArray(new AnAction[children.size()]);
    }
}
