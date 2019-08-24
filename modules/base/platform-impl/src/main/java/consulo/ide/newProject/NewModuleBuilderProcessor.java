/*
 * Copyright 2013-2016 consulo.io
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
package consulo.ide.newProject;

import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import consulo.annotations.DeprecationInfo;

import javax.annotation.Nonnull;

import javax.swing.*;

/**
 * @author VISTALL
 * @since 05.06.14
 */
@Deprecated
@DeprecationInfo("Use NewModuleBuilderProcessor2")
public interface NewModuleBuilderProcessor<T extends JComponent> {
  @Nonnull
  T createConfigurationPanel();

  default void setupModule(@Nonnull T panel, @Nonnull ContentEntry contentEntry, @Nonnull ModifiableRootModel modifiableRootModel) {
  }
}
