/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 28-Aug-2009
 */
package consulo.execution.test.action;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.Extension;
import consulo.component.extension.ExtensionPointName;
import consulo.execution.test.TestConsoleProperties;

import javax.annotation.Nonnull;

@Extension(ComponentScope.APPLICATION)
public interface ToggleModelActionProvider {
  ExtensionPointName<ToggleModelActionProvider> EP_NAME = ExtensionPointName.create(ToggleModelActionProvider.class);

  @Nonnull
  ToggleModelAction createToggleModelAction(TestConsoleProperties properties);
}