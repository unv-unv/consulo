/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package consulo.execution.impl.internal.action;

import consulo.annotation.component.ActionImpl;
import consulo.codeEditor.Editor;
import consulo.execution.ui.RunContentDescriptor;
import consulo.execution.ui.console.ConsoleView;
import consulo.execution.ui.console.ConsoleViewContentType;
import consulo.localize.LocalizeValue;
import consulo.process.ProcessHandler;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareAction;
import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author egor
 */
@ActionImpl(id = EOFAction.ACTION_ID)
public class EOFAction extends DumbAwareAction {
    public static final String ACTION_ID = "SendEOF";

    public EOFAction() {
        super(LocalizeValue.localizeTODO("Send EOF"));
    }

    @Override
    public int getExecuteWeight() {
        return 1_000_000;
    }

    @Override
    public void update(@Nonnull AnActionEvent e) {
        RunContentDescriptor descriptor = StopAction.getRecentlyStartedContentDescriptor(e.getDataContext());
        ProcessHandler handler = descriptor != null ? descriptor.getProcessHandler() : null;
        e.getPresentation().setEnabledAndVisible(
            e.hasData(ConsoleView.KEY) && e.hasData(Editor.KEY) && handler != null && !handler.isProcessTerminated()
        );
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(@Nonnull AnActionEvent e) {
        RunContentDescriptor descriptor = StopAction.getRecentlyStartedContentDescriptor(e.getDataContext());
        ProcessHandler activeProcessHandler = descriptor != null ? descriptor.getProcessHandler() : null;
        if (activeProcessHandler == null || activeProcessHandler.isProcessTerminated()) {
            return;
        }

        try {
            OutputStream input = activeProcessHandler.getProcessInput();
            if (input != null) {
                ConsoleView console = e.getData(ConsoleView.KEY);
                if (console != null) {
                    console.print("^D\n", ConsoleViewContentType.SYSTEM_OUTPUT);
                }
                input.close();
            }
        }
        catch (IOException ignored) {
        }
    }
}
