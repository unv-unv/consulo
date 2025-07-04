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
package consulo.ide.impl.idea.openapi.keymap.impl.ui;

import consulo.ide.impl.idea.openapi.actionSystem.ex.QuickList;
import consulo.ide.impl.idea.packageDependencies.ui.TreeExpansionMonitor;
import consulo.ide.impl.idea.util.ArrayUtil;
import consulo.ui.ex.awt.FormBuilder;
import consulo.application.AllIcons;
import consulo.application.ui.wm.IdeFocusManager;
import consulo.ui.ex.TreeExpander;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.FilterComponent;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.ui.ex.awt.event.DoubleClickListener;
import consulo.ui.ex.awt.tree.TreeUtil;
import consulo.ui.ex.awt.util.Alarm;
import consulo.ui.ex.keymap.KeyMapBundle;
import consulo.ui.ex.keymap.Keymap;
import consulo.ui.ex.keymap.localize.KeyMapLocalize;
import consulo.ui.ex.popup.JBPopup;
import consulo.ui.ex.popup.JBPopupFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

/**
 * @author max
 */
public class ChooseActionsDialog extends DialogWrapper {
  private final ActionsTree myActionsTree;
  private FilterComponent myFilterComponent;
  private TreeExpansionMonitor myTreeExpansionMonitor;
  private Keymap myKeymap;
  private QuickList[] myQuicklists;
  private JBPopup myPopup;

  public ChooseActionsDialog(Component parent, Keymap keymap, QuickList[] quicklists) {
    super(parent, true);
    myKeymap = keymap;
    myQuicklists = quicklists;

    myActionsTree = new ActionsTree(getDisposable());
    myActionsTree.reset(keymap, quicklists);
    myActionsTree.getTree().getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        doOKAction();
        return true;
      }
    }.installOn(myActionsTree.getTree());


    myTreeExpansionMonitor = TreeExpansionMonitor.install(myActionsTree.getTree());

    setTitle("Add Actions to Quick List");
    init();
  }

  @Override
  protected JComponent createNorthPanel() {
    return createToolbarPanel();
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());

    panel.add(myActionsTree.getComponent());
    panel.setPreferredSize(new Dimension(400, 500));

    return panel;
  }

  public String[] getTreeSelectedActionIds() {
    TreePath[] paths = myActionsTree.getTree().getSelectionPaths();
    if (paths == null) return ArrayUtil.EMPTY_STRING_ARRAY;

    ArrayList<String> actions = new ArrayList<String>();
    for (TreePath path : paths) {
      Object node = path.getLastPathComponent();
      if (node instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode defNode = (DefaultMutableTreeNode)node;
        Object userObject = defNode.getUserObject();
        if (userObject instanceof String) {
          actions.add((String)userObject);
        }
        else if (userObject instanceof QuickList) {
          actions.add(((QuickList)userObject).getActionId());
        }
      }
    }
    return ArrayUtil.toStringArray(actions);
  }

  private JPanel createToolbarPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    DefaultActionGroup group = new DefaultActionGroup();
    final JComponent toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent();
    final CommonActionsManager commonActionsManager = CommonActionsManager.getInstance();
    final TreeExpander treeExpander = new TreeExpander() {
      public void expandAll() {
        TreeUtil.expandAll(myActionsTree.getTree());
      }

      public boolean canExpand() {
        return true;
      }

      public void collapseAll() {
        TreeUtil.collapseAll(myActionsTree.getTree(), 0);
      }

      public boolean canCollapse() {
        return true;
      }
    };
    group.add(commonActionsManager.createExpandAllAction(treeExpander, myActionsTree.getTree()));
    group.add(commonActionsManager.createCollapseAllAction(treeExpander, myActionsTree.getTree()));

    panel.add(toolbar, BorderLayout.WEST);
    group = new DefaultActionGroup();
    final JComponent searchToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true).getComponent();
    final Alarm alarm = new Alarm();
    myFilterComponent = new FilterComponent("KEYMAP_IN_QUICK_LISTS", 5) {
      public void filter() {
        alarm.cancelAllRequests();
        alarm.addRequest(new Runnable() {
          public void run() {
            if (!myFilterComponent.isShowing()) return;
            if (!myTreeExpansionMonitor.isFreeze()) myTreeExpansionMonitor.freeze();
            final String filter = getFilter();
            myActionsTree.filter(filter, myQuicklists);
            final JTree tree = myActionsTree.getTree();
            TreeUtil.expandAll(tree);
            if (filter == null || filter.length() == 0) {
              TreeUtil.collapseAll(tree, 0);
              myTreeExpansionMonitor.restore();
            }
          }
        }, 300);
      }
    };
    myFilterComponent.reset();

    panel.add(myFilterComponent, BorderLayout.CENTER);

    group.add(new AnAction(KeyMapBundle.message("filter.shortcut.action.text"),
                           KeyMapBundle.message("filter.shortcut.action.text"),
                           AllIcons.Actions.ShortcutFilter) {
      public void actionPerformed(AnActionEvent e) {
        myFilterComponent.reset();
        if (myPopup == null || myPopup.getContent() == null) {
          myPopup = JBPopupFactory.getInstance().createComponentPopupBuilder(createFilteringPanel(), null)
            .setRequestFocus(true)
            .setTitle(KeyMapLocalize.filterSettingsPopupTitle())
            .setMovable(true)
            .createPopup();
        }
        myPopup.showUnderneathOf(searchToolbar);
      }
    });
    group.add(new AnAction(KeyMapBundle.message("filter.clear.action.text"),
                           KeyMapBundle.message("filter.clear.action.text"), AllIcons.Actions.GC) {
      public void actionPerformed(AnActionEvent e) {
        myActionsTree.filter(null, myQuicklists); //clear filtering
        TreeUtil.collapseAll(myActionsTree.getTree(), 0);
        myTreeExpansionMonitor.restore();
      }
    });

    panel.add(searchToolbar, BorderLayout.EAST);
    return panel;
  }

  private void filterTreeByShortcut(final ShortcutTextField firstShortcut,
                                    final JCheckBox enable2Shortcut,
                                    final ShortcutTextField secondShortcut) {
    final KeyStroke keyStroke = firstShortcut.getKeyStroke();
    if (keyStroke != null) {
      if (!myTreeExpansionMonitor.isFreeze()) myTreeExpansionMonitor.freeze();
      myActionsTree.filterTree(new KeyboardShortcut(keyStroke, enable2Shortcut.isSelected() ? secondShortcut.getKeyStroke() : null),
                               myQuicklists);
      final JTree tree = myActionsTree.getTree();
      TreeUtil.expandAll(tree);
    }
  }

  private JPanel createFilteringPanel() {
    myActionsTree.reset(myKeymap, myQuicklists);

    final JLabel firstLabel = new JLabel(KeyMapBundle.message("filter.first.stroke.input"));
    final JCheckBox enable2Shortcut = new JCheckBox(KeyMapBundle.message("filter.second.stroke.input"));
    final ShortcutTextField firstShortcut = new ShortcutTextField();
    firstShortcut.setColumns(10);
    final ShortcutTextField secondShortcut = new ShortcutTextField();
    secondShortcut.setColumns(10);

    enable2Shortcut.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        secondShortcut.setEnabled(enable2Shortcut.isSelected());
        if (enable2Shortcut.isSelected()) {
          secondShortcut.requestFocusInWindow();
        }
      }
    });

    firstShortcut.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        filterTreeByShortcut(firstShortcut, enable2Shortcut, secondShortcut);
      }
    });

    secondShortcut.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        filterTreeByShortcut(firstShortcut, enable2Shortcut, secondShortcut);
      }
    });

    JPanel filterComponent = FormBuilder.createFormBuilder()
      .addLabeledComponent(firstLabel, firstShortcut, true)
      .addComponent(enable2Shortcut)
      .setVerticalGap(0)
      .setIndent(5)
      .addComponent(secondShortcut)
      .getPanel();

    filterComponent.setBorder(new EmptyBorder(UIUtil.PANEL_SMALL_INSETS));

    enable2Shortcut.setSelected(false);
    secondShortcut.setEnabled(false);
    IdeFocusManager.getGlobalInstance().doForceFocusWhenFocusSettlesDown(firstShortcut);
    return filterComponent;
  }


  public void dispose() {
    super.dispose();
    if (myPopup != null && myPopup.isVisible()) {
      myPopup.cancel();
    }
    if (myFilterComponent != null) {
      myFilterComponent.dispose();
    }
  }
}
