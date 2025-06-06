/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
 * @author max
 * @author Konstantin Bulenkov
 */
public abstract class ColoredTableCellRenderer extends SimpleColoredRenderer implements TableCellRenderer {
  @Override
  public final Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int col) {
    clear();

    setBorder(JBCurrentTheme.listCellBorder());
      
    acquireState(table, isSelected, hasFocus, row, col);
    getCellState().updateRenderer(this);
    customizeCellRenderer(table, value, isSelected, hasFocus, row, col);
    return this;
  }

  protected abstract void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column);
}
