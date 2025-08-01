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

package consulo.localHistory.impl.internal.ui.action;

import consulo.codeEditor.Editor;
import consulo.localHistory.impl.internal.IdeaGateway;
import consulo.localHistory.impl.internal.LocalHistoryFacade;
import consulo.localHistory.impl.internal.ui.view.SelectionHistoryDialog;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;
import consulo.versionControlSystem.action.VcsContext;
import consulo.versionControlSystem.history.VcsSelection;
import consulo.versionControlSystem.history.VcsSelectionUtil;
import consulo.versionControlSystem.internal.VcsContextWrapper;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class ShowSelectionHistoryAction extends ShowHistoryAction {
  @Override
  protected void showDialog(Project p, IdeaGateway gw, VirtualFile f, AnActionEvent e) {
    VcsSelection sel = getSelection(e);

    int from = sel.getSelectionStartLineNumber();
    int to = sel.getSelectionEndLineNumber();

    new SelectionHistoryDialog(p, gw, f, from, to).show();
  }

  @Nonnull
  @Override
  protected LocalizeValue getTextValue(@Nonnull AnActionEvent e) {
    VcsSelection sel = getSelection(e);
    return sel == null ? super.getTextValue(e) : LocalizeValue.ofNullable(sel.getActionName());
  }

  @Override
  public void update(@Nonnull AnActionEvent e) {
    if (!e.hasData(Editor.KEY)) {
      e.getPresentation().setVisible(false);
    }
    else {
      super.update(e);
    }
  }

  @Override
  protected boolean isEnabled(@Nonnull LocalHistoryFacade vcs, @Nonnull IdeaGateway gw, VirtualFile f, @Nonnull AnActionEvent e) {
    return super.isEnabled(vcs, gw, f, e) && !f.isDirectory() && getSelection(e) != null;
  }

  @Nullable
  private static VcsSelection getSelection(AnActionEvent e) {
    VcsContext c = VcsContextWrapper.createCachedInstanceOn(e);
    return VcsSelectionUtil.getSelection(c);
  }
}
