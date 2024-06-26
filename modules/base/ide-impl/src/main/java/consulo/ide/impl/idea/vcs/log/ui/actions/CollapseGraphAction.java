/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package consulo.ide.impl.idea.vcs.log.ui.actions;

import consulo.ide.impl.idea.vcs.log.ui.VcsLogUiImpl;
import consulo.ui.image.Image;
import consulo.ide.impl.idea.vcs.log.VcsLogIcons;

import jakarta.annotation.Nonnull;

public class CollapseGraphAction extends CollapseOrExpandGraphAction {
  @Nonnull
  private static final String COLLAPSE = "Collapse";

  public CollapseGraphAction() {
    super(COLLAPSE);
  }

  @Override
  protected void executeAction(@Nonnull VcsLogUiImpl vcsLogUi) {
    vcsLogUi.collapseAll();
  }

  @Nonnull
  @Override
  protected String getPrefix() {
    return COLLAPSE + " ";
  }
}
