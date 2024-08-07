/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package consulo.language.parser;

import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.lexer.Lexer;
import consulo.application.progress.ProgressIndicatorProvider;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class PsiBuilderUtil {
    private PsiBuilderUtil() {
    }

    /**
     * Advances lexer by given number of tokens (but not beyond the end of token stream).
     *
     * @param builder PSI builder to operate on.
     * @param count   number of tokens to skip.
     */
    public static void advance(final PsiBuilder builder, final int count) {
        for (int i = 0; i < count && !builder.eof(); i++) {
            builder.getTokenType();
            builder.advanceLexer();
        }
    }

    /**
     * Advances lexer if current token is of expected type, does nothing otherwise.
     *
     * @param builder      PSI builder to operate on.
     * @param expectedType expected token.
     * @return true if token matches, false otherwise.
     */
    public static boolean expect(final PsiBuilder builder, final IElementType expectedType) {
        if (builder.getTokenType() == expectedType) {
            builder.advanceLexer();
            return true;
        }
        return false;
    }

    /**
     * Advances lexer if current token is of expected type, does nothing otherwise.
     *
     * @param builder       PSI builder to operate on.
     * @param expectedTypes expected token types.
     * @return true if token matches, false otherwise.
     */
    public static boolean expect(final PsiBuilder builder, final TokenSet expectedTypes) {
        if (expectedTypes.contains(builder.getTokenType())) {
            builder.advanceLexer();
            return true;
        }
        return false;
    }

    /**
     * Release group of allocated markers.
     *
     * @param markers markers to drop.
     */
    public static void drop(final PsiBuilder.Marker... markers) {
        for (PsiBuilder.Marker marker : markers) {
            if (marker != null) {
                marker.drop();
            }
        }
    }

    /**
     * Rolls the lexer back to position before given marker - if not null.
     *
     * @param marker marker to roll back to.
     */
    public static void rollbackTo(@Nullable PsiBuilder.Marker marker) {
        if (marker != null) {
            marker.rollbackTo();
        }
    }

    @Nonnull
    public static CharSequence rawTokenText(PsiBuilder builder, int index) {
        return builder.getOriginalText().subSequence(builder.rawTokenTypeStart(index), builder.rawTokenTypeStart(index + 1));
    }

    /**
     * Checks if `text` looks like a proper block.
     * In particular it
     * (1) checks brace balance
     * (2) verifies that the block's closing brace is the last token
     *
     * @param text       - text to check
     * @param lexer      - lexer to use
     * @param leftBrace  - left brace element type
     * @param rightBrace - right brace element type
     * @return true if `text` passes the checks
     */
    public static boolean hasProperBraceBalance(
        @Nonnull CharSequence text,
        @Nonnull Lexer lexer,
        @Nonnull IElementType leftBrace,
        @Nonnull IElementType rightBrace
    ) {
        lexer.start(text);

        if (lexer.getTokenType() != leftBrace) {
            return false;
        }

        lexer.advance();
        int balance = 1;

        while (true) {
            ProgressIndicatorProvider.checkCanceled();
            IElementType type = lexer.getTokenType();

            if (type == null) {
                //eof: checking balance
                return balance == 0;
            }

            if (balance == 0) {
                //the last brace is not the last token
                return false;
            }

            if (type == leftBrace) {
                balance++;
            }
            else if (type == rightBrace) {
                balance--;
            }

            lexer.advance();
        }
    }
}
