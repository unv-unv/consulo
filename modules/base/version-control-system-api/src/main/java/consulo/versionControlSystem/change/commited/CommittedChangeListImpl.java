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
package consulo.versionControlSystem.change.commited;

import consulo.versionControlSystem.AbstractVcs;
import consulo.versionControlSystem.change.Change;

import consulo.versionControlSystem.versionBrowser.CommittedChangeList;
import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

/**
 * @author yole
 * @since 2006-10-20
 */
public class CommittedChangeListImpl implements CommittedChangeList {
  private final String myCommitterName;
  private final Date myCommitDate;
  private final String myName;
  private String myComment;
  private final long myNumber;
  protected ArrayList<Change> myChanges;

  public CommittedChangeListImpl(final String name, final String comment, final String committerName,
                                 final long number, final Date commitDate, final Collection<Change> changes) {
    myCommitterName = committerName;
    myCommitDate = commitDate;
    myName = name;
    myComment = comment;
    myChanges = new ArrayList<Change>(changes);
    myNumber = number;
  }

  @Override
  public String getCommitterName() {
    return myCommitterName;
  }

  @Override
  public Date getCommitDate() {
    return myCommitDate;
  }

  @Override
  public long getNumber() {
    return myNumber;
  }

  @Override
  public String getBranch() {
    return null;
  }

  @Override
  public AbstractVcs getVcs() {
    return null;
  }

  @Override
  public Collection<Change> getChangesWithMovedTrees() {
    return getChangesWithMovedTreesImpl(this);
  }

  @Override
  public boolean isModifiable() {
    return true;
  }

  @Override
  public void setDescription(String newMessage) {
    myComment = newMessage;
  }

  public static Collection<Change> getChangesWithMovedTreesImpl(final CommittedChangeList list) {
    return list.getChanges();
  }

  @Override
  public Collection<Change> getChanges() {
    return myChanges;
  }

  @Override
  @Nonnull
  public String getName() {
    return myName;
  }

  @Override
  public String getComment() {
    return myComment;
  }
}
