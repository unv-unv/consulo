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
package consulo.ide.impl.idea.tools;

import consulo.annotation.component.ExtensionImpl;
import consulo.dataContext.DataManager;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.checkin.CheckinProjectPanel;
import consulo.versionControlSystem.change.CommitContext;
import consulo.versionControlSystem.checkin.CheckinHandler;
import consulo.versionControlSystem.checkin.CheckinHandlerFactory;
import consulo.versionControlSystem.ui.RefreshableOnComponent;
import consulo.ui.ex.awt.CollectionComboBoxModel;
import consulo.ui.ex.awt.ComboboxWithBrowseButton;
import consulo.ui.ex.awt.ListCellRendererWrapper;
import consulo.ui.ex.awt.UIUtil;
import consulo.disposer.Disposable;

import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author lene
 * @since 2012-08-06
 */
@ExtensionImpl
public class ExternalToolsCheckinHandlerFactory extends CheckinHandlerFactory {

  public static final Object NONE_TOOL = new Object();

  @Nonnull
  @Override
  public CheckinHandler createHandler(final CheckinProjectPanel panel, CommitContext commitContext) {
    final ToolsProjectConfig config = ToolsProjectConfig.getInstance(panel.getProject());

    return new CheckinHandler() {

      @Override
      public RefreshableOnComponent getAfterCheckinConfigurationPanel(Disposable parentDisposable) {
        final JLabel label = new JLabel(ToolsBundle.message("tools.after.commit.description"));
        ComboboxWithBrowseButton listComponent = new ComboboxWithBrowseButton();
        final JComboBox comboBox = listComponent.getComboBox();
        comboBox.setModel(new CollectionComboBoxModel(getComboBoxElements(), null));
        comboBox.setRenderer(new ListCellRendererWrapper<Object>() {
          @Override
          public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
            if (value instanceof ToolsGroup) {
              setText(StringUtil.notNullize(((ToolsGroup)value).getName(), ToolsBundle.message("tools.unnamed.group")));
            }
            else if (value instanceof Tool) {
              setText("  " + StringUtil.notNullize(((Tool)value).getName()));
            }
            else {
              setText(ToolsBundle.message("tools.list.item.none"));
            }
          }
        });

        listComponent.getButton().addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            final Object item = comboBox.getSelectedItem();
            String id = null;
            if (item instanceof Tool) {
              id = ((Tool)item).getActionId();
            }
            final ToolSelectDialog dialog = new ToolSelectDialog(panel.getProject(), id, new ToolsPanel());
            dialog.show();
            if (!dialog.isOK()) {
              return;
            }

            comboBox.setModel(new CollectionComboBoxModel(getComboBoxElements(), dialog.getSelectedTool()));
          }
        });

        BorderLayout layout = new BorderLayout();
        layout.setVgap(3);
        final JPanel panel = new JPanel(layout);
        panel.add(label, BorderLayout.NORTH);
        panel.add(listComponent, BorderLayout.CENTER);
        listComponent.setBorder(BorderFactory.createEmptyBorder(0, 0, 3, 0));

        if (comboBox.getItemCount() == 0 || (comboBox.getItemCount() == 1 && comboBox.getItemAt(0) == NONE_TOOL)) {
          return null;
        }

        return new RefreshableOnComponent() {
          @Override
          public JComponent getComponent() {
            return panel;
          }

          @Override
          public void refresh() {
            String id = config.getAfterCommitToolsId();
            if (id == null) {
              comboBox.setSelectedIndex(-1);
            }
            else {
              for (int i = 0; i < comboBox.getItemCount(); i++) {
                final Object itemAt = comboBox.getItemAt(i);
                if (itemAt instanceof Tool && id.equals(((Tool)itemAt).getActionId())) {
                  comboBox.setSelectedIndex(i);
                  return;
                }
              }
            }
          }

          @Override
          public void saveState() {
            Object item = comboBox.getSelectedItem();
            config.setAfterCommitToolId(item instanceof Tool ? ((Tool)item).getActionId() : null);
          }

          @Override
          public void restoreState() {
            refresh();
          }
        };
      }

      @Override
      public void checkinSuccessful() {
        final String id = config.getAfterCommitToolsId();
        if (id == null) {
          return;
        }
        DataManager.getInstance().getDataContextFromFocus().doWhenDone(context -> {
          UIUtil.invokeAndWaitIfNeeded(new Runnable() {

            @Override
            public void run() {
              ToolAction.runTool(id, context);
            }
          });
        });
      }
    };
  }

  private static List<Object> getComboBoxElements() {
    List<Object> result = new ArrayList<Object>();
    ToolManager manager = ToolManager.getInstance();
    result.add(NONE_TOOL);//for empty selection
    for (ToolsGroup group : manager.getGroups()) {
      result.add(group);
      Collections.addAll(result, manager.getTools(group.getName()));
    }

    return result;
  }
}
