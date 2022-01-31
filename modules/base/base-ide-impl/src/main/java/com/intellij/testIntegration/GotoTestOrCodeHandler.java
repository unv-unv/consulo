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

package com.intellij.testIntegration;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.navigation.GotoTargetHandler;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.icons.AllIcons;
import consulo.ui.ex.action.Shortcut;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import consulo.project.Project;
import com.intellij.pom.Navigatable;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.util.collection.SmartList;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

public class GotoTestOrCodeHandler extends GotoTargetHandler {
  @Override
  protected String getFeatureUsedKey() {
    return "navigation.goto.testOrCode";
  }

  @Override
  @Nullable
  protected GotoData getSourceAndTargetElements(final Editor editor, final PsiFile file) {
    PsiElement selectedElement = getSelectedElement(editor, file);
    PsiElement sourceElement = TestFinderHelper.findSourceElement(selectedElement);
    if (sourceElement == null) return null;

    List<AdditionalAction> actions = new SmartList<>();

    Collection<PsiElement> candidates;
    if (TestFinderHelper.isTest(selectedElement)) {
      candidates = TestFinderHelper.findClassesForTest(selectedElement);
    }
    else {
      candidates = TestFinderHelper.findTestsForClass(selectedElement);
      final TestCreator creator = LanguageTestCreators.INSTANCE.forLanguage(file.getLanguage());
      if (creator != null && creator.isAvailable(file.getProject(), editor, file)) {
        actions.add(new AdditionalAction() {
          @Nonnull
          @Override
          public String getText() {
            return "Create New Test...";
          }

          @Override
          public Image getIcon() {
            return AllIcons.Actions.IntentionBulb;
          }

          @Override
          public void execute() {
            creator.createTest(file.getProject(), editor, file);
          }
        });
      }
    }

    return new GotoData(sourceElement, PsiUtilCore.toPsiElementArray(candidates), actions);
  }

  @Nonnull
  public static PsiElement getSelectedElement(Editor editor, PsiFile file) {
    return PsiUtilCore.getElementAtOffset(file, editor.getCaretModel().getOffset());
  }

  @Override
  protected boolean shouldSortTargets() {
    return false;
  }

  @Nonnull
  @Override
  protected String getChooserTitle(@Nonnull PsiElement sourceElement, String name, int length, boolean finished) {
    String suffix = finished ? "" : " so far";
    if (TestFinderHelper.isTest(sourceElement)) {
      return CodeInsightBundle.message("goto.test.chooserTitle.subject", name, length, suffix);
    }
    else {
      return CodeInsightBundle.message("goto.test.chooserTitle.test", name, length, suffix);
    }
  }

  @Nonnull
  @Override
  protected String getFindUsagesTitle(@Nonnull PsiElement sourceElement, String name, int length) {
    if (TestFinderHelper.isTest(sourceElement)) {
      return CodeInsightBundle.message("goto.test.findUsages.subject.title", name);
    }
    else {
      return CodeInsightBundle.message("goto.test.findUsages.test.title", name);
    }
  }

  @Nonnull
  @Override
  protected String getNotFoundMessage(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiFile file) {
    return CodeInsightBundle.message("goto.test.notFound");
  }

  @Nullable
  @Override
  protected String getAdText(PsiElement source, int length) {
    if (length > 0 && !TestFinderHelper.isTest(source)) {
      final Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
      final Shortcut[] shortcuts = keymap.getShortcuts(DefaultRunExecutor.getRunExecutorInstance().getContextActionId());
      if (shortcuts.length > 0) {
        return ("Press " + KeymapUtil.getShortcutText(shortcuts[0]) + " to run selected tests");
      }
    }
    return null;
  }

  @Override
  protected void navigateToElement(@Nonnull Navigatable element) {
    if (element instanceof PsiElement) {
      NavigationUtil.activateFileWithPsiElement((PsiElement)element, true);
    }
    else {
      element.navigate(true);
    }
  }
}
