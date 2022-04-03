/*
 * Copyright 2013-2022 consulo.io
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
package consulo.execution.test.ui;

import consulo.execution.ui.console.ConsoleState;
import consulo.execution.ui.console.ConsoleView;
import consulo.execution.ui.console.ConsoleViewRunningState;
import consulo.process.ProcessHandler;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 03-Apr-22
 */
public class TestConsoleState extends ConsoleState.NotStartedStated {
  private final boolean myViewer;

  public TestConsoleState(boolean viewer) {
    myViewer = viewer;
  }

  @Nonnull
  @Override
  public ConsoleState attachTo(@Nonnull ConsoleView console, ProcessHandler processHandler) {
    return new ConsoleViewRunningState(console, processHandler, this, false, !myViewer);
  }
}
