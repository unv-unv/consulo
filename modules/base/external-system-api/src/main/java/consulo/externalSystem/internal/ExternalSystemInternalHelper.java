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
package consulo.externalSystem.internal;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.externalSystem.model.ProjectSystemId;
import consulo.externalSystem.model.execution.ExternalTaskPojo;
import consulo.externalSystem.model.task.ExternalSystemTask;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * @author VISTALL
 * @since 04/11/2022
 */
@ServiceAPI(ComponentScope.APPLICATION)
public interface ExternalSystemInternalHelper {
    ExternalSystemTask createExecuteSystemTask(@Nonnull ProjectSystemId externalSystemId,
                                               @Nonnull Project project,
                                               @Nonnull List<ExternalTaskPojo> tasksToExecute,
                                               @Nullable String vmOptions,
                                               @Nullable String scriptParameters,
                                               @Nullable String debuggerSetup);

    @RequiredUIAccess
    void ensureToolWindowInitialized(@Nonnull Project project, @Nonnull ProjectSystemId externalSystemId);
}
