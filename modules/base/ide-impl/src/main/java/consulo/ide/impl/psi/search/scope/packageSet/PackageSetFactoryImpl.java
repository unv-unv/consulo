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

package consulo.ide.impl.psi.search.scope.packageSet;

import consulo.annotation.component.ServiceImpl;
import consulo.language.editor.scope.AnalysisScopeBundle;
import consulo.language.lexer.Lexer;
import consulo.component.extension.Extensions;
import consulo.ide.impl.psi.search.scope.packageSet.lexer.ScopeTokenTypes;
import consulo.ide.impl.psi.search.scope.packageSet.lexer.ScopesLexer;
import consulo.content.scope.ComplementPackageSet;
import consulo.content.scope.PackageSet;
import consulo.content.scope.PackageSetFactory;
import consulo.content.scope.ParsingException;
import consulo.logging.Logger;
import jakarta.inject.Singleton;

import jakarta.annotation.Nullable;

@Singleton
@ServiceImpl
public class PackageSetFactoryImpl extends PackageSetFactory {
    private static final Logger LOG = Logger.getInstance(PackageSetFactoryImpl.class);

    @Override
    public PackageSet compile(String text) throws ParsingException {
        Lexer lexer = new ScopesLexer();
        lexer.start(text);
        return new Parser(lexer).parse();
    }

    private static class Parser {
        private final Lexer myLexer;

        public Parser(Lexer lexer) {
            myLexer = lexer;
        }

        public PackageSet parse() throws ParsingException {
            PackageSet set = parseUnion();
            if (myLexer.getTokenType() != null) {
                error(AnalysisScopeBundle.message("error.packageset.token.expectations", getTokenText()));
            }
            return set;
        }

        private PackageSet parseUnion() throws ParsingException {
            PackageSet result = parseIntersection();
            while (true) {
                if (myLexer.getTokenType() != ScopeTokenTypes.OROR) {
                    break;
                }
                myLexer.advance();
                result = new UnionPackageSet(result, parseIntersection());
            }
            return result;
        }

        private PackageSet parseIntersection() throws ParsingException {
            PackageSet result = parseTerm();
            while (true) {
                if (myLexer.getTokenType() != ScopeTokenTypes.ANDAND) {
                    break;
                }
                myLexer.advance();
                result = new IntersectionPackageSet(result, parseTerm());
            }
            return result;
        }

        private PackageSet parseTerm() throws ParsingException {
            if (myLexer.getTokenType() == ScopeTokenTypes.EXCL) {
                myLexer.advance();
                return new ComplementPackageSet(parseTerm());
            }

            if (myLexer.getTokenType() == ScopeTokenTypes.LPARENTH) {
                return parseParenthesized();
            }
            if (myLexer.getTokenType() == ScopeTokenTypes.IDENTIFIER && myLexer.getBufferSequence()
                .charAt(myLexer.getTokenStart()) == '$') {
                NamedPackageSetReference namedPackageSetReference = new NamedPackageSetReference(getTokenText());
                myLexer.advance();
                return namedPackageSetReference;
            }
            return parsePattern();
        }

        private PackageSet parsePattern() throws ParsingException {
            String scope = null;
            for (PackageSetParserExtension extension : Extensions.getExtensions(PackageSetParserExtension.EP_NAME)) {
                scope = extension.parseScope(myLexer);
                if (scope != null) {
                    break;
                }
            }
            if (scope == null) {
                error("Unknown scope type");
            }
            String modulePattern = parseModulePattern();

            if (myLexer.getTokenType() == ScopeTokenTypes.COLON) {
                myLexer.advance();
            }
            for (PackageSetParserExtension extension : Extensions.getExtensions(PackageSetParserExtension.EP_NAME)) {
                final PackageSet packageSet = extension.parsePackageSet(myLexer, scope, modulePattern);
                if (packageSet != null) {
                    return packageSet;
                }
            }
            error("Unknown scope type");
            return null; //not reachable
        }

        private String getTokenText() {
            int start = myLexer.getTokenStart();
            int end = myLexer.getTokenEnd();
            return myLexer.getBufferSequence().subSequence(start, end).toString();
        }

        @Nullable
        private String parseModulePattern() throws ParsingException {
            if (myLexer.getTokenType() != ScopeTokenTypes.LBRACKET) {
                return null;
            }
            myLexer.advance();
            StringBuffer pattern = new StringBuffer();
            while (true) {
                if (myLexer.getTokenType() == ScopeTokenTypes.RBRACKET ||
                    myLexer.getTokenType() == null) {
                    myLexer.advance();
                    break;
                }
                else if (myLexer.getTokenType() == ScopeTokenTypes.ASTERISK) {
                    pattern.append("*");
                }
                else if (myLexer.getTokenType() == ScopeTokenTypes.IDENTIFIER ||
                    myLexer.getTokenType() == ScopeTokenTypes.WHITE_SPACE ||
                    myLexer.getTokenType() == ScopeTokenTypes.INTEGER_LITERAL) {
                    pattern.append(getTokenText());
                }
                else if (myLexer.getTokenType() == ScopeTokenTypes.DOT) {
                    pattern.append(".");
                }
                else if (myLexer.getTokenType() == ScopeTokenTypes.MINUS) {
                    pattern.append("-");
                }
                else if (myLexer.getTokenType() == ScopeTokenTypes.TILDE) {
                    pattern.append("~");
                }
                else if (myLexer.getTokenType() == ScopeTokenTypes.SHARP) {
                    pattern.append("#");
                }
                else if (myLexer.getTokenType() == ScopeTokenTypes.COLON) {
                    pattern.append(":");
                }
                else {
                    error(AnalysisScopeBundle.message("error.packageset.token.expectations", getTokenText()));
                    break;
                }
                myLexer.advance();
            }
            if (pattern.length() == 0) {
                error(AnalysisScopeBundle.message("error.packageset.pattern.expectations"));
            }
            return pattern.toString();
        }

        private PackageSet parseParenthesized() throws ParsingException {
            LOG.assertTrue(myLexer.getTokenType() == ScopeTokenTypes.LPARENTH);
            myLexer.advance();

            PackageSet result = parseUnion();
            if (myLexer.getTokenType() != ScopeTokenTypes.RPARENTH) {
                error(AnalysisScopeBundle.message("error.packageset.rparen.expected"));
            }
            myLexer.advance();

            return result;
        }

        private void error(String message) throws ParsingException {
            throw new ParsingException(
                AnalysisScopeBundle.message("error.packageset.position.parsing.error", message, (myLexer.getTokenStart() + 1)));
        }
    }
}
