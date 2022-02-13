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

/*
 * User: anna
 * Date: 20-Dec-2007
 */
package com.intellij.codeInspection.lang;

import com.intellij.codeInspection.HTMLComposer;
import consulo.language.editor.inspection.reference.RefManager;
import consulo.component.extension.ExtensionPointName;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class InspectionExtensionsFactory {

  public static final ExtensionPointName<InspectionExtensionsFactory> EP_NAME = ExtensionPointName.create("consulo.codeInspection.InspectionExtension");

  public abstract GlobalInspectionContextExtension createGlobalInspectionContextExtension();
  @Nullable
  public abstract RefManagerExtension createRefManagerExtension(RefManager refManager);
  @javax.annotation.Nullable
  public abstract HTMLComposerExtension createHTMLComposerExtension(final HTMLComposer composer);

  public abstract boolean isToCheckMember(PsiElement element, String id);

  @Nullable
  public abstract String getSuppressedInspectionIdsIn(PsiElement element);

  public abstract boolean isProjectConfiguredToRunInspections(@Nonnull Project project, boolean online);

}
