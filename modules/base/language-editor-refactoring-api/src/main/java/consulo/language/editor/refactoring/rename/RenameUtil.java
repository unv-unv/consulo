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

package consulo.language.editor.refactoring.rename;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.ApplicationManager;
import consulo.document.Document;
import consulo.document.util.ProperTextRange;
import consulo.document.util.Segment;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.QualifiedNameProviderUtil;
import consulo.language.editor.refactoring.NamesValidator;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.event.UndoRefactoringElementListener;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.refactoring.util.NonCodeSearchDescriptionLocation;
import consulo.language.editor.refactoring.util.TextOccurrencesUtil;
import consulo.language.pom.PomTargetPsiElement;
import consulo.language.psi.*;
import consulo.language.psi.meta.PsiMetaData;
import consulo.language.psi.meta.PsiMetaOwner;
import consulo.language.psi.meta.PsiWritableMetaData;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.undoRedo.BasicUndoableAction;
import consulo.undoRedo.ProjectUndoManager;
import consulo.undoRedo.UndoableAction;
import consulo.undoRedo.UnexpectedUndoException;
import consulo.usage.MoveRenameUsageInfo;
import consulo.usage.NonCodeUsageInfo;
import consulo.usage.UsageInfo;
import consulo.usage.UsageInfoFactory;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.lang.function.Condition;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

public class RenameUtil {
  private static final Logger LOG = Logger.getInstance(RenameUtil.class);

  private RenameUtil() {
  }

  @Nonnull
  @RequiredReadAction
  public static UsageInfo[] findUsages(final PsiElement element,
                                       final String newName,
                                       boolean searchInStringsAndComments,
                                       boolean searchForTextOccurrences,
                                       Map<? extends PsiElement, String> allRenames) {
    final List<UsageInfo> result = Collections.synchronizedList(new ArrayList<UsageInfo>());

    PsiManager manager = element.getManager();
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(manager.getProject());
    RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(element);

    Collection<PsiReference> refs = processor.findReferences(element, searchInStringsAndComments);
    for (final PsiReference ref : refs) {
      if (ref == null) {
        LOG.error("null reference from processor " + processor);
        continue;
      }
      PsiElement referenceElement = ref.getElement();
      result.add(new MoveRenameUsageInfo(referenceElement, ref, ref.getRangeInElement().getStartOffset(),
                                         ref.getRangeInElement().getEndOffset(), element,
                                         ref.resolve() == null));
    }

    processor.findCollisions(element, newName, allRenames, result);

    final PsiElement searchForInComments = processor.getElementToSearchInStringsAndComments(element);

    if (searchInStringsAndComments && searchForInComments != null) {
      String stringToSearch = ElementDescriptionUtil.getElementDescription(searchForInComments, NonCodeSearchDescriptionLocation.STRINGS_AND_COMMENTS);
      if (stringToSearch.length() > 0) {
        final String stringToReplace = getStringToReplace(element, newName, false, processor);
        UsageInfoFactory factory = new NonCodeUsageInfoFactory(searchForInComments, stringToReplace);
        TextOccurrencesUtil.addUsagesInStringsAndComments(searchForInComments, stringToSearch, result, factory);
      }
    }

    if (searchForTextOccurrences && searchForInComments != null) {
      String stringToSearch = ElementDescriptionUtil.getElementDescription(searchForInComments, NonCodeSearchDescriptionLocation.NON_JAVA);
      if (stringToSearch.length() > 0) {
        final String stringToReplace = getStringToReplace(element, newName, true, processor);
        addTextOccurrence(searchForInComments, result, projectScope, stringToSearch, stringToReplace);
      }

      final Pair<String, String> additionalStringToSearch = processor.getTextOccurrenceSearchStrings(searchForInComments, newName);
      if (additionalStringToSearch != null && additionalStringToSearch.first.length() > 0) {
        addTextOccurrence(searchForInComments, result, projectScope, additionalStringToSearch.first, additionalStringToSearch.second);
      }
    }

    return result.toArray(new UsageInfo[result.size()]);
  }

  private static void addTextOccurrence(final PsiElement element, final List<UsageInfo> result, final GlobalSearchScope projectScope,
                                        final String stringToSearch, final String stringToReplace) {
    UsageInfoFactory factory = new UsageInfoFactory() {
      @Override
      public UsageInfo createUsageInfo(@Nonnull PsiElement usage, int startOffset, int endOffset) {
        TextRange textRange = usage.getTextRange();
        int start = textRange == null ? 0 : textRange.getStartOffset();
        return NonCodeUsageInfo.create(usage.getContainingFile(), start + startOffset, start + endOffset, element, stringToReplace);
      }
    };
    TextOccurrencesUtil.addTextOccurences(element, stringToSearch, projectScope, result, factory);
  }


  public static void buildPackagePrefixChangedMessage(final VirtualFile[] virtualFiles, StringBuffer message, final String qualifiedName) {
    if (virtualFiles.length > 0) {
      message.append(RefactoringLocalize.packageOccursInPackagePrefixesOfTheFollowingSourceFoldersN(qualifiedName).get());
      for (final VirtualFile virtualFile : virtualFiles) {
        message.append(virtualFile.getPresentableUrl()).append("\n");
      }
      message.append(RefactoringLocalize.thesePackagePrefixesWillBeChanged().get());
    }
  }

  private static String getStringToReplace(PsiElement element, String newName, boolean nonJava, final RenamePsiElementProcessor theProcessor) {
    if (element instanceof PsiMetaOwner psiMetaOwner) {
      final PsiMetaData metaData = psiMetaOwner.getMetaData();
      if (metaData != null) {
        return metaData.getName();
      }
    }

    if (theProcessor != null) {
      String result = theProcessor.getQualifiedNameAfterRename(element, newName, nonJava);
      if (result != null) {
        return result;
      }
    }

    if (element instanceof PsiNamedElement) {
      return newName;
    }
    else {
      LOG.error("Unknown element type");
      return null;
    }
  }

  public static void checkRename(PsiElement element, String newName) throws IncorrectOperationException {
    if (element instanceof PsiCheckedRenameElement psiCheckedRenameElement) {
      psiCheckedRenameElement.checkSetName(newName);
    }
  }

  public static void doRename(final PsiElement element, String newName, UsageInfo[] usages, final Project project,
                              @Nullable final RefactoringElementListener listener) throws IncorrectOperationException{
    final RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(element);
    final String fqn = element instanceof PsiFile file ? file.getVirtualFile().getPath()
      : QualifiedNameProviderUtil.elementToFqn(element, null);
    if (fqn != null) {
      UndoableAction action = new BasicUndoableAction() {
        @Override
        public void undo() throws UnexpectedUndoException {
          if (listener instanceof UndoRefactoringElementListener undoRefactoringElementListener) {
            undoRefactoringElementListener.undoElementMovedOrRenamed(element, fqn);
          }
        }

        @Override
        public void redo() throws UnexpectedUndoException {
        }
      };
      ProjectUndoManager.getInstance(project).undoableActionPerformed(action);
    }
    processor.renameElement(element, newName, usages, listener);
  }

  public static void showErrorMessage(final IncorrectOperationException e, final PsiElement element, final Project project) {
    // may happen if the file or package cannot be renamed. e.g. locked by another application
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException(e);
      //LOG.error(e);
      //return;
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        final String helpID = RenamePsiElementProcessor.forElement(element).getHelpID(element);
        String message = e.getMessage();
        if (StringUtil.isEmpty(message)) {
          message = RefactoringLocalize.renameNotSupported().get();
        }
        CommonRefactoringUtil.showErrorMessage(RefactoringLocalize.renameTitle().get(), message, helpID, project);
      }
    });
  }

  public static void doRenameGenericNamedElement(
    @Nonnull PsiElement namedElement, String newName, UsageInfo[] usages,
    @Nullable RefactoringElementListener listener
  ) throws IncorrectOperationException {
    PsiWritableMetaData writableMetaData = namedElement instanceof PsiMetaOwner metaOwner
      && metaOwner.getMetaData() instanceof PsiWritableMetaData wmd ? wmd : null;
    if (writableMetaData == null && !(namedElement instanceof PsiNamedElement)) {
      LOG.error("Unknown element type:" + namedElement);
    }

    boolean hasBindables = false;
    for (UsageInfo usage : usages) {
      if (!(usage.getReference() instanceof BindablePsiReference)) {
        rename(usage, newName);
      } else {
        hasBindables = true;
      }
    }

    if (writableMetaData != null) {
      writableMetaData.setName(newName);
    }
    else {
      PsiElement namedElementAfterRename = ((PsiNamedElement)namedElement).setName(newName);
      if (namedElementAfterRename != null) namedElement = namedElementAfterRename;
    }

    if (hasBindables) {
      for (UsageInfo usage : usages) {
        final PsiReference ref = usage.getReference();
        if (ref instanceof BindablePsiReference) {
          try {
            ref.bindToElement(namedElement);
          }
          catch (IncorrectOperationException e) {//fall back to old scheme
            ref.handleElementRename(newName);
          }
        }
      }
    }
    if (listener != null) {
      listener.elementRenamed(namedElement);
    }
  }

  public static void rename(UsageInfo info, String newName) throws IncorrectOperationException {
    if (info.getElement() == null) return;
    PsiReference ref = info.getReference();
    if (ref == null) return;
    ref.handleElementRename(newName);
  }

  @Nullable
  public static List<UnresolvableCollisionUsageInfo> removeConflictUsages(Set<UsageInfo> usages) {
    final List<UnresolvableCollisionUsageInfo> result = new ArrayList<UnresolvableCollisionUsageInfo>();
    for (Iterator<UsageInfo> iterator = usages.iterator(); iterator.hasNext();) {
      UsageInfo usageInfo = iterator.next();
      if (usageInfo instanceof UnresolvableCollisionUsageInfo unresolvableCollisionUsageInfo) {
        result.add(unresolvableCollisionUsageInfo);
        iterator.remove();
      }
    }
    return result.isEmpty() ? null : result;
  }

  public static void addConflictDescriptions(UsageInfo[] usages, MultiMap<PsiElement, String> conflicts) {
    for (UsageInfo usage : usages) {
      if (usage instanceof UnresolvableCollisionUsageInfo unresolvableCollisionUsageInfo) {
        conflicts.putValue(usage.getElement(), unresolvableCollisionUsageInfo.getDescription());
      }
    }
  }

  public static void renameNonCodeUsages(@Nonnull Project project, @Nonnull NonCodeUsageInfo[] usages) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    Map<Document, List<UsageOffset>> docsToOffsetsMap = new HashMap<Document, List<UsageOffset>>();
    final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    for (NonCodeUsageInfo usage : usages) {
      PsiElement element = usage.getElement();

      if (element == null) continue;
      element = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(element);
      if (element == null) continue;

      final ProperTextRange rangeInElement = usage.getRangeInElement();
      if (rangeInElement == null) continue;

      final PsiFile containingFile = element.getContainingFile();
      final Document document = psiDocumentManager.getDocument(containingFile);

      final Segment segment = usage.getSegment();
      LOG.assertTrue(segment != null);
      int fileOffset = segment.getStartOffset();

      List<UsageOffset> list = docsToOffsetsMap.get(document);
      if (list == null) {
        list = new ArrayList<UsageOffset>();
        docsToOffsetsMap.put(document, list);
      }

      list.add(new UsageOffset(fileOffset, fileOffset + rangeInElement.getLength(), usage.newText));
    }

    for (Document document : docsToOffsetsMap.keySet()) {
      List<UsageOffset> list = docsToOffsetsMap.get(document);
      LOG.assertTrue(list != null, document);
      UsageOffset[] offsets = list.toArray(new UsageOffset[list.size()]);
      Arrays.sort(offsets);

      for (int i = offsets.length - 1; i >= 0; i--) {
        UsageOffset usageOffset = offsets[i];
        document.replaceString(usageOffset.startOffset, usageOffset.endOffset, usageOffset.newText);
      }
      PsiDocumentManager.getInstance(project).commitDocument(document);
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();
  }

  public static boolean isValidName(final Project project, final PsiElement psiElement, final String newName) {
    if (newName == null || newName.length() == 0) {
      return false;
    }
    final Condition<String> inputValidator = RenameInputValidatorRegistry.getInputValidator(psiElement);
    if (inputValidator != null) {
      return inputValidator.value(newName);
    }
    if (psiElement instanceof PsiFile || psiElement instanceof PsiDirectory) {
      return newName.indexOf('\\') < 0 && newName.indexOf('/') < 0;
    }
    if (psiElement instanceof PomTargetPsiElement) {
      return !StringUtil.isEmptyOrSpaces(newName);
    }

    final PsiFile file = psiElement.getContainingFile();
    final Language elementLanguage = psiElement.getLanguage();

    final Language fileLanguage = file == null ? null : file.getLanguage();
    Language language = fileLanguage == null ? elementLanguage : fileLanguage.isKindOf(elementLanguage) ? fileLanguage : elementLanguage;

    return NamesValidator.forLanguage(language).isIdentifier(newName.trim(), project);
  }

  private static class UsageOffset implements Comparable<UsageOffset> {
    final int startOffset;
    final int endOffset;
    final String newText;

    public UsageOffset(int startOffset, int endOffset, String newText) {
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      this.newText = newText;
    }

    @Override
    public int compareTo(final UsageOffset o) {
      return startOffset - o.startOffset;
    }
  }
}
