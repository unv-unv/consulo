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
package consulo.ide.impl.idea.dvcs.push.ui;

import consulo.versionControlSystem.distributed.push.PushTargetPanel;
import consulo.ui.ex.awt.tree.ColoredTreeCellRenderer;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.tree.TreeUtil;
import jakarta.annotation.Nonnull;

public class SingleRepositoryNode extends RepositoryNode {
    @Nonnull
    private final RepositoryWithBranchPanel myRepositoryPanel;

    public SingleRepositoryNode(@Nonnull RepositoryWithBranchPanel repositoryPanel, @Nonnull CheckBoxModel model) {
        super(repositoryPanel, model, true);
        myRepositoryPanel = repositoryPanel;
    }

    @Override
    public boolean isCheckboxVisible() {
        return false;
    }

    @Override
    public void setChecked(boolean checked) {
    }

    @Override
    public void fireOnSelectionChange(boolean isSelected) {
    }

    @Override
    public void render(@Nonnull ColoredTreeCellRenderer renderer) {
        renderer.append(" ");
        renderer.append(myRepositoryPanel.getSourceName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        renderer.append(myRepositoryPanel.getArrow(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        PushTargetPanel pushTargetPanel = myRepositoryPanel.getTargetPanel();
        pushTargetPanel.render(renderer, renderer.getTree().isPathSelected(TreeUtil.getPathFromRoot(this)), true, null);
    }
}
