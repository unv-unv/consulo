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
package consulo.ide.impl.idea.ide.util.gotoByName;

import consulo.ide.impl.idea.util.ArrayUtil;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public abstract class SimpleChooseByNameModel implements ChooseByNameModel {
  private final Project myProject;
  private final String myPrompt;
  private final String myHelpId;

  protected SimpleChooseByNameModel(@Nonnull Project project, @Nonnull String prompt, @Nullable String helpId) {
    myProject = project;
    myPrompt = prompt;
    myHelpId = helpId;
  }

  public abstract String[] getNames();

  protected abstract Object[] getElementsByName(String name, String pattern);


  public Project getProject() {
    return myProject;
  }

  @Override
  public String getPromptText() {
    return myPrompt;
  }

  @Override
  public String getNotInMessage() {
    return InspectionLocalize.nothingFound().get();
  }

  @Override
  public String getNotFoundMessage() {
    return InspectionLocalize.nothingFound().get();
  }

  @Override
  public LocalizeValue getCheckBoxName() {
    return LocalizeValue.of();
  }

  @Override
  public boolean loadInitialCheckBoxState() {
    return false;
  }

  @Override
  public void saveInitialCheckBoxState(boolean state) {
  }

  @Nonnull
  @Override
  public String[] getNames(boolean checkBoxState) {
    return getNames();
  }

  @Nonnull
  @Override
  public Object[] getElementsByName(String name, boolean checkBoxState, String pattern) {
    return getElementsByName(name, pattern);
  }

  @Nonnull
  @Override
  public String[] getSeparators() {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public String getFullName(Object element) {
    return getElementName(element);
  }

  @Override
  public String getHelpId() {
    return myHelpId;
  }

  @Override
  public boolean willOpenEditor() {
    return false;
  }

  @Override
  public boolean useMiddleMatching() {
    return false;
  }
}
