/*
 * Copyright 2013-2024 consulo.io
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
package consulo.ide.impl.idea.ide.actionMacro;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.internal.PreloadingActivity;
import consulo.application.progress.ProgressIndicator;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;

/**
 * @author VISTALL
 * @since 2024-09-05
 */
@ExtensionImpl
public class ActionMacroPreloader extends PreloadingActivity {
    private final Application myApplication;

    @Inject
    public ActionMacroPreloader(Application application) {
        myApplication = application;
    }

    @Override
    public void preload(@Nonnull ProgressIndicator indicator) {
        // init action macro manager
        myApplication.getInstance(ActionMacroManagerState.class);
    }
}
