// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.language.impl.internal.psi;

import consulo.document.Document;
import consulo.document.event.DocumentEvent;
import consulo.document.util.ProperTextRange;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.impl.ast.FileElement;
import consulo.language.impl.ast.TreeUtil;
import consulo.language.impl.psi.ForeignLeafPsiElement;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

public class ChangedPsiRangeUtil {
  private static int getLeafMatchingLength(CharSequence leafText,
                                           CharSequence pattern,
                                           int patternIndex,
                                           int finalPatternIndex,
                                           int direction) {
    int leafIndex = direction == 1 ? 0 : leafText.length() - 1;
    int finalLeafIndex = direction == 1 ? leafText.length() - 1 : 0;
    int result = 0;
    while (leafText.charAt(leafIndex) == pattern.charAt(patternIndex)) {
      result++;
      if (leafIndex == finalLeafIndex || patternIndex == finalPatternIndex) {
        break;
      }
      leafIndex += direction;
      patternIndex += direction;
    }
    return result;
  }

  private static int getMatchingLength(@Nonnull FileElement treeElement, @Nonnull CharSequence text, boolean fromStart) {
    int patternIndex = fromStart ? 0 : text.length() - 1;
    int finalPatternIndex = fromStart ? text.length() - 1 : 0;
    int direction = fromStart ? 1 : -1;
    ASTNode leaf = fromStart ? TreeUtil.findFirstLeaf(treeElement, false) : TreeUtil.findLastLeaf(treeElement, false);
    int result = 0;
    while (leaf != null && (fromStart ? patternIndex <= finalPatternIndex : patternIndex >= finalPatternIndex)) {
      if (!(leaf instanceof ForeignLeafPsiElement)) {
        CharSequence chars = leaf.getChars();
        if (chars.length() > 0) {
          int matchingLength = getLeafMatchingLength(chars, text, patternIndex, finalPatternIndex, direction);
          result += matchingLength;
          if (matchingLength != chars.length()) {
            break;
          }
          patternIndex += fromStart ? matchingLength : -matchingLength;
        }
      }
      leaf = fromStart ? TreeUtil.nextLeaf(leaf, false) : TreeUtil.prevLeaf(leaf, false);
    }
    return result;
  }

  @Nullable
  public static TextRange getChangedPsiRange(@Nonnull PsiFile file,
                                             @Nonnull FileElement treeElement,
                                             @Nonnull CharSequence newDocumentText) {
    int psiLength = treeElement.getTextLength();
    if (!file.getViewProvider().supportsIncrementalReparse(file.getLanguage())) {
      return new TextRange(0, psiLength);
    }

    int commonPrefixLength = getMatchingLength(treeElement, newDocumentText, true);
    if (commonPrefixLength == newDocumentText.length() && newDocumentText.length() == psiLength) {
      return null;
    }

    int commonSuffixLength = Math.min(getMatchingLength(treeElement, newDocumentText, false), psiLength - commonPrefixLength);
    return new TextRange(commonPrefixLength, psiLength - commonSuffixLength);
  }

  @Nullable
  public static ProperTextRange getChangedPsiRange(@Nonnull PsiFile file,
                                                   @Nonnull Document document,
                                                   @Nonnull CharSequence oldDocumentText,
                                                   @Nonnull CharSequence newDocumentText) {
    int psiLength = oldDocumentText.length();
    if (!file.getViewProvider().supportsIncrementalReparse(file.getLanguage())) {
      return new ProperTextRange(0, psiLength);
    }
    List<DocumentEvent> events = ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(file.getProject())).getEventsSinceCommit(document);
    int prefix = Integer.MAX_VALUE;
    int suffix = Integer.MAX_VALUE;
    int lengthBeforeEvent = psiLength;
    for (DocumentEvent event : events) {
      prefix = Math.min(prefix, event.getOffset());
      suffix = Math.min(suffix, lengthBeforeEvent - event.getOffset() - event.getOldLength());
      lengthBeforeEvent = lengthBeforeEvent - event.getOldLength() + event.getNewLength();
    }
    if ((prefix == psiLength || suffix == psiLength) && newDocumentText.length() == psiLength) {
      return null;
    }
    //Important! delete+insert sequence can give some of same chars back, lets grow affixes to include them.
    int shortestLength = Math.min(psiLength, newDocumentText.length());
    while (prefix < shortestLength && oldDocumentText.charAt(prefix) == newDocumentText.charAt(prefix)) {
      prefix++;
    }
    while (suffix < shortestLength - prefix && oldDocumentText.charAt(psiLength - suffix - 1) == newDocumentText.charAt(newDocumentText.length() - suffix - 1)) {
      suffix++;
    }
    int end = Math.max(prefix, psiLength - suffix);
    if (end == prefix && newDocumentText.length() == oldDocumentText.length()) return null;
    return ProperTextRange.create(prefix, end);
  }
}
