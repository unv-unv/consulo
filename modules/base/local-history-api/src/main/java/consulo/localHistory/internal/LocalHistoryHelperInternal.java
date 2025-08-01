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
package consulo.localHistory.internal;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.application.Application;
import consulo.project.Project;

import java.util.List;

/**
 * @author VISTALL
 * @since 2025-08-01
 */
@ServiceAPI(ComponentScope.APPLICATION)
public interface LocalHistoryHelperInternal {
    static LocalHistoryHelperInternal getInstance() {
        return Application.get().getInstance(LocalHistoryHelperInternal.class);
    }

    void showDiff(Project project, List changes, int index);
}
