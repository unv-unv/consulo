/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package consulo.externalSystem.service.project;

import consulo.externalSystem.model.DataNode;
import consulo.externalSystem.model.setting.ExternalSystemExecutionSettings;
import consulo.externalSystem.model.task.ExternalSystemTaskId;
import consulo.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
* @author Denis Zhdanov
* @since 5/2/13 10:37 PM
*/
public interface ExternalProjectRefreshCallback {

  /**
   * Is expected to be called when
   * {@link ExternalSystemProjectResolver#resolveProjectInfo(ExternalSystemTaskId, String, boolean, ExternalSystemExecutionSettings, ExternalSystemTaskNotificationListener)}
   * returns without exception.
   *
   * @param externalProject  target external project (if available)
   */
  void onSuccess(@Nullable DataNode<ProjectData> externalProject);

  void onFailure(@Nonnull String errorMessage, @Nullable String errorDetails);
}
