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
package com.intellij.execution.testframework.actions;

import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.ide.TreeExpander;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.ui.ex.awt.tree.TreeUtil;

import javax.swing.*;

public class TestTreeExpander implements TreeExpander {
  private TestFrameworkRunningModel myModel;

  public void setModel(final TestFrameworkRunningModel model) {
    myModel = model;
    Disposer.register(model, new Disposable() {
      public void dispose() {
        myModel = null;
      }
    });
  }

  public void expandAll() {
    final JTree treeView = myModel.getTreeView();
    for (int i = 0; i < treeView.getRowCount(); i++)
      treeView.expandRow(i);
  }

  public boolean canExpand() {
    return treeHasMoreThanOneLevel();
  }

  public void collapseAll() {
    TreeUtil.collapseAll(myModel.getTreeView(), 1);
  }

  public boolean canCollapse() {
    return treeHasMoreThanOneLevel();
  }

  private boolean treeHasMoreThanOneLevel() {
    return myModel != null && myModel.hasTestSuites();
  }
}
