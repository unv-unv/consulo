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
package consulo.ide.impl.idea.openapi.vcs.actions;

import consulo.ide.impl.idea.openapi.actionSystem.ex.ActionImplUtil;
import consulo.codeEditor.Editor;
import consulo.ide.impl.idea.openapi.vcs.ex.LineStatusTracker;
import consulo.ide.impl.idea.openapi.vcs.ex.Range;

/**
 * author: lesya
 */
public class ShowNextChangeMarkerAction extends ShowChangeMarkerAction {

  public ShowNextChangeMarkerAction(final Range range, final LineStatusTracker lineStatusTracker, final Editor editor) {
    super(range, lineStatusTracker, editor);
    ActionImplUtil.copyFrom(this, "VcsShowNextChangeMarker");
  }

  public ShowNextChangeMarkerAction() {
  }

  protected Range extractRange(LineStatusTracker lineStatusTracker, int line, Editor editor) {
    return lineStatusTracker.getNextRange(line);
  }

}
