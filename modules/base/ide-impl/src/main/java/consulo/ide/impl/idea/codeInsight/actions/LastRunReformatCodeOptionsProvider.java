/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package consulo.ide.impl.idea.codeInsight.actions;

import consulo.ide.impl.idea.ide.util.PropertiesComponent;
import consulo.language.Language;
import consulo.language.psi.PsiFile;
import jakarta.annotation.Nonnull;

public class LastRunReformatCodeOptionsProvider {

  private static final String OPTIMIZE_IMPORTS_KEY     = "LayoutCode.optimizeImports";
  private static final String REARRANGE_ENTRIES_KEY    = "LayoutCode.rearrangeEntries";
  private static final String PROCESS_CHANGED_TEXT_KEY = "LayoutCode.processChangedText";

  private final PropertiesComponent myPropertiesComponent;

  public LastRunReformatCodeOptionsProvider(@Nonnull PropertiesComponent propertiesComponent) {
    myPropertiesComponent = propertiesComponent;
  }

  public ReformatCodeRunOptions getLastRunOptions(@Nonnull PsiFile file) {
    Language language = file.getLanguage();

    ReformatCodeRunOptions settings = new ReformatCodeRunOptions(getLastTextRangeType());
    settings.setOptimizeImports(getLastOptimizeImports());
    settings.setRearrangeCode(isRearrangeCode(language));

    return settings;
  }

  public void saveRearrangeState(@Nonnull Language language, boolean value) {
    String key = getRearrangeCodeKeyFor(language);
    myPropertiesComponent.setValue(key, Boolean.toString(value));
  }

  public void saveOptimizeImportsState(boolean value) {
    String optimizeImports = Boolean.toString(value);
    myPropertiesComponent.setValue(OPTIMIZE_IMPORTS_KEY, optimizeImports);
  }

  public boolean getLastOptimizeImports() {
    return myPropertiesComponent.getBoolean(OPTIMIZE_IMPORTS_KEY);
  }

  public TextRangeType getLastTextRangeType() {
    return myPropertiesComponent.getBoolean(PROCESS_CHANGED_TEXT_KEY) ? TextRangeType.VCS_CHANGED_TEXT : TextRangeType.WHOLE_FILE;
  }

  public void saveProcessVcsChangedTextState(boolean value) {
    String processOnlyVcsChangedText = Boolean.toString(value);
    myPropertiesComponent.setValue(PROCESS_CHANGED_TEXT_KEY, processOnlyVcsChangedText);
  }

  public void saveRearrangeCodeState(boolean value) {
    myPropertiesComponent.setValue(REARRANGE_ENTRIES_KEY, value);
  }

  public boolean getLastRearrangeCode() {
    return myPropertiesComponent.getBoolean(REARRANGE_ENTRIES_KEY);
  }

  public boolean isRearrangeCode(@Nonnull Language language) {
    String key = getRearrangeCodeKeyFor(language);
    return myPropertiesComponent.getBoolean(key);
  }

  private static String getRearrangeCodeKeyFor(@Nonnull Language language) {
    return REARRANGE_ENTRIES_KEY + language.getID();
  }

}
