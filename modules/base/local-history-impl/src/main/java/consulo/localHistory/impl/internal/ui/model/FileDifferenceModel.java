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

import consulo.application.util.DateFormatUtil;
import consulo.diff.DiffContentFactory;
import consulo.diff.content.DiffContent;
import consulo.diff.content.DocumentContent;
import consulo.document.Document;
import consulo.localHistory.LocalHistoryBundle;
import consulo.localHistory.impl.internal.IdeaGateway;
import consulo.localHistory.impl.internal.tree.Entry;
import consulo.project.Project;
import consulo.util.io.FileUtil;

public abstract class FileDifferenceModel {
  protected final Project myProject;
  protected final IdeaGateway myGateway;
  private final boolean isRightContentCurrent;

  protected FileDifferenceModel(Project p, IdeaGateway gw, boolean currentRightContent) {
    myProject = p;
    myGateway = gw;
    isRightContentCurrent = currentRightContent;
  }

  public String getTitle() {
    Entry e = getRightEntry();
    if (e == null) e = getLeftEntry();
    return FileUtil.toSystemDependentName(e.getPath());
  }

  public String getLeftTitle(RevisionProcessingProgress p) {
    if (!hasLeftEntry()) return LocalHistoryBundle.message("file.does.not.exist");
    return formatTitle(getLeftEntry(), isLeftContentAvailable(p));
  }

  public String getRightTitle(RevisionProcessingProgress p) {
    if (!hasRightEntry()) return LocalHistoryBundle.message("file.does.not.exist");
    if (!isRightContentAvailable(p)) {
      return formatTitle(getRightEntry(), false);
    }
    if (isRightContentCurrent) return LocalHistoryBundle.message("current.revision");
    return formatTitle(getRightEntry(), true);
  }

  private String formatTitle(Entry e, boolean isAvailable) {
    String result = DateFormatUtil.formatPrettyDateTime(e.getTimestamp()) + " - " + e.getName();
    if (!isAvailable) {
      result += " - " + LocalHistoryBundle.message("content.not.available");
    }
    return result;
  }

  protected abstract Entry getLeftEntry();

  protected abstract Entry getRightEntry();

  public DiffContent getLeftDiffContent(RevisionProcessingProgress p) {
    if (!hasLeftEntry()) return DiffContentFactory.getInstance().createEmpty();
    if (!isLeftContentAvailable(p)) return DiffContentFactory.getInstance().create("Content not available");
    return doGetLeftDiffContent(p);
  }

  public DiffContent getRightDiffContent(RevisionProcessingProgress p) {
    if (!hasRightEntry()) return DiffContentFactory.getInstance().createEmpty();
    if (!isRightContentAvailable(p)) return DiffContentFactory.getInstance().create("Content not available");
    if (isRightContentCurrent) return getEditableRightDiffContent(p);
    return getReadOnlyRightDiffContent(p);
  }

  private boolean hasLeftEntry() {
    return getLeftEntry() != null;
  }

  private boolean hasRightEntry() {
    return getRightEntry() != null;
  }

  protected abstract boolean isLeftContentAvailable(RevisionProcessingProgress p);

  protected abstract boolean isRightContentAvailable(RevisionProcessingProgress p);

  protected abstract DiffContent doGetLeftDiffContent(RevisionProcessingProgress p);

  protected abstract DiffContent getReadOnlyRightDiffContent(RevisionProcessingProgress p);

  protected abstract DiffContent getEditableRightDiffContent(RevisionProcessingProgress p);

  protected DocumentContent createSimpleDiffContent(String content, Entry e) {
    return DiffContentFactory.getInstance().create(content, myGateway.getFileType(e.getName()));
  }

  protected Document getDocument() {
    return myGateway.getDocument(getRightEntry().getPath());
  }
}
