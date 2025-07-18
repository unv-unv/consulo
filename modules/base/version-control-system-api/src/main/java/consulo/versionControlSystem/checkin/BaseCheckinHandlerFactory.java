/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package consulo.versionControlSystem.checkin;

import consulo.project.Project;
import consulo.versionControlSystem.change.CommitContext;

import jakarta.annotation.Nullable;

/**
 * just interface for checkin handlers creation
 *
 * @author irengrig
 * @since 2011-01-28
 */
public interface BaseCheckinHandlerFactory {
  /**
   * Creates a handler for a single Checkin Project or Checkin File operation.
   *
   *
   * @param panel the class which can be used to retrieve information about the files to be committed,
   *              and to get or set the commit message.
   * @param commitContext
   * @return the handler instance - null if not supported
   */
  @Nullable
  CheckinHandler createHandler(final CheckinProjectPanel panel, CommitContext commitContext);

  @Nullable
  BeforeCheckinDialogHandler createSystemReadyHandler(Project project);
}
