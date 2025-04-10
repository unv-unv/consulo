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

import consulo.ide.impl.idea.dvcs.push.VcsPushReferenceStrategy;
import consulo.ui.ex.awt.ComboBox;

import javax.swing.*;
import java.awt.*;

public class DvcsStrategyPanel extends JPanel {
    private ComboBox<VcsPushReferenceStrategy> myReferenceStrategyCombobox;

    public DvcsStrategyPanel() {
        setLayout(new BorderLayout());
        myReferenceStrategyCombobox = new ComboBox<>();
        DefaultComboBoxModel<VcsPushReferenceStrategy> comboModel = new DefaultComboBoxModel<>(VcsPushReferenceStrategy.values());
        myReferenceStrategyCombobox.setModel(comboModel);
        JPanel bottomPanel = new JPanel(new FlowLayout());
        JLabel referenceStrategyLabel = new JLabel("Push Reference Strategy: ");
        bottomPanel.add(referenceStrategyLabel, FlowLayout.LEFT);
        bottomPanel.add(myReferenceStrategyCombobox);
        add(bottomPanel, BorderLayout.WEST);
    }

    public VcsPushReferenceStrategy getStrategy() {
        return (VcsPushReferenceStrategy)myReferenceStrategyCombobox.getSelectedItem();
    }
}
