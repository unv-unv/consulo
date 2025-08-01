/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package consulo.desktop.awt.internal.diff.dir;

import consulo.application.progress.ProgressIndicator;
import consulo.application.ui.wm.IdeFocusManager;
import consulo.component.ProcessCanceledException;
import consulo.dataContext.DataManager;
import consulo.dataContext.DataProvider;
import consulo.desktop.awt.internal.diff.CacheDiffRequestProcessor;
import consulo.diff.DiffContentFactory;
import consulo.diff.DiffDataKeys;
import consulo.diff.DiffPlaces;
import consulo.diff.PrevNextDifferenceIterable;
import consulo.diff.chain.DiffRequestProducerException;
import consulo.diff.content.DiffContent;
import consulo.diff.dir.DiffElement;
import consulo.diff.impl.internal.dir.DirDiffElementImpl;
import consulo.diff.internal.DiffUserDataKeysEx.ScrollToPolicy;
import consulo.diff.request.DiffRequest;
import consulo.diff.request.SimpleDiffRequest;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.ide.impl.idea.ide.util.PropertiesComponent;
import consulo.desktop.awt.internal.diff.dir.action.DirDiffToolbarActions;
import consulo.desktop.awt.internal.diff.dir.action.RefreshDirDiffAction;
import consulo.ide.impl.idea.openapi.keymap.KeymapUtil;
import consulo.ui.ex.awt.JBLoadingPanel;
import consulo.ui.ex.awt.event.JBLoadingPanelListener;
import consulo.logging.Logger;
import consulo.navigation.Navigatable;
import consulo.project.Project;
import consulo.project.ui.internal.ProjectIdeFocusManager;
import consulo.ui.ex.RelativePoint;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.speedSearch.TableSpeedSearch;
import consulo.ui.ex.awt.table.JBTable;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.popup.Balloon;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import static consulo.ide.impl.idea.util.ArrayUtil.toObjectArray;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings({"unchecked"})
public class DirDiffPanel implements Disposable, DataProvider {
    private static final Logger LOG = Logger.getInstance(DirDiffPanel.class);

    public static final String DIVIDER_PROPERTY = "dir.diff.panel.divider.location";
    private static final int DIVIDER_PROPERTY_DEFAULT_VALUE = 200;
    private JPanel myDiffPanel;
    private JBTable myTable;
    private JPanel myComponent;
    private JSplitPane mySplitPanel;
    private TextFieldWithBrowseButton mySourceDirField;
    private TextFieldWithBrowseButton myTargetDirField;
    private JBLabel myTargetDirLabel;
    private JBLabel mySourceDirLabel;
    private JPanel myToolBarPanel;
    private JPanel myRootPanel;
    private JPanel myFilterPanel;
    private JBLabel myFilterLabel;
    private JPanel myFilesPanel;
    private JPanel myHeaderPanel;
    private FilterComponent myFilter;
    private final DirDiffTableModel myModel;
    private final DirDiffWindow myDiffWindow;
    private final MyDiffRequestProcessor myDiffRequestProcessor;
    private final PrevNextDifferenceIterable myPrevNextDifferenceIterable;
    private String oldFilter;
    public static final Key<DirDiffTableModel> DIR_DIFF_MODEL = Key.create("DIR_DIFF_MODEL");
    public static final Key<JTable> DIR_DIFF_TABLE = Key.create("DIR_DIFF_TABLE");

    public DirDiffPanel(DirDiffTableModel model, DirDiffWindow wnd) {
        myModel = model;
        myDiffWindow = wnd;
        mySourceDirField.setText(model.getSourceDir().getPath());
        myTargetDirField.setText(model.getTargetDir().getPath());
        mySourceDirField.setBorder(JBUI.Borders.emptyRight(8));
        myTargetDirField.setBorder(JBUI.Borders.emptyRight(12));
        mySourceDirLabel.setIcon(TargetAWT.to(model.getSourceDir().getIcon()));
        myTargetDirLabel.setIcon(TargetAWT.to(model.getTargetDir().getIcon()));
        myTargetDirLabel.setBorder(JBUI.Borders.emptyLeft(8));
        myModel.setTable(myTable);
        myModel.setPanel(this);
        Disposer.register(this, myModel);
        myTable.setModel(myModel);
        new TableSpeedSearch(myTable);

        final DirDiffTableCellRenderer renderer = new DirDiffTableCellRenderer();
        myTable.setExpandableItemsEnabled(false);
        myTable.setDefaultRenderer(Object.class, renderer);
        myTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        final Project project = myModel.getProject();
        myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                final int lastIndex = e.getLastIndex();
                final int firstIndex = e.getFirstIndex();
                final DirDiffElementImpl last = myModel.getElementAt(lastIndex);
                final DirDiffElementImpl first = myModel.getElementAt(firstIndex);
                if (last == null || first == null) {
                    update(false);
                    return;
                }
                if (last.isSeparator()) {
                    final int ind = lastIndex + ((lastIndex < firstIndex) ? 1 : -1);
                    myTable.getSelectionModel().addSelectionInterval(ind, ind);
                }
                else if (first.isSeparator()) {
                    final int ind = firstIndex + ((firstIndex < lastIndex) ? 1 : -1);
                    myTable.getSelectionModel().addSelectionInterval(ind, ind);
                }
                else {
                    update(false);
                }
                myDiffWindow.setTitle(myModel.getTitle());
            }
        });
        if (model.isOperationsEnabled()) {
            new AnAction("Change diff operation") {
                @Override
                public void actionPerformed(AnActionEvent e) {
                    changeOperationForSelection();
                }
            }.registerCustomShortcutSet(CustomShortcutSet.fromString("SPACE"), myTable);
            new ClickListener() {
                @Override
                public boolean onClick(@Nonnull MouseEvent e, int clickCount) {
                    if (e.getButton() == MouseEvent.BUTTON3) {
                        return false;
                    }
                    if (myTable.getRowCount() > 0) {
                        final int row = myTable.rowAtPoint(e.getPoint());
                        final int col = myTable.columnAtPoint(e.getPoint());

                        if (row != -1 && col == ((myTable.getColumnCount() - 1) / 2)) {
                            changeOperationForSelection();
                        }
                    }
                    return true;
                }
            }.installOn(myTable);
        }
        myTable.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                final int keyCode = e.getKeyCode();

                int row;
                if (keyCode == KeyEvent.VK_DOWN) {
                    row = getNextRow();
                }
                else if (keyCode == KeyEvent.VK_UP) {
                    row = getPrevRow();
                }
                else {
                    row = -1;
                }

                if (row != -1) {
                    selectRow(row, e.isShiftDown());
                    e.consume();
                }
            }
        });
        final TableColumnModel columnModel = myTable.getColumnModel();
        final TableColumn operationColumn = columnModel.getColumn((columnModel.getColumnCount() - 1) / 2);
        operationColumn.setMaxWidth(JBUI.scale(25));
        operationColumn.setMinWidth(JBUI.scale(25));
        for (int i = 0; i < columnModel.getColumnCount(); i++) {
            final String name = myModel.getColumnName(i);
            final TableColumn column = columnModel.getColumn(i);
            if (DirDiffTableModel.COLUMN_DATE.equals(name)) {
                column.setMaxWidth(JBUI.scale(90));
                column.setMinWidth(JBUI.scale(90));
            }
            else if (DirDiffTableModel.COLUMN_SIZE.equals(name)) {
                column.setMaxWidth(JBUI.scale(120));
                column.setMinWidth(JBUI.scale(120));
            }
        }
        final DirDiffToolbarActions actions = new DirDiffToolbarActions(myModel, myDiffPanel);
        final ActionManager actionManager = ActionManager.getInstance();
        final ActionToolbar toolbar = actionManager.createActionToolbar("DirDiff", actions, true);
        registerCustomShortcuts(actions, myTable);
        myToolBarPanel.add(toolbar.getComponent(), BorderLayout.CENTER);
        final JBLabel label = new JBLabel("Use Space button or mouse click to change operation for the selected elements." +
            " Enter to perform.", SwingConstants.CENTER);
        label.setForeground(UIUtil.getInactiveTextColor());
        UIUtil.applyStyle(UIUtil.ComponentStyle.MINI, label);
        DataManager.registerDataProvider(myFilesPanel, this);
        myTable.addMouseListener(new PopupHandler() {
            @Override
            public void invokePopup(Component comp, int x, int y) {
                final JPopupMenu popupMenu =
                    actionManager.createActionPopupMenu("DirDiffPanel", (ActionGroup) actionManager.getAction("DirDiffMenu"))
                        .getComponent();
                popupMenu.show(comp, x, y);
            }
        });
        myFilesPanel.add(label, BorderLayout.SOUTH);
        final JBLoadingPanel loadingPanel = new JBLoadingPanel(new BorderLayout(), wnd.getDisposable());
        loadingPanel.addListener(new JBLoadingPanelListener.Adapter() {
            boolean showHelp = true;

            @Override
            public void onLoadingFinish() {
                if (showHelp && myModel.isOperationsEnabled() && myModel.getRowCount() > 0) {
                    final long count = PropertiesComponent.getInstance().getOrInitLong("dir.diff.space.button.info", 0);
                    if (count < 3) {
                        JBPopupFactory.getInstance().createBalloonBuilder(new JLabel(" Use Space button to change operation"))
                            .setFadeoutTime(5000)
                            .setContentInsets(JBUI.insets(15))
                            .createBalloon().show(new RelativePoint(myTable, new Point(myTable.getWidth() / 2, 0)), Balloon.Position.above);
                        PropertiesComponent.getInstance().setValue("dir.diff.space.button.info", String.valueOf(count + 1));
                    }
                }
                showHelp = false;
            }
        });
        loadingPanel.add(myComponent, BorderLayout.CENTER);
        myTable.putClientProperty(myModel.DECORATOR, loadingPanel);
        myTable.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                myTable.removeComponentListener(this);
                myModel.reloadModel(false);
            }
        });
        myRootPanel.removeAll();
        myRootPanel.add(loadingPanel, BorderLayout.CENTER);
        myFilter = new FilterComponent("dir.diff.filter", 15, false) {
            @Override
            public void filter() {
                fireFilterUpdated();
            }

            @Override
            protected void onEscape(KeyEvent e) {
                e.consume();
                focusTable();
            }

            @Override
            protected JComponent getPopupLocationComponent() {
                return UIUtil.findComponentOfType(super.getPopupLocationComponent(), JTextComponent.class);
            }
        };

        myModel.addModelListener(new DirDiffModelListener() {
            @Override
            public void updateStarted() {
                myFilter.setEnabled(false);
            }

            @Override
            public void updateFinished() {
                myFilter.setEnabled(true);
            }
        });
        myFilter.getTextEditor().setColumns(10);
        myFilter.setFilter(myModel.getSettings().getFilter());
        //oldFilter = myFilter.getText();
        oldFilter = myFilter.getFilter();
        myFilterPanel.add(myFilter, BorderLayout.CENTER);
        myFilterLabel.setLabelFor(myFilter);
        final Callable<DiffElement> srcChooser = myModel.getSourceDir().getElementChooser(project);
        final Callable<DiffElement> trgChooser = myModel.getTargetDir().getElementChooser(project);
        mySourceDirField.setEditable(false);
        myTargetDirField.setEditable(false);

        if (srcChooser != null && myModel.getSettings().enableChoosers) {
            mySourceDirField.setButtonEnabled(true);
            mySourceDirField.addActionListener(new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        final Callable<DiffElement> chooser = myModel.getSourceDir().getElementChooser(project);
                        if (chooser == null) {
                            return;
                        }
                        final DiffElement newElement = chooser.call();
                        if (newElement != null) {
                            if (!StringUtil.equals(mySourceDirField.getText(), newElement.getPath())) {
                                myModel.setSourceDir(newElement);
                                mySourceDirField.setText(newElement.getPath());
                                String shortcutsText = KeymapUtil.getShortcutsText(RefreshDirDiffAction.REFRESH_SHORTCUT.getShortcuts());
                                myModel.clearWithMessage("Source or Target has been changed." +
                                    " Please run Refresh (" + shortcutsText + ")");
                            }
                        }
                    }
                    catch (Exception ignored) {
                    }
                }
            });
        }
        else {
            Dimension preferredSize = mySourceDirField.getPreferredSize();
            mySourceDirField.setButtonEnabled(false);
            mySourceDirField.getButton().setVisible(false);
            mySourceDirField.setPreferredSize(preferredSize);
        }

        if (trgChooser != null && myModel.getSettings().enableChoosers) {
            myTargetDirField.setButtonEnabled(true);
            myTargetDirField.addActionListener(new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        final Callable<DiffElement> chooser = myModel.getTargetDir().getElementChooser(project);
                        if (chooser == null) {
                            return;
                        }
                        final DiffElement newElement = chooser.call();
                        if (newElement != null) {
                            myModel.setTargetDir(newElement);
                            myTargetDirField.setText(newElement.getPath());
                        }
                    }
                    catch (Exception ignored) {
                    }
                }
            });
        }
        else {
            Dimension preferredSize = myTargetDirField.getPreferredSize();
            myTargetDirField.setButtonEnabled(false);
            myTargetDirField.getButton().setVisible(false);
            myTargetDirField.setPreferredSize(preferredSize);
        }

        myDiffRequestProcessor = new MyDiffRequestProcessor(project);
        Disposer.register(this, myDiffRequestProcessor);
        myDiffPanel.add(myDiffRequestProcessor.getComponent(), BorderLayout.CENTER);

        myPrevNextDifferenceIterable = new MyPrevNextDifferenceIterable();
    }

    private int getNextRow() {
        if (myTable.getSelectedRows().length == 0) {
            return -1;
        }
        int rowCount = myTable.getRowCount();
        int row = myTable.getSelectionModel().getLeadSelectionIndex();

        while (true) {
            if (row >= rowCount) {
                return -1;
            }
            row++;
            DirDiffElementImpl element = myModel.getElementAt(row);
            if (element == null) {
                return -1;
            }
            if (!element.isSeparator()) {
                break;
            }
        }

        return row;
    }

    private int getPrevRow() {
        if (myTable.getSelectedRows().length == 0) {
            return -1;
        }
        int row = myTable.getSelectionModel().getLeadSelectionIndex();

        while (true) {
            if (row <= 0) {
                return -1;
            }
            row--;
            DirDiffElementImpl element = myModel.getElementAt(row);
            if (element == null) {
                return -1;
            }
            if (!element.isSeparator()) {
                break;
            }
        }

        return row;
    }

    private void selectRow(int row, boolean extend) {
        if (row == -1) {
            return;
        }
        DirDiffElementImpl element = myModel.getElementAt(row);
        if (element == null || element.isSeparator()) {
            return;
        }
        myTable.changeSelection(row, (myModel.getColumnCount() - 1) / 2, false, extend);
    }

    public AnAction[] getActions() {
        return new DirDiffToolbarActions(myModel, myDiffPanel).getChildren(null);
    }

    public JComponent extractFilterPanel() {
        myHeaderPanel.setVisible(false);
        return myFilterPanel;
    }

    private void changeOperationForSelection() {
        for (int row : myTable.getSelectedRows()) {
            if (row != -1) {
                final DirDiffElementImpl element = myModel.getElementAt(row);
                if (element != null) {
                    element.setNextOperation();
                    myModel.fireTableRowsUpdated(row, row);
                }
            }
        }
    }

    public void update(boolean force) {
        myDiffRequestProcessor.updateRequest(force);
    }

    private void registerCustomShortcuts(DirDiffToolbarActions actions, JComponent component) {
        for (AnAction action : actions.getChildren(null)) {
            if (action instanceof ShortcutProvider shortcutProvider) {
                final ShortcutSet shortcut = shortcutProvider.getShortcut();
                if (shortcut != null) {
                    action.registerCustomShortcutSet(shortcut, component);
                }
            }
        }
    }

    public void focusTable() {
        final Project project = myModel.getProject();
        final IdeFocusManager focusManager = project == null || project.isDefault() ?
            IdeFocusManager.getGlobalInstance() : ProjectIdeFocusManager.getInstance(project);
        focusManager.requestFocus(myTable, true);
    }

    public String getFilter() {
        return myFilter.getFilter();
    }

    private void fireFilterUpdated() {
        final String newFilter = myFilter.getFilter();
        if (!StringUtil.equals(oldFilter, newFilter)) {
            oldFilter = newFilter;
            myModel.getSettings().setFilter(newFilter);
            myModel.applySettings();
        }
    }

    public JComponent getPanel() {
        return myRootPanel;
    }

    public JBTable getTable() {
        return myTable;
    }

    public void dispose() {
        myModel.stopUpdating();
        PropertiesComponent.getInstance().setValue(DIVIDER_PROPERTY, mySplitPanel.getDividerLocation(), DIVIDER_PROPERTY_DEFAULT_VALUE);
    }

    private void createUIComponents() {
        mySourceDirField = new TextFieldWithBrowseButton(null, this);
        myTargetDirField = new TextFieldWithBrowseButton(null, this);

        final AtomicBoolean callUpdate = new AtomicBoolean(true);
        myRootPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintChildren(Graphics g) {
                super.paintChildren(g);
                if (callUpdate.get()) {
                    callUpdate.set(false);
                    myModel.reloadModel(false);
                }
            }
        };
    }

    public void setupSplitter() {
        int value = PropertiesComponent.getInstance().getInt(DIVIDER_PROPERTY, DIVIDER_PROPERTY_DEFAULT_VALUE);
        mySplitPanel.setDividerLocation(Integer.valueOf(value));
    }

    @Override
    public Object getData(@Nonnull @NonNls Key<?> dataId) {
        if (Project.KEY == dataId) {
            return myModel.getProject();
        }
        else if (DIR_DIFF_MODEL == dataId) {
            return myModel;
        }
        else if (DIR_DIFF_TABLE == dataId) {
            return myTable;
        }
        else if (DiffDataKeys.NAVIGATABLE_ARRAY == dataId) {
            return getNavigatableArray();
        }
        else if (DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE == dataId) {
            return myPrevNextDifferenceIterable;
        }
        return null;
    }

    @Nullable
    private Navigatable[] getNavigatableArray() {
        Project project = myModel.getProject();
        List<DirDiffElementImpl> elements = myModel.getSelectedElements();
        List<Navigatable> navigatables = new ArrayList<>();
        for (DirDiffElementImpl element : elements) {
            DiffElement source = element.getSource();
            DiffElement target = element.getTarget();
            Navigatable navigatable1 = source != null ? source.getNavigatable(project) : null;
            Navigatable navigatable2 = target != null ? target.getNavigatable(project) : null;
            if (navigatable1 != null) {
                navigatables.add(navigatable1);
            }
            if (navigatable2 != null) {
                navigatables.add(navigatable2);
            }
        }
        return toObjectArray(navigatables, Navigatable.class);
    }

    private class MyPrevNextDifferenceIterable implements PrevNextDifferenceIterable {
        @Override
        public boolean canGoPrev() {
            return getPrevRow() != -1;
        }

        @Override
        public boolean canGoNext() {
            return getNextRow() != -1;
        }

        @Override
        public void goPrev() {
            selectRow(getPrevRow(), false);
        }

        @Override
        public void goNext() {
            selectRow(getNextRow(), false);
        }
    }

    private class MyDiffRequestProcessor extends CacheDiffRequestProcessor<ElementWrapper> {
        public MyDiffRequestProcessor(@Nullable Project project) {
            super(project, DiffPlaces.DIR_DIFF);
        }

        @Nullable
        @Override
        protected String getRequestName(@Nonnull ElementWrapper element) {
            return null;
        }

        @Override
        protected ElementWrapper getCurrentRequestProvider() {
            DirDiffElementImpl element = myModel.getElementAt(myTable.getSelectedRow());
            return element != null ? new ElementWrapper(element) : null;
        }

        @Nonnull
        @Override
        protected DiffRequest loadRequest(@Nonnull ElementWrapper element, @Nonnull ProgressIndicator indicator)
            throws ProcessCanceledException, DiffRequestProducerException {
            final Project project = myModel.getProject();
            DiffElement sourceElement = element.sourceElement;
            DiffElement targetElement = element.targetElement;

            DiffContent sourceContent = sourceElement != null ? sourceElement.createDiffContent(project, indicator) :
                DiffContentFactory.getInstance().createEmpty();
            DiffContent targetContent = targetElement != null ? targetElement.createDiffContent(project, indicator) :
                DiffContentFactory.getInstance().createEmpty();

            return new SimpleDiffRequest(null, sourceContent, targetContent, null, null);
        }

        //
        // Navigation
        //

        @Override
        protected boolean hasNextChange() {
            return getNextRow() != -1;
        }

        @Override
        protected boolean hasPrevChange() {
            return getPrevRow() != -1;
        }

        @Override
        protected void goToNextChange(boolean fromDifferences) {
            selectRow(getNextRow(), false);
            updateRequest(false, fromDifferences ? ScrollToPolicy.FIRST_CHANGE : null);
        }

        @Override
        protected void goToPrevChange(boolean fromDifferences) {
            selectRow(getPrevRow(), false);
            updateRequest(false, fromDifferences ? ScrollToPolicy.LAST_CHANGE : null);
        }

        @Override
        protected boolean isNavigationEnabled() {
            return myModel.getRowCount() > 0;
        }
    }

    private static class ElementWrapper {
        @Nullable
        public final DiffElement sourceElement;
        @Nullable
        public final DiffElement targetElement;

        public ElementWrapper(@Nonnull DirDiffElementImpl element) {
            sourceElement = element.getSource();
            targetElement = element.getTarget();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ElementWrapper wrapper = (ElementWrapper) o;

            if (sourceElement != null ? !sourceElement.equals(wrapper.sourceElement) : wrapper.sourceElement != null) {
                return false;
            }
            if (targetElement != null ? !targetElement.equals(wrapper.targetElement) : wrapper.targetElement != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = sourceElement != null ? sourceElement.hashCode() : 0;
            result = 31 * result + (targetElement != null ? targetElement.hashCode() : 0);
            return result;
        }
    }
}
