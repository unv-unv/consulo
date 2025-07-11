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
package consulo.execution.test.sm.runner.state;

import consulo.disposer.Disposable;
import consulo.execution.test.CompositePrintable;
import consulo.execution.test.Printer;
import consulo.execution.ui.console.ConsoleViewContentType;
import consulo.process.ProcessOutputTypes;
import consulo.util.collection.Lists;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class TestFailedState extends AbstractState implements Disposable {
    private final List<String> myPresentationText;

    public TestFailedState(@Nullable String localizedMessage, @Nullable String stackTrace) {
        myPresentationText =
            Lists.newLockFreeCopyOnWriteList(Collections.singleton(buildErrorPresentationText(localizedMessage, stackTrace)));
    }

    public void addError(@Nullable String localizedMessage, @Nullable String stackTrace, Printer printer) {
        String msg = buildErrorPresentationText(localizedMessage, stackTrace);
        if (msg != null) {
            myPresentationText.add(msg);
            if (printer != null) {
                printError(printer, Arrays.asList(msg), false);
            }
        }
    }

    @Override
    public void dispose() {
    }

    @Nullable
    public static String buildErrorPresentationText(@Nullable String localizedMessage, @Nullable String stackTrace) {
        String text = (StringUtil.isEmptyOrSpaces(localizedMessage) ? "" : localizedMessage + CompositePrintable.NEW_LINE) +
            (StringUtil.isEmptyOrSpaces(stackTrace) ? "" : stackTrace + CompositePrintable.NEW_LINE);
        return StringUtil.isEmptyOrSpaces(text) ? null : text;
    }

    public static void printError(@Nonnull Printer printer, @Nonnull List<String> errorPresentationText) {
        printError(printer, errorPresentationText, true);
    }

    private static void printError(
        @Nonnull Printer printer,
        @Nonnull List<String> errorPresentationText,
        boolean setMark
    ) {
        boolean addMark = setMark;
        for (String errorText : errorPresentationText) {
            if (errorText != null) {
                printer.print(CompositePrintable.NEW_LINE, ConsoleViewContentType.ERROR_OUTPUT);
                if (addMark) {
                    printer.mark();
                    addMark = false;
                }
                printer.printWithAnsiColoring(errorText, ProcessOutputTypes.STDERR);
            }
        }
    }

    @Override
    public void printOn(Printer printer) {
        super.printOn(printer);
        printError(printer, myPresentationText);
    }

    @Override
    public boolean isDefect() {
        return true;
    }

    @Override
    public boolean wasLaunched() {
        return true;
    }

    @Override
    public boolean isFinal() {
        return true;
    }

    @Override
    public boolean isInProgress() {
        return false;
    }

    @Override
    public boolean wasTerminated() {
        return false;
    }

    @Override
    public Magnitude getMagnitude() {
        return Magnitude.FAILED_INDEX;
    }

    @Override
    public String toString() {
        //noinspection HardCodedStringLiteral
        return "TEST FAILED";
    }
}
