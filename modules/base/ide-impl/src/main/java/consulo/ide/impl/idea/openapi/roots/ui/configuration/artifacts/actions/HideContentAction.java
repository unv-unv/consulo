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
package consulo.ide.impl.idea.openapi.roots.ui.configuration.artifacts.actions;

import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ide.impl.idea.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import consulo.ide.impl.idea.openapi.roots.ui.configuration.artifacts.LayoutTreeSelection;
import consulo.ide.impl.idea.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import consulo.ide.impl.idea.openapi.roots.ui.configuration.artifacts.nodes.PackagingNodeSource;
import jakarta.annotation.Nonnull;

import java.util.Collection;

/**
 * @author nik
 */
public class HideContentAction extends DumbAwareAction {
  private final ArtifactEditorEx myArtifactEditor;

  public HideContentAction(ArtifactEditorEx artifactEditor) {
    super("Hide Content");
    myArtifactEditor = artifactEditor;
  }

  @Override
  public void update(AnActionEvent e) {
    final LayoutTreeSelection selection = myArtifactEditor.getLayoutTreeComponent().getSelection();
    final PackagingElementNode<?> node = selection.getNodeIfSingle();
    if (node != null) {
      final Collection<PackagingNodeSource> sources = node.getNodeSources();
      if (!sources.isEmpty()) {
        String description;
        if (sources.size() == 1) {
          description = "Hide Content of '" + sources.iterator().next().getPresentableName() + "'";
        }
        else {
          description = "Hide Content";
        }
        e.getPresentation().setVisible(true);
        e.getPresentation().setText(description);
        return;
      }
    }
    e.getPresentation().setVisible(false);
  }

  @Override
  @RequiredUIAccess
  public void actionPerformed(@Nonnull AnActionEvent e) {
    final LayoutTreeSelection selection = myArtifactEditor.getLayoutTreeComponent().getSelection();
    final PackagingElementNode<?> node = selection.getNodeIfSingle();
    if (node == null) return;

    final Collection<PackagingNodeSource> sources = node.getNodeSources();
    for (PackagingNodeSource source : sources) {
      myArtifactEditor.getSubstitutionParameters().doNotSubstitute(source.getSourceElement());
      myArtifactEditor.getLayoutTreeComponent().getLayoutTree().addSubtreeToUpdate(source.getSourceParentNode());
    }
  }
}
