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
package consulo.ide.impl.idea.ui.roots;

import consulo.ui.ex.awt.JBUI;

import javax.swing.*;
import java.awt.*;

/**
 * @author cdr
 */
public class ResizingWrapper extends JComponent {
    protected final JComponent myWrappedComponent;

    public ResizingWrapper(JComponent wrappedComponent) {
        myWrappedComponent = wrappedComponent;
        setLayout(new GridBagLayout());
        setOpaque(false);

        add(wrappedComponent, new GridBagConstraints(0, 0, 1, 1, 0.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0));

        add(Box.createHorizontalGlue(), new GridBagConstraints(1, 0, 1, 1, 1.0, 1.0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, JBUI.emptyInsets(), 0, 0));
    }
}