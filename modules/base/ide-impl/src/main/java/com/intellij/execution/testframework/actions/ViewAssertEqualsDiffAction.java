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

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.impl.DiffRequestProcessor;
import com.intellij.diff.impl.DiffWindowBase;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import consulo.dataContext.DataContext;
import consulo.execution.test.AbstractTestProxy;
import consulo.execution.test.TestFrameworkRunningModel;
import consulo.execution.test.action.TestTreeViewAction;
import consulo.execution.test.stacktrace.DiffHyperlink;
import consulo.execution.test.ui.TestTreeView;
import consulo.language.editor.CommonDataKeys;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.awt.Messages;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ViewAssertEqualsDiffAction extends AnAction implements TestTreeViewAction {
  @NonNls
  public static final String ACTION_ID = "openAssertEqualsDiff";

  @Override
  public void actionPerformed(final AnActionEvent e) {
    if (!openDiff(e.getDataContext(), null)) {
      final Component component = e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
      Messages.showInfoMessage(component, "Comparison error was not found", "No Comparison Data Found");
    }
  }

  public static boolean openDiff(DataContext context, @javax.annotation.Nullable DiffHyperlink currentHyperlink) {
    final AbstractTestProxy testProxy = context.getData(AbstractTestProxy.DATA_KEY);
    final Project project = context.getData(CommonDataKeys.PROJECT);
    if (testProxy != null) {
      DiffHyperlink diffViewerProvider = testProxy.getDiffViewerProvider();
      if (diffViewerProvider != null) {
        final List<DiffHyperlink> providers = collectAvailableProviders(context.getData(TestTreeView.MODEL_DATA_KEY));
        int index = currentHyperlink != null ? providers.indexOf(currentHyperlink) : -1;
        if (index == -1) index = providers.indexOf(diffViewerProvider);
        new MyDiffWindow(project, providers, Math.max(0, index)).show();
        return true;
      }
    }
    if (currentHyperlink != null) {
      new MyDiffWindow(project, currentHyperlink).show();
      return true;
    }
    return false;
  }

  private static List<DiffHyperlink> collectAvailableProviders(TestFrameworkRunningModel model) {
    final List<DiffHyperlink> providers = new ArrayList<DiffHyperlink>();
    if (model != null) {
      final AbstractTestProxy root = model.getRoot();
      final List<? extends AbstractTestProxy> allTests = root.getAllTests();
      for (AbstractTestProxy test : allTests) {
        if (test.isLeaf()) {
          providers.addAll(test.getDiffViewerProviders());
        }
      }
    }
    return providers;
  }

  @Override
  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final boolean enabled;
    final DataContext dataContext = e.getDataContext();
    if (dataContext.getData(CommonDataKeys.PROJECT) == null) {
      enabled = false;
    }
    else {
      final AbstractTestProxy test = dataContext.getData(AbstractTestProxy.DATA_KEY);
      if (test != null) {
        if (test.isLeaf()) {
          enabled = test.getDiffViewerProvider() != null;
        }
        else if (test.isDefect()) {
          enabled = true;
        }
        else {
          enabled = false;
        }
      }
      else {
        enabled = false;
      }
    }
    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
  }

  private static class MyDiffWindow extends DiffWindowBase {
    @Nonnull
    private final List<DiffHyperlink> myRequests;
    private final int myIndex;

    public MyDiffWindow(@Nullable Project project, @Nonnull DiffHyperlink request) {
      this(project, Collections.singletonList(request), 0);
    }

    public MyDiffWindow(@javax.annotation.Nullable Project project, @Nonnull List<DiffHyperlink> requests, int index) {
      super(project, DiffDialogHints.DEFAULT);
      myRequests = requests;
      myIndex = index;
    }

    @Nonnull
    @Override
    protected DiffRequestProcessor createProcessor() {
      return new MyTestDiffRequestProcessor(myProject, myRequests, myIndex);
    }

    private class MyTestDiffRequestProcessor extends TestDiffRequestProcessor {
      public MyTestDiffRequestProcessor(@javax.annotation.Nullable Project project, @Nonnull List<DiffHyperlink> requests, int index) {
        super(project, requests, index);
        putContextUserData(DiffUserDataKeys.DIALOG_GROUP_KEY, "#com.intellij.execution.junit2.states.ComparisonFailureState$DiffDialog");
      }

      @Override
      protected void setWindowTitle(@Nonnull String title) {
        getWrapper().setTitle(title);
      }

      @Override
      protected void onAfterNavigate() {
        DiffUtil.closeWindow(getWrapper().getWindow(), true, true);
      }
    }
  }
}
