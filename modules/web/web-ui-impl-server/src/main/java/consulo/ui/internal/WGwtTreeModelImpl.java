/*
 * Copyright 2013-2017 consulo.io
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
package consulo.ui.internal;

import consulo.ui.ItemPresentation;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author VISTALL
 * @since 09-Sep-17
 */
public abstract class WGwtTreeModelImpl<N> {
  @NotNull
  public abstract N fetchRootNode();

  @NotNull
  public List<N> fetchChildNodes(N node) {
    return Collections.emptyList();
  }

  public void renderNode(@NotNull N node, @NotNull ItemPresentation presentation) {
    presentation.append(node.toString());
  }
}
