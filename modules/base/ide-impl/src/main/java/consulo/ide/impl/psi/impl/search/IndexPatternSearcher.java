// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ide.impl.psi.impl.search;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.progress.ProgressManager;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.ide.impl.idea.util.text.CharArrayUtil;
import consulo.ide.impl.psi.impl.cache.CacheUtil;
import consulo.language.Language;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.custom.CustomSyntaxTableFileType;
import consulo.language.editor.highlight.SyntaxHighlighter;
import consulo.language.editor.highlight.SyntaxHighlighterFactory;
import consulo.language.file.FileViewProvider;
import consulo.language.internal.custom.CustomHighlighterTokenType;
import consulo.language.lexer.Lexer;
import consulo.language.parser.ParserDefinition;
import consulo.language.pattern.StringPattern;
import consulo.language.plain.psi.PsiPlainTextFile;
import consulo.language.psi.PsiBinaryFile;
import consulo.language.psi.PsiCompiledElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.search.*;
import consulo.language.psi.stub.todo.TodoCacheManager;
import consulo.language.version.LanguageVersion;
import consulo.project.util.query.QueryExecutorBase;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.collection.primitive.ints.IntLists;
import consulo.util.lang.CharSequenceSubSequence;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nonnull;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class IndexPatternSearcher extends QueryExecutorBase<IndexPatternOccurrence, IndexPatternSearch.SearchParameters> {
    private static final String WHITESPACE = " \t";

    public IndexPatternSearcher() {
        super(true);
    }

    @Override
    @RequiredReadAction
    public void processQuery(
        @Nonnull IndexPatternSearch.SearchParameters queryParameters,
        @Nonnull Predicate<? super IndexPatternOccurrence> consumer
    ) {
        PsiFile file = queryParameters.getFile();
        VirtualFile virtualFile = file.getVirtualFile();
        if (file instanceof PsiBinaryFile || file instanceof PsiCompiledElement || virtualFile == null) {
            return;
        }

        TodoCacheManager cacheManager = TodoCacheManager.getInstance(file.getProject());
        IndexPatternProvider patternProvider = queryParameters.getPatternProvider();
        int count = patternProvider != null
            ? cacheManager.getTodoCount(virtualFile, patternProvider)
            : cacheManager.getTodoCount(virtualFile, queryParameters.getPattern());
        if (count != 0) {
            executeImpl(queryParameters, consumer);
        }
    }

    @RequiredReadAction
    protected static void executeImpl(
        IndexPatternSearch.SearchParameters queryParameters,
        Predicate<? super IndexPatternOccurrence> consumer
    ) {
        IndexPatternProvider patternProvider = queryParameters.getPatternProvider();
        PsiFile file = queryParameters.getFile();
        CharSequence chars = file.getViewProvider().getContents();
        boolean multiLine = queryParameters.isMultiLine();
        List<CommentRange> commentRanges = findCommentTokenRanges(file, chars, queryParameters.getRange(), multiLine);
        IntList occurrences = IntLists.newArrayList(1);
        IndexPattern[] patterns =
            patternProvider != null ? patternProvider.getIndexPatterns() : new IndexPattern[]{queryParameters.getPattern()};

        for (int i = 0; i < commentRanges.size(); i++) {
            occurrences.clear();

            for (int j = patterns.length - 1; j >= 0; --j) {
                if (!collectPatternMatches(
                    patterns,
                    patterns[j],
                    chars,
                    commentRanges,
                    i,
                    file,
                    queryParameters.getRange(),
                    consumer,
                    occurrences,
                    multiLine
                )) {
                    return;
                }
            }
        }
    }


    private static final TokenSet COMMENT_TOKENS =
        TokenSet.create(CustomHighlighterTokenType.LINE_COMMENT, CustomHighlighterTokenType.MULTI_LINE_COMMENT);

    @RequiredReadAction
    private static List<CommentRange> findCommentTokenRanges(PsiFile file, CharSequence chars, TextRange range, boolean multiLine) {
        if (file instanceof PsiPlainTextFile) {
            FileType fType = file.getFileType();
            if (fType instanceof CustomSyntaxTableFileType) {
                Lexer lexer =
                    SyntaxHighlighterFactory.getSyntaxHighlighter(fType, file.getProject(), file.getVirtualFile()).getHighlightingLexer();
                return findComments(lexer, chars, range, COMMENT_TOKENS, null, multiLine, file.getLanguageVersion());
            }
            else {
                return Collections.singletonList(new CommentRange(0, file.getTextLength()));
            }
        }
        else {
            List<CommentRange> commentRanges = new ArrayList<>();
            FileViewProvider viewProvider = file.getViewProvider();
            Set<Language> relevantLanguages = viewProvider.getLanguages();
            for (Language lang : relevantLanguages) {
                SyntaxHighlighter syntaxHighlighter =
                    SyntaxHighlighterFactory.getSyntaxHighlighter(lang, file.getProject(), file.getVirtualFile());
                Lexer lexer = syntaxHighlighter.getHighlightingLexer();
                TokenSet commentTokens = null;
                IndexPatternBuilder builderForFile = null;
                for (IndexPatternBuilder builder : IndexPatternBuilder.EP_NAME.getExtensionList()) {
                    Lexer lexerFromBuilder = builder.getIndexingLexer(file);
                    if (lexerFromBuilder != null) {
                        lexer = lexerFromBuilder;
                        commentTokens = builder.getCommentTokenSet(file);
                        builderForFile = builder;
                    }
                }
                if (builderForFile == null) {
                    ParserDefinition parserDefinition = ParserDefinition.forLanguage(lang);
                    if (parserDefinition != null) {
                        commentTokens = parserDefinition.getCommentTokens(file.getLanguageVersion());
                    }
                }

                if (commentTokens != null) {
                    List<CommentRange> langRanges =
                        findComments(lexer, chars, range, commentTokens, builderForFile, multiLine, file.getLanguageVersion());
                    mergeCommentLists(commentRanges, langRanges);
                }
            }
            return commentRanges;
        }
    }

    private static void mergeCommentLists(List<CommentRange> target, List<CommentRange> source) {
        List<CommentRange> merged = target.isEmpty() ? source : ContainerUtil.mergeSortedLists(
            target,
            source,
            CommentRange.BY_START_OFFSET_THEN_BY_END_OFFSET,
            true
        );
        target.clear();
        target.addAll(merged);
    }

    private static List<CommentRange> findComments(
        Lexer lexer,
        CharSequence chars,
        TextRange range,
        TokenSet commentTokens,
        IndexPatternBuilder builderForFile,
        boolean multiLine,
        LanguageVersion languageVersion
    ) {
        List<CommentRange> commentRanges = new ArrayList<>();
        int lastEndOffset = -1;
        for (lexer.start(chars); ; lexer.advance()) {
            IElementType tokenType = lexer.getTokenType();
            if (tokenType == null) {
                break;
            }

            if (range != null) {
                if (lexer.getTokenEnd() <= range.getStartOffset()) {
                    continue;
                }
                if (lexer.getTokenStart() >= range.getEndOffset() && (!multiLine || lastEndOffset < 0 || !containsOneLineBreak(
                    chars,
                    lastEndOffset,
                    lexer.getTokenStart()
                ))) {
                    break;
                }
            }

            boolean isComment = commentTokens.contains(tokenType) || CacheUtil.isInComments(tokenType, languageVersion);

            if (isComment) {
                int startDelta =
                    builderForFile != null ? builderForFile.getCommentStartDelta(lexer.getTokenType(), lexer.getTokenSequence()) : 0;
                int endDelta = builderForFile != null ? builderForFile.getCommentEndDelta(lexer.getTokenType()) : 0;

                int start = lexer.getTokenStart() + startDelta;
                int end = lexer.getTokenEnd() - endDelta;

                if (start < end && end <= chars.length()) {
                    commentRanges.add(new CommentRange(
                        start,
                        end,
                        startDelta,
                        endDelta,
                        builderForFile == null ? "" : builderForFile.getCharsAllowedInContinuationPrefix(tokenType)
                    ));
                }
                lastEndOffset = end;
            }
        }
        return commentRanges;
    }

    private static boolean containsOneLineBreak(CharSequence text, int startOffset, int endOffset) {
        boolean hasLineBreak = false;
        for (int i = startOffset; i < endOffset; i++) {
            if (text.charAt(i) == '\n') {
                if (hasLineBreak) {
                    return false;
                }
                hasLineBreak = true;
            }
        }
        return hasLineBreak;
    }

    private static boolean collectPatternMatches(
        IndexPattern[] allIndexPatterns,
        IndexPattern indexPattern,
        CharSequence chars,
        List<? extends CommentRange> commentRanges,
        int commentNum,
        PsiFile file,
        TextRange range,
        Predicate<? super IndexPatternOccurrence> consumer,
        IntList matches,
        boolean multiLine
    ) {
        CommentRange commentRange = commentRanges.get(commentNum);
        int commentStart = commentRange.startOffset;
        int commentEnd = commentRange.endOffset;
        int commentPrefixLength = commentRange.prefixLength;
        int commentSuffixLength = commentRange.suffixLength;
        Pattern pattern = indexPattern.getPattern();
        if (pattern != null) {
            ProgressManager.checkCanceled();

            CharSequence input = StringPattern.newBombedCharSequence(new CharSequenceSubSequence(
                chars,
                commentStart - commentPrefixLength,
                commentEnd + commentSuffixLength
            ));
            Matcher matcher = pattern.matcher(input);
            while (true) {
                //long time1 = System.currentTimeMillis();
                boolean found = matcher.find();
                //long time2 = System.currentTimeMillis();
                //System.out.println("scanned text of length " + (lexer.getTokenEnd() - lexer.getTokenStart() + " in " + (time2 - time1) + " ms"));

                if (!found) {
                    break;
                }
                int suffixStartOffset = input.length() - commentSuffixLength;
                int start = fitToRange(matcher.start(), commentPrefixLength, suffixStartOffset) + commentStart - commentPrefixLength;
                int end = fitToRange(matcher.end(), commentPrefixLength, suffixStartOffset) + commentStart - commentPrefixLength;
                if (start != end) {
                    if ((range == null || range.getStartOffset() <= start && end <= range.getEndOffset()) && matches.indexOf(start) == -1) {
                        List<TextRange> additionalRanges = multiLine
                            ? findContinuation(start, chars, allIndexPatterns, commentRanges, commentNum)
                            : Collections.emptyList();
                        if (range != null && !additionalRanges.isEmpty()
                            && additionalRanges.get(additionalRanges.size() - 1).getEndOffset() > range.getEndOffset()) {
                            continue;
                        }
                        matches.add(start);
                        IndexPatternOccurrenceImpl occurrence =
                            new IndexPatternOccurrenceImpl(file, start, end, indexPattern, additionalRanges);
                        if (!consumer.test(occurrence)) {
                            return false;
                        }
                    }
                }

                ProgressManager.checkCanceled();
            }
        }
        return true;
    }

    private static int fitToRange(int value, int minValue, int maxValue) {
        return Math.max(minValue, Math.min(value, maxValue));
    }

    private static List<TextRange> findContinuation(
        int mainRangeStartOffset,
        CharSequence text,
        IndexPattern[] allIndexPatterns,
        List<? extends CommentRange> commentRanges,
        int commentNum
    ) {
        CommentRange commentRange = commentRanges.get(commentNum);
        int lineStartOffset = CharArrayUtil.shiftBackwardUntil(text, mainRangeStartOffset - 1, "\n") + 1;
        int lineEndOffset = CharArrayUtil.shiftForwardUntil(text, mainRangeStartOffset, "\n");
        int offsetInLine = mainRangeStartOffset - lineStartOffset;
        int maxCommentStartOffsetInLine = Math.max(0, commentRange.startOffset - lineStartOffset);
        List<TextRange> result = new ArrayList<>();
        outer:
        while (lineEndOffset < text.length()) {
            lineStartOffset = lineEndOffset + 1;
            lineEndOffset = CharArrayUtil.shiftForwardUntil(text, lineStartOffset, "\n");
            int refOffset = lineStartOffset + offsetInLine;
            int continuationStartOffset = CharArrayUtil.shiftForward(text, refOffset, lineEndOffset, WHITESPACE);
            if (continuationStartOffset == refOffset || continuationStartOffset >= lineEndOffset) {
                break;
            }
            if (continuationStartOffset >= commentRange.endOffset) {
                commentNum++;
                if (commentNum >= commentRanges.size()) {
                    break;
                }
                commentRange = commentRanges.get(commentNum);
                if (continuationStartOffset < commentRange.startOffset || continuationStartOffset >= commentRange.endOffset) {
                    break;
                }
            }
            int commentStartOffset = Math.max(lineStartOffset, commentRange.startOffset);
            int continuationEndOffset = Math.min(lineEndOffset, commentRange.endOffset);
            if (refOffset < commentStartOffset ||
                commentStartOffset > lineStartOffset + maxCommentStartOffsetInLine ||
                CharArrayUtil.shiftBackward(
                    text,
                    commentStartOffset,
                    refOffset - 1,
                    WHITESPACE + commentRange.allowedContinuationPrefixChars
                ) + 1 != commentStartOffset) {
                break;
            }
            CharSequence commentText = StringPattern.newBombedCharSequence(text.subSequence(commentStartOffset, continuationEndOffset));
            for (IndexPattern pattern : allIndexPatterns) {
                Pattern p = pattern.getPattern();
                if (p != null && p.matcher(commentText).find()) {
                    break outer;
                }
            }
            result.add(new TextRange(continuationStartOffset, continuationEndOffset));
        }
        return result.isEmpty() ? Collections.emptyList() : result;
    }

    private static class CommentRange {
        private static final Comparator<CommentRange> BY_START_OFFSET_THEN_BY_END_OFFSET =
            Comparator.comparingInt((CommentRange o) -> o.startOffset).thenComparingInt((CommentRange o) -> o.endOffset);

        private final int startOffset;
        private final int endOffset;
        private final int prefixLength;
        private final int suffixLength;
        private final String allowedContinuationPrefixChars;

        private CommentRange(int startOffset, int endOffset) {
            this(startOffset, endOffset, 0, 0, "");
        }

        private CommentRange(int startOffset, int endOffset, int prefixLength, int suffixLength, String chars) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.prefixLength = prefixLength;
            this.suffixLength = suffixLength;
            allowedContinuationPrefixChars = chars;
        }
    }
}
