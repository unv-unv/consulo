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
package consulo.ide.impl.idea.util.ui;

import consulo.util.collection.ListWithSelection;
import consulo.logging.Logger;
import consulo.util.lang.ObjectUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.Arrays;

public class ComboBoxTableCellRenderer extends JPanel implements TableCellRenderer {
  public final static TableCellRenderer INSTANCE = new ComboBoxTableCellRenderer();

  /**
   * DefaultTableCellRenderer, that displays JComboBox on selected value.
   */
  public final static TableCellRenderer COMBO_WHEN_SELECTED_RENDERER = new DefaultTableCellRenderer() {
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (isSelected) {
        value = new ListWithSelection<Object>(Arrays.asList(value));
        return INSTANCE.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      }
      return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    }
  };

  private static final Logger LOG = Logger.getInstance(ComboBoxTableCellRenderer.class);

  private final JComboBox myCombo = new JComboBox();

  private ComboBoxTableCellRenderer() {
    super(new GridBagLayout());
    add(myCombo,
        new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
  }

  public JComponent getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    if (value instanceof ListWithSelection) {
      final ListWithSelection tags = (ListWithSelection)value;
      if (tags.getSelection() == null) {
        tags.selectFirst();
      }
      myCombo.removeAllItems();
      for (Object tag : tags) {
        myCombo.addItem(tag);
      }
      myCombo.setSelectedItem(tags.getSelection());
    }
    else {
      if (value != null) {
        LOG.error("value " + ObjectUtil.objectInfo(value) + ", at " + row + ":" + column + ", in " + table.getModel());
      }
      myCombo.removeAllItems();
      myCombo.setSelectedIndex(-1);
    }

    return this;
  }
}
