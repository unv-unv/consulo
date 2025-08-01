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

package consulo.localHistory.impl.internal;

import consulo.localHistory.impl.internal.revision.ChangeRevision;
import consulo.localHistory.impl.internal.revision.CurrentRevision;
import consulo.localHistory.impl.internal.revision.Revision;
import consulo.localHistory.impl.internal.tree.RootEntry;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RevisionsCollector extends ChangeSetsProcessor {
  private final LocalHistoryFacade myFacade;
  private final RootEntry myRoot;
  private final String myProjectId;
  private final String myPattern;

  private final List<Revision> myResult = new ArrayList<Revision>();

  public RevisionsCollector(LocalHistoryFacade facade, RootEntry rootEntry, @Nonnull String path, String projectId, @Nullable String pattern) {
    super(path);
    myFacade = facade;
    myRoot = rootEntry;
    myProjectId = projectId;
    myPattern = pattern;
  }

  public List<Revision> getResult() {
    process();
    return myResult;
  }

  @Override
  protected void process() {
    myResult.add(new CurrentRevision(myRoot, myPath));
    super.process();
  }

  @Override
  protected Pair<String, List<ChangeSet>> collectChanges() {
    // todo optimize to not collect all change sets + do not process changes twice
    ChangeCollectingVisitor v = new ChangeCollectingVisitor(myPath, myProjectId, myPattern);
    myFacade.accept(v);
    return Pair.create(v.getPath(), v.getChanges());
  }

  @Override
  protected void nothingToVisit() {
  }

  @Override
  protected void visit(ChangeSet changeSet) {
    myResult.add(new ChangeRevision(myFacade, myRoot, myPath, changeSet, true));
  }
}
