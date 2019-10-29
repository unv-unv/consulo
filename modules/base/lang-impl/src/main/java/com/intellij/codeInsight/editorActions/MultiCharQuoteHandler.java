// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.PsiFile;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Max Medvedev
 */
public interface MultiCharQuoteHandler extends QuoteHandler {
  /**
   * Returns a closing quote for an opening quote placed immediately before offset, or {@code null} when there is no matching quote.
   */
  @Nullable
  CharSequence getClosingQuote(@Nonnull HighlighterIterator iterator, int offset);

  /**
   * Should insert the {@code closingQuote} returned from {@link #getClosingQuote(HighlighterIterator, int)} into the document.
   * Override this method for languages with multi-root PSI.
   */
  default void insertClosingQuote(@Nonnull Editor editor, int offset, @Nonnull PsiFile file, @Nonnull CharSequence closingQuote) {
    insertClosingQuote(editor, offset, closingQuote);
  }

  /**
   * Should insert the {@code closingQuote} returned from {@link #getClosingQuote(HighlighterIterator, int)} into the document.
   * Override this method for languages with single-root PSI.
   */
  default void insertClosingQuote(@Nonnull Editor editor, int offset, @Nonnull CharSequence closingQuote) {
    editor.getDocument().insertString(offset, closingQuote);
    if (closingQuote.length() == 1) {
      TabOutScopesTracker.getInstance().registerEmptyScope(editor, offset);
    }
  }
}