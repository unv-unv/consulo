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
package consulo.compiler.scope;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.Extension;
import consulo.component.extension.ExtensionPointName;
import consulo.module.Module;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 12:55/20.12.13
 */
@Extension(ComponentScope.APPLICATION)
public interface CompileModuleScopeFactory {
  ExtensionPointName<CompileModuleScopeFactory> EP_NAME = ExtensionPointName.create(CompileModuleScopeFactory.class);

  @Nullable
  FileIndexCompileScope createScope(@Nonnull final Module module, final boolean includeDependentModules);
}
