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

import consulo.ui.ex.awt.tree.CheckboxTree;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.SimpleTextAttributes;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;

public class PushLogTreeUtil {
    public static final String EDIT_MODE_PROP = "tree.edit.mode";

    @Nullable
    public static Object getTagAtForRenderer(CheckboxTree.CheckboxTreeCellRenderer renderer, MouseEvent e) {
        JTree tree = (JTree)e.getSource();
        Object tag = null;
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path != null) {
            Rectangle rectangle = tree.getPathBounds(path);
            assert rectangle != null;
            int dx = e.getX() - rectangle.x;
            TreeNode treeNode = (TreeNode)path.getLastPathComponent();
            int row = tree.getRowForLocation(e.getX(), e.getY());
            tree.getCellRenderer().getTreeCellRendererComponent(tree, treeNode, false, false, true, row, true);
            if (treeNode instanceof RepositoryNode repositoryNode) {
                int checkBoxWidth = repositoryNode.isCheckboxVisible() ? renderer.getCheckbox().getWidth() : 0;
                tag = renderer.getTextRenderer().getFragmentTagAt(dx - checkBoxWidth);
            }
            else {
                tag = renderer.getTextRenderer().getFragmentTagAt(dx);
            }
        }
        return tag;
    }

    public static SimpleTextAttributes addTransparencyIfNeeded(@Nonnull SimpleTextAttributes baseStyle, boolean isActive) {
        if (isActive) {
            return baseStyle;
        }
        Color color = baseStyle.getFgColor();
        if (color == null) {
            color = JBColor.black;
        }
        //noinspection UseJBColor
        return new SimpleTextAttributes(
            baseStyle.getStyle(),
            new Color(color.getRed(), color.getGreen(), color.getBlue(), 85)
        );
    }
}
