// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package consulo.language.codeStyle.impl.internal;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.codeEditor.*;
import consulo.codeEditor.action.EditorActionManager;
import consulo.codeEditor.util.EditorUtil;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.document.Document;
import consulo.document.DocumentWindow;
import consulo.document.FileDocumentManager;
import consulo.document.RangeMarker;
import consulo.document.util.Segment;
import consulo.document.util.TextRange;
import consulo.document.util.TextRangeUtil;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.*;
import consulo.language.codeStyle.impl.internal.formatting.FormatConstants;
import consulo.language.codeStyle.impl.internal.formatting.FormattingProgressTask;
import consulo.language.codeStyle.internal.CodeStyleInternalHelper;
import consulo.language.codeStyle.internal.FormatterEx;
import consulo.language.editor.LanguageLineWrapPositionStrategy;
import consulo.language.editor.util.PsiUtilBase;
import consulo.language.file.FileViewProvider;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.impl.psi.SourceTreeToPsiMap;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.inject.impl.internal.InjectedLanguageUtil;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiLanguageInjectionHost;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.IdeActions;
import consulo.undoRedo.CommandProcessor;
import consulo.util.dataholder.Key;
import consulo.util.dataholder.UserDataHolder;
import consulo.util.lang.CharArrayUtil;
import consulo.util.lang.ObjectUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.awt.*;
import java.util.List;
import java.util.*;

public class CodeFormatterFacade {
    private static final Logger LOG = Logger.getInstance(CodeFormatterFacade.class);

    private static final String WRAP_LINE_COMMAND_NAME = "AutoWrapLongLine";

    /**
     * This key is used as a flag that indicates if {@code 'wrap long line during formatting'} activity is performed now.
     *
     * @see CommonCodeStyleSettings#WRAP_LONG_LINES
     */
    public static final Key<Boolean> WRAP_LONG_LINE_DURING_FORMATTING_IN_PROGRESS_KEY =
        new Key<>("WRAP_LONG_LINE_DURING_FORMATTING_IN_PROGRESS_KEY");

    private final CodeStyleSettings mySettings;
    private final FormatterTagHandler myTagHandler;
    private final int myRightMargin;
    private final boolean myCanChangeWhitespaceOnly;

    public CodeFormatterFacade(CodeStyleSettings settings, @Nullable Language language) {
        this(settings, language, false);
    }

    public CodeFormatterFacade(CodeStyleSettings settings, @Nullable Language language, boolean canChangeWhitespaceOnly) {
        mySettings = settings;
        myTagHandler = new FormatterTagHandler(settings);
        myRightMargin = mySettings.getRightMargin(language);
        myCanChangeWhitespaceOnly = canChangeWhitespaceOnly;
    }

    @RequiredUIAccess
    public ASTNode processElement(ASTNode element) {
        TextRange range = element.getTextRange();
        return processRange(element, range.getStartOffset(), range.getEndOffset());
    }

    @RequiredUIAccess
    public ASTNode processRange(ASTNode element, int startOffset, int endOffset) {
        PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(element);
        assert psiElement != null;
        PsiFile file = psiElement.getContainingFile();
        Document document = file.getViewProvider().getDocument();

        PsiElement elementToFormat = document instanceof DocumentWindow
            ? InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file)
            : psiElement;
        PsiFile fileToFormat = elementToFormat.getContainingFile();

        RangeMarker rangeMarker = null;
        FormattingModelBuilder builder = FormattingModelBuilder.forContext(fileToFormat);
        if (builder != null) {
            if (document != null && endOffset < document.getTextLength()) {
                rangeMarker = document.createRangeMarker(startOffset, endOffset);
            }

            TextRange range = preprocess(element, TextRange.create(startOffset, endOffset));
            if (document instanceof DocumentWindow documentWindow) {
                range = documentWindow.injectedToHost(range);
            }

            //SmartPsiElementPointer pointer = SmartPointerManager.getInstance(psiElement.getProject()).createSmartPsiElementPointer(psiElement);
            FormattingModel model =
                CoreFormatterUtil.buildModel(builder, elementToFormat, range, mySettings, FormattingMode.REFORMAT);
            if (file.getTextLength() > 0) {
                try {
                    FormatTextRanges ranges = new FormatTextRanges(range, true);
                    setDisabledRanges(fileToFormat, ranges);
                    FormatterEx.getInstanceEx().format(model, mySettings, mySettings.getIndentOptionsByFile(fileToFormat, range), ranges);

                    wrapLongLinesIfNecessary(file, document, startOffset, endOffset);
                }
                catch (IncorrectOperationException e) {
                    LOG.error(e);
                }
            }

            if (!psiElement.isValid()) {
                if (rangeMarker != null) {
                    PsiElement at = file.findElementAt(rangeMarker.getStartOffset());
                    PsiElement result = PsiTreeUtil.getParentOfType(at, psiElement.getClass(), false);
                    assert result != null;
                    rangeMarker.dispose();
                    return result.getNode();
                }
                else {
                    assert false;
                }
            }
//      return SourceTreeToPsiMap.psiElementToTree(pointer.getElement());
        }

        if (rangeMarker != null) {
            rangeMarker.dispose();
        }
        return element;
    }

    @RequiredUIAccess
    public void processText(@Nonnull PsiFile file, FormatTextRanges ranges, boolean doPostponedFormatting) {
        Project project = file.getProject();
        Document document = file.getViewProvider().getDocument();
        List<FormatTextRange> textRanges = ranges.getRanges();
        if (document instanceof DocumentWindow documentWindow) {
            file = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);
            for (FormatTextRange range : textRanges) {
                range.setTextRange(documentWindow.injectedToHost(range.getTextRange()));
            }
            document = documentWindow.getDelegate();
        }

        FormattingModelBuilder builder = FormattingModelBuilder.forContext(file);
        if (builder != null) {
            if (file.getTextLength() > 0) {
                LOG.assertTrue(document != null);
                ranges.setExtendedRanges(new FormattingRangesExtender(document, file).getExtendedRanges(ranges.getTextRanges()));
                try {
                    ASTNode containingNode = findContainingNode(file, ranges.getBoundRange());
                    if (containingNode != null) {
                        for (FormatTextRange range : ranges.getRanges()) {
                            TextRange rangeToUse = preprocess(containingNode, range.getTextRange());
                            range.setTextRange(rangeToUse);
                        }
                    }
                    if (doPostponedFormatting) {
                        invokePostponedFormatting(file, document, textRanges);
                    }
                    if (FormattingProgressTask.FORMATTING_CANCELLED_FLAG.get()) {
                        return;
                    }

                    TextRange formattingModelRange = ObjectUtil.notNull(ranges.getBoundRange(), file.getTextRange());

                    FormattingModel originalModel =
                        CoreFormatterUtil.buildModel(builder, file, formattingModelRange, mySettings, FormattingMode.REFORMAT);
                    FormattingModel model =
                        new DocumentBasedFormattingModel(originalModel, document, project, mySettings, file.getFileType(), file);

                    FormatterEx formatter = FormatterEx.getInstanceEx();
                    if (CodeStyleManager.getInstance(project).isSequentialProcessingAllowed()) {
                        formatter.setProgressTask(new FormattingProgressTask(project, file, document));
                    }

                    CommonCodeStyleSettings.IndentOptions indentOptions =
                        mySettings.getIndentOptionsByFile(file, textRanges.size() == 1 ? textRanges.get(0).getTextRange() : null);

                    setDisabledRanges(file, ranges);
                    formatter.format(model, mySettings, indentOptions, ranges);
                    for (FormatTextRange range : textRanges) {
                        TextRange textRange = range.getTextRange();
                        wrapLongLinesIfNecessary(file, document, textRange.getStartOffset(), textRange.getEndOffset());
                    }
                }
                catch (IncorrectOperationException e) {
                    LOG.error(e);
                }
            }
        }
    }

    @RequiredReadAction
    private void setDisabledRanges(@Nonnull PsiFile file, FormatTextRanges ranges) {
        Iterable<TextRange> excludedRangesIterable =
            TextRangeUtil.excludeRanges(file.getTextRange(), myTagHandler.getEnabledRanges(file.getNode(), file.getTextRange()));
        ranges.setDisabledRanges((Collection<TextRange>)excludedRangesIterable);
    }

    private static void invokePostponedFormatting(@Nonnull PsiFile file, Document document, List<FormatTextRange> textRanges) {
        RangeMarker[] markers = new RangeMarker[textRanges.size()];
        int i = 0;
        for (FormatTextRange range : textRanges) {
            TextRange textRange = range.getTextRange();
            int start = textRange.getStartOffset();
            int end = textRange.getEndOffset();
            if (start >= 0 && end > start && end <= document.getTextLength()) {
                markers[i] = document.createRangeMarker(textRange);
                markers[i].setGreedyToLeft(true);
                markers[i].setGreedyToRight(true);
                i++;
            }
        }
        PostprocessReformattingAspectImpl component =
            (PostprocessReformattingAspectImpl)PostprocessReformattingAspect.getInstance(file.getProject());
        FormattingProgressTask.FORMATTING_CANCELLED_FLAG.set(false);
        component.doPostponedFormatting(file.getViewProvider());
        i = 0;
        for (FormatTextRange range : textRanges) {
            RangeMarker marker = markers[i];
            if (marker != null) {
                range.setTextRange(TextRange.create(marker));
                marker.dispose();
            }
            i++;
        }
    }

    @Nullable
    @RequiredReadAction
    static ASTNode findContainingNode(@Nonnull PsiFile file, @Nullable TextRange range) {
        Language language = file.getLanguage();
        if (range == null) {
            return null;
        }
        FileViewProvider viewProvider = file.getViewProvider();
        PsiElement startElement = viewProvider.findElementAt(range.getStartOffset(), language);
        PsiElement endElement = viewProvider.findElementAt(range.getEndOffset() - 1, language);
        PsiElement commonParent =
            startElement != null && endElement != null ? PsiTreeUtil.findCommonParent(startElement, endElement) : null;
        ASTNode node = null;
        if (commonParent != null) {
            node = commonParent.getNode();
            // Find the topmost parent with the same range.
            ASTNode parent = node.getTreeParent();
            while (parent != null && parent.getTextRange().equals(commonParent.getTextRange())) {
                node = parent;
                parent = parent.getTreeParent();
            }
        }
        if (node == null) {
            node = file.getNode();
        }
        return node;
    }

    @RequiredReadAction
    private TextRange preprocess(@Nonnull ASTNode node, @Nonnull TextRange range) {
        TextRange result = range;
        PsiElement psi = node.getPsi();
        if (!psi.isValid()) {
            return result;
        }

        PsiFile file = psi.getContainingFile();

        // We use a set here because we encountered a situation when more than one PSI leaf points to the same injected fragment
        // (at least for sql injected into sql).
        LinkedHashSet<TextRange> injectedFileRangesSet = new LinkedHashSet<>();

        if (!psi.getProject().isDefault()) {
            List<DocumentWindow> injectedDocuments =
                InjectedLanguageManager.getInstance(file.getProject()).getCachedInjectedDocumentsInRange(file, file.getTextRange());
            if (!injectedDocuments.isEmpty()) {
                for (DocumentWindow injectedDocument : injectedDocuments) {
                    injectedFileRangesSet.add(TextRange.from(injectedDocument.injectedToHost(0), injectedDocument.getTextLength()));
                }
            }
            else {
                Collection<PsiLanguageInjectionHost> injectionHosts = collectInjectionHosts(file, range);
                PsiLanguageInjectionHost.InjectedPsiVisitor visitor = (injectedPsi, places) -> {
                    for (PsiLanguageInjectionHost.Shred place : places) {
                        Segment rangeMarker = place.getHostRangeMarker();
                        injectedFileRangesSet.add(TextRange.create(rangeMarker.getStartOffset(), rangeMarker.getEndOffset()));
                    }
                };
                for (PsiLanguageInjectionHost host : injectionHosts) {
                    InjectedLanguageManager.getInstance(file.getProject()).enumerate(host, visitor);
                }
            }
        }

        if (!injectedFileRangesSet.isEmpty()) {
            List<TextRange> ranges = new ArrayList<>(injectedFileRangesSet);
            Collections.reverse(ranges);
            for (TextRange injectedFileRange : ranges) {
                int startHostOffset = injectedFileRange.getStartOffset();
                int endHostOffset = injectedFileRange.getEndOffset();
                if (startHostOffset >= range.getStartOffset() && endHostOffset <= range.getEndOffset()) {
                    PsiFile injected = InjectedLanguageUtil.findInjectedPsiNoCommit(file, startHostOffset);
                    if (injected != null) {
                        TextRange initialInjectedRange = TextRange.create(0, injected.getTextLength());
                        TextRange injectedRange = initialInjectedRange;
                        for (PreFormatProcessor processor : PreFormatProcessor.EP_NAME.getExtensionList()) {
                            if (processor.changesWhitespacesOnly() || !myCanChangeWhitespaceOnly) {
                                injectedRange = processor.process(injected.getNode(), injectedRange);
                            }
                        }

                        // Allow only range expansion (not reduction) for injected context.
                        if ((initialInjectedRange.getStartOffset() > injectedRange.getStartOffset()
                            && initialInjectedRange.getStartOffset() > 0)
                            || (initialInjectedRange.getEndOffset() < injectedRange.getEndOffset()
                            && initialInjectedRange.getEndOffset() < injected.getTextLength())) {
                            range = TextRange.create(
                                range.getStartOffset() + injectedRange.getStartOffset() - initialInjectedRange.getStartOffset(),
                                range.getEndOffset() + initialInjectedRange.getEndOffset() - injectedRange.getEndOffset()
                            );
                        }
                    }
                }
            }
        }

        if (!mySettings.FORMATTER_TAGS_ENABLED) {
            for (PreFormatProcessor processor : PreFormatProcessor.EP_NAME.getExtensionList()) {
                if (processor.changesWhitespacesOnly() || !myCanChangeWhitespaceOnly) {
                    result = processor.process(node, result);
                }
            }
        }
        else {
            result = preprocessEnabledRanges(node, result);
        }

        return result;
    }

    private TextRange preprocessEnabledRanges(@Nonnull ASTNode node, @Nonnull TextRange range) {
        TextRange result = TextRange.create(range.getStartOffset(), range.getEndOffset());
        List<TextRange> enabledRanges = myTagHandler.getEnabledRanges(node, result);
        int delta = 0;
        for (TextRange enabledRange : enabledRanges) {
            enabledRange = enabledRange.shiftRight(delta);
            for (PreFormatProcessor processor : PreFormatProcessor.EP_NAME.getExtensionList()) {
                if (processor.changesWhitespacesOnly() || !myCanChangeWhitespaceOnly) {
                    TextRange processedRange = processor.process(node, enabledRange);
                    delta += processedRange.getLength() - enabledRange.getLength();
                }
            }
        }
        result = result.grown(delta);
        return result;
    }

    @Nonnull
    @RequiredReadAction
    private static Collection<PsiLanguageInjectionHost> collectInjectionHosts(@Nonnull PsiFile file, @Nonnull TextRange range) {
        Stack<PsiElement> toProcess = new Stack<>();
        for (PsiElement e = file.findElementAt(range.getStartOffset()); e != null; e = e.getNextSibling()) {
            if (e.getTextRange().getStartOffset() >= range.getEndOffset()) {
                break;
            }
            toProcess.push(e);
        }
        if (toProcess.isEmpty()) {
            return Collections.emptySet();
        }
        Set<PsiLanguageInjectionHost> result = null;
        while (!toProcess.isEmpty()) {
            PsiElement e = toProcess.pop();
            if (e instanceof PsiLanguageInjectionHost languageInjectionHost) {
                if (result == null) {
                    result = new HashSet<>();
                }
                result.add(languageInjectionHost);
            }
            else {
                for (PsiElement child = e.getFirstChild(); child != null; child = child.getNextSibling()) {
                    if (e.getTextRange().getStartOffset() >= range.getEndOffset()) {
                        break;
                    }
                    toProcess.push(child);
                }
            }
        }
        return result == null ? Collections.emptySet() : result;
    }


    /**
     * Inspects all lines of the given document and wraps all of them that exceed {@link CodeStyleSettings#getRightMargin(Language)}
     * right margin}.
     * <p/>
     * I.e. the algorithm is to do the following for every line:
     * <p/>
     * <pre>
     * <ol>
     *   <li>
     *      Check if the line exceeds {@link CodeStyleSettings#getRightMargin(Language)}  right margin}. Go to the next line in the case of
     *      negative answer;
     *   </li>
     *   <li>Determine line wrap position; </li>
     *   <li>
     *      Perform 'smart wrap', i.e. not only wrap the line but insert additional characters over than line feed if necessary.
     *      For example consider that we wrap a single-line comment - we need to insert comment symbols on a start of the wrapped
     *      part as well. Generally, we get the same behavior as during pressing 'Enter' at wrap position during editing document;
     *   </li>
     * </ol>
     * </pre>
     *
     * @param file        file that holds parsed document tree
     * @param document    target document
     * @param startOffset start offset of the first line to check for wrapping (inclusive)
     * @param endOffset   end offset of the first line to check for wrapping (exclusive)
     */
    @RequiredUIAccess
    private void wrapLongLinesIfNecessary(@Nonnull PsiFile file, @Nullable Document document, int startOffset, int endOffset) {
        if (!mySettings.getCommonSettings(file.getLanguage()).WRAP_LONG_LINES ||
            PostprocessReformattingAspect.getInstance(file.getProject()).isViewProviderLocked(file.getViewProvider()) ||
            document == null) {
            return;
        }

        FormatterTagHandler formatterTagHandler = new FormatterTagHandler(CodeStyle.getSettings(file));
        List<TextRange> enabledRanges = formatterTagHandler.getEnabledRanges(file.getNode(), new TextRange(startOffset, endOffset));

        VirtualFile vFile = FileDocumentManager.getInstance().getFile(document);
        if ((vFile == null || vFile instanceof LightVirtualFile) && !Application.get().isUnitTestMode()) {
            // we assume that control flow reaches this place when the document is backed by a "virtual" file so any changes made by
            // a formatter affect only PSI and it is out of sync with a document text
            return;
        }

        Editor editor = PsiUtilBase.findEditor(file);
        EditorFactory editorFactory = null;
        if (editor == null) {
            if (!Application.get().isDispatchThread()) {
                return;
            }
            editorFactory = EditorFactory.getInstance();
            editor = editorFactory.createEditor(document, file.getProject(), file.getVirtualFile(), false);
        }
        try {
            Editor editorToUse = editor;
            Application.get().runWriteAction(() -> {
                CaretModel caretModel = editorToUse.getCaretModel();
                int caretOffset = caretModel.getOffset();
                RangeMarker caretMarker = editorToUse.getDocument().createRangeMarker(caretOffset, caretOffset);
                doWrapLongLinesIfNecessary(
                    editorToUse,
                    file.getProject(),
                    editorToUse.getDocument(),
                    startOffset,
                    endOffset,
                    enabledRanges
                );
                if (caretMarker.isValid() && caretModel.getOffset() != caretMarker.getStartOffset()) {
                    caretModel.moveToOffset(caretMarker.getStartOffset());
                }
            });
        }
        finally {
            PsiDocumentManager documentManager = PsiDocumentManager.getInstance(file.getProject());
            if (documentManager.isUncommited(document)) {
                documentManager.commitDocument(document);
            }
            if (editorFactory != null) {
                editorFactory.releaseEditor(editor);
            }
        }
    }

    @RequiredUIAccess
    public void doWrapLongLinesIfNecessary(
        @Nonnull Editor editor,
        @Nonnull Project project,
        @Nonnull Document document,
        int startOffset,
        int endOffset,
        List<? extends TextRange> enabledRanges
    ) {
        // Normalization.
        int startOffsetToUse = Math.min(document.getTextLength(), Math.max(0, startOffset));
        int endOffsetToUse = Math.min(document.getTextLength(), Math.max(0, endOffset));

        LineWrapPositionStrategy strategy = LanguageLineWrapPositionStrategy.forEditor(editor);
        CharSequence text = document.getCharsSequence();
        int startLine = document.getLineNumber(startOffsetToUse);
        int endLine = document.getLineNumber(Math.max(0, endOffsetToUse - 1));
        int maxLine = Math.min(document.getLineCount(), endLine + 1);
        int tabSize = EditorUtil.getTabSize(editor);
        if (tabSize <= 0) {
            tabSize = 1;
        }
        int spaceSize = CodeStyleInternalHelper.getInstance().getSpaceWidth(Font.PLAIN, editor);
        int[] shifts = new int[2];
        // shifts[0] - lines shift.
        // shift[1] - offset shift.
        int cumulativeShift = 0;

        for (int line = startLine; line < maxLine; line++) {
            int startLineOffset = document.getLineStartOffset(line);
            int endLineOffset = document.getLineEndOffset(line);
            if (!canWrapLine(
                Math.max(startOffsetToUse, startLineOffset),
                Math.min(endOffsetToUse, endLineOffset),
                cumulativeShift,
                enabledRanges
            )) {
                continue;
            }

            int preferredWrapPosition =
                calculatePreferredWrapPosition(editor, text, tabSize, spaceSize, startLineOffset, endLineOffset, endOffsetToUse);

            if (preferredWrapPosition < 0 || preferredWrapPosition >= endLineOffset) {
                continue;
            }
            if (preferredWrapPosition >= endOffsetToUse) {
                return;
            }

            // We know that current line exceeds right margin if control flow reaches this place, so, wrap it.
            int wrapOffset = strategy.calculateWrapPosition(
                document,
                editor.getProject(),
                Math.max(startLineOffset, startOffsetToUse),
                Math.min(endLineOffset, endOffsetToUse),
                preferredWrapPosition,
                false,
                false
            );
            if (wrapOffset < 0 // No appropriate wrap position is found.
                // No point in splitting line when its left part contains only white spaces, example:
                //    line start -> |                   | <- right margin
                //                  |   aaaaaaaaaaaaaaaa|aaaaaaaaaaaaaaaaaaaa() <- don't want to wrap this line even if it exceeds right margin
                || CharArrayUtil.shiftBackward(text, startLineOffset, wrapOffset - 1, " \t") < startLineOffset) {
                continue;
            }

            // Move caret to the target position and emulate pressing <enter>.
            editor.getCaretModel().moveToOffset(wrapOffset);
            emulateEnter(editor, project, shifts);

            //If number of inserted symbols on new line after wrapping more or equal then symbols left on previous line
            //there was no point to wrapping it, so reverting to before wrapping version
            if (shifts[1] - 1 >= wrapOffset - startLineOffset) {
                document.deleteString(wrapOffset, wrapOffset + shifts[1]);
            }
            else {
                // We know that number of lines is just increased, hence, update the data accordingly.
                maxLine += shifts[0];
                endOffsetToUse += shifts[1];
                cumulativeShift += shifts[1];
            }

        }
    }

    private static boolean canWrapLine(int startOffset, int endOffset, int offsetShift, @Nonnull List<? extends TextRange> enabledRanges) {
        for (TextRange range : enabledRanges) {
            if (range.containsOffset(startOffset - offsetShift) && range.containsOffset(endOffset - offsetShift)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Emulates pressing {@code Enter} at current caret position.
     *
     * @param editor  target editor
     * @param project target project
     * @param shifts  two-elements array which is expected to be filled with the following info:
     *                1. The first element holds added lines number;
     *                2. The second element holds added symbols number;
     */
    @RequiredUIAccess
    private static void emulateEnter(@Nonnull Editor editor, @Nonnull Project project, int[] shifts) {
        DataContext dataContext = prepareContext(editor.getComponent(), project);
        int caretOffset = editor.getCaretModel().getOffset();
        Document document = editor.getDocument();
        SelectionModel selectionModel = editor.getSelectionModel();
        int startSelectionOffset = 0;
        int endSelectionOffset = 0;
        boolean restoreSelection = selectionModel.hasSelection();
        if (restoreSelection) {
            startSelectionOffset = selectionModel.getSelectionStart();
            endSelectionOffset = selectionModel.getSelectionEnd();
            selectionModel.removeSelection();
        }
        int textLengthBeforeWrap = document.getTextLength();
        int lineCountBeforeWrap = document.getLineCount();

        DataManager.getInstance().saveInDataContext(dataContext, WRAP_LONG_LINE_DURING_FORMATTING_IN_PROGRESS_KEY, true);
        CommandProcessor commandProcessor = CommandProcessor.getInstance();
        try {
            Runnable command =
                () -> EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER).execute(editor, dataContext);
            if (!commandProcessor.hasCurrentCommand()) {
                commandProcessor.newCommand()
                    .project(editor.getProject())
                    .name(LocalizeValue.ofNullable(WRAP_LINE_COMMAND_NAME))
                    .run(command);
            }
            else {
                command.run();
            }
        }
        finally {
            DataManager.getInstance().saveInDataContext(dataContext, WRAP_LONG_LINE_DURING_FORMATTING_IN_PROGRESS_KEY, null);
        }
        int symbolsDiff = document.getTextLength() - textLengthBeforeWrap;
        if (restoreSelection) {
            int newSelectionStart = startSelectionOffset;
            int newSelectionEnd = endSelectionOffset;
            if (startSelectionOffset >= caretOffset) {
                newSelectionStart += symbolsDiff;
            }
            if (endSelectionOffset >= caretOffset) {
                newSelectionEnd += symbolsDiff;
            }
            selectionModel.setSelection(newSelectionStart, newSelectionEnd);
        }
        shifts[0] = document.getLineCount() - lineCountBeforeWrap;
        shifts[1] = symbolsDiff;
    }

    /**
     * Checks if it's worth to try to wrap target line (it's long enough) and tries to calculate preferred wrap position.
     *
     * @param editor               target editor
     * @param text                 text contained at the given editor
     * @param tabSize              tab space to use (number of visual columns occupied by a tab)
     * @param spaceSize            space width in pixels
     * @param startLineOffset      start offset of the text line to process
     * @param endLineOffset        end offset of the text line to process
     * @param targetRangeEndOffset target text region's end offset
     * @return negative value if no wrapping should be performed for the target line;
     * preferred wrap position otherwise
     */
    private int calculatePreferredWrapPosition(
        @Nonnull Editor editor,
        @Nonnull CharSequence text,
        int tabSize,
        int spaceSize,
        int startLineOffset,
        int endLineOffset,
        int targetRangeEndOffset
    ) {
        boolean hasTabs = false;
        boolean canOptimize = true;
        boolean hasNonSpaceSymbols = false;
        loop:
        for (int i = startLineOffset; i < Math.min(endLineOffset, targetRangeEndOffset); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\t': {
                    hasTabs = true;
                    if (hasNonSpaceSymbols) {
                        canOptimize = false;
                        break loop;
                    }
                }
                case ' ':
                    break;
                default:
                    hasNonSpaceSymbols = true;
            }
        }

        int reservedWidthInColumns = FormatConstants.getReservedLineWrapWidthInColumns(editor);

        if (!hasTabs) {
            return wrapPositionForTextWithoutTabs(startLineOffset, endLineOffset, targetRangeEndOffset, reservedWidthInColumns);
        }
        else if (canOptimize) {
            return wrapPositionForTabbedTextWithOptimization(
                text,
                tabSize,
                startLineOffset,
                endLineOffset,
                targetRangeEndOffset,
                reservedWidthInColumns
            );
        }
        else {
            return wrapPositionForTabbedTextWithoutOptimization(
                editor,
                text,
                spaceSize,
                startLineOffset,
                endLineOffset,
                targetRangeEndOffset,
                reservedWidthInColumns
            );
        }
    }

    private int wrapPositionForTextWithoutTabs(
        int startLineOffset,
        int endLineOffset,
        int targetRangeEndOffset,
        int reservedWidthInColumns
    ) {
        if (Math.min(endLineOffset, targetRangeEndOffset) - startLineOffset > myRightMargin) {
            return startLineOffset + myRightMargin - reservedWidthInColumns;
        }
        return -1;
    }

    private int wrapPositionForTabbedTextWithOptimization(
        @Nonnull CharSequence text,
        int tabSize,
        int startLineOffset,
        int endLineOffset,
        int targetRangeEndOffset,
        int reservedWidthInColumns
    ) {
        int width = 0;
        int symbolWidth;
        int result = Integer.MAX_VALUE;
        boolean wrapLine = false;
        for (int i = startLineOffset; i < Math.min(endLineOffset, targetRangeEndOffset); i++) {
            char c = text.charAt(i);
            symbolWidth = c == '\t' ? tabSize - (width % tabSize) : 1;
            if (width + symbolWidth + reservedWidthInColumns >= myRightMargin
                && (Math.min(endLineOffset, targetRangeEndOffset) - i) >= reservedWidthInColumns) {
                // Remember preferred position.
                result = i - 1;
            }
            if (width + symbolWidth >= myRightMargin) {
                wrapLine = true;
                break;
            }
            width += symbolWidth;
        }
        return wrapLine ? result : -1;
    }

    private int wrapPositionForTabbedTextWithoutOptimization(
        @Nonnull Editor editor,
        @Nonnull CharSequence text,
        int spaceSize,
        int startLineOffset,
        int endLineOffset,
        int targetRangeEndOffset,
        int reservedWidthInColumns
    ) {
        int width = 0;
        int x = 0;
        int newX;
        int symbolWidth;
        int result = Integer.MAX_VALUE;
        boolean wrapLine = false;
        for (int i = startLineOffset; i < Math.min(endLineOffset, targetRangeEndOffset); i++) {
            char c = text.charAt(i);
            if (c == '\t') {
                newX = CodeStyleInternalHelper.getInstance().nextTabStop(x, editor);
                int diffInPixels = newX - x;
                symbolWidth = diffInPixels / spaceSize;
                if (diffInPixels % spaceSize > 0) {
                    symbolWidth++;
                }
            }
            else {
                newX = x + CodeStyleInternalHelper.getInstance().charWidth(c, Font.PLAIN, editor);
                symbolWidth = 1;
            }
            if (width + symbolWidth + reservedWidthInColumns >= myRightMargin
                && (Math.min(endLineOffset, targetRangeEndOffset) - i) >= reservedWidthInColumns) {
                result = i - 1;
            }
            if (width + symbolWidth >= myRightMargin) {
                wrapLine = true;
                break;
            }
            x = newX;
            width += symbolWidth;
        }
        return wrapLine ? result : -1;
    }

    @Nonnull
    private static DataContext prepareContext(@Nonnull Component component, @Nonnull Project project) {
        // There is a possible case that formatting is performed from project view and editor is not opened yet. The problem is that
        // its data context doesn't contain information about project then. So, we explicitly support that here (see IDEA-72791).
        DataContext baseDataContext = DataManager.getInstance().getDataContext(component);
        return new DelegatingDataContext(baseDataContext) {
            @Override
            public Object getData(@Nonnull Key dataId) {
                Object result = baseDataContext.getData(dataId);
                if (result == null && Project.KEY == dataId) {
                    result = project;
                }
                return result;
            }
        };
    }

    private static class DelegatingDataContext implements DataContext, UserDataHolder {
        private final DataContext myDataContextDelegate;
        private final UserDataHolder myDataHolderDelegate;

        DelegatingDataContext(DataContext delegate) {
            myDataContextDelegate = delegate;
            myDataHolderDelegate = delegate instanceof UserDataHolder userDataHolder ? userDataHolder : null;
        }

        @Override
        public Object getData(@Nonnull Key dataId) {
            return myDataContextDelegate.getData(dataId);
        }

        @Override
        public <T> T getUserData(@Nonnull Key<T> key) {
            return myDataHolderDelegate == null ? null : myDataHolderDelegate.getUserData(key);
        }

        @Override
        public <T> void putUserData(@Nonnull Key<T> key, @Nullable T value) {
            if (myDataHolderDelegate != null) {
                myDataHolderDelegate.putUserData(key, value);
            }
        }
    }
}

