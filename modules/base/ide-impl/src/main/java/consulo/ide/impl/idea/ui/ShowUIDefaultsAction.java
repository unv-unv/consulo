/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package consulo.ide.impl.idea.ui;

import consulo.application.dumb.DumbAware;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.Cell;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.awt.ColorChooser;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.EmptyIcon;
import consulo.ui.ex.awt.JBScrollPane;
import consulo.ui.ex.awt.speedSearch.TableSpeedSearch;
import consulo.ui.ex.awt.table.JBTable;
import consulo.ui.ex.awt.util.ColorUtil;
import consulo.ui.ex.awt.util.TableUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.function.PairFunction;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ColorUIResource;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.EventObject;

/**
 * @author Konstantin Bulenkov
 */
public class ShowUIDefaultsAction extends AnAction implements DumbAware {
  @Override
  @RequiredUIAccess
  public void actionPerformed(@Nonnull AnActionEvent e) {
    final UIDefaults defaults = UIManager.getDefaults();
    Enumeration keys = defaults.keys();
    final Object[][] data = new Object[defaults.size()][2];
    int i = 0;
    while (keys.hasMoreElements()) {
      Object key = keys.nextElement();
      data[i][0] = key;
      data[i][1] = defaults.get(key);
      i++;
    }

    Arrays.sort(data, (o1, o2) -> StringUtil.naturalCompare(o1[0].toString(), o2[0].toString()));

    final Project project = e.getData(Project.KEY);
    new DialogWrapper(project) {
      {
        setTitle("Edit LaF Defaults");
        setModal(false);
        init();
      }

      public JBTable myTable;

      @Nullable
      @Override
      public JComponent getPreferredFocusedComponent() {
        return myTable;
      }

      @Nullable
      @Override
      protected String getDimensionServiceKey() {
        return project == null ? null : "UI.Defaults.Dialog";
      }

      @Override
      protected JComponent createCenterPanel() {
        final JBTable table = new JBTable(new DefaultTableModel(data, new Object[]{"Name", "Value"}) {
          @Override
          public boolean isCellEditable(int row, int column) {
            return column == 1 && getValueAt(row, column) instanceof Color;
          }
        }) {
          @Override
          public boolean editCellAt(int row, int column, EventObject e) {
            if (isCellEditable(row, column) && e instanceof MouseEvent) {
              final Object color = getValueAt(row, column);

              ColorChooser.chooseColor(this, "Choose Color", (Color)color, true, true, newColor -> {
                if (newColor != null) {
                  final ColorUIResource colorUIResource = new ColorUIResource(newColor);
                  final Object key = getValueAt(row, 0);
                  UIManager.put(key, colorUIResource);
                  setValueAt(colorUIResource, row, column);
                }
              });

            }
            return false;
          }
        };
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
          @Override
          public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            final JPanel panel = new JPanel(new BorderLayout());
            final JLabel label = new JLabel(value == null ? "" : value.toString());
            panel.add(label, BorderLayout.CENTER);
            if (value instanceof Color) {
              final Color c = (Color)value;
              label.setText(String.format("[r=%d,g=%d,b=%d] hex=0x%s", c.getRed(), c.getGreen(), c.getBlue(), ColorUtil.toHex(c)));
              label.setForeground(ColorUtil.isDark(c) ? Color.white : Color.black);
              panel.setBackground(c);
              return panel;
            }
            else if (value instanceof Icon) {
              try {
                final Icon icon = new IconWrap((Icon)value);
                if (icon.getIconHeight() <= 20) {
                  label.setIcon(icon);
                }
                label.setText(String.format("(%dx%d) %s)", icon.getIconWidth(), icon.getIconHeight(), label.getText()));
              }
              catch (Throwable e1) {//
              }
              return panel;
            }
            else if (value instanceof Border) {
              try {
                final Insets i = ((Border)value).getBorderInsets(null);
                label.setText(String.format("[%d, %d, %d, %d] %s", i.top, i.left, i.bottom, i.right, label.getText()));
                return panel;
              }
              catch (Exception ignore) {
              }
            }
            return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
          }
        });
        final JBScrollPane pane = new JBScrollPane(table);
        new TableSpeedSearch(table, new PairFunction<>() {
          @Nullable
          @Override
          public String fun(Object o, Cell cell) {
            return cell.column == 1 ? null : String.valueOf(o);
          }
        });
        table.setShowGrid(false);
        final JPanel panel = new JPanel(new BorderLayout());
        panel.add(pane, BorderLayout.CENTER);
        myTable = table;
        TableUtil.ensureSelectionExists(myTable);
        return panel;
      }
    }.show();
  }

  private class IconWrap implements Icon {
    private final Icon myIcon;

    public IconWrap(Icon icon) {
      myIcon = icon;
    }


    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      try {
        myIcon.paintIcon(c, g, x, y);
      }
      catch (Exception e) {
        EmptyIcon.ICON_0.paintIcon(c, g, x, y);
      }
    }

    @Override
    public int getIconWidth() {
      return myIcon.getIconWidth();
    }

    @Override
    public int getIconHeight() {
      return myIcon.getIconHeight();
    }
  }
}
