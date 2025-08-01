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

import consulo.localHistory.impl.internal.revision.Difference;
import consulo.localHistory.impl.internal.revision.Revision;
import consulo.localHistory.impl.internal.tree.Entry;
import consulo.project.Project;
import consulo.util.collection.Lists;
import consulo.virtualFileSystem.VirtualFile;

import java.io.IOException;
import java.util.*;

public class DifferenceReverter extends Reverter {
  private final IdeaGateway myGateway;
  private final List<Difference> myDiffs;
  private final Revision myLeftRevision;

  public DifferenceReverter(Project p, LocalHistoryFacade vcs, IdeaGateway gw, List<Difference> diffs, Revision leftRevision) {
    super(p, vcs, gw);
    myGateway = gw;
    myDiffs = diffs;
    myLeftRevision = leftRevision;
  }

  @Override
  protected Revision getTargetRevision() {
    return myLeftRevision;
  }

  @Override
  protected List<VirtualFile> getFilesToClearROStatus() throws IOException {
    LinkedHashSet<VirtualFile> files = new LinkedHashSet<VirtualFile>();
    for (Difference each : myDiffs) {
      Entry l = each.getLeft();
      Entry r = each.getRight();
      VirtualFile f = l == null ? null : myGateway.findVirtualFile(l.getPath());
      if (f != null) files.add(f);

      f = r == null ? null : myGateway.findVirtualFile(r.getPath());
      if (f != null) files.add(f);
    }
    return new ArrayList<VirtualFile>(files);
  }

  @Override
  protected void doRevert() throws IOException {
    doRevert(true);
  }

  public void doRevert(boolean revertContentChanges) throws IOException {
    Set<String> vetoedFiles = new HashSet<String>();

    for (Difference each : Lists.iterateBackward(myDiffs)) {
      Entry l = each.getLeft();
      Entry r = each.getRight();

      if (l == null) {
        revertCreation(r, vetoedFiles);
        continue;
      }

      vetoedFiles.add(l.getPath());
      if (r == null) {
        revertDeletion(l);
        continue;
      }

      VirtualFile file = myGateway.findOrCreateFileSafely(r.getPath(), r.isDirectory());
      revertRename(l, file);
      if (revertContentChanges) revertContentChange(l, file);
    }
  }

  private void revertCreation(Entry r, Set<String> vetoedFiles) throws IOException {
    String path = r.getPath();
    for (String each : vetoedFiles) {
      if (Paths.isParent(path, each)) return;
    }
    
    VirtualFile f = myGateway.findVirtualFile(path);
    if (f != null) f.delete(this);
  }

  private void revertDeletion(Entry l) throws IOException {
    VirtualFile f = myGateway.findOrCreateFileSafely(l.getPath(), l.isDirectory());
    if (l.isDirectory()) return;
    setContent(l, f);
  }

  private void revertRename(Entry l, VirtualFile file) throws IOException {
    String oldName = l.getName();
    if (!oldName.equals(file.getName())) {
      VirtualFile existing = file.getParent().findChild(oldName);
      if (existing != null) {
        existing.delete(this);
      }
      file.rename(this, oldName);
    }
  }

  private void revertContentChange(Entry l, VirtualFile file) throws IOException {
    if (l.isDirectory()) return;
    if (file.getTimeStamp() != l.getTimestamp()) {
      setContent(l, file);
    }
  }

  private void setContent(Entry l, VirtualFile file) throws IOException {
    Content c = l.getContent();
    if (!c.isAvailable()) return;
    file.setBinaryContent(c.getBytes(), -1, l.getTimestamp());
  }
}
