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
package consulo.ui.ex.awt;

import consulo.util.lang.xml.XmlStringUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author Anna.Kozlova
 * @since 2006-07-16
 */
public class PanelWithText extends JPanel {
    private final JLabel myLabel = new JLabel();

    public PanelWithText() {
        this("");
    }

    public PanelWithText(String text) {
        super(new GridBagLayout());
        myLabel.setText(XmlStringUtil.wrapInHtml(text));
        add(myLabel, new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, JBUI.insets(8), 0, 0));
    }

    public void setText(String text) {
        myLabel.setText(XmlStringUtil.wrapInHtml(text));
    }
}
