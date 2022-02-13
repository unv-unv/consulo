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
package com.intellij.packageDependencies;

import com.intellij.openapi.components.ServiceManager;
import consulo.project.Project;
import consulo.language.psi.PsiFile;
import consulo.language.psi.search.scope.NamedScopesHolder;
import consulo.language.psi.search.scope.PackageSet;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Map;

/**
 * User: anna
 * Date: Mar 2, 2005
 */
public abstract class DependencyValidationManager extends NamedScopesHolder {
  public DependencyValidationManager(final Project project) {
    super(project);
  }

  public static DependencyValidationManager getInstance(Project project) {
    return ServiceManager.getService(project, DependencyValidationManager.class);
  }

  public abstract boolean hasRules();

  @Nullable
  public abstract DependencyRule getViolatorDependencyRule(PsiFile from, PsiFile to);

  @Nonnull
  public abstract DependencyRule[] getViolatorDependencyRules(PsiFile from, PsiFile to);

  @Nonnull
  public abstract DependencyRule[] getApplicableRules(PsiFile file);

  public abstract DependencyRule[] getAllRules();

  public abstract void removeAllRules();

  public abstract void addRule(DependencyRule rule);

  public abstract boolean skipImportStatements();

  public abstract void setSkipImportStatements(boolean skip);

  public abstract Map<String,PackageSet> getUnnamedScopes();

  public abstract void reloadRules();
}
