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

package consulo.language.impl.internal;

import consulo.annotation.component.ExtensionImpl;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.document.util.TextRange;
import consulo.language.psi.AbstractElementManipulator;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nonnull;

/**
 * @author peter
 */
@ExtensionImpl
public class PsiCommentManipulator extends AbstractElementManipulator<PsiComment> {
  @Override
  public PsiComment handleContentChange(@Nonnull PsiComment psiComment, @Nonnull TextRange range, String newContent) throws IncorrectOperationException {
    String oldText = psiComment.getText();
    String newText = oldText.substring(0, range.getStartOffset()) + newContent + oldText.substring(range.getEndOffset());
    FileType type = psiComment.getContainingFile().getFileType();
    PsiFile fromText = PsiFileFactory.getInstance(psiComment.getProject()).createFileFromText("__." + type.getDefaultExtension(), type, newText);
    PsiComment newElement = PsiTreeUtil.getParentOfType(fromText.findElementAt(0), psiComment.getClass(), false);
    assert newElement != null;
    return (PsiComment)psiComment.replace(newElement);
  }

  @Nonnull
  @Override
  public TextRange getRangeInElement(@Nonnull final PsiComment element) {
    final String text = element.getText();
    if (text.startsWith("//")) return new TextRange(2, element.getTextLength());
    final int length = text.length();
    if (length > 4 && text.startsWith("/**") && text.endsWith("*/")) return new TextRange(3, element.getTextLength()-2);
    if (length > 3 && text.startsWith("/*") && text.endsWith("*/")) return new TextRange(2, element.getTextLength()-2);
    if (length > 6 && text.startsWith("<!--") && text.endsWith("-->")) return new TextRange(4, element.getTextLength()-3);
    if (text.startsWith("--")) return new TextRange(2, element.getTextLength());
    if (text.startsWith("#")) return new TextRange(1, element.getTextLength());
    return super.getRangeInElement(element);
  }

  @Nonnull
  @Override
  public Class<PsiComment> getElementClass() {
    return PsiComment.class;
  }
}