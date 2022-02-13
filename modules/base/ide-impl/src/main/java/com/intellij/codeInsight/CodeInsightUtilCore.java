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
package com.intellij.codeInsight;

import consulo.language.Language;
import consulo.document.Document;
import consulo.document.RangeMarker;
import consulo.language.editor.FileModificationService;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.PsiUtilCore;
import com.intellij.util.ReflectionUtil;
import consulo.annotation.access.RequiredReadAction;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Arrays;
import java.util.List;

public abstract class CodeInsightUtilCore extends FileModificationService {
  @RequiredReadAction
  public static <T extends PsiElement> T findElementInRange(@Nonnull PsiFile file,
                                                            int startOffset,
                                                            int endOffset,
                                                            @Nonnull Class<T> klass,
                                                            @Nonnull Language language) {
    return findElementInRange(file, startOffset, endOffset, klass, language, null);
  }

  @RequiredReadAction
  private static <T extends PsiElement> T findElementInRange(@Nonnull PsiFile file,
                                                             int startOffset,
                                                             int endOffset,
                                                             @Nonnull Class<T> klass,
                                                             @Nonnull Language language,
                                                             @Nullable PsiElement initialElement) {
    PsiElement element1 = file.getViewProvider().findElementAt(startOffset, language);
    PsiElement element2 = file.getViewProvider().findElementAt(endOffset - 1, language);
    if (element1 instanceof PsiWhiteSpace) {
      startOffset = element1.getTextRange().getEndOffset();
      element1 = file.getViewProvider().findElementAt(startOffset, language);
    }
    if (element2 instanceof PsiWhiteSpace) {
      endOffset = element2.getTextRange().getStartOffset();
      element2 = file.getViewProvider().findElementAt(endOffset - 1, language);
    }
    if (element2 == null || element1 == null) return null;
    final PsiElement commonParent = PsiTreeUtil.findCommonParent(element1, element2);
    final T element =
            ReflectionUtil.isAssignable(klass, commonParent.getClass())
            ? (T)commonParent : PsiTreeUtil.getParentOfType(commonParent, klass);

    if (element == initialElement) {
      return element;
    }

    if (element == null || element.getTextRange().getStartOffset() != startOffset || element.getTextRange().getEndOffset() != endOffset) {
      return null;
    }
    return element;
  }

  @RequiredReadAction
  public static <T extends PsiElement> T forcePsiPostprocessAndRestoreElement(@Nonnull T element) {
    return forcePsiPostprocessAndRestoreElement(element, false);
  }

  @RequiredReadAction
  public static <T extends PsiElement> T forcePsiPostprocessAndRestoreElement(@Nonnull T element,
                                                                              boolean useFileLanguage) {
    final PsiFile psiFile = element.getContainingFile();
    final Document document = psiFile.getViewProvider().getDocument();
    //if (document == null) return element;
    final Language language = useFileLanguage ? psiFile.getLanguage() : PsiUtilCore.getDialect(element);
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(psiFile.getProject());
    final RangeMarker rangeMarker = document.createRangeMarker(element.getTextRange());
    documentManager.doPostponedOperationsAndUnblockDocument(document);
    documentManager.commitDocument(document);

    T elementInRange = findElementInRange(psiFile, rangeMarker.getStartOffset(), rangeMarker.getEndOffset(),
                                          (Class<? extends T>)element.getClass(),
                                          language, element);
    rangeMarker.dispose();
    return elementInRange;
  }

  public static boolean parseStringCharacters(@Nonnull String chars, @Nonnull StringBuilder outChars, @Nullable int[] sourceOffsets) {
    return parseStringCharacters(chars, outChars, sourceOffsets, '"', '\'');
  }

  public static boolean parseStringCharacters(@Nonnull String chars, @Nonnull StringBuilder outChars, @Nullable int[] sourceOffsets, Character... endChars) {
    assert sourceOffsets == null || sourceOffsets.length == chars.length()+1;
    List<Character> endCharList = Arrays.asList(endChars);
    if (chars.indexOf('\\') < 0) {
      outChars.append(chars);
      if (sourceOffsets != null) {
        for (int i = 0; i < sourceOffsets.length; i++) {
          sourceOffsets[i] = i;
        }
      }
      return true;
    }
    int index = 0;
    final int outOffset = outChars.length();
    while (index < chars.length()) {
      char c = chars.charAt(index++);
      if (sourceOffsets != null) {
        sourceOffsets[outChars.length()-outOffset] = index - 1;
        sourceOffsets[outChars.length() + 1 -outOffset] = index;
      }
      if (c != '\\') {
        outChars.append(c);
        continue;
      }
      if (index == chars.length()) return false;
      c = chars.charAt(index++);
      switch (c) {
        case'b':
          outChars.append('\b');
          break;

        case't':
          outChars.append('\t');
          break;

        case'n':
          outChars.append('\n');
          break;

        case'f':
          outChars.append('\f');
          break;

        case'r':
          outChars.append('\r');
          break;

        case'\\':
          outChars.append('\\');
          break;

        case'0':
        case'1':
        case'2':
        case'3':
        case'4':
        case'5':
        case'6':
        case'7':
          char startC = c;
          int v = (int)c - '0';
          if (index < chars.length()) {
            c = chars.charAt(index++);
            if ('0' <= c && c <= '7') {
              v <<= 3;
              v += c - '0';
              if (startC <= '3' && index < chars.length()) {
                c = chars.charAt(index++);
                if ('0' <= c && c <= '7') {
                  v <<= 3;
                  v += c - '0';
                }
                else {
                  index--;
                }
              }
            }
            else {
              index--;
            }
          }
          outChars.append((char)v);
          break;

        case'u':
          // uuuuu1234 is valid too
          while (index != chars.length() && chars.charAt(index) == 'u') {
            index++;
          }
          if (index + 4 <= chars.length()) {
            try {
              int code = Integer.parseInt(chars.substring(index, index + 4), 16);
              //line separators are invalid here
              if (code == 0x000a || code == 0x000d) return false;
              c = chars.charAt(index);
              if (c == '+' || c == '-') return false;
              outChars.append((char)code);
              index += 4;
            }
            catch (Exception e) {
              return false;
            }
          }
          else {
            return false;
          }
          break;

        default:
          if (endCharList.contains(c)) {
            outChars.append(c);
          } else {
            return false;
          }
      }
      if (sourceOffsets != null) {
        sourceOffsets[outChars.length()-outOffset] = index;
      }
    }
    return true;
  }
}
