// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.language.lexer;

import consulo.language.ast.IElementType;

/**
 * An interface for getting additional information from preceding tokens in {@link RestartableLexer#start(CharSequence, int, int, int, TokenIterator)}
 */
public interface TokenIterator {
    /**
     * current token start offset
     */
    int getStartOffset(int index);

    /**
     * current token end offset
     */
    int getEndOffset(int index);

    /**
     * current token type offset
     */
    IElementType getType(int index);

    /**
     * current token state
     */
    int getState(int index);

    /**
     * @return number of tokens in document
     */
    int getTokenCount();

    /**
     * @return position on which
     */
    int initialTokenIndex();
}