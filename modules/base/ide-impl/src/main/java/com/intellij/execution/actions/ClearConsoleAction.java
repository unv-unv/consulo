// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.actions;

import consulo.execution.ExecutionBundle;
import com.intellij.execution.ui.ConsoleView;
import consulo.application.AllIcons;
import consulo.ui.ex.action.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import javax.annotation.Nonnull;

public class ClearConsoleAction extends DumbAwareAction {
  public ClearConsoleAction() {
    super(ExecutionBundle.message("clear.all.from.console.action.name"), "Clear the contents of the console", AllIcons.Actions.GC);
  }

  @Override
  public void update(@Nonnull AnActionEvent e) {
    ConsoleView data = e.getData(LangDataKeys.CONSOLE_VIEW);
    boolean enabled = data != null && data.getContentSize() > 0;
    e.getPresentation().setEnabled(enabled);
  }

  @Override
  public void actionPerformed(@Nonnull final AnActionEvent e) {
    final ConsoleView consoleView = e.getData(LangDataKeys.CONSOLE_VIEW);
    if (consoleView != null) {
      consoleView.clear();
    }
  }
}
