// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.execution.debug.memory;

import jakarta.annotation.Nonnull;

import java.util.List;

@FunctionalInterface
public interface InstancesProvider {
    @Nonnull
    List<ReferenceInfo> getInstances(int limit);
}
