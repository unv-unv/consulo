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
package consulo.remoteServer.agent.shared.util.log;

/**
 * @author michael.golubev
 */
public interface LogListener {

    // TODO: rename lineLogged -> print
    void lineLogged(String line);

    void printHyperlink(String line, Runnable action);

    void close();

    void clear();

    void scrollTo(int offset);

    LogListener NULL = new LogListener() {

        @Override
        public void lineLogged(String line) {
            //
        }

        @Override
        public void printHyperlink(String line, Runnable action) {
            //
        }

        @Override
        public void close() {
            //
        }

        @Override
        public void clear() {
            //
        }

        @Override
        public void scrollTo(int offset) {
            //
        }
    };
}
