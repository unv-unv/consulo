/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package consulo.execution.debug.impl.internal.ui.tree.node;

import consulo.application.ApplicationManager;
import consulo.execution.debug.frame.XValueGroup;
import consulo.execution.debug.impl.internal.ui.tree.XDebuggerTree;
import consulo.execution.debug.ui.XValuePresentationUtil;
import consulo.ui.ex.SimpleTextAttributes;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author nik
 */
public class XValueGroupNodeImpl extends XValueContainerNode<XValueGroup> implements RestorableStateNode {
  public XValueGroupNodeImpl(XDebuggerTree tree, XDebuggerTreeNode parent, @Nonnull XValueGroup group) {
    super(tree, parent, group);
    setLeaf(false);
    setIcon(group.getIcon());
    myText.append(group.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    String comment = group.getComment();
    if (comment != null) {
      XValuePresentationUtil.appendSeparator(myText, group.getSeparator());
      myText.append(comment, SimpleTextAttributes.GRAY_ATTRIBUTES);
    }

    if (group.isAutoExpand()) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          if (!isObsolete()) {
            myTree.expandPath(getPath());
          }
        }
      });
    }
    myTree.nodeLoaded(this, group.getName());
  }

  @Override
  public String toString() {
    return "Group:" + myValueContainer.getName();
  }

  @Nullable
  @Override
  public String getName() {
    return myValueContainer.getName();
  }

  @Nullable
  @Override
  public String getRawValue() {
    return null;
  }

  @Override
  public boolean isComputed() {
    return true;
  }

  @Override
  public void markChanged() {
  }
}
