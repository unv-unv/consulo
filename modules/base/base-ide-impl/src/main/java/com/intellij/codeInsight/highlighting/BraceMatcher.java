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

package com.intellij.codeInsight.highlighting;

import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import consulo.component.extension.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeExtensionPoint;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface BraceMatcher {
  ExtensionPointName<FileTypeExtensionPoint<BraceMatcher>> EP_NAME = ExtensionPointName.create("com.intellij.braceMatcher");

  int getBraceTokenGroupId(IElementType tokenType);
  boolean isLBraceToken(HighlighterIterator iterator,CharSequence fileText, FileType fileType);
  boolean isRBraceToken(HighlighterIterator iterator,CharSequence fileText, FileType fileType);
  boolean isPairBraces(IElementType tokenType,IElementType tokenType2);
  boolean isStructuralBrace(HighlighterIterator iterator,CharSequence text, FileType fileType);
  @Nullable IElementType getOppositeBraceTokenType(@Nonnull IElementType type);
  boolean isPairedBracesAllowedBeforeType(@Nonnull IElementType lbraceType, @Nullable IElementType contextType);

  /**
   * Returns the start offset of the code construct which owns the opening structural brace at the specified offset. For example,
   * if the opening brace belongs to an 'if' statement, returns the start offset of the 'if' statement.
   *
   * @param file the file in which brace matching is performed.
   * @param openingBraceOffset the offset of an opening structural brace.
   * @return the offset of corresponding code construct, or the same offset if not defined.
   */
  int getCodeConstructStart(final PsiFile file, int openingBraceOffset);
}
