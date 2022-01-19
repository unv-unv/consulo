// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import consulo.component.extension.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.function.Consumer;

/**
 * @author Konstantin Bulenkov
 */
public interface SearchTopHitProvider {
  ExtensionPointName<SearchTopHitProvider> EP_NAME = ExtensionPointName.create("consulo.search.topHitProvider");

  void consumeTopHits(@Nonnull String pattern, @Nonnull Consumer<Object> collector, @Nullable Project project);

  static String getTopHitAccelerator() {
    return Registry.is("new.search.everywhere") ? "/" : "#";
  }
}
