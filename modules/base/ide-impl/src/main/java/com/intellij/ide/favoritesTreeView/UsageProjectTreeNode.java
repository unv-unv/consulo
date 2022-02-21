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
package com.intellij.ide.favoritesTreeView;

import consulo.ui.ex.awt.tree.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import consulo.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.TextChunk;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsagePresentation;
import com.intellij.util.Function;
import javax.annotation.Nonnull;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 6/6/12
 * Time: 7:24 PM
 */
public class UsageProjectTreeNode extends ProjectViewNodeWithChildrenList<UsageInfo> {
  private final UsagePresentation myUsagePresentation;

  public UsageProjectTreeNode(Project project, UsageInfo usage, ViewSettings viewSettings) {
    super(project, usage, viewSettings);
    final UsageInfo2UsageAdapter adapter = new UsageInfo2UsageAdapter(usage);
    myUsagePresentation = adapter.getPresentation();
  }

  @Override
  public boolean contains(@Nonnull VirtualFile file) {
    final UsageInfo info = getValue();
    if (info == null) return false;
    final PsiElement element = info.getElement();
    return element != null && file.equals(element.getContainingFile().getVirtualFile());
  }

  @Override
  public String toString() {
    return myUsagePresentation.getPlainText();
  }

  @Override
  protected void update(PresentationData presentation) {
    presentation.setIcon(myUsagePresentation.getIcon());
    presentation.setTooltip(myUsagePresentation.getTooltipText());
    final TextChunk[] text = myUsagePresentation.getText();
    updatePresentationWithTextChunks(presentation, text);
    presentation.setPresentableText(StringUtil.join(text, new Function<TextChunk, String>() {
      @Override
      public String fun(TextChunk chunk) {
        return chunk.getText();
      }
    }, ""));
  }

  public static void updatePresentationWithTextChunks(PresentationData presentation, TextChunk[] text) {
    for (TextChunk chunk : text) {
      presentation.addText(chunk.getText(), chunk.getSimpleAttributesIgnoreBackground());
    }
  }

  @Override
  public void navigate(boolean requestFocus) {
    UsageViewUtil.navigateTo(getValue(), requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return canNavigateToSource();
  }

  @Override
  public boolean canNavigateToSource() {
    return getValue().getElement().isValid();
  }
}
