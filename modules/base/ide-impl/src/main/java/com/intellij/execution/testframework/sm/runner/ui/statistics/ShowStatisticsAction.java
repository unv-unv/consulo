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
package com.intellij.execution.testframework.sm.runner.ui.statistics;

import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerTestTreeView;
import com.intellij.execution.testframework.sm.runner.ui.TestResultsViewer;
import consulo.execution.test.AbstractTestProxy;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;

import javax.annotation.Nullable;

/**
 * @author Roman Chernyatchik
 */
public class ShowStatisticsAction extends AnAction {

  public void actionPerformed(final AnActionEvent e) {
    final SMTRunnerTestTreeView sender = e.getData(SMTRunnerTestTreeView.SM_TEST_RUNNER_VIEW);
    if (sender == null) {
      return;
    }

    final TestResultsViewer resultsViewer = sender.getResultsViewer();
    assert resultsViewer != null;

    resultsViewer.showStatisticsForSelectedProxy();
  }

  @Override
  public void update(final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();

    // visible only in SMTRunnerTestTreeView 
    presentation.setVisible(e.getData(SMTRunnerTestTreeView.SM_TEST_RUNNER_VIEW) != null);
    // enabled if some proxy is selected
    presentation.setEnabled(getSelectedTestProxy(e) != null);
  }

  @Nullable
  private static Object getSelectedTestProxy(final AnActionEvent e) {
    return e.getDataContext().getData(AbstractTestProxy.DATA_KEY);
  }
}
