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

import consulo.content.scope.NamedScope;
import consulo.language.editor.inspection.InspectionTool;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.IOException;

/**
 * @author anna
 * @since 2006-02-15
 */
public interface ModifiableModel extends Profile {

  InspectionProfile getParentProfile();

  @Nullable
  String getBaseProfileName();

  void setBaseProfile(InspectionProfile profile);

  void enableTool(String inspectionTool, NamedScope namedScope, Project project);

  void disableTool(String inspectionTool, NamedScope namedScope, @Nonnull Project project);

  void setErrorLevel(HighlightDisplayKey key, @Nonnull HighlightDisplayLevel level, Project project);

  HighlightDisplayLevel getErrorLevel(HighlightDisplayKey inspectionToolKey, PsiElement element);

  boolean isToolEnabled(HighlightDisplayKey key);

  boolean isToolEnabled(HighlightDisplayKey key, PsiElement element);

  void commit() throws IOException;

  boolean isChanged();

  void setModified(final boolean toolsSettingsChanged);

  boolean isProperSetting(@Nonnull String toolId);

  void resetToBase(Project project);

  void resetToEmpty(Project project);

  /**
   * @return {@link InspectionToolWrapper}
   * @see #getUnwrappedTool(String, PsiElement)
   */
  InspectionToolWrapper getInspectionTool(String shortName, PsiElement element);

  /**
   * @return tool by shortName and scope
   */
  <T extends InspectionTool> T getUnwrappedTool(@Nonnull String shortName, @Nonnull PsiElement element);

  /**
   * @return nullable if tool by shortName not found
   */
  @Nullable
  <S> S getToolState(@Nonnull String shortName, @Nonnull PsiElement element);

  InspectionToolWrapper[] getInspectionTools(PsiElement element);

  void copyFrom(InspectionProfile profile);

  void setEditable(String toolDisplayName);

  void save() throws IOException;

  boolean isProfileLocked();

  void lockProfile(boolean isLocked);

  void disableTool(@Nonnull String toolId, @Nonnull PsiElement element);

  void disableTool(String inspectionTool, Project project);
}
