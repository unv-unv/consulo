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
package consulo.ide.impl.idea.ide.actions.searcheverywhere;

import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.idea.ide.actions.GotoActionBase;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 2025-06-01
 */
@ExtensionImpl
public class RecentFilesSEContributorFactory implements SearchEverywhereContributorFactory<Object> {
    @Nullable
    @Override
    public SearchEverywhereContributor<Object> createContributor(@Nonnull AnActionEvent e) {
        return new RecentFilesSEContributor(e.getData(Project.KEY), GotoActionBase.getPsiContext(e));
    }
}
