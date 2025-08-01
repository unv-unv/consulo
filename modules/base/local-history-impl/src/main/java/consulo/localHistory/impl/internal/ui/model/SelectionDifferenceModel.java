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

package consulo.localHistory.impl.internal.ui.model;

import consulo.application.util.diff.FilesTooBigForDiffException;
import consulo.diff.DiffContentFactory;
import consulo.diff.content.DiffContent;
import consulo.diff.content.DocumentContent;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.localHistory.impl.internal.IdeaGateway;
import consulo.localHistory.impl.internal.revision.Revision;
import consulo.localHistory.impl.internal.tree.Entry;
import consulo.project.Project;

public class SelectionDifferenceModel extends FileDifferenceModel {
  private final SelectionCalculator myCalculator;
  private final Revision myLeftRevision;
  private final Revision myRightRevision;
  private final int myFrom;
  private final int myTo;

  public SelectionDifferenceModel(Project p,
                                  IdeaGateway gw,
                                  SelectionCalculator c,
                                  Revision left,
                                  Revision right,
                                  int from,
                                  int to,
                                  boolean editableRightContent) {
    super(p, gw, editableRightContent);
    myCalculator = c;
    myLeftRevision = left;
    myRightRevision = right;
    myFrom = from;
    myTo = to;
  }

  @Override
  protected Entry getLeftEntry() {
    return myLeftRevision.findEntry();
  }

  @Override
  protected Entry getRightEntry() {
    return myRightRevision.findEntry();
  }

  @Override
  protected boolean isLeftContentAvailable(RevisionProcessingProgress p) {
    try {
      return myCalculator.canCalculateFor(myLeftRevision, p);
    }
    catch (FilesTooBigForDiffException e) {
      return false;
    }
  }

  @Override
  protected boolean isRightContentAvailable(RevisionProcessingProgress p) {
    try {
      return myCalculator.canCalculateFor(myRightRevision, p);
    }
    catch (FilesTooBigForDiffException e) {
      return false;
    }
  }

  @Override
  protected DiffContent doGetLeftDiffContent(RevisionProcessingProgress p) {
    return getDiffContent(myLeftRevision, p);
  }

  @Override
  protected DiffContent getReadOnlyRightDiffContent(RevisionProcessingProgress p) {
    return getDiffContent(myRightRevision, p);
  }

  @Override
  protected DiffContent getEditableRightDiffContent(RevisionProcessingProgress p) {
    Document d = getDocument();

    int fromOffset = d.getLineStartOffset(myFrom);
    int toOffset = d.getLineEndOffset(myTo);

    DocumentContent documentContent = DiffContentFactory.getInstance().create(myProject, d);
    return DiffContentFactory.getInstance().createFragment(myProject, documentContent, new TextRange(fromOffset, toOffset));
  }

  private DocumentContent getDiffContent(Revision r, RevisionProcessingProgress p) {
    return createSimpleDiffContent(getContentOf(r, p), r.findEntry());
  }

  private String getContentOf(Revision r, RevisionProcessingProgress p) {
    try {
      return myCalculator.getSelectionFor(r, p).getBlockContent();
    }
    catch (FilesTooBigForDiffException e) {
      return "";
    }
  }
}
