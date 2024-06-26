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
package consulo.desktop.awt.execution.ui;

import consulo.project.ui.wm.dock.DockContainer;
import consulo.project.ui.wm.dock.DockContainerFactory;
import consulo.project.ui.wm.dock.DockManager;
import consulo.project.ui.wm.dock.DockableContent;
import jakarta.annotation.Nonnull;

/**
 * @author Dennis.Ushakov
 */
public class DockableGridContainerFactory implements DockContainerFactory {
  public static final String TYPE = "runner-grid";

  @Nonnull
  @Override
  public String getId() {
    return TYPE;
  }

  @Override
  public DockContainer createContainer(DockManager dockManager, DockableContent content) {
    final RunnerContentUiImpl.DockableGrid dockableGrid = (RunnerContentUiImpl.DockableGrid)content;
    return new RunnerContentUiImpl(dockableGrid.getRunnerUi(), dockableGrid.getOriginalRunnerUi(), dockableGrid.getWindow());
  }
}
