/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package consulo.ide.impl.idea.codeEditor.printing;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.application.progress.PerformInBackgroundOption;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.application.util.function.Computable;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorHighlighter;
import consulo.codeEditor.SelectionModel;
import consulo.component.ProcessCanceledException;
import consulo.dataContext.DataContext;
import consulo.document.internal.DocumentEx;
import consulo.language.editor.highlight.HighlighterFactory;
import consulo.language.editor.util.PsiUtilBase;
import consulo.language.plain.PlainTextFileType;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.logging.Logger;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.print.*;
import java.util.ArrayList;
import java.util.List;

class PrintManager {
  private static final Logger LOG = Logger.getInstance(PrintManager.class);

  @RequiredReadAction
  public static void executePrint(DataContext dataContext) {
    final Project project = dataContext.getData(Project.KEY);

    final PrinterJob printerJob = PrinterJob.getPrinterJob();

    final PsiDirectory[] psiDirectory = new PsiDirectory[1];
    PsiElement psiElement = dataContext.getData(PsiElement.KEY);
    if (psiElement instanceof PsiDirectory) {
      psiDirectory[0] = (PsiDirectory)psiElement;
    }

    final PsiFile psiFile = dataContext.getData(PsiFile.KEY);
    final String[] shortFileName = new String[1];
    final String[] directoryName = new String[1];
    if (psiFile != null || psiDirectory[0] != null) {
      if (psiFile != null) {
        shortFileName[0] = psiFile.getVirtualFile().getName();
        if(psiDirectory[0] == null) {
          psiDirectory[0] = psiFile.getContainingDirectory();
        }
      }
      if (psiDirectory[0] != null) {
        directoryName[0] = psiDirectory[0].getVirtualFile().getPresentableUrl();
      }
    }

    Editor editor = dataContext.getData(Editor.KEY);
    String text = null;
    if (editor != null) {
      if (editor.getSelectionModel().hasSelection()) {
        text = CodeEditorBundle.message("print.selected.text.radio");
      } else {
        text = psiFile == null ? "Console text" : null;
      }
    }
    PrintDialog printDialog = new PrintDialog(shortFileName[0], directoryName[0], text, project);
    printDialog.reset();
    printDialog.show();
    if (!printDialog.isOK()) {
      return;
    }
    printDialog.apply();

    final PageFormat pageFormat = createPageFormat();
    PrintSettings printSettings = PrintSettings.getInstance();
    Printable painter;

    if (printSettings.getPrintScope() != PrintSettings.PRINT_DIRECTORY) {
      if (psiFile == null && editor == null) {
        return;
      }

      TextPainter textPainter =
        psiFile != null ? initTextPainter(psiFile, editor) : initTextPainter((DocumentEx)editor.getDocument(), project);
      if (textPainter == null) {
        return;
      }

      if (printSettings.getPrintScope() == PrintSettings.PRINT_SELECTED_TEXT
        && editor != null && editor.getSelectionModel().hasSelection()) {
        SelectionModel selection = editor.getSelectionModel();
        int firstLine = editor.getDocument().getLineNumber(selection.getSelectionStart());
        textPainter.setSegment(
          selection.getSelectionStart(),
          selection.getSelectionEnd(),
          firstLine + 1
        );
      }
      painter = textPainter;
    }
    else {
      List<Pair<PsiFile, Editor>> filesList = new ArrayList<>();
      boolean isRecursive = printSettings.isIncludeSubdirectories();
      addToPsiFileList(psiDirectory[0], filesList, isRecursive);

      painter = new MultiFilePainter(filesList);
    }
    final Printable painter0 = painter;
    Pageable document = new Pageable(){
      @Override
      public int getNumberOfPages() {
        return Pageable.UNKNOWN_NUMBER_OF_PAGES;
      }

      @Override
      public PageFormat getPageFormat(int pageIndex)
        throws IndexOutOfBoundsException {
        return pageFormat;
      }

      @Override
      public Printable getPrintable(int pageIndex)
        throws IndexOutOfBoundsException {
        return painter0;
      }
    };


    try {
      printerJob.setPageable(document);
      printerJob.setPrintable(painter, pageFormat);
      if(!printerJob.printDialog()) {
        return;
      }
    } catch (Exception e) {
      // In case print dialog is not supported on some platform. Strange thing but there was a checking
      // for Windows only...
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    ProgressManager.getInstance().run(
      new Task.Backgroundable(
        project,
        CodeEditorBundle.message("print.progress"),
        true,
        PerformInBackgroundOption.ALWAYS_BACKGROUND
      ) {
        @Override
        public void run(@Nonnull ProgressIndicator indicator) {
          try {
            ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
            if (painter0 instanceof MultiFilePainter multiFilePainter) {
              multiFilePainter.setProgress(progress);
            }
            else {
              ((TextPainter)painter0).setProgress(progress);
            }

            printerJob.print();
          }
          catch(final PrinterException e) {
            SwingUtilities.invokeLater(() -> Messages.showErrorDialog(project, e.getMessage(), CommonLocalize.titleError().get()));
            LOG.info(e);
          }
          catch(ProcessCanceledException e) {
            printerJob.cancel();
          }
        }
      }
    );
  }

  @RequiredReadAction
  private static void addToPsiFileList(PsiDirectory psiDirectory, List<Pair<PsiFile, Editor>> filesList, boolean isRecursive) {
    PsiFile[] files = psiDirectory.getFiles();
    for (PsiFile file : files) {
      filesList.add(Pair.create(file, PsiUtilBase.findEditor(file)));
    }
    if (isRecursive) {
      for (PsiDirectory directory : psiDirectory.getSubdirectories()) {
        if (!Project.DIRECTORY_STORE_FOLDER.equals(directory.getName())) {
          addToPsiFileList(directory, filesList, isRecursive);
        }
      }
    }
  }


  private static PageFormat createPageFormat() {
    PrintSettings printSettings = PrintSettings.getInstance();
    PageFormat pageFormat = new PageFormat();
    Paper paper = new Paper();
    String paperSize = printSettings.PAPER_SIZE;
    double paperWidth = PageSizes.getWidth(paperSize)*72;
    double paperHeight = PageSizes.getHeight(paperSize)*72;
    double leftMargin = printSettings.LEFT_MARGIN*72;
    double rightMargin = printSettings.RIGHT_MARGIN*72;
    double topMargin = printSettings.TOP_MARGIN*72;
    double bottomMargin = printSettings.BOTTOM_MARGIN*72;

    paper.setSize(paperWidth, paperHeight);
    if(printSettings.PORTRAIT_LAYOUT) {
      pageFormat.setOrientation(PageFormat.PORTRAIT);
      paperWidth -= leftMargin + rightMargin;
      paperHeight -= topMargin + bottomMargin;
      paper.setImageableArea(leftMargin, topMargin, paperWidth, paperHeight);
    }
    else{
      pageFormat.setOrientation(PageFormat.LANDSCAPE);
      paperWidth -= topMargin + bottomMargin;
      paperHeight -= leftMargin + rightMargin;
      paper.setImageableArea(rightMargin, topMargin, paperWidth, paperHeight);
    }
    pageFormat.setPaper(paper);
    return pageFormat;
  }

  public static TextPainter initTextPainter(final PsiFile psiFile, final Editor editor) {
    return Application.get().runReadAction((Computable<TextPainter>) () -> doInitTextPainter(psiFile, editor));
  }

  private static TextPainter doInitTextPainter(final PsiFile psiFile, final Editor editor) {
    final String fileName = psiFile.getVirtualFile().getPresentableUrl();
    DocumentEx doc = (DocumentEx)PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
    if (doc == null) return null;
    EditorHighlighter highlighter = HighlighterFactory.createHighlighter(psiFile.getProject(), psiFile.getVirtualFile());
    highlighter.setText(doc.getCharsSequence());
    return new TextPainter(doc, highlighter, fileName, psiFile, psiFile.getFileType(), editor);
  }

  public static TextPainter initTextPainter(@Nonnull final DocumentEx doc, final Project project) {
    final TextPainter[] res = new TextPainter[1];
    Application.get().runReadAction(() -> {
      res[0] = doInitTextPainter(doc, project);
    });
    return res[0];
  }

  private static TextPainter doInitTextPainter(@Nonnull final DocumentEx doc, Project project) {
    EditorHighlighter highlighter = HighlighterFactory.createHighlighter(project, "unknown");
    highlighter.setText(doc.getCharsSequence());
    return new TextPainter(doc, highlighter, "unknown", project, PlainTextFileType.INSTANCE, null);
  }
}
