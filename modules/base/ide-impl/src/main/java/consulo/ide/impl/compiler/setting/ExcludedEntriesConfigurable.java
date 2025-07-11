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

package consulo.ide.impl.compiler.setting;

import consulo.compiler.CompilerBundle;
import consulo.compiler.localize.CompilerLocalize;
import consulo.compiler.setting.ExcludeEntryDescription;
import consulo.compiler.setting.ExcludedEntriesConfiguration;
import consulo.ui.ex.JBColor;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.configurable.UnnamedConfigurable;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.status.FileStatusManager;
import consulo.ui.ex.awt.ToolbarDecorator;
import consulo.virtualFileSystem.VirtualFile;
import consulo.application.ui.wm.IdeFocusManager;
import consulo.ide.impl.idea.ui.*;
import consulo.ui.ex.awt.table.JBTable;
import consulo.disposer.Disposer;
import consulo.fileChooser.FileChooser;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;

public class ExcludedEntriesConfigurable implements UnnamedConfigurable {
  private final Project myProject;
  private final ArrayList<ExcludeEntryDescription> myExcludeEntryDescriptions = new ArrayList<ExcludeEntryDescription>();
  private final FileChooserDescriptor myDescriptor;
  private final ExcludedEntriesConfiguration myConfiguration;
  private ExcludedEntriesPanel myExcludedEntriesPanel;

  public ExcludedEntriesConfigurable(Project project, FileChooserDescriptor descriptor, final ExcludedEntriesConfiguration configuration) {
    myDescriptor = descriptor;
    myConfiguration = configuration;
    myProject = project;
  }

  public void reset() {
    ExcludeEntryDescription[] descriptions = myConfiguration.getExcludeEntryDescriptions();
    disposeMyDescriptions();
    for (ExcludeEntryDescription description : descriptions) {
      myExcludeEntryDescriptions.add(description.copy(myProject));
    }
    ((AbstractTableModel)myExcludedEntriesPanel.myExcludedTable.getModel()).fireTableDataChanged();
  }

  public void addEntry(ExcludeEntryDescription description) {
    myExcludeEntryDescriptions.add(description);
    ((AbstractTableModel)myExcludedEntriesPanel.myExcludedTable.getModel()).fireTableDataChanged();
  }

  private void disposeMyDescriptions() {
    for (ExcludeEntryDescription description : myExcludeEntryDescriptions) {
      Disposer.dispose(description);
    }
    myExcludeEntryDescriptions.clear();
  }

  public void apply() {
    myConfiguration.removeAllExcludeEntryDescriptions();
    for (ExcludeEntryDescription description : myExcludeEntryDescriptions) {
      myConfiguration.addExcludeEntryDescription(description.copy(myProject));
    }
    FileStatusManager.getInstance(myProject).fileStatusesChanged(); // refresh exclude from compile status
  }

  public boolean isModified() {
    ExcludeEntryDescription[] excludeEntryDescriptions = myConfiguration.getExcludeEntryDescriptions();
    if(excludeEntryDescriptions.length != myExcludeEntryDescriptions.size()) {
      return true;
    }
    for(int i = 0; i < excludeEntryDescriptions.length; i++) {
      ExcludeEntryDescription description = excludeEntryDescriptions[i];
      if(!Comparing.equal(description, myExcludeEntryDescriptions.get(i))) {
        return true;
      }
    }
    return false;
  }

  public JComponent createComponent() {
    if (myExcludedEntriesPanel == null) {
      myExcludedEntriesPanel = new ExcludedEntriesPanel();
    }
    return myExcludedEntriesPanel;
  }

  public void disposeUIResources() {
    myExcludedEntriesPanel = null;
  }

  private class ExcludedEntriesPanel extends JPanel {
    private JBTable myExcludedTable;

    public ExcludedEntriesPanel() {
      super(new BorderLayout());

      add(createMainComponent(), BorderLayout.CENTER);
    }

    private void addPath(FileChooserDescriptor descriptor) {
      FileChooser.chooseFiles(descriptor, myProject, null).doWhenDone(chosen -> {
        int selected = -1 /*myExcludedTable.getSelectedRow() + 1*/;
        if (selected < 0) {
          selected = myExcludeEntryDescriptions.size();
        }
        int savedSelected = selected;

        for (final VirtualFile chosenFile : chosen) {
          if (isFileExcluded(chosenFile)) {
            continue;
          }
          ExcludeEntryDescription description;
          if (chosenFile.isDirectory()) {
            description = new ExcludeEntryDescription(chosenFile, true, false, myProject);
          }
          else {
            description = new ExcludeEntryDescription(chosenFile, false, true, myProject);
          }
          myExcludeEntryDescriptions.add(selected, description);
          selected++;
        }
        if (selected > savedSelected) { // actually added something
          AbstractTableModel model = (AbstractTableModel)myExcludedTable.getModel();
          model.fireTableRowsInserted(savedSelected, selected - 1);
          myExcludedTable.setRowSelectionInterval(savedSelected, selected - 1);
        }
      });
    }

    private boolean isFileExcluded(VirtualFile file) {
      for (final ExcludeEntryDescription description : myExcludeEntryDescriptions) {
        final VirtualFile descriptionFile = description.getVirtualFile();
        if (descriptionFile == null) {
          continue;
        }
        if (file.equals(descriptionFile)) {
          return true;
        }
      }
      return false;
    }

    private void removePaths() {
      int[] selected = myExcludedTable.getSelectedRows();
      if(selected == null || selected.length <= 0) {
        return;
      }
      if(myExcludedTable.isEditing()) {
        TableCellEditor editor = myExcludedTable.getCellEditor();
        if (editor != null) {
          editor.stopCellEditing();
        }
      }
      AbstractTableModel model = (AbstractTableModel)myExcludedTable.getModel();
      Arrays.sort(selected);
      int indexToSelect = selected[selected.length - 1];
      int removedCount = 0;
      for (int indexToRemove : selected) {
        final int row = indexToRemove - removedCount;
        ExcludeEntryDescription description = myExcludeEntryDescriptions.get(row);
        Disposer.dispose(description);
        myExcludeEntryDescriptions.remove(row);
        model.fireTableRowsDeleted(row, row);
        removedCount += 1;
      }
      if(indexToSelect >= myExcludeEntryDescriptions.size()) {
        indexToSelect = myExcludeEntryDescriptions.size() - 1;
      }
      if(indexToSelect >= 0) {
        myExcludedTable.setRowSelectionInterval(indexToSelect, indexToSelect);
      }
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
        IdeFocusManager.getGlobalInstance().requestFocus(myExcludedTable, true);
      });
    }

    private JComponent createMainComponent() {
      final String[] names = {
        CompilerBundle.message("exclude.from.compile.table.path.column.name"),
        CompilerBundle.message("exclude.from.compile.table.recursively.column.name")
      };
      // Create a model of the data.
      TableModel dataModel = new AbstractTableModel() {
        public int getColumnCount() {
          return names.length;
        }

        public int getRowCount() {
          return myExcludeEntryDescriptions.size();
        }

        public Object getValueAt(int row, int col) {
          ExcludeEntryDescription description = myExcludeEntryDescriptions.get(row);
          if(col == 0) {
            return description.getPresentableUrl();
          }
          if(col == 1) {
            if(!description.isFile()) {
              return description.isIncludeSubdirectories() ? Boolean.TRUE : Boolean.FALSE;
            }
            else {
              return null;
            }
          }
          return null;
        }

        public String getColumnName(int column) {
          return names[column];
        }

        public Class getColumnClass(int c) {
          if(c == 0) {
            return String.class;
          }
          if(c == 1) {
            return Boolean.class;
          }
          return null;
        }

        public boolean isCellEditable(int row, int col) {
          if(col == 1) {
            ExcludeEntryDescription description = myExcludeEntryDescriptions.get(row);
            return !description.isFile();
          }
          return true;
        }

        public void setValueAt(Object aValue, int row, int col) {
          ExcludeEntryDescription description = myExcludeEntryDescriptions.get(row);
          if (col == 1) {
            description.setIncludeSubdirectories(aValue.equals(Boolean.TRUE));
          } else {
            final String path = (String)aValue;
            description.setPresentableUrl(path);
          }
        }
      };

      myExcludedTable = new JBTable(dataModel);
      myExcludedTable.setEnableAntialiasing(true);

      myExcludedTable.getEmptyText().setText(CompilerLocalize.noExcludes());
      myExcludedTable.setPreferredScrollableViewportSize(new Dimension(300, myExcludedTable.getRowHeight() * 6));
      myExcludedTable.setDefaultRenderer(Boolean.class, new BooleanRenderer());
      myExcludedTable.setDefaultRenderer(Object.class, new MyObjectRenderer());
      myExcludedTable.getColumn(names[0]).setPreferredWidth(350);
      final int cbWidth = 15 + myExcludedTable.getTableHeader().getFontMetrics(myExcludedTable.getTableHeader().getFont()).stringWidth(names[1]);
      final TableColumn cbColumn = myExcludedTable.getColumn(names[1]);
      cbColumn.setPreferredWidth(cbWidth);
      cbColumn.setMaxWidth(cbWidth);
      myExcludedTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      TableCellEditor editor = myExcludedTable.getDefaultEditor(String.class);
      if(editor instanceof DefaultCellEditor) {
        ((DefaultCellEditor)editor).setClickCountToStart(1);
      }

      return ToolbarDecorator.createDecorator(myExcludedTable)
        .disableUpAction()
        .disableDownAction()
        .setAddAction(anActionButton -> addPath(myDescriptor))
        .setRemoveAction(anActionButton -> removePaths()).createPanel();
    }
  }

  private static class BooleanRenderer extends JCheckBox implements TableCellRenderer {
    private final JPanel myPanel = new JPanel();

    public BooleanRenderer() {
      setHorizontalAlignment(CENTER);
    }

    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
      if(value == null) {
        if(isSelected) {
          myPanel.setBackground(table.getSelectionBackground());
        }
        else {
          myPanel.setBackground(table.getBackground());
        }
        return myPanel;
      }
      if(isSelected) {
        setForeground(table.getSelectionForeground());
        super.setBackground(table.getSelectionBackground());
      }
      else {
        setForeground(table.getForeground());
        setBackground(table.getBackground());
      }
      setSelected(((Boolean)value).booleanValue());
      return this;
    }
  }

  private class MyObjectRenderer extends DefaultTableCellRenderer {
    public MyObjectRenderer() {
      setUI(new RightAlignedLabelUI());
    }

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      final Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      final ExcludeEntryDescription description = myExcludeEntryDescriptions.get(row);
      component.setForeground(!description.isValid() ? JBColor.RED : isSelected ? table.getSelectionForeground() : table.getForeground());
      component.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
      return component;
    }
  }
}
