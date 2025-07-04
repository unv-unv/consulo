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
package consulo.ui.ex.action;

import consulo.annotation.DeprecationInfo;
import consulo.annotation.internal.MigratedExtensionsTo;
import consulo.component.util.localize.AbstractBundle;
import consulo.platform.base.localize.ActionLocalize;
import org.jetbrains.annotations.PropertyKey;

/**
 * @author Eugene Zhuravlev
 * @since 2005-08-29
 */
@Deprecated(forRemoval = true)
@DeprecationInfo("Use ActionLocalize")
@MigratedExtensionsTo(ActionLocalize.class)
public class ActionsBundle extends AbstractBundle{
  private static final ActionsBundle ourInstance = new ActionsBundle();

  private ActionsBundle() {
    super("consulo.ui.ex.action.ActionsBundle");
  }

  public static String message(@PropertyKey(resourceBundle = "consulo.ui.ex.action.ActionsBundle") String key) {
    return ourInstance.getMessage(key);
  }

  public static String message(@PropertyKey(resourceBundle = "consulo.ui.ex.action.ActionsBundle") String key, Object... params) {
    return ourInstance.getMessage(key, params);
  }

  @SuppressWarnings({"HardCodedStringLiteral", "UnresolvedPropertyKey"})
  public static String actionText(String actionId) {
    return message("action." + actionId + ".text");
  }

  @SuppressWarnings({"HardCodedStringLiteral", "UnresolvedPropertyKey"})
  public static String groupText(String actionId) {
    return message("group." + actionId + ".text");
  }

  @SuppressWarnings({"HardCodedStringLiteral", "UnresolvedPropertyKey"})
  public static String actionDescription(String actionId) {
    return message("action." + actionId + ".description");
  }
}
