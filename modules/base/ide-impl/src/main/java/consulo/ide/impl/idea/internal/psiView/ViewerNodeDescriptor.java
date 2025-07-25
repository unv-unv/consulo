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
package consulo.ide.impl.idea.internal.psiView;

import consulo.ui.ex.tree.NodeDescriptor;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;

/**
 * @author Jeka
 * @since 2001-08-25
 */
public class ViewerNodeDescriptor extends NodeDescriptor {
  private final Object myElement;

  public ViewerNodeDescriptor(Project project, Object element, NodeDescriptor parentDescriptor) {
    super(parentDescriptor);
    myElement = element;
    myName = myElement.toString();
  }

  @RequiredUIAccess
  @Override
  public boolean update() {
    return false;
  }

  @Override
  public Object getElement() {
    return myElement;
  }
}
