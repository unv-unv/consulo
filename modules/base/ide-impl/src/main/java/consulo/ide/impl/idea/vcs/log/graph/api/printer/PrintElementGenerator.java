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

package consulo.ide.impl.idea.vcs.log.graph.api.printer;

import consulo.versionControlSystem.log.graph.PrintElement;
import consulo.ide.impl.idea.vcs.log.graph.impl.print.elements.PrintElementWithGraphElement;
import javax.annotation.Nonnull;

import java.util.Collection;

public interface PrintElementGenerator {

  @Nonnull
  Collection<PrintElementWithGraphElement> getPrintElements(int visibleRow);

  @Nonnull
  PrintElementWithGraphElement withGraphElement(@Nonnull PrintElement printElement);
}
