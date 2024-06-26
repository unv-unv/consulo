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

/*
 * Class NewWatchAction
 * @author Jeka
 */
package consulo.execution.impl.internal.ui.layout.action;

import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareAction;

public class RestoreLayoutAction extends DumbAwareAction {

  @Override
  public void actionPerformed(final AnActionEvent e) {
    ToggleToolbarLayoutAction.getRunnerUi(e).restoreLayout();
  }

  @Override
  public void update(final AnActionEvent e) {
    e.getPresentation().setEnabled(ToggleToolbarLayoutAction.getRunnerUi(e) != null);
  }
}