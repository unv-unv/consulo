/*
 * Copyright 2013-2022 consulo.io
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
import consulo.application.Application;
import consulo.ide.impl.idea.ide.actions.GotoActionBase;
import consulo.ide.navigation.GotoClassOrTypeContributor;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;
import jakarta.inject.Inject;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class ClassSearchEverywhereContributorFactory implements SearchEverywhereContributorFactory<Object> {
    private final Application myApplication;

    @Inject
    public ClassSearchEverywhereContributorFactory(Application application) {
        myApplication = application;
    }

    @Nullable
    @Override
    public SearchEverywhereContributor<Object> createContributor(@Nonnull AnActionEvent initEvent) {
        if (!myApplication.getExtensionPoint(GotoClassOrTypeContributor.class).hasAnyExtensions()) {
            return null;
        }
        return new ClassSearchEverywhereContributor(initEvent.getData(Project.KEY), GotoActionBase.getPsiContext(initEvent));
    }
}
