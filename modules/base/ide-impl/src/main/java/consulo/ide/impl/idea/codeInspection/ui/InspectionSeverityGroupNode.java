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

package consulo.ide.impl.idea.codeInspection.ui;

import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.ui.image.Image;


/**
 * @author anna
 * @since 2005-03-15
 */
public class InspectionSeverityGroupNode extends InspectionTreeNode {

  private final HighlightDisplayLevel myLevel;
  private final Project myProject;

  public InspectionSeverityGroupNode(final Project project, final HighlightDisplayLevel level) {
    super(level);
    myLevel = level;
    myProject = project;
  }

  @Override
  public Image getIcon() {
    return myLevel.getIcon();
  }

  @Override
  public boolean appearsBold() {
    return true;
  }

  public String toString() {
    return StringUtil.capitalize(myLevel.toString().toLowerCase());
  }

  public HighlightDisplayLevel getSeverityLevel() {
    return myLevel;
  }

  public Project getProject() {
    return myProject;
  }
}
