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
package consulo.externalSystem.impl.internal.service;

import consulo.externalSystem.model.ProjectSystemId;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

/**
 * @author Denis Zhdanov
 * @since 4/1/13 3:42 PM
 */
public interface DisposableExternalSystemService {

  /**
   * Is expected to be called when external system with the given id is unlinked from the given ide project.
   * <p/>
   * General idea is to allow to release external system-specific resources at this point.
   * 
   * @param externalSystemId  target external system id
   * @param ideProject        target ide project
   */
  void onExternalSystemUnlinked(@Nonnull ProjectSystemId externalSystemId, @Nonnull Project ideProject);
}
