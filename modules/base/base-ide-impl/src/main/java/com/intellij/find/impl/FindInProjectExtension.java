/*
 * Copyright 2013-2021 consulo.io
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
package com.intellij.find.impl;

import com.intellij.find.FindModel;
import com.intellij.openapi.actionSystem.DataContext;
import consulo.application.Application;
import consulo.component.extension.ExtensionType;
import consulo.component.extension.ExtensionList;

/**
 * @author VISTALL
 * @since 12/12/2021
 */
@ExtensionType(value = "findInProjectExtension", component = Application.class)
public interface FindInProjectExtension {
  ExtensionList<FindInProjectExtension, Application> EP_NAME = ExtensionList.of(FindInProjectExtension.class);

  boolean initModelFromContext(FindModel model, DataContext dataContext);
}
