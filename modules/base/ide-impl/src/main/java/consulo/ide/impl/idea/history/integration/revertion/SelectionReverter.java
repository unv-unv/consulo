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

package consulo.ide.impl.idea.history.integration.revertion;

import consulo.diff.Block;
import consulo.ide.impl.idea.history.core.LocalHistoryFacade;
import consulo.ide.impl.idea.history.core.revisions.Revision;
import consulo.ide.impl.idea.history.core.tree.Entry;
import consulo.ide.impl.idea.history.integration.IdeaGateway;
import consulo.ide.impl.idea.history.integration.ui.models.Progress;
import consulo.ide.impl.idea.history.integration.ui.models.SelectionCalculator;
import consulo.document.Document;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.application.util.diff.FilesTooBigForDiffException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class SelectionReverter extends Reverter {
  private final SelectionCalculator myCalculator;
  private final Revision myLeftRevision;
  private final Entry myRightEntry;
  private final int myFromLine;
  private final int myToLine;

  public SelectionReverter(Project p,
                           LocalHistoryFacade vcs,
                           IdeaGateway gw,
                           SelectionCalculator c,
                           Revision leftRevision,
                           Entry rightEntry,
                           int fromLine,
                           int toLine) {
    super(p, vcs, gw);
    myCalculator = c;
    myLeftRevision = leftRevision;
    myRightEntry = rightEntry;
    myFromLine = fromLine;
    myToLine = toLine;
  }

  @Override
  protected Revision getTargetRevision() {
    return myLeftRevision;
  }

  @Override
  protected List<VirtualFile> getFilesToClearROStatus() throws IOException {
    VirtualFile file = myGateway.findVirtualFile(myRightEntry.getPath());
    return Collections.singletonList(file);
  }

  protected void doRevert() throws IOException, FilesTooBigForDiffException {
    Block b = myCalculator.getSelectionFor(myLeftRevision, new Progress() {
      public void processed(int percentage) {
        // should be already processed.
      }
    });

    Document d = myGateway.getDocument(myRightEntry.getPath());

    int from = d.getLineStartOffset(myFromLine);
    int to = d.getLineEndOffset(myToLine);

    d.replaceString(from, to, b.getBlockContent());
  }
}
