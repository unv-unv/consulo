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
package consulo.ide.impl.idea.codeInsight.hint;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.AllIcons;
import consulo.application.Application;
import consulo.codeEditor.*;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.FileEditorProvider;
import consulo.fileEditor.FileEditorProviderManager;
import consulo.fileEditor.impl.internal.OpenFileDescriptorImpl;
import consulo.fileEditor.internal.FileEditorManagerEx;
import consulo.fileEditor.text.TextEditorProvider;
import consulo.ide.impl.idea.find.FindUtil;
import consulo.ide.impl.idea.openapi.actionSystem.CompositeShortcutSet;
import consulo.language.editor.ImplementationTextSelectioner;
import consulo.language.editor.highlight.HighlighterFactory;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.*;
import consulo.localize.LocalizeValue;
import consulo.navigation.ItemPresentation;
import consulo.navigation.NavigationItem;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.util.ScreenUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.popup.JBPopup;
import consulo.ui.image.Image;
import consulo.undoRedo.CommandProcessor;
import consulo.usage.UsageView;
import consulo.util.lang.Comparing;
import consulo.util.lang.function.PairFunction;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.status.FileStatusManager;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ImplementationViewComponent extends JPanel {
  @NonNls private static final String TEXT_PAGE_KEY = "Text";
  @NonNls private static final String BINARY_PAGE_KEY = "Binary";

  private PsiElement[] myElements;
  private int myIndex;

  private final Editor myEditor;
  private final JPanel myViewingPanel;
  private final JLabel myLocationLabel;
  private final JLabel myCountLabel;
  private final CardLayout myBinarySwitch;
  private final JPanel myBinaryPanel;
  private ComboBox myFileChooser;
  private FileEditor myNonTextEditor;
  private FileEditorProvider myCurrentNonTextEditorProvider;
  private JBPopup myHint;
  private String myTitle;
  private final ActionToolbar myToolbar;
  private JLabel myLabel;

  public void setHint(final JBPopup hint, final String title) {
    myHint = hint;
    myTitle = title;
  }

  public boolean hasElementsToShow() {
    return myElements != null && myElements.length > 0;
  }

  private static class FileDescriptor {
    public final PsiFile myFile;
    public final String myElementPresentation;

    public FileDescriptor(PsiFile file, PsiElement element) {
      myFile = file;
      myElementPresentation = getPresentation(element);
    }

    @Nullable
    private static String getPresentation(PsiElement element) {
      if (element instanceof NavigationItem navigationItem) {
        final ItemPresentation presentation = navigationItem.getPresentation();
        if (presentation != null) {
          return presentation.getPresentableText();
        }
      }

      if (element instanceof PsiNamedElement namedElement) return namedElement.getName();
      return null;
    }

    public String getPresentableName(VirtualFile vFile) {
      final String presentableName = vFile.getPresentableName();
      if (myElementPresentation == null) {
        return presentableName;
      }

      if (Comparing.strEqual(vFile.getName(), myElementPresentation + "." + vFile.getExtension())){
        return presentableName;
      }

      return presentableName + " (" + myElementPresentation + ")";
    }
  }

  public ImplementationViewComponent(PsiElement[] elements, final int index) {
    super(new BorderLayout());

    final Project project = elements.length > 0 ? elements[0].getProject() : null;
    EditorFactory factory = EditorFactory.getInstance();
    Document doc = factory.createDocument("");
    doc.setReadOnly(true);
    myEditor = factory.createEditor(doc, project);
    ((EditorEx)myEditor).setBackgroundColor(EditorFragmentComponent.getBackgroundColor(myEditor));

    final EditorSettings settings = myEditor.getSettings();
    settings.setAdditionalLinesCount(1);
    settings.setAdditionalColumnsCount(1);
    settings.setLineMarkerAreaShown(false);
    settings.setIndentGuidesShown(false);
    settings.setLineNumbersShown(false);
    settings.setFoldingOutlineShown(false);

    myBinarySwitch = new CardLayout();
    myViewingPanel = new JPanel(myBinarySwitch);
    myEditor.setBorder(null);
    ((EditorEx)myEditor).getScrollPane().setViewportBorder(JBScrollPane.createIndentBorder());
    myViewingPanel.add(myEditor.getComponent(), TEXT_PAGE_KEY);

    myBinaryPanel = new JPanel(new BorderLayout());
    myViewingPanel.add(myBinaryPanel, BINARY_PAGE_KEY);

    add(myViewingPanel, BorderLayout.CENTER);

    myToolbar = createToolbar();
    myLocationLabel = new JLabel();
    myCountLabel = new JLabel();

    final JPanel header = new JPanel(new BorderLayout(2, 0));
    header.setBorder(BorderFactory.createCompoundBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM),
                                                        IdeBorderFactory.createEmptyBorder(0, 0, 0, 5)));
    final JPanel toolbarPanel = new JPanel(new GridBagLayout());
    final GridBagConstraints gc = new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,2,0,0), 0,0);
    toolbarPanel.add(myToolbar.getComponent(), gc);

    setPreferredSize(new Dimension(600, 400));
    
    update(elements, (psiElements, fileDescriptors) -> {
      if (psiElements.length == 0) return false;
      myElements = psiElements;

      myIndex = index < myElements.length ? index : 0;
      PsiFile psiFile = getContainingFile(myElements[myIndex]);

      VirtualFile virtualFile = psiFile.getVirtualFile();
      EditorHighlighter highlighter;
      if (virtualFile != null)
        highlighter = HighlighterFactory.createHighlighter(project, virtualFile);
      else {
        String fileName = psiFile.getName();  // some artificial psi file, lets do best we can
        highlighter = HighlighterFactory.createHighlighter(project, fileName);
      }

      ((EditorEx)myEditor).setHighlighter(highlighter);

      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.weightx = 1;
      myLabel = new JLabel();
      myFileChooser = new ComboBox(fileDescriptors.toArray(new FileDescriptor[fileDescriptors.size()]), 250);
      myFileChooser.addActionListener(e -> {
        int index1 = myFileChooser.getSelectedIndex();
        if (myIndex != index1) {
          myIndex = index1;
          updateControls();
        }
      });
      toolbarPanel.add(myFileChooser, gc);

      if (myElements.length > 1) {
        updateRenderer(project);
        myLabel.setVisible(false);
      }
      else {
        myFileChooser.setVisible(false);
        myCountLabel.setVisible(false);

        VirtualFile file = psiFile.getVirtualFile();
        if (file != null) {
          myLabel.setIcon(getIconForFile(psiFile));
          myLabel.setForeground(TargetAWT.to(FileStatusManager.getInstance(project).getStatus(file).getColor()));
          myLabel.setText(file.getPresentableName());
          myLabel.setBorder(new CompoundBorder(
            IdeBorderFactory.createRoundedBorder(),
            IdeBorderFactory.createEmptyBorder(0, 0, 0, 5)
          ));
        }
        toolbarPanel.add(myLabel, gc);
      }

      gc.fill = GridBagConstraints.NONE;
      gc.weightx = 0;
      toolbarPanel.add(myCountLabel, gc);

      header.add(toolbarPanel, BorderLayout.CENTER);
      header.add(myLocationLabel, BorderLayout.EAST);

      add(header, BorderLayout.NORTH);

      updateControls();
      return true;
    });
  }

  private void updateRenderer(final Project project) {
    myFileChooser.setRenderer(new ListCellRendererWrapper<FileDescriptor>() {
      @Override
      public void customize(JList list, FileDescriptor value, int index, boolean selected, boolean hasFocus) {
        final PsiFile file = value.myFile;
        setIcon(getIconForFile(file));
        final VirtualFile vFile = file.getVirtualFile();
        setForeground(TargetAWT.to(FileStatusManager.getInstance(project).getStatus(vFile).getColor()));
        //noinspection ConstantConditions
        setText(value.getPresentableName(vFile));
      }
    });
  }

  @TestOnly
  public String[] getVisibleFiles() {
    final ComboBoxModel model = myFileChooser.getModel();
    String[] result = new String[model.getSize()];
    for (int i = 0; i < model.getSize(); i++) {
      FileDescriptor o = (FileDescriptor)model.getElementAt(i);
      result[i] = o.getPresentableName(o.myFile.getVirtualFile());
    }
    return result;
  }

  public void update(@Nonnull final PsiElement[] elements, final int index) {
    update(elements, (psiElements, fileDescriptors) -> {
      if (psiElements.length == 0) return false;

      final Project project = psiElements[0].getProject();
      myElements = psiElements;

      myIndex = index < myElements.length ? index : 0;
      PsiFile psiFile = getContainingFile(myElements[myIndex]);

      VirtualFile virtualFile = psiFile.getVirtualFile();
      EditorHighlighter highlighter;
      if (virtualFile != null)
        highlighter = HighlighterFactory.createHighlighter(project, virtualFile);
      else {
        String fileName = psiFile.getName();  // some artificial psi file, lets do best we can
        highlighter = HighlighterFactory.createHighlighter(project, fileName);
      }

      ((EditorEx)myEditor).setHighlighter(highlighter);

      if (myElements.length > 1) {
        myFileChooser.setVisible(true);
        myCountLabel.setVisible(true);
        myLabel.setVisible(false);

        myFileChooser.setModel(new DefaultComboBoxModel(fileDescriptors.toArray(new FileDescriptor[fileDescriptors.size()])));
        updateRenderer(project);
      }
      else {
        myFileChooser.setVisible(false);
        myCountLabel.setVisible(false);

        VirtualFile file = psiFile.getVirtualFile();
        if (file != null) {
          myLabel.setIcon(getIconForFile(psiFile));
          myLabel.setForeground(TargetAWT.to(FileStatusManager.getInstance(project).getStatus(file).getColor()));
          myLabel.setText(file.getPresentableName());
          myLabel.setBorder(new CompoundBorder(IdeBorderFactory.createRoundedBorder(), IdeBorderFactory.createEmptyBorder(0, 0, 0, 5)));
          myLabel.setVisible(true);
        }
      }

      updateControls();

      revalidate();
      repaint();

      return true;
    });
    
  }
  
  private static void update(
    @Nonnull PsiElement[] elements,
    @Nonnull PairFunction<PsiElement[], List<FileDescriptor>, Boolean> fun
  ) {
    List<PsiElement> candidates = new ArrayList<>(elements.length);
    List<FileDescriptor> files = new ArrayList<>(elements.length);
    final Set<String> names = new HashSet<>();
    for (PsiElement element : elements) {
      if (element instanceof PsiNamedElement namedElement) {
        names.add(namedElement.getName());
      }
    }
    for (PsiElement element : elements) {
      PsiFile file = getContainingFile(element);
      if (file == null) continue;
      final PsiElement parent = element.getParent();
      files.add(new FileDescriptor(file, names.size() > 1 || parent == file ? element : parent));
      candidates.add(element);
    }
    
    fun.fun(PsiUtilCore.toPsiElementArray(candidates), files);
  }
  
  private static Icon getIconForFile(PsiFile psiFile) {
    return TargetAWT.to(IconDescriptorUpdaters.getIcon(psiFile.getNavigationElement(), 0));
  }

  public JComponent getPreferredFocusableComponent() {
    return myElements.length > 1 ? myFileChooser : myEditor.getContentComponent();
  }

  private void updateControls() {
    updateLabels();
    updateCombo();
    updateEditorText();
    myToolbar.updateActionsImmediately();
  }

  private void updateCombo() {
    if (myFileChooser != null && myFileChooser.isVisible()) {
      myFileChooser.setSelectedIndex(myIndex);
    }
  }

  private void updateEditorText() {
    disposeNonTextEditor();

    final PsiElement elt = myElements[myIndex].getNavigationElement();
    Project project = elt.getProject();
    PsiFile psiFile = getContainingFile(elt);
    final VirtualFile vFile = psiFile.getVirtualFile();
    if (vFile == null) return;
    final FileEditorProvider[] providers = FileEditorProviderManager.getInstance().getProviders(project, vFile);
    for (FileEditorProvider provider : providers) {
      if (provider instanceof TextEditorProvider) {
        updateTextElement(elt);
        myBinarySwitch.show(myViewingPanel, TEXT_PAGE_KEY);
        break;
      }
      else if (provider.accept(project, vFile)) {
        myCurrentNonTextEditorProvider = provider;
        myNonTextEditor = myCurrentNonTextEditorProvider.createEditor(project, vFile);
        myBinaryPanel.removeAll();
        myBinaryPanel.add(myNonTextEditor.getComponent());
        myBinarySwitch.show(myViewingPanel, BINARY_PAGE_KEY);
        break;
      }
    }
  }

  private void disposeNonTextEditor() {
    if (myNonTextEditor != null) {
      myCurrentNonTextEditorProvider.disposeEditor(myNonTextEditor);
      myNonTextEditor = null;
      myCurrentNonTextEditorProvider = null;
    }
  }

  @RequiredUIAccess
  private void updateTextElement(final PsiElement elt) {
    final String newText = getNewText(elt);
    if (newText == null || Comparing.strEqual(newText, myEditor.getDocument().getText())) return;
    CommandProcessor.getInstance().runUndoTransparentAction(() -> Application.get().runWriteAction(() -> {
      Document fragmentDoc = myEditor.getDocument();
      fragmentDoc.setReadOnly(false);

      fragmentDoc.replaceString(0, fragmentDoc.getTextLength(), newText);
      fragmentDoc.setReadOnly(true);
      myEditor.getCaretModel().moveToOffset(0);
      myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }));
  }


  @Nullable
  @RequiredReadAction
  public static String getNewText(PsiElement elt) {
    Project project = elt.getProject();
    PsiFile psiFile = getContainingFile(elt);

    final Document doc = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    if (doc == null) return null;

    if (elt.getTextRange() == TextRange.EMPTY_RANGE) {
      return null;
    }

    final ImplementationTextSelectioner implementationTextSelectioner = ImplementationTextSelectioner.forLanguage(elt.getLanguage());
    int start = implementationTextSelectioner.getTextStartOffset(elt);
    final int end = implementationTextSelectioner.getTextEndOffset(elt);

    final int lineStart = doc.getLineStartOffset(doc.getLineNumber(start));
    final int lineEnd = end < doc.getTextLength() ? doc.getLineEndOffset(doc.getLineNumber(end)) : doc.getTextLength();
    return doc.getCharsSequence().subSequence(lineStart, lineEnd).toString();
  }

  private static PsiFile getContainingFile(final PsiElement elt) {
    PsiFile psiFile = elt.getContainingFile();
    if (psiFile == null) return null;
    return psiFile.getOriginalFile();
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    if (!ScreenUtil.isStandardAddRemoveNotify(this))
      return;
    EditorFactory.getInstance().releaseEditor(myEditor);
    disposeNonTextEditor();
  }

  private void updateLabels() {
    //TODO: Move from JavaDoc to somewhere more appropriate place.
    ElementLocationUtil.customizeElementLabel(myElements[myIndex], myLocationLabel);
    //noinspection AutoBoxing
    myCountLabel.setText(CodeInsightLocalize.nOfM(myIndex + 1, myElements.length).get());
  }

  private ActionToolbar createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();

    BackAction back = new BackAction();
    back.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0)), this);
    group.add(back);

    ForwardAction forward = new ForwardAction();
    forward.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0)), this);
    group.add(forward);

    EditSourceActionBase edit = new EditSourceAction();
    edit.registerCustomShortcutSet(new CompositeShortcutSet(CommonShortcuts.getEditSource(), CommonShortcuts.ENTER), this);
    group.add(edit);

    edit = new ShowSourceAction();
    edit.registerCustomShortcutSet(new CompositeShortcutSet(CommonShortcuts.getViewSource(), CommonShortcuts.CTRL_ENTER), this);
    group.add(edit);

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
  }

  private void goBack() {
    myIndex--;
    updateControls();
  }

  private void goForward() {
    myIndex++;
    updateControls();
  }

  public int getIndex() {
    return myIndex;
  }

  public PsiElement[] getElements() {
    return myElements;
  }

  public UsageView showInUsageView() {
    return FindUtil.showInUsageView(null, collectNonBinaryElements(), myTitle, myEditor.getProject());
  }

  private class BackAction extends AnAction implements HintManagerImpl.ActionToIgnore {
    public BackAction() {
      super(CodeInsightLocalize.quickDefinitionBack(), null, AllIcons.Actions.Back);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      goBack();
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(myIndex > 0);
    }
  }

  private class ForwardAction extends AnAction implements HintManagerImpl.ActionToIgnore {
    public ForwardAction() {
      super(CodeInsightLocalize.quickDefinitionForward(), null, AllIcons.Actions.Forward);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      goForward();
    }

    @Override
    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(myElements != null && myIndex < myElements.length - 1);
    }
  }

  private class EditSourceAction extends EditSourceActionBase {
    public EditSourceAction() {
      super(true, AllIcons.Actions.EditSource, CodeInsightLocalize.quickDefinitionEditSource());
    }

    @Override public void actionPerformed(AnActionEvent e) {
      super.actionPerformed(e);
      if (myHint.isVisible()) {
        myHint.cancel();
      }
    }
  }

  private class ShowSourceAction extends EditSourceActionBase implements HintManagerImpl.ActionToIgnore {
    public ShowSourceAction() {
      super(false, AllIcons.Actions.Preview, CodeInsightLocalize.quickDefinitionShowSource());
    }
  }

  private class EditSourceActionBase extends AnAction {
    private final boolean myFocusEditor;

    public EditSourceActionBase(boolean focusEditor, Image icon, LocalizeValue text) {
      super(text, null, icon);
      myFocusEditor = focusEditor;
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myFileChooser == null || !myFileChooser.isPopupVisible());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      PsiElement element = myElements[myIndex];
      PsiElement navigationElement = element.getNavigationElement();
      PsiFile file = getContainingFile(navigationElement);
      if (file == null) return;
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile == null) return;
      Project project = element.getProject();
      FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
      OpenFileDescriptorImpl descriptor = new OpenFileDescriptorImpl(project, virtualFile, navigationElement.getTextOffset());
      fileEditorManager.openTextEditor(descriptor, myFocusEditor);
    }
  }

  private PsiElement[] collectNonBinaryElements() {
    List<PsiElement> result = new ArrayList<>();
    for (PsiElement element : myElements) {
      if (!(element instanceof PsiBinaryFile)) {
        result.add(element);
      }
    }
    return PsiUtilCore.toPsiElementArray(result);
  }
}
