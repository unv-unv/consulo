/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package consulo.ide.impl.idea.tasks.actions.context;

import consulo.application.util.DateFormatUtil;
import consulo.dataContext.DataContext;
import consulo.task.impl.internal.action.BaseTaskAction;
import consulo.ide.impl.idea.tasks.actions.SwitchTaskAction;
import consulo.ide.impl.idea.tasks.context.LoadContextUndoableAction;
import consulo.ide.impl.idea.ui.popup.list.ListPopupImpl;
import consulo.ide.impl.idea.util.NullableFunction;
import consulo.project.Project;
import consulo.task.LocalTask;
import consulo.task.TaskManager;
import consulo.task.TasksIcons;
import consulo.task.impl.internal.context.ContextInfo;
import consulo.task.impl.internal.context.WorkingContextManager;
import consulo.task.util.TaskUtil;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public class LoadContextAction extends BaseTaskAction {
  private static final int MAX_ROW_COUNT = 10;

  @Override
  @RequiredUIAccess
  public void actionPerformed(@Nonnull AnActionEvent e) {
    final Project project = getProject(e);
    assert project != null;
    DefaultActionGroup group = new DefaultActionGroup();
    final WorkingContextManager manager = WorkingContextManager.getInstance(project);
    List<ContextInfo> history = manager.getContextHistory();
    List<ContextHolder> infos = new ArrayList<ContextHolder>(ContainerUtil.map2List(history, info -> new ContextHolder() {
      @Override
      void load(final boolean clear) {
        LoadContextUndoableAction undoableAction = LoadContextUndoableAction.createAction(manager, clear, info.name);
        UndoableCommand.execute(project, undoableAction, "Load context " + info.comment, "Context");
      }

      @Override
      void remove() {
        manager.removeContext(info.name);
      }

      @Override
      Date getDate() {
        return new Date(info.date);
      }

      @Override
      String getComment() {
        return info.comment;
      }

      @Override
      Image getIcon() {
        return TasksIcons.SavedContext;
      }
    }));
    final TaskManager taskManager = TaskManager.getManager(project);
    List<LocalTask> tasks = taskManager.getLocalTasks();
    infos.addAll(ContainerUtil.mapNotNull(tasks, new NullableFunction<LocalTask, ContextHolder>() {
      public ContextHolder apply(final LocalTask task) {
        if (task.isActive()) {
          return null;
        }
        return new ContextHolder() {
          @Override
          void load(boolean clear) {
            LoadContextUndoableAction undoableAction = LoadContextUndoableAction.createAction(manager, clear, task);
            UndoableCommand.execute(project, undoableAction, "Load context " + TaskUtil.getTrimmedSummary(task), "Context");
          }

          @Override
          void remove() {
            SwitchTaskAction.removeTask(project, task, taskManager);
          }

          @Override
          Date getDate() {
            return task.getUpdated();
          }

          @Override
          String getComment() {
            return TaskUtil.getTrimmedSummary(task);
          }

          @Override
          Image getIcon() {
            return task.getIcon();
          }
        };
      }
    }));

    Collections.sort(infos, new Comparator<ContextHolder>() {
      public int compare(ContextHolder o1, ContextHolder o2) {
        return o2.getDate().compareTo(o1.getDate());
      }
    });

    final Ref<Boolean> shiftPressed = Ref.create(false);
    boolean today = true;
    Calendar now = Calendar.getInstance();
    for (int i = 0, historySize = Math.min(MAX_ROW_COUNT, infos.size()); i < historySize; i++) {
      final ContextHolder info = infos.get(i);
      Calendar calendar = Calendar.getInstance();
      calendar.setTime(info.getDate());
      if (today && (calendar.get(Calendar.YEAR) != now.get(Calendar.YEAR) || calendar.get(Calendar.DAY_OF_YEAR) != now.get(Calendar.DAY_OF_YEAR))) {
        group.addSeparator();
        today = false;
      }
      group.add(createItem(info, shiftPressed));
    }

    final ListPopupImpl popup =
      (ListPopupImpl)JBPopupFactory.getInstance()
                                   .createActionGroupPopup("Load Context",
                                                           group,
                                                           e.getDataContext(),
                                                           JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                                                           false,
                                                           null,
                                                           MAX_ROW_COUNT);
    popup.setAdText("Press SHIFT to merge with current context");
    popup.registerAction("shiftPressed", KeyStroke.getKeyStroke("shift pressed SHIFT"), new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        shiftPressed.set(true);
        popup.setCaption("Merge with Current Context");
      }
    });
    popup.registerAction("shiftReleased", KeyStroke.getKeyStroke("released SHIFT"), new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        shiftPressed.set(false);
        popup.setCaption("Load Context");
      }
    });
    popup.registerAction("invoke", KeyStroke.getKeyStroke("shift ENTER"), new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        popup.handleSelect(true);
      }
    });
    popup.showCenteredInCurrentWindow(project);
  }

  abstract static class ContextHolder {

    abstract void load(boolean clear);

    abstract void remove();

    abstract Date getDate();

    abstract String getComment();

    abstract Image getIcon();
  }

  private static ActionGroup createItem(final ContextHolder holder, final Ref<Boolean> shiftPressed) {
    String text = DateFormatUtil.formatPrettyDateTime(holder.getDate());
    String comment = holder.getComment();
    if (!StringUtil.isEmpty(comment)) {
      text = comment + " (" + text + ")";
    }
    final AnAction loadAction = new AnAction("Load") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        holder.load(!shiftPressed.get());
      }
    };
    ActionGroup contextGroup = new ActionGroup(text, text, holder.getIcon()) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        loadAction.actionPerformed(e);
      }

      @Nonnull
      @Override
      public AnAction[] getChildren(@Nullable AnActionEvent e) {
        return new AnAction[]{loadAction, new AnAction("Remove") {
          @Override
          public void actionPerformed(AnActionEvent e) {
            holder.remove();
          }
        }};
      }

      @Override
      public boolean canBePerformed(DataContext context) {
        return true;
      }

    };
    contextGroup.setPopup(true);
    return contextGroup;
  }
}
