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

import consulo.localHistory.impl.internal.DifferenceReverter;
import consulo.localHistory.impl.internal.IdeaGateway;
import consulo.localHistory.impl.internal.LocalHistoryFacade;
import consulo.localHistory.impl.internal.Reverter;
import consulo.localHistory.impl.internal.revision.Revision;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

public abstract class FileHistoryDialogModel extends HistoryDialogModel {
  public FileHistoryDialogModel(Project p, IdeaGateway gw, LocalHistoryFacade vcs, VirtualFile f) {
    super(p, gw, vcs, f);
  }

  public abstract FileDifferenceModel getDifferenceModel();

  @Override
  public Reverter createReverter() {
    Revision l = getLeftRevision();
    Revision r = getRightRevision();
    return new DifferenceReverter(myProject, myVcs, myGateway, l.getDifferencesWith(r), l);
  }
}
