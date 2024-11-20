// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.remoteServer.impl.internal.runtime.log;

import consulo.disposer.Disposer;
import consulo.execution.process.AnsiEscapeDecoder;
import consulo.execution.ui.console.*;
import consulo.process.ProcessHandler;
import consulo.process.ProcessOutputTypes;
import consulo.project.Project;
import consulo.remoteServer.runtime.log.LoggingHandler;
import consulo.util.dataholder.Key;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class LoggingHandlerImpl extends LoggingHandlerBase implements LoggingHandler {
    private final ConsoleView myConsole;
    private boolean myClosed = false;

    public LoggingHandlerImpl(String presentableName, @NotNull Project project) {
        this(presentableName, project, false);
    }

    public LoggingHandlerImpl(String presentableName, @NotNull Project project, boolean isViewer) {
        super(presentableName);

        final TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);

        builder.setViewer(isViewer);

        myConsole = builder.getConsole();

        Disposer.register(this, myConsole);
    }

    @Override
    public JComponent getComponent() {
        return myConsole.getComponent();
    }

    public @NotNull ConsoleView getConsole() {
        return myConsole;
    }

    @Override
    public void print(@NotNull String s) {
        printText(s, ConsoleViewContentType.NORMAL_OUTPUT);
    }

    protected void printText(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
        myConsole.print(text, contentType);
    }

    @Override
    public void printHyperlink(@NotNull String url) {
        printHyperlink(url, new BrowserHyperlinkInfo(url));
    }

    @Override
    public void printHyperlink(@NotNull String text, HyperlinkInfo info) {
        myConsole.printHyperlink(text, info);
    }

    public void printlnSystemMessage(@NotNull String s) {
        printText(s + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
    }

    @Override
    public void attachToProcess(@NotNull ProcessHandler handler) {
        myConsole.attachToProcess(handler);
    }

    @Override
    public void scrollTo(int offset) {
        myConsole.scrollTo(offset);
    }

    @Override
    public void clear() {
        myConsole.clear();
    }

    @Override
    public boolean isClosed() {
        return myClosed;
    }

    public void close() {
        myClosed = true;
    }

    public static class Colored extends LoggingHandlerImpl {

        private final AnsiEscapeDecoder myAnsiEscapeDecoder = new AnsiEscapeDecoder();

        public Colored(String presentableName, @NotNull Project project) {
            super(presentableName, project);
        }

        public Colored(String presentableName, @NotNull Project project, boolean isViewer) {
            super(presentableName, project, isViewer);
        }

        @Override
        public void print(@NotNull String s) {
            myAnsiEscapeDecoder.escapeText(s, ProcessOutputTypes.STDOUT, this::printTextWithOutputKey);
        }

        private void printTextWithOutputKey(@NotNull String text, Key outputType) {
            printText(text, ConsoleViewContentType.getConsoleViewType(outputType));
        }
    }
}
