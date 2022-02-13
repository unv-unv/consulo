/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.macro;

import consulo.language.editor.CodeInsightBundle;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.openapi.util.text.StringUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class ReplaceUnderscoresToCamelCaseMacro extends MacroBase {
  public ReplaceUnderscoresToCamelCaseMacro() {
    super("underscoresToCamelCase", CodeInsightBundle.message("macro.undescoresToCamelCase.string"));
  }

  @Nullable
  @Override
  protected Result calculateResult(@Nonnull Expression[] params, ExpressionContext context, boolean quick) {
    final String text = getTextResult(params, context, true);
    if (text != null) {
      final List<String> strings = StringUtil.split(text, "_");
      if (strings.size() > 0) {
        final StringBuilder buf = new StringBuilder();
        buf.append(strings.get(0).toLowerCase());
        for (int i = 1; i < strings.size(); i++) {
          buf.append(StringUtil.capitalize(strings.get(i).toLowerCase()));
        }
        return new TextResult(buf.toString());
      }
    }
    return null;
  }
}
