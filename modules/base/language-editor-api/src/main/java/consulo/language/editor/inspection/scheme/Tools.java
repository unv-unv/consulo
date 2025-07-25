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
package consulo.language.editor.inspection.scheme;

import consulo.language.editor.internal.inspection.ScopeToolState;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * @author anna
 * @since 2009-04-24
 */
public interface Tools {
  @Nonnull
  InspectionToolWrapper getInspectionTool(PsiElement element);

  @Nonnull
  String getShortName();

  @Nonnull
  InspectionToolWrapper getTool();

  @Nonnull
  List<ScopeToolState> getTools();

  @Nonnull
  ScopeToolState getDefaultState();

  boolean isEnabled();

  boolean isEnabled(@Nullable PsiElement element);

  @Nullable
  InspectionToolWrapper getEnabledTool(@Nullable PsiElement element);
}