// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.language.impl.internal.psi.pointer;

import consulo.application.ReadAction;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.document.event.DocumentEvent;
import consulo.document.impl.FrozenDocument;
import consulo.document.util.ProperTextRange;
import consulo.document.util.Segment;
import consulo.document.util.TextRange;
import consulo.document.util.UnfairTextRange;
import consulo.language.Language;
import consulo.language.impl.internal.psi.PsiDocumentManagerBase;
import consulo.language.impl.psi.pointer.Identikit;
import consulo.language.psi.*;
import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

public class SelfElementInfo extends SmartPointerElementInfo {
  private static final FileDocumentManager ourFileDocManager = FileDocumentManager.getInstance();
  private volatile Identikit myIdentikit;
  private final VirtualFile myFile;
  private final boolean myForInjected;
  private int myStartOffset;
  private int myEndOffset;

  SelfElementInfo(@Nullable ProperTextRange range, @Nonnull Identikit identikit, @Nonnull PsiFile containingFile, boolean forInjected) {
    myForInjected = forInjected;
    myIdentikit = identikit;

    myFile = containingFile.getViewProvider().getVirtualFile();
    setRange(range);
  }

  void switchToAnchor(@Nonnull PsiElement element) {
    switchTo(element, findAnchor(element));
  }

  @Nullable
  private Pair<IdentikitImpl.ByAnchor, PsiElement> findAnchor(@Nonnull PsiElement element) {
    Language language = myIdentikit.getFileLanguage();
    if (language == null) return null;
    return IdentikitImpl.withAnchor(element, language);
  }

  private void switchTo(@Nonnull PsiElement element, @Nullable Pair<IdentikitImpl.ByAnchor, PsiElement> pair) {
    if (pair != null) {
      assert pair.first.hashCode() == myIdentikit.hashCode();
      myIdentikit = pair.first;
      setRange(pair.second.getTextRange());
    }
    else {
      setRange(element.getTextRange());
    }
  }

  boolean updateRangeToPsi(@Nonnull Segment pointerRange, PsiElement cachedElement) {
    Pair<IdentikitImpl.ByAnchor, PsiElement> pair = findAnchor(cachedElement);
    TextRange range = (pair != null ? pair.second : cachedElement).getTextRange();
    if (range != null && range.intersects(pointerRange)) {
      switchTo(cachedElement, pair);
      return true;
    }
    return false;
  }


  void setRange(@Nullable Segment range) {
    if (range == null) {
      myStartOffset = -1;
      myEndOffset = -1;
    }
    else {
      myStartOffset = range.getStartOffset();
      myEndOffset = range.getEndOffset();
    }
  }

  boolean hasRange() {
    return myStartOffset >= 0;
  }

  int getPsiStartOffset() {
    return myStartOffset;
  }

  int getPsiEndOffset() {
    return myEndOffset;
  }

  boolean isGreedy() {
    return myForInjected || myIdentikit.isForPsiFile();
  }

  @Override
  Document getDocumentToSynchronize() {
    return ourFileDocManager.getCachedDocument(getVirtualFile());
  }

  @Override
  PsiElement restoreElement(@Nonnull SmartPointerManagerImpl manager) {
    Segment segment = getPsiRange(manager);
    if (segment == null) return null;

    PsiFile file = restoreFile(manager);
    if (file == null || !file.isValid()) return null;

    return myIdentikit.findPsiElement(file, segment.getStartOffset(), segment.getEndOffset());
  }

  @Nullable
  @Override
  TextRange getPsiRange(@Nonnull SmartPointerManagerImpl manager) {
    return calcPsiRange();
  }

  boolean isForInjected() {
    return myForInjected;
  }

  @Nullable
  private TextRange calcPsiRange() {
    return hasRange() ? new UnfairTextRange(myStartOffset, myEndOffset) : null;
  }

  @Override
  @Nullable
  PsiFile restoreFile(@Nonnull SmartPointerManagerImpl manager) {
    Language language = myIdentikit.getFileLanguage();
    if (language == null) return null;
    return restoreFileFromVirtual(getVirtualFile(), manager.getProject(), language);
  }

  @Override
  void cleanup() {
    setRange(null);
  }

  @Nullable
  public static PsiFile restoreFileFromVirtual(@Nonnull VirtualFile virtualFile, @Nonnull Project project, @Nonnull Language language) {
    return ReadAction.compute(() -> {
      if (project.isDisposed()) return null;
      VirtualFile child = restoreVFile(virtualFile);
      if (child == null || !child.isValid()) return null;
      PsiFile file = PsiManager.getInstance(project).findFile(child);
      if (file != null) {
        return file.getViewProvider().getPsi(language == Language.ANY ? file.getViewProvider().getBaseLanguage() : language);
      }

      return null;
    });
  }

  @Nullable
  public static PsiDirectory restoreDirectoryFromVirtual(@Nonnull VirtualFile virtualFile, @Nonnull final Project project) {
    return ReadAction.compute(() -> {
      if (project.isDisposed()) return null;
      VirtualFile child = restoreVFile(virtualFile);
      if (child == null || !child.isValid()) return null;
      PsiDirectory file = PsiManager.getInstance(project).findDirectory(child);
      if (file == null || !file.isValid()) return null;
      return file;
    });
  }

  @Nullable
  private static VirtualFile restoreVFile(@Nonnull VirtualFile virtualFile) {
    VirtualFile child;
    if (virtualFile.isValid()) {
      child = virtualFile;
    }
    else {
      VirtualFile vParent = virtualFile.getParent();
      if (vParent == null || !vParent.isValid()) return null;
      String name = virtualFile.getName();
      child = vParent.findChild(name);
    }
    return child;
  }

  @Override
  int elementHashCode() {
    return getVirtualFile().hashCode() + myIdentikit.hashCode() * 31;
  }

  @Override
  boolean pointsToTheSameElementAs(@Nonnull SmartPointerElementInfo other, @Nonnull SmartPointerManagerImpl manager) {
    if (other instanceof SelfElementInfo) {
      final SelfElementInfo otherInfo = (SelfElementInfo)other;
      if (!getVirtualFile().equals(other.getVirtualFile()) || myIdentikit != otherInfo.myIdentikit) return false;

      return ReadAction.compute(() -> {
        Segment range1 = getPsiRange(manager);
        Segment range2 = otherInfo.getPsiRange(manager);
        return range1 != null && range2 != null && range1.getStartOffset() == range2.getStartOffset() && range1.getEndOffset() == range2.getEndOffset();
      });
    }
    return false;
  }

  @Override
  @Nonnull
  final VirtualFile getVirtualFile() {
    return myFile;
  }

  @Override
  @Nullable
  Segment getRange(@Nonnull SmartPointerManagerImpl manager) {
    if (hasRange()) {
      Document document = getDocumentToSynchronize();
      if (document != null) {
        PsiDocumentManagerBase documentManager = manager.getPsiDocumentManager();
        List<DocumentEvent> events = documentManager.getEventsSinceCommit(document);
        if (!events.isEmpty()) {
          SmartPointerTracker tracker = manager.getTracker(getVirtualFile());
          if (tracker != null) {
            return tracker.getUpdatedRange(this, (FrozenDocument)documentManager.getLastCommittedDocument(document), events);
          }
        }
      }
    }
    return calcPsiRange();
  }

  @Override
  public String toString() {
    return "psi:range=" + calcPsiRange() + ",type=" + myIdentikit;
  }

  public static Segment calcActualRangeAfterDocumentEvents(@Nonnull PsiFile containingFile, @Nonnull Document document, @Nonnull Segment segment, boolean isSegmentGreedy) {
    Project project = containingFile.getProject();
    PsiDocumentManagerBase documentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(project);
    List<DocumentEvent> events = documentManager.getEventsSinceCommit(document);
    if (!events.isEmpty()) {
      SmartPointerManagerImpl pointerManager = (SmartPointerManagerImpl)SmartPointerManager.getInstance(project);
      SmartPointerTracker tracker = pointerManager.getTracker(containingFile.getViewProvider().getVirtualFile());
      if (tracker != null) {
        return tracker.getUpdatedRange(containingFile, segment, isSegmentGreedy, (FrozenDocument)documentManager.getLastCommittedDocument(document), events);
      }
    }
    return null;
  }
}
