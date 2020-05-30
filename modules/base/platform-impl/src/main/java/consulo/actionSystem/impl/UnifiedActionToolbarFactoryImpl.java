/*
 * Copyright 2013-2020 consulo.io
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
package consulo.actionSystem.impl;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionToolbar;
import consulo.actionSystem.ActionToolbarFactory;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 2020-05-11
 */
public class UnifiedActionToolbarFactoryImpl implements ActionToolbarFactory {
  @Nonnull
  @Override
  public ActionToolbar createActionToolbar(String place, ActionGroup group, boolean horizontal, boolean decorateButtons) {
    return new UnifiedActionToolbarImpl(place, group, horizontal, decorateButtons);
  }
}
