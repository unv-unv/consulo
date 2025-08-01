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
package consulo.ide.impl.idea.tools;

import consulo.application.HelpManager;
import consulo.ui.ex.awt.*;
import consulo.application.ui.wm.IdeFocusManager;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.util.ListUtil;

import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Yura Cangea
 */
public class OutputFiltersDialog extends DialogWrapper {
  private final DefaultListModel myFiltersModel = new DefaultListModel();
  private final JList myFiltersList = new JBList(myFiltersModel);
  private boolean myModified = false;
  private FilterInfo[] myFilters;

  public OutputFiltersDialog(Component parent, FilterInfo[] filters) {
    super(parent, true);
    myFilters = filters;

    setTitle(ToolsBundle.message("tools.filters.title"));
    init();
    initGui();
  }

  @Override
  @Nonnull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("reference.settings.ide.settings.external.tools.output.filters");
  }

  private void initGui() {
    myFiltersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myFiltersList.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        FilterInfo info = (FilterInfo)value;
        append(info.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    });
    ScrollingUtil.ensureSelectionExists(myFiltersList);
  }

  private String suggestFilterName() {
    String prefix = ToolsBundle.message("tools.filters.name.template") + " ";

    int number = 1;
    for (int i = 0; i < myFiltersModel.getSize(); i++) {
      FilterInfo wrapper = (FilterInfo)myFiltersModel.getElementAt(i);
      String name = wrapper.getName();
      if (name.startsWith(prefix)) {
        try {
          int n = Integer.valueOf(name.substring(prefix.length()).trim()).intValue();
          number = Math.max(number, n + 1);
        }
        catch (NumberFormatException e) {
        }
      }
    }

    return prefix + number;
  }

  @Override
  protected void doOKAction() {
    if (myModified) {
      myFilters = new FilterInfo[myFiltersModel.getSize()];
      for (int i = 0; i < myFiltersModel.getSize(); i++) {
        myFilters[i] = (FilterInfo)myFiltersModel.get(i);
      }
    }
    super.doOKAction();
  }

  @Override
  protected JComponent createCenterPanel() {
    for (FilterInfo myFilter : myFilters) {
      myFiltersModel.addElement(myFilter.createCopy());
    }

    JPanel panel = ToolbarDecorator.createDecorator(myFiltersList)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          FilterInfo filterInfo = new FilterInfo();
          filterInfo.setName(suggestFilterName());
          boolean wasCreated = FilterDialog.editFilter(filterInfo, myFiltersList, ToolsBundle.message("tools.filters.add.title"));
          if (wasCreated) {
            myFiltersModel.addElement(filterInfo);
            setModified(true);
          }
          IdeFocusManager.getGlobalInstance().doForceFocusWhenFocusSettlesDown(myFiltersList);
        }
      }).setEditAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          int index = myFiltersList.getSelectedIndex();
          FilterInfo filterInfo = (FilterInfo)myFiltersModel.getElementAt(index);
          boolean wasEdited = FilterDialog.editFilter(filterInfo, myFiltersList, ToolsBundle.message("tools.filters.edit.title"));
          if (wasEdited) {
            setModified(true);
          }
          IdeFocusManager.getGlobalInstance().doForceFocusWhenFocusSettlesDown(myFiltersList);
        }
      }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          if (myFiltersList.getSelectedIndex() >= 0) {
            myFiltersModel.removeElementAt(myFiltersList.getSelectedIndex());
            setModified(true);
          }
          IdeFocusManager.getGlobalInstance().doForceFocusWhenFocusSettlesDown(myFiltersList);
        }
      }).setMoveUpAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          int movedCount = ListUtil.moveSelectedItemsUp(myFiltersList);
          if (movedCount > 0) {
            setModified(true);
          }
          IdeFocusManager.getGlobalInstance().doForceFocusWhenFocusSettlesDown(myFiltersList);
        }
      }).setMoveDownAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          int movedCount = ListUtil.moveSelectedItemsDown(myFiltersList);
          if (movedCount > 0) {
            setModified(true);
          }
          IdeFocusManager.getGlobalInstance().doForceFocusWhenFocusSettlesDown(myFiltersList);
        }
      })
      .createPanel();

    return panel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myFiltersList;
  }

  private void setModified(boolean modified) {
    myModified = modified;
  }

  public FilterInfo[] getData() {
    return myFilters;
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#consulo.ide.impl.idea.tools.OutputFiltersDialog";
  }
}
