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

package com.intellij.codeInsight.template;

import com.intellij.codeInsight.lookup.LookupElement;
import consulo.component.extension.ExtensionPointName;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A macro which can be used in live templates.
 */
public abstract class Macro {
  public static final ExtensionPointName<Macro> EP_NAME = ExtensionPointName.create("consulo.liveTemplateMacro");
  
  @NonNls
  public abstract String getName();

  /**
   * @return a presentable string that will be shown in the combobox in Edit Template Variables dialog
   */
  public abstract String getPresentableName();

  @NonNls
  @Nonnull
  public String getDefaultValue() {
    return "";
  }

  @Nullable
  public abstract Result calculateResult(@Nonnull Expression[] params, ExpressionContext context);

  @javax.annotation.Nullable
  public Result calculateQuickResult(@Nonnull Expression[] params, ExpressionContext context) {
    return null;
  }

  @Nullable
  public LookupElement[] calculateLookupItems(@Nonnull Expression[] params, ExpressionContext context) {
    return null;
  }

  public boolean isAcceptableInContext(TemplateContextType context) {
    return true;
  }
}
