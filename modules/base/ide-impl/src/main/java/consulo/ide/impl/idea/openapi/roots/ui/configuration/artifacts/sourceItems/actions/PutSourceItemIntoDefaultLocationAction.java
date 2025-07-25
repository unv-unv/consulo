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
package consulo.ide.impl.idea.openapi.roots.ui.configuration.artifacts.sourceItems.actions;

import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.ide.impl.idea.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import consulo.ide.impl.idea.openapi.roots.ui.configuration.artifacts.sourceItems.SourceItemsTree;
import consulo.util.lang.StringUtil;
import consulo.compiler.artifact.ui.PackagingSourceItem;
import jakarta.annotation.Nonnull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public class PutSourceItemIntoDefaultLocationAction extends PutIntoDefaultLocationActionBase {
  public PutSourceItemIntoDefaultLocationAction(SourceItemsTree sourceItemsTree, ArtifactEditorEx artifactEditor) {
    super(sourceItemsTree, artifactEditor);
  }

  @Override
  public void update(@Nonnull AnActionEvent e) {
    final List<PackagingSourceItem> items = mySourceItemsTree.getSelectedItems();
    boolean enabled = false;
    final Presentation presentation = e.getPresentation();
    if (!items.isEmpty()) {
      enabled = true;
      Set<String> paths = new HashSet<String>();
      for (PackagingSourceItem item : items) {
        final String path = getDefaultPath(item);
        if (path == null) {
          enabled = false;
          break;
        }
        paths.add(StringUtil.trimStart(StringUtil.trimEnd(path, "/"), "/"));
      }
      presentation.setText("Put into " + PutIntoDefaultLocationActionBase.getTargetLocationText(paths));
    }
    presentation.setEnabledAndVisible(enabled);
  }

  @Override
  @RequiredUIAccess
  public void actionPerformed(@Nonnull AnActionEvent e) {
    myArtifactEditor.getLayoutTreeComponent().putIntoDefaultLocations(mySourceItemsTree.getSelectedItems());
  }
}
