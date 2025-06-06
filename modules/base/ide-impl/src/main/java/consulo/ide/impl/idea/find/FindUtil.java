/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package consulo.ide.impl.idea.find;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.codeEditor.*;
import consulo.codeEditor.action.EditorActionManager;
import consulo.codeEditor.action.EditorActionUtil;
import consulo.codeEditor.event.CaretAdapter;
import consulo.codeEditor.event.CaretEvent;
import consulo.codeEditor.event.CaretListener;
import consulo.codeEditor.markup.HighlighterLayer;
import consulo.codeEditor.markup.HighlighterTargetArea;
import consulo.codeEditor.markup.RangeHighlighter;
import consulo.codeEditor.markup.RangeHighlighterEx;
import consulo.colorScheme.EditorColorsManager;
import consulo.colorScheme.TextAttributes;
import consulo.dataContext.DataContext;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.document.ReadOnlyFragmentModificationException;
import consulo.document.util.TextRange;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.TextEditor;
import consulo.find.*;
import consulo.find.localize.FindLocalize;
import consulo.ide.impl.find.PsiElement2UsageTargetAdapter;
import consulo.ide.impl.idea.codeInsight.hint.HintManagerImpl;
import consulo.ide.impl.idea.find.impl.FindInProjectUtil;
import consulo.ide.impl.idea.find.replaceInProject.ReplaceInProjectManager;
import consulo.ide.impl.idea.openapi.editor.actions.IncrementalFindAction;
import consulo.fileEditor.history.IdeDocumentHistory;
import consulo.ide.impl.idea.openapi.keymap.KeymapUtil;
import consulo.ide.impl.idea.openapi.util.JDOMUtil;
import consulo.ide.impl.idea.ui.LightweightHintImpl;
import consulo.ide.impl.idea.usages.impl.UsageViewImpl;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.language.editor.hint.HintManager;
import consulo.language.editor.ui.awt.HintUtil;
import consulo.language.psi.*;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.IdeActions;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.undoRedo.CommandProcessor;
import consulo.usage.*;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class FindUtil {
    private static final Key<Direction> KEY = Key.create("FindUtil.KEY");

    private FindUtil() {
    }

    @Nullable
    private static VirtualFile getVirtualFile(@Nonnull Editor myEditor) {
        Project project = myEditor.getProject();
        PsiFile file = project != null ? PsiDocumentManager.getInstance(project).getPsiFile(myEditor.getDocument()) : null;
        return file != null ? file.getVirtualFile() : null;
    }

    public static void initStringToFindWithSelection(FindModel findModel, Editor editor) {
        if (editor != null) {
            String s = editor.getSelectionModel().getSelectedText();
            if (s != null && s.length() < 10000) {
                FindModel.initStringToFindNoMultiline(findModel, s);
            }
        }
    }

    private static boolean isMultilineSelection(Editor editor) {
        SelectionModel selectionModel = editor != null ? editor.getSelectionModel() : null;
        if (selectionModel != null) {
            String selectedText = selectionModel.getSelectedText();
            if (selectedText != null && selectedText.contains("\n")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWholeLineSelection(Editor editor) {
        SelectionModel selectionModel = editor != null ? editor.getSelectionModel() : null;
        if (selectionModel != null) {
            String selectedText = selectionModel.getSelectedText();
            final Document document = editor.getDocument();
            final int line = document.getLineNumber(selectionModel.getSelectionStart());
            final String lineText = document.getText(new TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)));
            if (lineText.trim().equals(selectedText)) {
                return true;
            }
        }
        return false;
    }

    public static void configureFindModel(boolean replace, @Nullable Editor editor, FindModel model, boolean firstSearch) {
        boolean isGlobal = true;
        String stringToFind = null;
        final SelectionModel selectionModel = editor != null ? editor.getSelectionModel() : null;
        String selectedText = selectionModel != null ? selectionModel.getSelectedText() : null;
        if (!StringUtil.isEmpty(selectedText)) {
            if (replace && (isMultilineSelection(editor) || isWholeLineSelection(editor))) {
                isGlobal = false;
                stringToFind = model.getStringToFind();
            }
            else if (isMultilineSelection(editor)) {
                model.setMultiline(true);
            }
            if (stringToFind == null) {
                stringToFind = selectedText;
            }
        }
        else {
            stringToFind = firstSearch ? "" : model.getStringToFind();
        }
        model.setReplaceState(replace);
        model.setStringToFind(stringToFind);
        model.setGlobal(isGlobal);
        model.setPromptOnReplace(false);
    }

    public static void updateFindInFileModel(@Nullable Project project, @Nonnull FindModel with, boolean saveFindString) {
        FindModel model = FindManager.getInstance(project).getFindInFileModel();
        model.setCaseSensitive(with.isCaseSensitive());
        model.setWholeWordsOnly(with.isWholeWordsOnly());
        model.setRegularExpressions(with.isRegularExpressions());
        model.setSearchContext(with.getSearchContext());

        if (saveFindString && !with.getStringToFind().isEmpty()) {
            model.setStringToFind(with.getStringToFind());
        }

        if (with.isReplaceState()) {
            model.setPreserveCase(with.isPreserveCase());
            if (saveFindString) {
                model.setStringToReplace(with.getStringToReplace());
            }
        }
    }

    public static void useFindStringFromFindInFileModel(FindModel findModel, Editor editor) {
        if (editor != null) {
            EditorSearchSession editorSearchSession = EditorSearchSession.get(editor);
            if (editorSearchSession != null) {
                FindModel currentFindModel = editorSearchSession.getFindModel();
                findModel.setStringToFind(currentFindModel.getStringToFind());
                if (findModel.isReplaceState()) {
                    findModel.setStringToReplace(currentFindModel.getStringToReplace());
                }
            }
        }
    }

    private enum Direction {
        UP,
        DOWN
    }

    @RequiredUIAccess
    public static void findWordAtCaret(Project project, Editor editor) {
        int caretOffset = editor.getCaretModel().getOffset();
        Document document = editor.getDocument();
        CharSequence text = document.getCharsSequence();
        int start = 0;
        int end = document.getTextLength();
        if (!editor.getSelectionModel().hasSelection()) {
            for (int i = caretOffset - 1; i >= 0; i--) {
                char c = text.charAt(i);
                if (!Character.isJavaIdentifierPart(c)) {
                    start = i + 1;
                    break;
                }
            }
            for (int i = caretOffset; i < document.getTextLength(); i++) {
                char c = text.charAt(i);
                if (!Character.isJavaIdentifierPart(c)) {
                    end = i;
                    break;
                }
            }
        }
        else {
            start = editor.getSelectionModel().getSelectionStart();
            end = editor.getSelectionModel().getSelectionEnd();
        }
        if (start >= end) {
            return;
        }
        FindManager findManager = FindManager.getInstance(project);
        FindInProjectSettings findInProjectSettings = FindInProjectSettings.getInstance(project);
        String s = text.subSequence(start, end).toString();
        findInProjectSettings.addStringToFind(s);
        findManager.getFindInFileModel().setStringToFind(s);
        findManager.setFindWasPerformed();
        findManager.clearFindingNextUsageInFile();
        FindModel model = new FindModel();
        model.setStringToFind(s);
        model.setCaseSensitive(true);
        model.setWholeWordsOnly(!editor.getSelectionModel().hasSelection());

        EditorSearchSession searchSession = EditorSearchSession.get(editor);
        if (searchSession != null) {
            searchSession.setTextInField(model.getStringToFind());
        }

        findManager.setFindNextModel(model);
        doSearch(project, editor, caretOffset, true, model, true);
    }

    @RequiredUIAccess
    public static void find(@Nonnull final Project project, @Nonnull final Editor editor) {
        UIAccess.assertIsUIThread();
        final FindManager findManager = FindManager.getInstance(project);
        String s = editor.getSelectionModel().getSelectedText();

        final FindModel model = findManager.getFindInFileModel().clone();
        if (StringUtil.isEmpty(s)) {
            model.setGlobal(true);
        }
        else {
            if (s.indexOf('\n') >= 0) {
                model.setGlobal(false);
            }
            else {
                model.setStringToFind(s);
                model.setGlobal(true);
            }
        }

        model.setReplaceState(false);
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        model.setFindAllEnabled(psiFile != null);

        findManager.showFindDialog(
            model,
            () -> {
                if (model.isFindAll()) {
                    findManager.setFindNextModel(model);
                    findAllAndShow(project, psiFile, model);
                    return;
                }

                if (!model.isGlobal() && editor.getSelectionModel().hasSelection()) {
                    int offset = model.isForward()
                        ? editor.getSelectionModel().getSelectionStart()
                        : editor.getSelectionModel().getSelectionEnd();
                    ScrollType scrollType = model.isForward() ? ScrollType.CENTER_DOWN : ScrollType.CENTER_UP;
                    moveCaretAndDontChangeSelection(editor, offset, scrollType);
                }

                int offset;
                if (model.isGlobal()) {
                    if (model.isFromCursor()) {
                        offset = editor.getCaretModel().getOffset();
                    }
                    else {
                        offset = model.isForward() ? 0 : editor.getDocument().getTextLength();
                    }
                }
                else {
                    // in selection

                    if (!editor.getSelectionModel().hasSelection()) {
                        // TODO[anton] actually, this should never happen - Find dialog should not allow such combination
                        findManager.setFindNextModel(null);
                        return;
                    }

                    offset =
                        model.isForward() ? editor.getSelectionModel().getSelectionStart() : editor.getSelectionModel().getSelectionEnd();
                }

                findManager.setFindNextModel(null);
                findManager.getFindInFileModel().copyFrom(model);
                doSearch(project, editor, offset, true, model, true);
            }
        );
    }

    @Nullable
    static List<Usage> findAll(@Nonnull Project project, @Nonnull PsiFile psiFile, @Nonnull FindModel findModel) {
        if (project.isDisposed()) {
            return null;
        }
        psiFile = (PsiFile)psiFile.getNavigationElement();
        if (psiFile == null) {
            return null;
        }
        Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
        if (document == null) {
            return null;
        }

        CharSequence text = document.getCharsSequence();
        int textLength = document.getTextLength();
        FindManager findManager = FindManager.getInstance(project);
        findModel.setForward(true); // when find all there is no diff in direction

        int offset = 0;
        VirtualFile virtualFile = psiFile.getVirtualFile();

        final List<Usage> usages = new ArrayList<>();
        while (offset < textLength) {
            FindResult result = findManager.findString(text, offset, findModel, virtualFile);
            if (!result.isStringFound()) {
                break;
            }

            usages.add(new UsageInfo2UsageAdapter(new UsageInfo(psiFile, result.getStartOffset(), result.getEndOffset())));

            final int prevOffset = offset;
            offset = result.getEndOffset();

            if (prevOffset == offset) {
                // for regular expr the size of the match could be zero -> could be infinite loop in finding usages!
                ++offset;
            }
        }
        return usages;
    }

    @RequiredReadAction
    static void findAllAndShow(@Nonnull Project project, @Nonnull PsiFile psiFile, @Nonnull FindModel findModel) {
        findModel.setCustomScope(true);
        findModel.setProjectScope(false);
        findModel.setCustomScopeName("File " + psiFile.getName());
        List<Usage> usages = findAll(project, psiFile, findModel);
        if (usages == null) {
            return;
        }
        final UsageTarget[] usageTargets = {new FindInProjectUtil.StringUsageTarget(project, findModel)};
        final UsageViewPresentation usageViewPresentation = FindInProjectUtil.setupViewPresentation(false, findModel);
        UsageView view =
            UsageViewManager.getInstance(project).showUsages(usageTargets, usages.toArray(Usage.EMPTY_ARRAY), usageViewPresentation);
        view.setRerunAction(new AbstractAction() {
            @Override
            @RequiredReadAction
            public void actionPerformed(ActionEvent e) {
                findAllAndShow(project, psiFile, findModel);
            }

            @Override
            public boolean isEnabled() {
                return !project.isDisposed() && psiFile.isValid();
            }
        });
    }

    @RequiredUIAccess
    public static void searchBack(Project project, FileEditor fileEditor, @Nullable DataContext dataContext) {
        if (fileEditor instanceof TextEditor textEditor) {
            searchBack(project, textEditor.getEditor(), dataContext);
        }
    }

    @RequiredUIAccess
    public static void searchBack(Project project, Editor editor, @Nullable DataContext context) {
        FindManager findManager = FindManager.getInstance(project);
        if (!findManager.findWasPerformed() && !findManager.selectNextOccurrenceWasPerformed()) {
            new IncrementalFindAction().getHandler().execute(editor, context);
            return;
        }

        FindModel model = findManager.getFindNextModel(editor);
        if (model == null) {
            model = findManager.getFindInFileModel();
        }
        model = model.clone();
        model.setForward(!model.isForward());
        if (!model.isGlobal() && !editor.getSelectionModel().hasSelection()) {
            model.setGlobal(true);
        }

        int offset;
        if (Direction.UP.equals(editor.getUserData(KEY)) && !model.isForward()) {
            offset = editor.getDocument().getTextLength();
        }
        else if (Direction.DOWN.equals(editor.getUserData(KEY)) && model.isForward()) {
            offset = 0;
        }
        else {
            editor.putUserData(KEY, null);
            offset = editor.getCaretModel().getOffset();
            if (!model.isForward() && offset > 0) {
                offset--;
            }
        }
        searchAgain(project, editor, offset, model);
    }

    @RequiredUIAccess
    public static boolean searchAgain(Project project, FileEditor fileEditor, @Nullable DataContext context) {
        return fileEditor instanceof TextEditor textEditor && searchAgain(project, textEditor.getEditor(), context);
    }

    @RequiredUIAccess
    private static boolean searchAgain(final Project project, final Editor editor, @Nullable DataContext context) {
        FindManager findManager = FindManager.getInstance(project);
        if (!findManager.findWasPerformed() && !findManager.selectNextOccurrenceWasPerformed()) {
            new IncrementalFindAction().getHandler().execute(editor, context);
            return false;
        }

        FindModel model = findManager.getFindNextModel(editor);
        if (model == null) {
            model = findManager.getFindInFileModel();
        }
        model = model.clone();

        int offset;
        if (Direction.DOWN.equals(editor.getUserData(KEY)) && model.isForward()) {
            offset = 0;
        }
        else if (Direction.UP.equals(editor.getUserData(KEY)) && !model.isForward()) {
            offset = editor.getDocument().getTextLength();
        }
        else {
            editor.putUserData(KEY, null);
            offset = model.isGlobal() && model.isForward()
                ? editor.getSelectionModel().getSelectionEnd()
                : editor.getCaretModel().getOffset();
            if (!model.isForward() && offset > 0) {
                offset--;
            }
        }
        return searchAgain(project, editor, offset, model);
    }

    @RequiredUIAccess
    private static boolean searchAgain(Project project, Editor editor, int offset, FindModel model) {
        if (!model.isGlobal() && !editor.getSelectionModel().hasSelection()) {
            model.setGlobal(true);
        }
        model.setFromCursor(false);
        if (model.isReplaceState()) {
            model.setPromptOnReplace(true);
            model.setReplaceAll(false);
            replace(project, editor, offset, model);
            return true;
        }
        else {
            doSearch(project, editor, offset, true, model, true);
            return false;
        }
    }

    @RequiredUIAccess
    public static void replace(final Project project, final Editor editor) {
        final FindManager findManager = FindManager.getInstance(project);
        final FindModel model = findManager.getFindInFileModel().clone();
        final String s = editor.getSelectionModel().getSelectedText();
        if (!StringUtil.isEmpty(s)) {
            if (s.indexOf('\n') >= 0) {
                model.setGlobal(false);
            }
            else {
                model.setStringToFind(s);
                model.setGlobal(true);
            }
        }
        else {
            model.setGlobal(true);
        }
        model.setReplaceState(true);

        findManager.showFindDialog(
            model,
            () -> {
                if (!model.isGlobal() && editor.getSelectionModel().hasSelection()) {
                    int offset = model.isForward()
                        ? editor.getSelectionModel().getSelectionStart()
                        : editor.getSelectionModel().getSelectionEnd();
                    ScrollType scrollType = model.isForward() ? ScrollType.CENTER_DOWN : ScrollType.CENTER_UP;
                    moveCaretAndDontChangeSelection(editor, offset, scrollType);
                }
                int offset;
                if (model.isGlobal()) {
                    if (model.isFromCursor()) {
                        offset = editor.getCaretModel().getOffset();
                        if (!model.isForward()) {
                            offset++;
                        }
                    }
                    else {
                        offset = model.isForward() ? 0 : editor.getDocument().getTextLength();
                    }
                }
                else {
                    // in selection

                    if (!editor.getSelectionModel().hasSelection()) {
                        // TODO[anton] actually, this should never happen - Find dialog should not allow such combination
                        findManager.setFindNextModel(null);
                        return;
                    }

                    offset =
                        model.isForward() ? editor.getSelectionModel().getSelectionStart() : editor.getSelectionModel().getSelectionEnd();
                }

                if (s != null && editor.getSelectionModel().hasSelection() && s.equals(model.getStringToFind())) {
                    if (model.isFromCursor() && model.isForward()) {
                        offset = Math.min(editor.getSelectionModel().getSelectionStart(), offset);
                    }
                    else if (model.isFromCursor() && !model.isForward()) {
                        offset = Math.max(editor.getSelectionModel().getSelectionEnd(), offset);
                    }
                }
                findManager.setFindNextModel(null);
                findManager.getFindInFileModel().copyFrom(model);
                replace(project, editor, offset, model);
            }
        );
    }

    @RequiredUIAccess
    public static boolean replace(Project project, Editor editor, int offset, FindModel model) {
        return replace(project, editor, offset, model, (range, replace) -> true);
    }

    @RequiredUIAccess
    public static boolean replace(Project project, Editor editor, int offset, FindModel model, ReplaceDelegate delegate) {
        Document document = editor.getDocument();

        if (!FileDocumentManager.getInstance().requestWriting(document, project)) {
            return false;
        }

        document.startGuardedBlockChecking();
        boolean toPrompt = model.isPromptOnReplace();

        try {
            doReplace(project, editor, model, document, offset, toPrompt, delegate);
        }
        catch (ReadOnlyFragmentModificationException e) {
            EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(document).handle(e);
        }
        finally {
            document.stopGuardedBlockChecking();
        }

        return true;
    }

    @RequiredUIAccess
    private static void doReplace(
        Project project,
        final Editor editor,
        final FindModel aModel,
        final Document document,
        int caretOffset,
        boolean toPrompt,
        ReplaceDelegate delegate
    ) {
        FindManager findManager = FindManager.getInstance(project);
        final FindModel model = aModel.clone();
        int occurrences = 0;

        List<Pair<TextRange, String>> rangesToChange = new ArrayList<>();

        boolean replaced = false;
        boolean reallyReplaced = false;

        int offset = caretOffset;
        while (offset >= 0 && offset < editor.getDocument().getTextLength()) {
            caretOffset = offset;
            FindResult result = doSearch(project, editor, offset, !replaced, model, toPrompt);
            if (result == null) {
                break;
            }
            int startResultOffset = result.getStartOffset();
            model.setFromCursor(true);

            int startOffset = result.getStartOffset();
            int endOffset = result.getEndOffset();
            String foundString = document.getCharsSequence().subSequence(startOffset, endOffset).toString();
            String toReplace;
            try {
                toReplace = findManager.getStringToReplace(foundString, model, startOffset, document.getCharsSequence());
            }
            catch (FindManager.MalformedReplacementStringException e) {
                if (!Application.get().isUnitTestMode()) {
                    Messages.showErrorDialog(project, e.getMessage(), FindLocalize.findReplaceInvalidReplacementStringTitle().get());
                }
                break;
            }

            if (toPrompt) {
                int promptResult = findManager.showPromptDialog(model, FindLocalize.findReplaceDialogTitle().get());
                if (promptResult == FindManager.PromptResult.SKIP) {
                    offset = model.isForward() ? result.getEndOffset() : startResultOffset;
                    continue;
                }
                if (promptResult == FindManager.PromptResult.CANCEL) {
                    break;
                }
                if (promptResult == FindManager.PromptResult.ALL) {
                    toPrompt = false;
                }
            }
            int newOffset;
            if (delegate == null || delegate.shouldReplace(result, toReplace)) {
                if (toPrompt) {
                    //[SCR 7258]
                    if (!reallyReplaced) {
                        editor.getCaretModel().moveToOffset(0);
                        reallyReplaced = true;
                    }
                }
                TextRange textRange = doReplace(project, document, model, result, toReplace, toPrompt, rangesToChange);
                replaced = true;
                newOffset = model.isForward() ? textRange.getEndOffset() : textRange.getStartOffset();
                if (textRange.isEmpty()) {
                    ++newOffset;
                }
                occurrences++;
            }
            else {
                newOffset = model.isForward() ? result.getEndOffset() : result.getStartOffset();
            }

            if (newOffset == offset) {
                newOffset += model.isForward() ? 1 : -1;
            }
            offset = newOffset;
        }

        if (replaced) {
            if (!toPrompt) {
                CharSequence text = document.getCharsSequence();
                final StringBuilder newText = new StringBuilder(document.getTextLength());
                Collections.sort(rangesToChange, (o1, o2) -> o1.getFirst().getStartOffset() - o2.getFirst().getStartOffset());
                int offsetBefore = 0;
                for (Pair<TextRange, String> pair : rangesToChange) {
                    TextRange range = pair.getFirst();
                    String replace = pair.getSecond();
                    newText.append(text, offsetBefore, range.getStartOffset()); //before change
                    if (delegate == null || delegate.shouldReplace(range, replace)) {
                        newText.append(replace);
                    }
                    else {
                        newText.append(text.subSequence(range.getStartOffset(), range.getEndOffset()));
                    }
                    offsetBefore = range.getEndOffset();
                    if (offsetBefore < caretOffset) {
                        caretOffset += replace.length() - range.getLength();
                    }
                }
                newText.append(text, offsetBefore, text.length()); //tail
                if (caretOffset > newText.length()) {
                    caretOffset = newText.length();
                }
                final int finalCaretOffset = caretOffset;
                CommandProcessor.getInstance().newCommand()
                    .project(project)
                    .groupId(document)
                    .inWriteAction()
                    .run(() -> {
                        document.setText(newText);
                        editor.getCaretModel().moveToOffset(finalCaretOffset);
                        if (model.isGlobal()) {
                            editor.getSelectionModel().removeSelection();
                        }
                    });
            }
            else if (reallyReplaced) {
                if (caretOffset > document.getTextLength()) {
                    caretOffset = document.getTextLength();
                }
                editor.getCaretModel().moveToOffset(caretOffset);
            }
        }

        ReplaceInProjectManager.reportNumberReplacedOccurrences(project, occurrences);
    }


    private static boolean selectionMayContainRange(SelectionModel selection, TextRange range) {
        int[] starts = selection.getBlockSelectionStarts();
        int[] ends = selection.getBlockSelectionEnds();
        return starts.length != 0 && new TextRange(starts[0], ends[starts.length - 1]).contains(range);
    }

    private static boolean selectionStrictlyContainsRange(SelectionModel selection, TextRange range) {
        int[] starts = selection.getBlockSelectionStarts();
        int[] ends = selection.getBlockSelectionEnds();
        for (int i = 0; i < starts.length; ++i) {
            if (new TextRange(starts[i], ends[i]).contains(range)) {  //todo
                return true;
            }
        }
        return false;
    }

    @Nullable
    @RequiredUIAccess
    private static FindResult doSearch(
        @Nonnull Project project,
        @Nonnull final Editor editor,
        int offset,
        boolean toWarn,
        @Nonnull FindModel model,
        boolean adjustEditor
    ) {
        FindManager findManager = FindManager.getInstance(project);
        Document document = editor.getDocument();

        final FindResult result = findManager.findString(document.getCharsSequence(), offset, model, getVirtualFile(editor));

        boolean isFound = result.isStringFound();
        final SelectionModel selection = editor.getSelectionModel();
        if (isFound && !model.isGlobal()) {
            if (!selectionMayContainRange(selection, result)) {
                isFound = false;
            }
            else if (!selectionStrictlyContainsRange(selection, result)) {
                final int[] starts = selection.getBlockSelectionStarts();
                for (int newOffset : starts) {
                    if (newOffset > result.getStartOffset()) {
                        return doSearch(project, editor, newOffset, toWarn, model, adjustEditor);
                    }
                }
            }
        }
        if (!isFound) {
            if (toWarn) {
                processNotFound(editor, model.getStringToFind(), model, project);
            }
            return null;
        }

        if (adjustEditor) {
            final CaretModel caretModel = editor.getCaretModel();
            final ScrollingModel scrollingModel = editor.getScrollingModel();
            int oldCaretOffset = caretModel.getOffset();
            boolean forward = oldCaretOffset < result.getStartOffset();
            final ScrollType scrollType = forward ? ScrollType.CENTER_DOWN : ScrollType.CENTER_UP;

            if (model.isGlobal()) {
                int targetCaretPosition = result.getEndOffset();
                if (selection.getSelectionEnd() - selection.getSelectionStart() == result.getLength()) {
                    // keeping caret's position relative to selection
                    // use case: FindNext is used after SelectNextOccurrence action
                    targetCaretPosition = caretModel.getOffset() - selection.getSelectionStart() + result.getStartOffset();
                }
                if (caretModel.getCaretAt(editor.offsetToVisualPosition(targetCaretPosition)) != null) {
                    // if there's a different caret at target position, don't move current caret/selection
                    // use case: FindNext is used after SelectNextOccurrence action
                    return result;
                }
                caretModel.moveToOffset(targetCaretPosition);
                selection.removeSelection();
                scrollingModel.scrollToCaret(scrollType);
                scrollingModel.runActionOnScrollingFinished(
                    () -> {
                        scrollingModel.scrollTo(editor.offsetToLogicalPosition(result.getStartOffset()), scrollType);
                        scrollingModel.scrollTo(editor.offsetToLogicalPosition(result.getEndOffset()), scrollType);
                    }
                );
            }
            else {
                moveCaretAndDontChangeSelection(editor, result.getStartOffset(), scrollType);
                moveCaretAndDontChangeSelection(editor, result.getEndOffset(), scrollType);
            }
            IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();

            EditorColorsManager manager = EditorColorsManager.getInstance();
            TextAttributes selectionAttributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);

            if (!model.isGlobal()) {
                final RangeHighlighterEx segmentHighlighter = (RangeHighlighterEx)editor.getMarkupModel().addRangeHighlighter(
                    result.getStartOffset(),
                    result.getEndOffset(),
                    HighlighterLayer.SELECTION + 1,
                    selectionAttributes, HighlighterTargetArea.EXACT_RANGE
                );
                MyListener listener = new MyListener(editor, segmentHighlighter);
                caretModel.addCaretListener(listener);
            }
            else {
                selection.setSelection(result.getStartOffset(), result.getEndOffset());
            }
        }

        return result;
    }

    private static class MyListener extends CaretAdapter {
        private final Editor myEditor;
        private final RangeHighlighter mySegmentHighlighter;

        private MyListener(@Nonnull Editor editor, @Nonnull RangeHighlighter segmentHighlighter) {
            myEditor = editor;
            mySegmentHighlighter = segmentHighlighter;
        }

        @Override
        public void caretPositionChanged(CaretEvent e) {
            removeAll();
        }

        private void removeAll() {
            myEditor.getCaretModel().removeCaretListener(this);
            mySegmentHighlighter.dispose();
        }
    }

    @RequiredUIAccess
    public static void processNotFound(final Editor editor, String stringToFind, FindModel model, Project project) {
        LocalizeValue message = FindLocalize.findSearchStringNotFoundMessage(stringToFind);

        short position = HintManager.UNDER;
        if (model.isGlobal()) {
            final FindModel newModel = model.clone();
            FindManager findManager = FindManager.getInstance(project);
            Document document = editor.getDocument();
            FindResult result = findManager.findString(document.getCharsSequence(),
                newModel.isForward() ? 0 : document.getTextLength(), model, getVirtualFile(editor)
            );
            if (!result.isStringFound()) {
                result = null;
            }

            FindModel modelForNextSearch = findManager.getFindNextModel(editor);
            if (modelForNextSearch == null) {
                modelForNextSearch = findManager.getFindInFileModel();
            }

            if (result != null) {
                if (newModel.isForward()) {
                    AnAction action = ActionManager.getInstance().getAction(
                        modelForNextSearch.isForward() ? IdeActions.ACTION_FIND_NEXT : IdeActions.ACTION_FIND_PREVIOUS);
                    String shortcutsText = KeymapUtil.getFirstKeyboardShortcutText(action);
                    if (!shortcutsText.isEmpty()) {
                        message = FindLocalize.findSearchAgainFromTopHotkeyMessage(message, shortcutsText);
                    }
                    else {
                        message = FindLocalize.findSearchAgainFromTopActionMessage(message);
                    }
                    editor.putUserData(KEY, Direction.DOWN);
                }
                else {
                    AnAction action = ActionManager.getInstance().getAction(
                        modelForNextSearch.isForward() ? IdeActions.ACTION_FIND_PREVIOUS : IdeActions.ACTION_FIND_NEXT);
                    String shortcutsText = KeymapUtil.getFirstKeyboardShortcutText(action);
                    if (!shortcutsText.isEmpty()) {
                        message = FindLocalize.findSearchAgainFromBottomHotkeyMessage(message, shortcutsText);
                    }
                    else {
                        message = FindLocalize.findSearchAgainFromBottomActionMessage(message);
                    }
                    editor.putUserData(KEY, Direction.UP);
                    position = HintManager.ABOVE;
                }
            }
            CaretListener listener = new CaretAdapter() {
                @Override
                public void caretPositionChanged(CaretEvent e) {
                    editor.putUserData(KEY, null);
                    editor.getCaretModel().removeCaretListener(this);
                }
            };
            editor.getCaretModel().addCaretListener(listener);
        }
        JComponent component = HintUtil.createInformationLabel(JDOMUtil.escapeText(message.get(), false, false));
        final LightweightHintImpl hint = new LightweightHintImpl(component);
        HintManagerImpl.getInstanceImpl().showEditorHint(
            hint,
            editor,
            position,
            HintManager.HIDE_BY_ANY_KEY
                | HintManager.HIDE_BY_TEXT_CHANGE
                | HintManager.HIDE_BY_SCROLLING,
            0,
            false
        );
    }

    @RequiredUIAccess
    public static TextRange doReplace(
        final Project project,
        final Document document,
        final FindModel model,
        FindResult result,
        @Nonnull String stringToReplace,
        boolean reallyReplace,
        List<Pair<TextRange, String>> rangesToChange
    ) {
        final int startOffset = result.getStartOffset();
        final int endOffset = result.getEndOffset();

        int newOffset;
        if (reallyReplace) {
            newOffset = doReplace(project, document, startOffset, endOffset, stringToReplace);
        }
        else {
            final String converted = StringUtil.convertLineSeparators(stringToReplace);
            TextRange textRange = new TextRange(startOffset, endOffset);
            rangesToChange.add(Pair.create(textRange, converted));

            newOffset = endOffset;
        }

        int start = startOffset;
        int end = newOffset;
        if (model.isRegularExpressions()) {
            String toFind = model.getStringToFind();
            if (model.isForward()) {
                if (StringUtil.endsWithChar(toFind, '$')) {
                    int i = 0;
                    int length = toFind.length();
                    while (i + 2 <= length && toFind.charAt(length - i - 2) == '\\') i++;
                    if (i % 2 == 0) {
                        end++; //This $ is a special symbol in regexp syntax
                    }
                }
                else if (StringUtil.startsWithChar(toFind, '^')) {
                    while (end < document.getTextLength() && document.getCharsSequence().charAt(end) != '\n') end++;
                }
            }
            else {
                if (StringUtil.startsWithChar(toFind, '^')) {
                    start--;
                }
                else if (StringUtil.endsWithChar(toFind, '$')) {
                    while (start >= 0 && document.getCharsSequence().charAt(start) != '\n') start--;
                }
            }
        }
        return new TextRange(start, end);
    }

    @RequiredUIAccess
    private static int doReplace(
        Project project,
        final Document document,
        final int startOffset,
        final int endOffset,
        final String stringToReplace
    ) {
        final String converted = StringUtil.convertLineSeparators(stringToReplace);
        CommandProcessor.getInstance().newCommand()
            .project(project)
            .document(document)
            .inWriteAction()
            .run(() -> {
                //[ven] I doubt converting is a good solution to SCR 21224
                document.replaceString(startOffset, endOffset, converted);
            });
        return startOffset + converted.length();
    }

    private static void moveCaretAndDontChangeSelection(final Editor editor, int offset, ScrollType scrollType) {
        LogicalPosition pos = editor.offsetToLogicalPosition(offset);
        editor.getCaretModel().moveToLogicalPosition(pos);
        editor.getScrollingModel().scrollToCaret(scrollType);
    }

    @FunctionalInterface
    public interface ReplaceDelegate {
        boolean shouldReplace(TextRange range, String replace);
    }

    @Nullable
    @RequiredReadAction
    public static UsageView showInUsageView(
        @Nullable PsiElement sourceElement,
        @Nonnull PsiElement[] targets,
        @Nonnull String title,
        @Nonnull final Project project
    ) {
        if (targets.length == 0) {
            return null;
        }
        final UsageViewPresentation presentation = new UsageViewPresentation();
        presentation.setCodeUsagesString(title);
        presentation.setTabName(title);
        presentation.setTabText(title);
        UsageTarget[] usageTargets =
            sourceElement == null ? UsageTarget.EMPTY_ARRAY : new UsageTarget[]{new PsiElement2UsageTargetAdapter(sourceElement)};

        PsiElement[] primary = sourceElement == null ? PsiElement.EMPTY_ARRAY : new PsiElement[]{sourceElement};
        UsageView view = UsageViewManager.getInstance(project).showUsages(usageTargets, Usage.EMPTY_ARRAY, presentation);

        SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(project);
        List<SmartPsiElementPointer> pointers = ContainerUtil.map(targets, smartPointerManager::createSmartPsiElementPointer);

        // usage view will load document/AST so still referencing all these PSI elements might lead to out of memory
        //noinspection UnusedAssignment
        targets = PsiElement.EMPTY_ARRAY;

        ProgressManager.getInstance().run(new Task.Backgroundable(project, FindLocalize.progressTitleUpdatingUsageView()) {
            @Override
            public void run(@Nonnull ProgressIndicator indicator) {
                for (final SmartPsiElementPointer pointer : pointers) {
                    if (view.isDisposed()) {
                        break;
                    }
                    Application.get().runReadAction(() -> {
                        final PsiElement target = pointer.getElement();
                        if (target != null) {
                            view.appendUsage(UsageInfoToUsageConverter.convert(primary, new UsageInfo(target)));
                        }
                    });
                }
                UIUtil.invokeLaterIfNeeded(((UsageViewImpl)view)::expandAll);
            }
        });
        return view;
    }

    /**
     * Creates a selection in editor per each search result. Existing carets and selections in editor are discarded.
     *
     * @param caretShiftFromSelectionStart if non-negative, defines caret position relative to selection start, for each created selection.
     *                                     if negative, carets will be positioned at selection ends
     */
    public static void selectSearchResultsInEditor(
        @Nonnull Editor editor,
        @Nonnull Iterator<FindResult> resultIterator,
        int caretShiftFromSelectionStart
    ) {
        if (!editor.getCaretModel().supportsMultipleCarets()) {
            return;
        }
        ArrayList<CaretState> caretStates = new ArrayList<>();
        while (resultIterator.hasNext()) {
            FindResult findResult = resultIterator.next();
            int caretOffset = getCaretPosition(findResult, caretShiftFromSelectionStart);
            int selectionStartOffset = findResult.getStartOffset();
            int selectionEndOffset = findResult.getEndOffset();
            EditorActionUtil.makePositionVisible(editor, caretOffset);
            EditorActionUtil.makePositionVisible(editor, selectionStartOffset);
            EditorActionUtil.makePositionVisible(editor, selectionEndOffset);
            caretStates.add(new CaretState(
                editor.offsetToLogicalPosition(caretOffset),
                editor.offsetToLogicalPosition(selectionStartOffset),
                editor.offsetToLogicalPosition(selectionEndOffset)
            ));
        }
        if (caretStates.isEmpty()) {
            return;
        }
        editor.getCaretModel().setCaretsAndSelections(caretStates);
    }

    /**
     * Attempts to add a new caret to editor, with selection corresponding to given search result.
     *
     * @param caretShiftFromSelectionStart if non-negative, defines caret position relative to selection start, for each created selection.
     *                                     if negative, caret will be positioned at selection end
     * @return <code>true</code> if caret was added successfully, <code>false</code> if it cannot be done, e.g. because a caret already
     * exists at target position
     */
    public static boolean selectSearchResultInEditor(@Nonnull Editor editor, @Nonnull FindResult result, int caretShiftFromSelectionStart) {
        if (!editor.getCaretModel().supportsMultipleCarets()) {
            return false;
        }
        int caretOffset = getCaretPosition(result, caretShiftFromSelectionStart);
        EditorActionUtil.makePositionVisible(editor, caretOffset);
        Caret newCaret = editor.getCaretModel().addCaret(editor.offsetToVisualPosition(caretOffset));
        if (newCaret == null) {
            return false;
        }
        else {
            int selectionStartOffset = result.getStartOffset();
            int selectionEndOffset = result.getEndOffset();
            EditorActionUtil.makePositionVisible(editor, selectionStartOffset);
            EditorActionUtil.makePositionVisible(editor, selectionEndOffset);
            newCaret.setSelection(selectionStartOffset, selectionEndOffset);
            return true;
        }
    }

    private static int getCaretPosition(FindResult findResult, int caretShiftFromSelectionStart) {
        return caretShiftFromSelectionStart < 0
            ? findResult.getEndOffset()
            : Math.min(findResult.getStartOffset() + caretShiftFromSelectionStart, findResult.getEndOffset());
    }
}
