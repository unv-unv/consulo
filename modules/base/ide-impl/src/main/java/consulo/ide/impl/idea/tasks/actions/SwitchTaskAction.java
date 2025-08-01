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
package consulo.ide.impl.idea.tasks.actions;

import consulo.annotation.component.ActionImpl;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.ide.impl.idea.openapi.ui.playback.commands.ActionCommand;
import consulo.ide.impl.idea.ui.popup.list.ListPopupImpl;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.task.ChangeListInfo;
import consulo.task.LocalTask;
import consulo.task.TaskManager;
import consulo.task.impl.internal.TaskManagerImpl;
import consulo.task.impl.internal.action.BaseTaskAction;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIExAWTDataKey;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.popup.*;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ref.SimpleReference;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.versionControlSystem.change.LocalChangeList;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
@ActionImpl(id = "tasks.switch")
public class SwitchTaskAction extends BaseTaskAction {
    public SwitchTaskAction() {
      super(LocalizeValue.localizeTODO("_Switch Task..."));
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(@Nonnull AnActionEvent e) {
        Project project = e.getRequiredData(Project.KEY);
        ListPopupImpl popup = createPopup(e.getDataContext(), null, true);
        popup.showCenteredInCurrentWindow(project);
    }

    public static ListPopupImpl createPopup(DataContext dataContext, @Nullable Runnable onDispose, boolean withTitle) {
        final Project project = dataContext.getData(Project.KEY);
        final SimpleReference<Boolean> shiftPressed = SimpleReference.create(false);
        final SimpleReference<JComponent> componentRef = SimpleReference.create();
        List<TaskListItem> items = project == null ? Collections.<TaskListItem>emptyList() :
            createPopupActionGroup(project, shiftPressed, dataContext.getData(UIExAWTDataKey.CONTEXT_COMPONENT));
        final String title = withTitle ? "Switch to Task" : null;
        ListPopupStep<TaskListItem> step = new MultiSelectionListPopupStep<>(title, items) {
            @Override
            public PopupStep<?> onChosen(List<TaskListItem> selectedValues, boolean finalChoice) {
                if (finalChoice) {
                    selectedValues.get(0).select();
                    return FINAL_CHOICE;
                }
                ActionGroup group = createActionsStep(selectedValues, project, shiftPressed);
                return JBPopupFactory.getInstance().createActionsStep(
                    group,
                    DataManager.getInstance().getDataContext(componentRef.get()),
                    null,
                    false,
                    false,
                    null,
                    null,
                    true,
                    0,
                    false
                );
            }

            @Override
            public Image getIconFor(TaskListItem aValue) {
                return aValue.getIcon();
            }

            @Nonnull
            @Override
            public String getTextFor(TaskListItem value) {
                return value.getText();
            }

            @Nullable
            @Override
            public ListSeparator getSeparatorAbove(TaskListItem value) {
                return value.getSeparator() == null ? null : new ListSeparator(value.getSeparator());
            }

            @Override
            public boolean hasSubstep(List<TaskListItem> selectedValues) {
                return selectedValues.size() > 1 || selectedValues.get(0).getTask() != null;
            }
        };

        final ListPopupImpl popup = (ListPopupImpl) JBPopupFactory.getInstance().createListPopup(step);

        componentRef.set(popup.getComponent());
        if (items.size() <= 2) {
            return popup;
        }

        popup.setAdText("Press SHIFT to merge with current context");

        popup.registerAction(
            "shiftPressed",
            KeyStroke.getKeyStroke("shift pressed SHIFT"),
            new AbstractAction() {
                @Override
                @RequiredUIAccess
                public void actionPerformed(@Nonnull ActionEvent e) {
                    shiftPressed.set(true);
                    popup.setCaption("Merge with Current Context");
                }
            }
        );
        popup.registerAction(
            "shiftReleased",
            KeyStroke.getKeyStroke("released SHIFT"),
            new AbstractAction() {
                @Override
                @RequiredUIAccess
                public void actionPerformed(@Nonnull ActionEvent e) {
                    shiftPressed.set(false);
                    popup.setCaption("Switch to Task");
                }
            }
        );
        popup.registerAction(
            "invoke",
            KeyStroke.getKeyStroke("shift ENTER"),
            new AbstractAction() {
                @Override
                @RequiredUIAccess
                public void actionPerformed(@Nonnull ActionEvent e) {
                    popup.handleSelect(true);
                }
            }
        );
        return popup;
    }

    private static ActionGroup createActionsStep(
        final List<TaskListItem> tasks,
        final Project project,
        final SimpleReference<Boolean> shiftPressed
    ) {
        ActionGroup.Builder group = ActionGroup.newImmutableBuilder();
        final TaskManager manager = TaskManager.getManager(project);
        final LocalTask task = tasks.get(0).getTask();
        if (tasks.size() == 1 && task != null) {
            group.add(new AnAction(LocalizeValue.localizeTODO("&Switch to")) {
                @Override
                @RequiredUIAccess
                public void actionPerformed(@Nonnull AnActionEvent e) {
                    manager.activateTask(task, !shiftPressed.get());
                }
            });
        }
        AnAction remove = new AnAction(LocalizeValue.localizeTODO("&Remove")) {
            @Override
            @RequiredUIAccess
            public void actionPerformed(@Nonnull AnActionEvent e) {
                for (TaskListItem item : tasks) {
                    LocalTask itemTask = item.getTask();
                    if (itemTask != null) {
                        removeTask(project, itemTask, manager);
                    }
                }
            }
        };
        group.add(remove);

        return group.build();
    }

    @Nonnull
    private static List<TaskListItem> createPopupActionGroup(
        @Nonnull Project project,
        final SimpleReference<Boolean> shiftPressed,
        final Component contextComponent
    ) {
        List<TaskListItem> group = new ArrayList<>();

        AnAction action = ActionManager.getInstance().getAction(GotoTaskAction.ID);
        assert action instanceof GotoTaskAction;
        final GotoTaskAction gotoTaskAction = (GotoTaskAction) action;
        group.add(new TaskListItem(gotoTaskAction.getTemplatePresentation().getText(), gotoTaskAction.getTemplatePresentation().getIcon()) {
            @Override
            void select() {
                ActionManager.getInstance().tryToExecute(gotoTaskAction, ActionCommand.getInputEvent(GotoTaskAction.ID),
                    contextComponent, ActionPlaces.UNKNOWN, false
                );
            }
        });

        final TaskManager manager = TaskManager.getManager(project);
        LocalTask activeTask = manager.getActiveTask();
        List<LocalTask> localTasks = manager.getLocalTasks();
        Collections.sort(localTasks, TaskManagerImpl.TASK_UPDATE_COMPARATOR);
        ArrayList<LocalTask> temp = new ArrayList<>();
        for (final LocalTask task : localTasks) {
            if (task == activeTask) {
                continue;
            }
            if (manager.isLocallyClosed(task)) {
                temp.add(task);
                continue;
            }

            group.add(new TaskListItem(task, group.size() == 1 ? "" : null, false) {
                @Override
                void select() {
                    manager.activateTask(task, !shiftPressed.get());
                }
            });
        }
        if (!temp.isEmpty()) {
            for (int i = 0, tempSize = temp.size(); i < Math.min(tempSize, 15); i++) {
                final LocalTask task = temp.get(i);

                group.add(new TaskListItem(task, i == 0 ? "Recently Closed Tasks" : null, true) {
                    @Override
                    void select() {
                        manager.activateTask(task, !shiftPressed.get());
                    }
                });
            }
        }
        return group;
    }

    @RequiredUIAccess
    public static void removeTask(@Nonnull Project project, LocalTask task, TaskManager manager) {
        if (task.isDefault()) {
            Messages.showInfoMessage(project, "Default task cannot be removed", "Cannot Remove");
        }
        else {

            List<ChangeListInfo> infos = task.getChangeLists();
            List<LocalChangeList> lists = ContainerUtil.mapNotNull(
                infos,
                changeListInfo -> {
                    LocalChangeList changeList = ChangeListManager.getInstance(project).getChangeList(changeListInfo.id);
                    return changeList != null && !changeList.isDefault() ? changeList : null;
                }
            );

            boolean removeIt = true;
            l:
            for (LocalChangeList list : lists) {
                if (!list.getChanges().isEmpty()) {
                    int result = Messages.showYesNoCancelDialog(
                        project,
                        "Changelist associated with '" + task.getSummary() + "' is not empty.\n" +
                            "Do you want to remove it and move the changes to the active changelist?",
                        "Changelist Not Empty",
                        UIUtil.getWarningIcon()
                    );
                    switch (result) {
                        case 0:
                            break l;
                        case 1:
                            removeIt = false;
                            break;
                        default:
                            return;
                    }
                }
            }
            if (removeIt) {
                for (LocalChangeList list : lists) {
                    ChangeListManager.getInstance(project).removeChangeList(list);
                }
            }
            manager.removeTask(task);
        }
    }
}
