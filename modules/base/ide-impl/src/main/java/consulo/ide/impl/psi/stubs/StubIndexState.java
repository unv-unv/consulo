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
package consulo.ide.impl.psi.stubs;

import consulo.language.psi.stub.StubIndexKey;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author max
 */
public class StubIndexState {
    public List<String> registeredIndices = new ArrayList<>();

    public StubIndexState() {
    }

    public StubIndexState(@Nonnull Collection<StubIndexKey<?, ?>> keys) {
        for (StubIndexKey key : keys) {
            registeredIndices.add(key.getName());
        }
    }
}
