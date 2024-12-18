/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package consulo.execution.debug.impl.internal.frame.action;

import consulo.execution.debug.impl.internal.frame.XWatchesView;
import consulo.execution.debug.impl.internal.ui.tree.XDebuggerTree;
import consulo.ui.ex.action.AnActionEvent;
import jakarta.annotation.Nonnull;

/**
 * @author nik
 */
public class XRemoveAllWatchesAction extends XWatchesTreeActionBase {
  @Override
  protected boolean isEnabled(@Nonnull AnActionEvent e, @Nonnull XDebuggerTree tree) {
    return tree.getRoot().getChildCount() > 0;
  }

  @Override
  protected void perform(@Nonnull AnActionEvent e, @Nonnull XDebuggerTree tree, @Nonnull XWatchesView watchesView) {
    watchesView.removeAllWatches();
  }
}
