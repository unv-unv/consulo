/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package consulo.project;

import consulo.annotation.DeprecationInfo;
import consulo.annotation.internal.MigratedExtensionsTo;
import consulo.component.util.localize.AbstractBundle;
import consulo.project.localize.ProjectLocalize;
import org.jetbrains.annotations.PropertyKey;

@Deprecated(forRemoval = true)
@DeprecationInfo("Use ProjectLocalize")
@MigratedExtensionsTo(ProjectLocalize.class)
public class ProjectBundle extends AbstractBundle {
  private static final ProjectBundle ourInstance = new ProjectBundle();

  private ProjectBundle() {
    super("consulo.project.ProjectBundle");
  }

  public static String message(@PropertyKey(resourceBundle = "consulo.project.ProjectBundle") String key) {
    return ourInstance.getMessage(key);
  }

  public static String message(@PropertyKey(resourceBundle = "consulo.project.ProjectBundle") String key, Object... params) {
    return ourInstance.getMessage(key, params);
  }
}
