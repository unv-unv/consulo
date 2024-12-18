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
package consulo.execution.debug.impl.internal.breakpoint.ui.tree;

import consulo.project.Project;
import consulo.ui.ex.awt.tree.CheckboxTree;
import consulo.ui.ex.awt.tree.ColoredTreeCellRenderer;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.execution.debug.breakpoint.ui.XBreakpointGroup;
import consulo.execution.debug.impl.internal.breakpoint.ui.BreakpointItem;

import javax.swing.*;

public class BreakpointsTreeCellRenderer  {
  private static void customizeRenderer(Project project,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        ColoredTreeCellRenderer renderer) {
    if (value instanceof BreakpointItemNode) {
      BreakpointItemNode node = (BreakpointItemNode)value;
      BreakpointItem breakpoint = node.getBreakpointItem();
      breakpoint.setupRenderer(renderer, project, selected);
    }
    else if (value instanceof BreakpointsGroupNode) {
      XBreakpointGroup group = ((BreakpointsGroupNode)value).getGroup();
      renderer.setIcon(group.getIcon(expanded));
      renderer.append(group.getName(), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
    }
  }

  public static class BreakpointsCheckboxTreeCellRenderer extends CheckboxTree.CheckboxTreeCellRenderer {
    private final Project myProject;

    public BreakpointsCheckboxTreeCellRenderer(Project project) {
      myProject = project;
    }

    @Override
    public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      BreakpointsTreeCellRenderer.customizeRenderer(myProject, value, selected, expanded, getTextRenderer());
    }
  }

  public static class BreakpointsSimpleTreeCellRenderer extends ColoredTreeCellRenderer {
    private final Project myProject;

    public BreakpointsSimpleTreeCellRenderer(Project project) {
      myProject = project;
    }

    @Override
    public void customizeCellRenderer(JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      customizeRenderer(myProject, value, selected, expanded, this);
    }
  }
}
