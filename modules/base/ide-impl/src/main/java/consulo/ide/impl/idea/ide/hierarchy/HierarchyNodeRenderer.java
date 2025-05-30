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

package consulo.ide.impl.idea.ide.hierarchy;

import consulo.ui.ex.awt.tree.NodeRenderer;
import consulo.ui.annotation.RequiredUIAccess;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public final class HierarchyNodeRenderer extends NodeRenderer {
    public HierarchyNodeRenderer() {
        setOpaque(false);
        setIconOpaque(false);
        setTransparentIconBackground(true);
    }

    @Override
    protected void doPaint(Graphics2D g) {
        super.doPaint(g);
        setOpaque(false);
    }

    @Override
    @RequiredUIAccess
    public void customizeCellRenderer(
        @Nonnull JTree tree,
        Object value,
        boolean selected,
        boolean expanded,
        boolean leaf,
        int row,
        boolean hasFocus
    ) {
        if (value instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof HierarchyNodeDescriptor descriptor) {
            descriptor.getHighlightedText().customize(this);
            setIcon(descriptor.getIcon());
        }
    }
}
