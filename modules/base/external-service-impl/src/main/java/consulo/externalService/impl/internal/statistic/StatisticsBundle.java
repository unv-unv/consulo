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
package consulo.externalService.impl.internal.statistic;

import consulo.component.util.localize.AbstractBundle;
import org.jetbrains.annotations.PropertyKey;

/**
 * @author Denis Zhdanov
 * @since 2013-04-25
 */
public class StatisticsBundle extends AbstractBundle {
  private static final StatisticsBundle ourInstance = new StatisticsBundle();

  private StatisticsBundle() {
    super("messages.StatisticsBundle");
  }

  public static String message(@PropertyKey(resourceBundle = "messages.StatisticsBundle") String key) {
    return ourInstance.getMessage(key);
  }

  public static String message(@PropertyKey(resourceBundle = "messages.StatisticsBundle") String key, Object... params) {
    return ourInstance.getMessage(key, params);
  }
}
