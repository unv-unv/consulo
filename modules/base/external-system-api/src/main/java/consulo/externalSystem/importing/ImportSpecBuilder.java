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
package consulo.externalSystem.importing;

import consulo.externalSystem.model.ProjectSystemId;
import consulo.externalSystem.model.task.ProgressExecutionMode;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

/**
 * @author Vladislav.Soroka
 * @since 2014-05-29
 */
public class ImportSpecBuilder {

  @Nonnull
  private final Project myProject;
  @Nonnull
  private final ProjectSystemId myExternalSystemId;
  @Nonnull
  private ProgressExecutionMode myProgressExecutionMode;
  private boolean myForceWhenUptodate;
  private boolean myWhenAutoImportEnabled;
  //private boolean isPreviewMode;
  //private boolean isReportRefreshError;

  public ImportSpecBuilder(@Nonnull Project project, @Nonnull ProjectSystemId id) {
    myProject = project;
    myExternalSystemId = id;
    myProgressExecutionMode = ProgressExecutionMode.IN_BACKGROUND_ASYNC;
  }

  public ImportSpecBuilder whenAutoImportEnabled() {
    myWhenAutoImportEnabled = true;
    return this;
  }

  public ImportSpecBuilder use(@Nonnull ProgressExecutionMode executionMode) {
    myProgressExecutionMode = executionMode;
    return this;
  }

  public ImportSpecBuilder forceWhenUptodate() {
    return forceWhenUptodate(true);
  }

  public ImportSpecBuilder forceWhenUptodate(boolean force) {
    myForceWhenUptodate = force;
    return this;
  }

  //public ImportSpecBuilder usePreviewMode() {
  //  isPreviewMode = true;
  //  return this;
  //}

  public ImportSpec build() {
    ImportSpec mySpec = new ImportSpec(myProject, myExternalSystemId);
    mySpec.setWhenAutoImportEnabled(myWhenAutoImportEnabled);
    mySpec.setProgressExecutionMode(myProgressExecutionMode);
    mySpec.setForceWhenUptodate(myForceWhenUptodate);
    //mySpec.setPreviewMode(isPreviewMode);
    //mySpec.setReportRefreshError(isReportRefreshError);
    return mySpec;
  }
}
