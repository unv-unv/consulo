/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testIntegration;

import com.intellij.execution.Location;
import consulo.component.extension.ExtensionPointName;
import com.intellij.openapi.project.Project;
import javax.annotation.Nonnull;

import java.util.List;

/** @deprecated override SMTRunnerConsoleProperties.getTestLocator() instead (to be removed in IDEA 17) */
public interface TestLocationProvider {
  @SuppressWarnings("deprecation") ExtensionPointName<TestLocationProvider> EP_NAME = ExtensionPointName.create("com.intellij.testSrcLocator");

  @Nonnull
  List<Location> getLocation(@Nonnull String protocolId, @Nonnull String locationData, Project project);
}