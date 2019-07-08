/*
 * Copyright 2013-2019 consulo.io
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
package com.intellij.ide.scratch;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.Registry;

// TODO [VISTALL] stub
public class ScratchProjectViewPane {
  public static boolean isScratchesMergedIntoProjectTab() {
    return Registry.is("ide.scratch.in.project.view") && !ApplicationManager.getApplication().isUnitTestMode();
  }
}
