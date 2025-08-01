/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package consulo.execution.test.sm.runner.event;

import jetbrains.buildServer.messages.serviceMessages.BaseTestMessage;

import jakarta.annotation.Nonnull;

public class TestOutputEvent extends TreeNodeEvent {
    private final String myText;
    private final boolean myStdOut;

    public TestOutputEvent(@Nonnull BaseTestMessage message, @Nonnull String text, boolean stdOut) {
        super(message.getTestName(), TreeNodeEvent.getNodeId(message));
        myText = text;
        myStdOut = stdOut;
    }

    public TestOutputEvent(@Nonnull String testName, @Nonnull String text, boolean stdOut) {
        super(testName, null);
        myText = text;
        myStdOut = stdOut;
    }

    @Nonnull
    public String getText() {
        return myText;
    }

    public boolean isStdOut() {
        return myStdOut;
    }

    @Override
    protected void appendToStringInfo(@Nonnull StringBuilder buf) {
        append(buf, "text", myText);
        append(buf, "stdOut", myStdOut);
    }
}
