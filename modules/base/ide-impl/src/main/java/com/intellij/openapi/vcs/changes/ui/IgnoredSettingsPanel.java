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

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 20.12.2006
 * Time: 19:39:53
 */
package com.intellij.openapi.vcs.changes.ui;

import consulo.configurable.Configurable;
import consulo.configurable.SearchableConfigurable;
import consulo.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.IgnoreSettingsType;
import com.intellij.openapi.vcs.changes.IgnoredFileBean;
import com.intellij.ui.*;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.util.ListUtil;
import org.jetbrains.annotations.Nls;
import javax.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

public class IgnoredSettingsPanel implements SearchableConfigurable, Configurable.NoScroll {
  private JBList myList;
  private JPanel myPanel;
  private final Project myProject;
  private DefaultListModel myModel;
  private final ChangeListManagerImpl myChangeListManager;
  private final Set<String> myDirectoriesManuallyRemovedFromIgnored = new HashSet<String>();

  public IgnoredSettingsPanel(Project project) {
    myList = new JBList();
    myList.setCellRenderer(new MyCellRenderer());
    myList.getEmptyText().setText(VcsBundle.message("no.ignored.files"));

    myProject = project;
    myChangeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);
  }

  private void setItems(final IgnoredFileBean[] filesToIgnore) {
    myModel = new DefaultListModel();
    for (IgnoredFileBean bean : filesToIgnore) {
      myModel.addElement(bean);
    }
    myList.setModel(myModel);
  }

  public IgnoredFileBean[] getItems() {
    final int count = myList.getModel().getSize();
    IgnoredFileBean[] result = new IgnoredFileBean[count];
    for (int i = 0; i < count; i++) {
      result[i] = (IgnoredFileBean)myList.getModel().getElementAt(i);
    }
    return result;
  }

  private void addItem() {
    IgnoreUnversionedDialog dlg = new IgnoreUnversionedDialog(myProject);
    dlg.show();
    if (dlg.isOK()) {
      final IgnoredFileBean[] ignoredFiles = dlg.getSelectedIgnoredFiles();
      for (IgnoredFileBean bean : ignoredFiles) {
        myModel.addElement(bean);
      }
    }
  }

  private void editItem() {
    IgnoredFileBean bean = (IgnoredFileBean)myList.getSelectedValue();
    if (bean == null) return;
    IgnoreUnversionedDialog dlg = new IgnoreUnversionedDialog(myProject);
    dlg.setIgnoredFile(bean);
    dlg.show();
    if (dlg.isOK()) {
      IgnoredFileBean[] beans = dlg.getSelectedIgnoredFiles();
      assert beans.length == 1;
      int selectedIndex = myList.getSelectedIndex();
      myModel.setElementAt(beans[0], selectedIndex);
    }
  }

  private void deleteItems() {
    for (Object o : myList.getSelectedValues()) {
      IgnoredFileBean bean = (IgnoredFileBean)o;
      if (bean.getType() == IgnoreSettingsType.UNDER_DIR) {
        myDirectoriesManuallyRemovedFromIgnored.add(bean.getPath());
      }
    }
    ListUtil.removeSelectedItems(myList);
  }

  @Override
  public void reset() {
    setItems(myChangeListManager.getFilesToIgnore());
    myDirectoriesManuallyRemovedFromIgnored.clear();
    myDirectoriesManuallyRemovedFromIgnored.addAll(myChangeListManager.getIgnoredFilesComponent().getDirectoriesManuallyRemovedFromIgnored());
  }

  @Override
  public void apply() {
    IgnoredFileBean[] toIgnore = getItems();
    myChangeListManager.setFilesToIgnore(toIgnore);
    for (IgnoredFileBean bean : toIgnore) {
      if (bean.getType() == IgnoreSettingsType.UNDER_DIR) {
        myDirectoriesManuallyRemovedFromIgnored.remove(bean.getPath());
      }
    }
    myChangeListManager.getIgnoredFilesComponent().setDirectoriesManuallyRemovedFromIgnored(myDirectoriesManuallyRemovedFromIgnored);
  }

  @Override
  public boolean isModified() {
    return !Comparing.equal(myChangeListManager.getFilesToIgnore(), getItems());
  }

  @Override
  public JComponent createComponent() {
    if (myPanel == null) {
      myPanel = ToolbarDecorator.createDecorator(myList)
              .setAddAction(new AnActionButtonRunnable() {
                @Override
                public void run(AnActionButton button) {
                  addItem();
                }
              }).setEditAction(new AnActionButtonRunnable() {
                @Override
                public void run(AnActionButton button) {
                  editItem();
                }
              }).setRemoveAction(new AnActionButtonRunnable() {
                @Override
                public void run(AnActionButton button) {
                  deleteItems();
                }
              }).disableUpDownActions().createPanel();
    }
    return myPanel;
  }

  @Override
  public void disposeUIResources() {
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Ignored Files";
  }

  @Override
  @Nonnull
  public String getId() {
    return "project.propVCSSupport.Ignored.Files";
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  private static class MyCellRenderer extends ColoredListCellRenderer {
    @Override
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (UIUtil.isUnderGTKLookAndFeel()) {
        final Color background = selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground();
        UIUtil.changeBackGround(this, background);
      }

      IgnoredFileBean bean = (IgnoredFileBean)value;
      final String path = bean.getPath();
      if (path != null) {
        if (path.endsWith("/")) {
          append(VcsBundle.message("ignored.configure.item.directory", path), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
        else {
          append(VcsBundle.message("ignored.configure.item.file", path), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }
      else if (bean.getMask() != null) {
        append(VcsBundle.message("ignored.configure.item.mask", bean.getMask()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
  }
}
