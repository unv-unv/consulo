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
package consulo.ui.ex.awt;

import consulo.annotation.DeprecationInfo;
import consulo.application.ApplicationManager;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.table.TableView;
import consulo.ui.image.Image;
import consulo.util.collection.SmartList;
import consulo.util.lang.Pair;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * @author Konstantin Bulenkov
 * @see #createDecorator(javax.swing.JList)
 * @see #createDecorator(javax.swing.JTable)
 * @see #createDecorator(javax.swing.JTree)
 */
@SuppressWarnings("UnusedDeclaration")
public abstract class ToolbarDecorator implements CommonActionsPanel.ListenerFactory {
    protected Border myPanelBorder;
    protected Border myToolbarBorder;
    protected boolean myAddActionEnabled;
    protected boolean myEditActionEnabled;
    protected boolean myRemoveActionEnabled;
    protected boolean myUpActionEnabled;
    protected boolean myDownActionEnabled;
    protected Border myActionsPanelBorder;
    private Color myToolbarBackgroundColor;
    private final List<AnAction> myExtraActions = new SmartList<>();
    private ActionToolbarPosition myToolbarPosition;
    protected AnActionButtonRunnable myAddAction;
    protected AnActionButtonRunnable myEditAction;
    protected AnActionButtonRunnable myRemoveAction;
    protected AnActionButtonRunnable myUpAction;
    protected AnActionButtonRunnable myDownAction;
    private String myAddName;
    private String myEditName;
    private String myRemoveName;
    private String myMoveUpName;
    private String myMoveDownName;
    private AnActionButtonUpdater myAddActionUpdater = null;
    private AnActionButtonUpdater myRemoveActionUpdater = null;
    private AnActionButtonUpdater myEditActionUpdater = null;
    private AnActionButtonUpdater myMoveUpActionUpdater = null;
    private AnActionButtonUpdater myMoveDownActionUpdater = null;
    private Dimension myPreferredSize;
    private CommonActionsPanel myActionsPanel;
    private Comparator<AnAction> myButtonComparator;
    private boolean myAsUsualTopToolbar = false;
    private Image myAddIcon;
    private boolean myForcedDnD = false;

    protected abstract JComponent getComponent();

    protected abstract void updateButtons();

    protected void updateExtraElementActions(boolean someElementSelected) {
        for (AnAction action : myExtraActions) {
            if (action instanceof ElementActionButton) {
                ((ElementActionButton) action).setEnabled(someElementSelected);
            }
        }
    }

    public final CommonActionsPanel getActionsPanel() {
        return myActionsPanel;
    }

    public ToolbarDecorator initPosition() {
        setToolbarPosition(ActionToolbarPosition.TOP);
        return this;
    }

    public ToolbarDecorator setAsUsualTopToolbar() {
        myAsUsualTopToolbar = true;
        setToolbarPosition(ActionToolbarPosition.TOP);
        return this;
    }

    public static ToolbarDecorator createDecorator(@Nonnull JTable table) {
        return new TableToolbarDecorator(table, null).initPosition();
    }

    public static ToolbarDecorator createDecorator(@Nonnull JTree tree) {
        return createDecorator(tree, null);
    }

    private static ToolbarDecorator createDecorator(@Nonnull JTree tree, @Nullable ElementProducer<?> producer) {
        return new TreeToolbarDecorator(tree, producer).initPosition();
    }

    public static ToolbarDecorator createDecorator(@Nonnull JList list) {
        return new ListToolbarDecorator(list, null).initPosition();
    }

    public static ToolbarDecorator createDecorator(@Nonnull JList list, EditableModel editableModel) {
        return new ListToolbarDecorator(list, editableModel).initPosition();
    }

    public static <T> ToolbarDecorator createDecorator(@Nonnull TableView<T> table, @Nullable ElementProducer<T> producer) {
        return new TableToolbarDecorator(table, producer).initPosition();
    }

    public ToolbarDecorator disableAddAction() {
        myAddActionEnabled = false;
        return this;
    }

    public ToolbarDecorator disableRemoveAction() {
        myRemoveActionEnabled = false;
        return this;
    }

    public ToolbarDecorator disableUpAction() {
        myUpActionEnabled = false;
        return this;
    }

    public ToolbarDecorator disableUpDownActions() {
        myUpActionEnabled = false;
        myDownActionEnabled = false;
        return this;
    }

    public ToolbarDecorator disableDownAction() {
        myDownActionEnabled = false;
        return this;
    }

    public ToolbarDecorator setToolbarBackgroundColor(@Nonnull Color color) {
        myToolbarBackgroundColor = color;
        return this;
    }

    public ToolbarDecorator setPanelBorder(Border border) {
        myPanelBorder = border;
        return this;
    }

    public ToolbarDecorator setToolbarBorder(Border border) {
        myActionsPanelBorder = border;
        return this;
    }

    @Deprecated
    @DeprecationInfo("Use #setActionComparator(Comparator<AnAction>)")
    public ToolbarDecorator setButtonComparator(Comparator<AnActionButton> buttonComparator) {
        myButtonComparator = (o1, o2) -> {
            if (o1 instanceof AnActionButton && o2 instanceof AnActionButton) {
                return buttonComparator.compare((AnActionButton) o1, (AnActionButton) o2);
            }
            return 0;
        };
        return this;
    }

    public ToolbarDecorator setActionComparator(Comparator<AnAction> buttonComparator) {
        myButtonComparator = buttonComparator;
        return this;
    }

    public ToolbarDecorator setButtonComparator(String... actionNames) {
        final List<String> names = Arrays.asList(actionNames);
        myButtonComparator = (o1, o2) -> {
            final String t1 = o1.getTemplatePresentation().getText();
            final String t2 = o2.getTemplatePresentation().getText();
            if (t1 == null || t2 == null) {
                return 0;
            }

            final int ind1 = names.indexOf(t1);
            final int ind2 = names.indexOf(t2);
            if (ind1 == -1 && ind2 >= 0) {
                return 1;
            }
            if (ind2 == -1 && ind1 >= 0) {
                return -1;
            }
            return ind1 - ind2;
        };
        return this;
    }

    public ToolbarDecorator setLineBorder(int top, int left, int bottom, int right) {
        return setToolbarBorder(new CustomLineBorder(top, left, bottom, right));
    }

    public ToolbarDecorator addExtraActions(AnAction... actions) {
        for (AnAction action : actions) {
            if (action != null) {
                addExtraAction(action);
            }
        }
        return this;
    }

    public ToolbarDecorator addExtraAction(AnAction action) {
        myExtraActions.add(action);
        return this;
    }

    @Deprecated(forRemoval = true)
    @DeprecationInfo("Use #addExtraActions(AnAction)")
    public ToolbarDecorator addExtraActions(AnActionButton... actions) {
        for (AnActionButton action : actions) {
            if (action != null) {
                addExtraAction(action);
            }
        }
        return this;
    }

    @Deprecated(forRemoval = true)
    @DeprecationInfo("Use #addExtraActions(AnAction)")
    public ToolbarDecorator addExtraAction(AnActionButton action) {
        myExtraActions.add(action);
        return this;
    }

    public ToolbarDecorator setToolbarPosition(ActionToolbarPosition position) {
        myToolbarPosition = position;
        myActionsPanelBorder =
            new CustomLineBorder(
                myToolbarPosition == ActionToolbarPosition.BOTTOM ? 1 : 0,
                myToolbarPosition == ActionToolbarPosition.RIGHT ? 1 : 0,
                myToolbarPosition == ActionToolbarPosition.TOP ? 1 : 0,
                myToolbarPosition == ActionToolbarPosition.LEFT ? 1 : 0
            );
        return this;
    }

    public ToolbarDecorator setAddAction(AnActionButtonRunnable action) {
        myAddActionEnabled = action != null;
        myAddAction = action;
        return this;
    }

    public ToolbarDecorator setEditAction(AnActionButtonRunnable action) {
        myEditActionEnabled = action != null;
        myEditAction = action;
        return this;
    }

    public ToolbarDecorator setRemoveAction(AnActionButtonRunnable action) {
        myRemoveActionEnabled = action != null;
        myRemoveAction = action;
        return this;
    }

    public ToolbarDecorator setMoveUpAction(AnActionButtonRunnable action) {
        myUpActionEnabled = action != null;
        myUpAction = action;
        return this;
    }

    public ToolbarDecorator setMoveDownAction(AnActionButtonRunnable action) {
        myDownActionEnabled = action != null;
        myDownAction = action;
        return this;
    }

    public ToolbarDecorator setAddActionName(String name) {
        myAddName = name;
        return this;
    }

    public ToolbarDecorator setEditActionName(String name) {
        myEditName = name;
        return this;
    }

    public ToolbarDecorator setRemoveActionName(String name) {
        myRemoveName = name;
        return this;
    }

    public ToolbarDecorator setMoveUpActionName(String name) {
        myMoveUpName = name;
        return this;
    }

    public ToolbarDecorator setMoveDownActionName(String name) {
        myMoveDownName = name;
        return this;
    }

    public ToolbarDecorator setAddActionUpdater(AnActionButtonUpdater updater) {
        myAddActionUpdater = updater;
        return this;
    }

    public ToolbarDecorator setRemoveActionUpdater(AnActionButtonUpdater updater) {
        myRemoveActionUpdater = updater;
        return this;
    }

    public ToolbarDecorator setEditActionUpdater(AnActionButtonUpdater updater) {
        myEditActionUpdater = updater;
        return this;
    }

    public ToolbarDecorator setMoveUpActionUpdater(AnActionButtonUpdater updater) {
        myMoveUpActionUpdater = updater;
        return this;
    }

    public ToolbarDecorator setMoveDownActionUpdater(AnActionButtonUpdater updater) {
        myMoveDownActionUpdater = updater;
        return this;
    }

    public ToolbarDecorator setForcedDnD() {
        myForcedDnD = true;
        return this;

    }

    public ToolbarDecorator setActionGroup(@Nonnull ActionGroup actionGroup) {
        AnAction[] actions = actionGroup.getChildren(null);
        for (AnAction action : actions) {
            addExtraAction(action instanceof AnSeparator ? action : AnActionButton.fromAction(action));
        }
        return this;
    }

    public ToolbarDecorator setPreferredSize(Dimension size) {
        myPreferredSize = size;
        return this;
    }

    public ToolbarDecorator setVisibleRowCount(int rowCount) {
        return this;//do nothing by default
    }

    public ToolbarDecorator setAddIcon(Image addIcon) {
        myAddIcon = addIcon;
        return this;
    }

    /**
     * @return panel that contains wrapped component (with added scrollpane) and toolbar panel.
     */
    public JPanel createPanel() {
        final CommonActionsPanel.Buttons[] buttons = getButtons();
        final JComponent contextComponent = getComponent();
        myActionsPanel = new CommonActionsPanel(
            this,
            contextComponent,
            myToolbarPosition,
            myExtraActions.toArray(new AnAction[myExtraActions.size()]),
            myButtonComparator,
            myAddName,
            myRemoveName,
            myMoveUpName,
            myMoveDownName,
            myEditName,
            myAddIcon,
            buttons
        );
        final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(contextComponent, true);
        if (myPreferredSize != null) {
            scrollPane.setPreferredSize(myPreferredSize);
        }
        final JPanel panel = new JPanel(new BorderLayout()) {
            @Override
            public void addNotify() {
                super.addNotify();
                updateButtons();
            }
        };
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(myActionsPanel, getPlacement());
        installUpdaters();
        updateButtons();
        installDnD();
        panel.putClientProperty(ActionToolbar.ACTION_TOOLBAR_PROPERTY_KEY, myActionsPanel.getComponent(0));

        if (myToolbarBackgroundColor != null) {
            myActionsPanel.setBackground(myToolbarBackgroundColor);
        }

        Border mainBorder = myPanelBorder != null ? myPanelBorder : IdeBorderFactory.createBorder(SideBorder.ALL);
        if (myAsUsualTopToolbar) {
            scrollPane.setBorder(mainBorder);
        }
        else {
            myActionsPanel.setBorder(myActionsPanelBorder);
            panel.setBorder(mainBorder);
        }
        return panel;
    }

    private void installUpdaters() {
        if (myAddActionEnabled && myAddAction != null && myAddActionUpdater != null) {
            myActionsPanel.getAnActionButton(CommonActionsPanel.Buttons.ADD).addCustomUpdater(myAddActionUpdater);
        }
        if (myEditActionEnabled && myEditAction != null && myEditActionUpdater != null) {
            myActionsPanel.getAnActionButton(CommonActionsPanel.Buttons.EDIT).addCustomUpdater(myEditActionUpdater);
        }
        if (myRemoveActionEnabled && myRemoveAction != null && myRemoveActionUpdater != null) {
            myActionsPanel.getAnActionButton(CommonActionsPanel.Buttons.REMOVE).addCustomUpdater(myRemoveActionUpdater);
        }
        if (myUpActionEnabled && myUpAction != null && myMoveUpActionUpdater != null) {
            myActionsPanel.getAnActionButton(CommonActionsPanel.Buttons.UP).addCustomUpdater(myMoveUpActionUpdater);
        }
        if (myDownActionEnabled && myDownAction != null && myMoveDownActionUpdater != null) {
            myActionsPanel.getAnActionButton(CommonActionsPanel.Buttons.DOWN).addCustomUpdater(myMoveDownActionUpdater);
        }
    }

    protected void installDnD() {
        if ((myForcedDnD || (myUpAction != null && myUpActionEnabled && myDownAction != null && myDownActionEnabled))
            && !ApplicationManager.getApplication().isHeadlessEnvironment() && isModelEditable()) {
            installDnDSupport();
        }
    }

    protected abstract void installDnDSupport();

    protected abstract boolean isModelEditable();

    private Object getPlacement() {
        switch (myToolbarPosition) {
            case TOP:
                return BorderLayout.NORTH;
            case LEFT:
                return BorderLayout.WEST;
            case BOTTOM:
                return BorderLayout.SOUTH;
            case RIGHT:
                return BorderLayout.EAST;
        }
        return BorderLayout.SOUTH;
    }

    private CommonActionsPanel.Buttons[] getButtons() {
        final ArrayList<CommonActionsPanel.Buttons> buttons = new ArrayList<>();
        final HashMap<CommonActionsPanel.Buttons, Pair<Boolean, AnActionButtonRunnable>> map = new HashMap<>();
        map.put(CommonActionsPanel.Buttons.ADD, Pair.create(myAddActionEnabled, myAddAction));
        map.put(CommonActionsPanel.Buttons.REMOVE, Pair.create(myRemoveActionEnabled, myRemoveAction));
        map.put(CommonActionsPanel.Buttons.EDIT, Pair.create(myEditActionEnabled, myEditAction));
        map.put(CommonActionsPanel.Buttons.UP, Pair.create(myUpActionEnabled, myUpAction));
        map.put(CommonActionsPanel.Buttons.DOWN, Pair.create(myDownActionEnabled, myDownAction));

        for (CommonActionsPanel.Buttons button : CommonActionsPanel.Buttons.values()) {
            final Pair<Boolean, AnActionButtonRunnable> action = map.get(button);
            if (action != null && action.first != null && action.first && action.second != null) {
                buttons.add(button);
            }
        }

        return buttons.toArray(new CommonActionsPanel.Buttons[buttons.size()]);
    }

    @Override
    public CommonActionsPanel.Listener createListener(final CommonActionsPanel panel) {
        return new CommonActionsPanel.Listener() {
            @Override
            public void doAdd() {
                if (myAddAction != null) {
                    myAddAction.run(panel.getAnActionButton(CommonActionsPanel.Buttons.ADD));
                }
            }

            @Override
            public void doEdit() {
                if (myEditAction != null) {
                    myEditAction.run(panel.getAnActionButton(CommonActionsPanel.Buttons.EDIT));
                }
            }

            @Override
            public void doRemove() {
                if (myRemoveAction != null) {
                    myRemoveAction.run(panel.getAnActionButton(CommonActionsPanel.Buttons.REMOVE));
                }
            }

            @Override
            public void doUp() {
                if (myUpAction != null) {
                    myUpAction.run(panel.getAnActionButton(CommonActionsPanel.Buttons.UP));
                }
            }

            @Override
            public void doDown() {
                if (myDownAction != null) {
                    myDownAction.run(panel.getAnActionButton(CommonActionsPanel.Buttons.DOWN));
                }
            }
        };
    }

    public static AnActionButton findAddButton(@Nonnull JComponent container) {
        return findButton(container, CommonActionsPanel.Buttons.ADD);
    }

    public static AnActionButton findEditButton(@Nonnull JComponent container) {
        return findButton(container, CommonActionsPanel.Buttons.EDIT);
    }

    public static AnActionButton findRemoveButton(@Nonnull JComponent container) {
        return findButton(container, CommonActionsPanel.Buttons.REMOVE);
    }

    public static AnActionButton findUpButton(@Nonnull JComponent container) {
        return findButton(container, CommonActionsPanel.Buttons.UP);
    }

    public static AnActionButton findDownButton(@Nonnull JComponent container) {
        return findButton(container, CommonActionsPanel.Buttons.DOWN);
    }

    private static AnActionButton findButton(JComponent comp, CommonActionsPanel.Buttons type) {
        final CommonActionsPanel panel = UIUtil.findComponentOfType(comp, CommonActionsPanel.class);
        if (panel != null) {
            return panel.getAnActionButton(type);
        }
        //noinspection ConstantConditions
        return null;
    }

    /**
     * Marker interface, button will be disabled if no selected element
     */
    public abstract static class ElementActionButton extends AnActionButton {
        public ElementActionButton(String text, String description, @Nullable Image icon) {
            super(text, description, icon);
        }

        public ElementActionButton(String text, Image icon) {
            super(text, icon);
        }

        protected ElementActionButton(@Nonnull LocalizeValue text, @Nullable Image image) {
            super(text, LocalizeValue.empty(), image);
        }

        public ElementActionButton() {
        }

        public ElementActionButton(String text) {
            super(text);
        }
    }
}
