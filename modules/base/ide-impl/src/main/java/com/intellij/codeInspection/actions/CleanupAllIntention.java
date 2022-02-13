/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.codeInspection.actions;

import consulo.language.editor.AnalysisScope;
import consulo.language.editor.inspection.InspectionsBundle;
import consulo.project.Project;
import consulo.language.psi.PsiFile;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CleanupAllIntention extends CleanupIntention {

  public static final CleanupAllIntention INSTANCE = new CleanupAllIntention();

  private CleanupAllIntention() {}

  @Nonnull
  @Override
  public String getFamilyName() {
    return InspectionsBundle.message("cleanup.in.file");
  }

  @Nullable
  @Override
  protected AnalysisScope getScope(Project project, PsiFile file) {
    return new AnalysisScope(file);
  }
}
