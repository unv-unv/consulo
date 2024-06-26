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
package consulo.versionControlSystem.impl.internal.change;

import consulo.util.collection.MultiMap;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.LocalChangeList;
import consulo.versionControlSystem.impl.internal.change.local.*;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.LinkedList;
import java.util.List;

/** synchronization aspect is external for this class; only logic here
 * have internal command queue; applies commands to another copy of change lists (ChangeListWorker) and sends notifications
 * (after update is done)
 */
public class Modifier implements ChangeListsWriteOperations {
  private ChangeListWorker myWorker;
  private boolean myInsideUpdate;
  private final List<ChangeListCommand> myCommandQueue;
  private final DelayedNotificator myNotificator;

  public Modifier(final ChangeListWorker worker, final DelayedNotificator notificator) {
    myWorker = worker;
    myNotificator = notificator;
    myCommandQueue = new LinkedList<ChangeListCommand>();
  }

  @Override
  public LocalChangeList addChangeList(@Nonnull final String name, @jakarta.annotation.Nullable final String comment, @Nullable Object data) {
    final AddList command = new AddList(name, comment, data);
    impl(command);
    return command.getNewListCopy();
  }

  @Override
  public String setDefault(final String name) {
    final SetDefault command = new SetDefault(name);
    impl(command);
    return command.getPrevious();
  }

  @Override
  public boolean removeChangeList(@Nonnull final String name) {
    final RemoveList command = new RemoveList(name);
    impl(command);
    return command.isRemoved();
  }

  @Override
  public MultiMap<LocalChangeList, Change> moveChangesTo(final String name, final Change[] changes) {
    final MoveChanges command = new MoveChanges(name, changes);
    impl(command);
    return command.getMovedFrom();
  }

  private void impl(final ChangeListCommand command) {
    command.apply(myWorker);
    if (myInsideUpdate) {
      myCommandQueue.add(command);
      // notify after change lsist are synchronized
    } else {
      // notify immediately
      myNotificator.callNotify(command);
    }
  }

  @Override
  public boolean setReadOnly(final String name, final boolean value) {
    final SetReadOnly command = new SetReadOnly(name, value);
    impl(command);
    return command.isResult();
  }

  @Override
  public boolean editName(@Nonnull final String fromName, @Nonnull final String toName) {
    final EditName command = new EditName(fromName, toName);
    impl(command);
    return command.isResult();
  }

  @Override
  public String editComment(@Nonnull final String fromName, final String newComment) {
    final EditComment command = new EditComment(fromName, newComment);
    impl(command);
    return command.getOldComment();
  }

  public boolean isInsideUpdate() {
    return myInsideUpdate;
  }

  public void enterUpdate() {
    myInsideUpdate = true;
  }

  public void finishUpdate(final ChangeListWorker worker) {
    exitUpdate();
    // should be applied for notifications to be delivered (they were delayed)
    apply(worker);
    clearQueue();
  }

  private void exitUpdate() {
    myInsideUpdate = false;
  }

  private void clearQueue() {
    myCommandQueue.clear();
  }

  private void apply(final ChangeListWorker worker) {
    for (ChangeListCommand command : myCommandQueue) {
      command.apply(worker);
      myNotificator.callNotify(command);
    }
  }

  public void setWorker(ChangeListWorker worker) {
    myWorker = worker;
  }
}
