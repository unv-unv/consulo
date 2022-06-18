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
package consulo.execution;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.Service;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.project.Project;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 05-Apr-22
 */
@Service(ComponentScope.PROJECT)
public interface RunConfigurationEditor {
  static RunConfigurationEditor getInstance(Project project) {
    return project.getInstance(RunConfigurationEditor.class);
  }

  default boolean editConfiguration(final Project project, final RunnerAndConfigurationSettings configuration, final String title) {
    return editConfiguration(project, configuration, title, null);
  }

  default boolean editConfiguration(@Nonnull ExecutionEnvironment environment, @Nonnull String title) {
    return editConfiguration(environment.getProject(), environment.getRunnerAndConfigurationSettings(), title, environment.getExecutor());
  }

  boolean editConfiguration(final Project project, final RunnerAndConfigurationSettings configuration, final String title, @Nullable final Executor executor);
}