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

package consulo.localHistory.impl.internal.revision;

import consulo.localHistory.impl.internal.ChangeSet;
import consulo.localHistory.impl.internal.LocalHistoryFacade;
import consulo.localHistory.impl.internal.Paths;
import consulo.localHistory.impl.internal.change.Change;
import consulo.localHistory.impl.internal.tree.Entry;
import consulo.localHistory.impl.internal.tree.RootEntry;
import consulo.util.collection.SmartList;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;

import java.util.List;

public class ChangeRevision extends Revision {
  private final LocalHistoryFacade myFacade;
  private final RootEntry myRoot;
  @Nonnull
  private final String myEntryPath;
  private final long myTimestamp;
  private final Change myChangeToRevert;

  private final boolean myBefore;

  private final long myId;
  private final String myName;
  private final String myLabel;
  private final int myLabelColor;
  private final Pair<List<String>, Integer> myAffectedFiles;

  public ChangeRevision(LocalHistoryFacade facade, RootEntry r, @Nonnull String entryPath, ChangeSet changeSet, boolean before) {
    myFacade = facade;
    myRoot = r;
    myEntryPath = entryPath;
    myBefore = before;

    myTimestamp = changeSet.getTimestamp();
    myChangeToRevert = before ? changeSet.getFirstChange() : changeSet.getLastChange();

    myId = changeSet.getId();
    myLabel = changeSet.getLabel();
    myLabelColor = changeSet.getLabelColor();
    myName = changeSet.getName();

    List<String> allAffectedFiles = changeSet.getAffectedPaths();
    List<String> someAffectedFiles = new SmartList<>();
    for (String each : allAffectedFiles.subList(0, Math.min(3, allAffectedFiles.size()))) {
      someAffectedFiles.add(Paths.getNameOf(each));
    }
    myAffectedFiles = Pair.create(someAffectedFiles, allAffectedFiles.size());
  }

  @Override
  public long getTimestamp() {
    return myTimestamp;
  }

  @Override
  public Entry findEntry() {
    RootEntry rootCopy = myRoot.copy();

    boolean revertThis = myBefore;
    String path = myFacade.revertUpTo(rootCopy, myEntryPath, null, myChangeToRevert, revertThis, true);

    return rootCopy.findEntry(path);
  }

  @Override
  public String getLabel() {
    return myLabel;
  }

  @Override
  public int getLabelColor() {
    return myLabelColor;
  }

  @Override
  public Long getChangeSetId() {
    return myId;
  }

  @Override
  public String getChangeSetName() {
    return myName;
  }

  @Override
  public Pair<List<String>, Integer> getAffectedFileNames() {
    return myAffectedFiles;
  }

  public String toString() {
    return getClass().getSimpleName() + ": " + myChangeToRevert;
  }

  public boolean containsChangeWithId(long id) {
    return myChangeToRevert.getId() == id;
  }
}