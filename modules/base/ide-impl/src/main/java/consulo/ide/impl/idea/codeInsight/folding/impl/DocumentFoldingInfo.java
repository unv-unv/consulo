// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package consulo.ide.impl.idea.codeInsight.folding.impl;

import consulo.codeEditor.internal.FoldingUtil;
import consulo.language.ast.ASTNode;
import consulo.language.editor.folding.FoldingBuilder;
import consulo.language.editor.folding.FoldingDescriptor;
import consulo.language.editor.folding.LanguageFolding;
import consulo.application.ApplicationManager;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.codeEditor.FoldRegion;
import consulo.document.RangeMarker;
import consulo.document.FileDocumentManager;
import consulo.fileEditor.text.CodeFoldingState;
import consulo.project.Project;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.Comparing;
import consulo.util.dataholder.Key;
import consulo.document.util.TextRange;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.ide.impl.idea.util.text.StringTokenizer;
import consulo.ide.impl.idea.xml.util.XmlStringUtil;
import consulo.logging.Logger;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nonnull;

import java.util.*;

class DocumentFoldingInfo implements CodeFoldingState {
  private static final Logger LOG = Logger.getInstance(DocumentFoldingInfo.class);
  private static final Key<FoldingInfo> FOLDING_INFO_KEY = Key.create("FOLDING_INFO");

  @Nonnull
  private final Project myProject;
  private final VirtualFile myFile;

  @Nonnull
  private final List<Info> myInfos = ContainerUtil.createLockFreeCopyOnWriteList();
  @Nonnull
  private final List<RangeMarker> myRangeMarkers = ContainerUtil.createLockFreeCopyOnWriteList();
  private static final String DEFAULT_PLACEHOLDER = "...";
  @NonNls
  private static final String ELEMENT_TAG = "element";
  @NonNls
  private static final String SIGNATURE_ATT = "signature";
  @NonNls
  private static final String EXPANDED_ATT = "expanded";
  @NonNls
  private static final String MARKER_TAG = "marker";
  @NonNls
  private static final String DATE_ATT = "date";
  @NonNls
  private static final String PLACEHOLDER_ATT = "ph";

  DocumentFoldingInfo(@Nonnull Project project, @Nonnull Document document) {
    myProject = project;
    myFile = FileDocumentManager.getInstance().getFile(document);
  }

  @RequiredUIAccess
  void loadFromEditor(@Nonnull Editor editor) {
    UIAccess.assertIsUIThread();
    LOG.assertTrue(!editor.isDisposed());
    clear();

    FoldRegion[] foldRegions = editor.getFoldingModel().getAllFoldRegions();
    for (FoldRegion region : foldRegions) {
      if (!region.isValid()) continue;
      boolean expanded = region.isExpanded();
      String signature = region.getUserData(UpdateFoldRegionsOperation.SIGNATURE);
      if (signature == UpdateFoldRegionsOperation.NO_SIGNATURE) continue;
      Boolean storedCollapseByDefault = region.getUserData(UpdateFoldRegionsOperation.COLLAPSED_BY_DEFAULT);
      boolean collapseByDefault = storedCollapseByDefault != null && storedCollapseByDefault && !FoldingUtil.caretInsideRange(editor, TextRange.create(region));
      if (collapseByDefault == expanded || signature == null) {
        if (signature != null) {
          myInfos.add(new Info(signature, expanded));
        }
        else {
          RangeMarker marker = editor.getDocument().createRangeMarker(region.getStartOffset(), region.getEndOffset());
          myRangeMarkers.add(marker);
          marker.putUserData(FOLDING_INFO_KEY, new FoldingInfo(region.getPlaceholderText(), expanded));
        }
      }
    }
  }

  @Override
  @RequiredUIAccess
  public void setToEditor(@Nonnull final Editor editor) {
    UIAccess.assertIsUIThread();
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    if (psiManager.isDisposed()) return;

    if (!myFile.isValid()) return;
    final PsiFile psiFile = psiManager.findFile(myFile);
    if (psiFile == null) return;

    Map<PsiElement, FoldingDescriptor> ranges = null;
    for (Info info : myInfos) {
      PsiElement element = FoldingPolicy.restoreBySignature(psiFile, info.signature);
      if (element == null || !element.isValid()) {
        continue;
      }

      if (ranges == null) {
        ranges = buildRanges(editor, psiFile);
      }
      FoldingDescriptor descriptor = ranges.get(element);
      if (descriptor == null) {
        continue;
      }

      TextRange range = descriptor.getRange();
      FoldRegion region = FoldingUtil.findFoldRegion(editor, range.getStartOffset(), range.getEndOffset());
      if (region != null) {
        region.setExpanded(info.expanded);
      }
    }
    for (RangeMarker marker : myRangeMarkers) {
      if (!marker.isValid() || marker.getStartOffset() == marker.getEndOffset()) {
        continue;
      }
      FoldRegion region = FoldingUtil.findFoldRegion(editor, marker.getStartOffset(), marker.getEndOffset());
      FoldingInfo info = marker.getUserData(FOLDING_INFO_KEY);
      if (region == null) {
        if (info != null) {
          region = editor.getFoldingModel().addFoldRegion(marker.getStartOffset(), marker.getEndOffset(), info.placeHolder);
        }
        if (region == null) {
          return;
        }
      }

      boolean state = info != null && info.expanded;
      region.setExpanded(state);
    }
  }

  @Nonnull
  private static Map<PsiElement, FoldingDescriptor> buildRanges(@Nonnull Editor editor, @Nonnull PsiFile psiFile) {
    final FoldingBuilder foldingBuilder = FoldingBuilder.forLanguageComposite(psiFile.getLanguage());
    final ASTNode node = psiFile.getNode();
    if (node == null) return Collections.emptyMap();
    final FoldingDescriptor[] descriptors = LanguageFolding.buildFoldingDescriptors(foldingBuilder, psiFile, editor.getDocument(), true);
    Map<PsiElement, FoldingDescriptor> ranges = new HashMap<>();
    for (FoldingDescriptor descriptor : descriptors) {
      final ASTNode ast = descriptor.getElement();
      final PsiElement psi = ast.getPsi();
      if (psi != null) {
        ranges.put(psi, descriptor);
      }
    }
    return ranges;
  }

  void clear() {
    myInfos.clear();
    for (RangeMarker marker : myRangeMarkers) {
      marker.dispose();
    }
    myRangeMarkers.clear();
  }

  void writeExternal(@Nonnull Element element) {
    if (myInfos.isEmpty() && myRangeMarkers.isEmpty()) {
      return;
    }

    for (Info info : myInfos) {
      Element e = new Element(ELEMENT_TAG);
      e.setAttribute(SIGNATURE_ATT, info.signature);
      if (info.expanded) {
        e.setAttribute(EXPANDED_ATT, Boolean.toString(true));
      }
      element.addContent(e);
    }

    String date = null;
    for (RangeMarker marker : myRangeMarkers) {
      FoldingInfo fi = marker.getUserData(FOLDING_INFO_KEY);
      boolean state = fi != null && fi.expanded;

      Element e = new Element(MARKER_TAG);
      if (date == null) {
        date = getTimeStamp();
      }
      if (date.isEmpty()) {
        continue;
      }

      e.setAttribute(DATE_ATT, date);
      e.setAttribute(EXPANDED_ATT, Boolean.toString(state));
      String signature = marker.getStartOffset() + ":" + marker.getEndOffset();
      e.setAttribute(SIGNATURE_ATT, signature);
      String placeHolderText = fi == null ? DEFAULT_PLACEHOLDER : fi.placeHolder;
      e.setAttribute(PLACEHOLDER_ATT, XmlStringUtil.escapeIllegalXmlChars(placeHolderText));
      element.addContent(e);
    }
  }

  void readExternal(final Element element) {
    ApplicationManager.getApplication().runReadAction(() -> {
      clear();

      if (!myFile.isValid()) return;

      final Document document = FileDocumentManager.getInstance().getDocument(myFile);
      if (document == null) return;

      String date = null;
      for (final Element e : element.getChildren()) {
        String signature = e.getAttributeValue(SIGNATURE_ATT);
        if (signature == null) {
          continue;
        }

        boolean expanded = Boolean.parseBoolean(e.getAttributeValue(EXPANDED_ATT));
        if (ELEMENT_TAG.equals(e.getName())) {
          myInfos.add(new Info(signature, expanded));
        }
        else if (MARKER_TAG.equals(e.getName())) {
          if (date == null) {
            date = getTimeStamp();
          }
          if (date.isEmpty()) continue;

          if (!date.equals(e.getAttributeValue(DATE_ATT)) || FileDocumentManager.getInstance().isDocumentUnsaved(document)) continue;
          StringTokenizer tokenizer = new StringTokenizer(signature, ":");
          try {
            int start = Integer.valueOf(tokenizer.nextToken()).intValue();
            int end = Integer.valueOf(tokenizer.nextToken()).intValue();
            if (start < 0 || end >= document.getTextLength() || start > end) continue;
            RangeMarker marker = document.createRangeMarker(start, end);
            myRangeMarkers.add(marker);
            String placeholderAttributeValue = e.getAttributeValue(PLACEHOLDER_ATT);
            String placeHolderText = placeholderAttributeValue == null ? DEFAULT_PLACEHOLDER : XmlStringUtil.unescapeIllegalXmlChars(placeholderAttributeValue);
            FoldingInfo fi = new FoldingInfo(placeHolderText, expanded);
            marker.putUserData(FOLDING_INFO_KEY, fi);
          }
          catch (NoSuchElementException exc) {
            LOG.error(exc);
          }
        }
        else {
          throw new IllegalStateException("unknown tag: " + e.getName());
        }
      }
    });
  }

  private String getTimeStamp() {
    if (!myFile.isValid()) return "";
    return Long.toString(myFile.getTimeStamp());
  }

  @Override
  public int hashCode() {
    int result = myProject.hashCode();
    result = 31 * result + (myFile != null ? myFile.hashCode() : 0);
    result = 31 * result + myInfos.hashCode();
    result = 31 * result + myRangeMarkers.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    DocumentFoldingInfo info = (DocumentFoldingInfo)o;

    if (myFile != null ? !myFile.equals(info.myFile) : info.myFile != null) {
      return false;
    }
    if (!myProject.equals(info.myProject) || !myInfos.equals(info.myInfos)) {
      return false;
    }

    if (myRangeMarkers.size() != info.myRangeMarkers.size()) return false;
    for (int i = 0; i < myRangeMarkers.size(); i++) {
      RangeMarker marker = myRangeMarkers.get(i);
      RangeMarker other = info.myRangeMarkers.get(i);
      if (marker == other || !marker.isValid() || !other.isValid()) {
        continue;
      }
      if (!TextRange.areSegmentsEqual(marker, other)) return false;

      FoldingInfo fi = marker.getUserData(FOLDING_INFO_KEY);
      FoldingInfo ofi = other.getUserData(FOLDING_INFO_KEY);
      if (!Comparing.equal(fi, ofi)) return false;
    }
    return true;
  }

  private static class Info {
    private final String signature;
    private final boolean expanded;

    Info(@Nonnull String signature, boolean expanded) {
      this.signature = signature;
      this.expanded = expanded;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Info info = (Info)o;
      return expanded == info.expanded && Objects.equals(signature, info.signature);
    }

    @Override
    public int hashCode() {
      return Objects.hash(signature, expanded);
    }
  }

  private static class FoldingInfo {
    private final String placeHolder;
    private final boolean expanded;

    private FoldingInfo(@Nonnull String placeHolder, boolean expanded) {
      this.placeHolder = placeHolder;
      this.expanded = expanded;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      FoldingInfo info = (FoldingInfo)o;

      return expanded == info.expanded && placeHolder.equals(info.placeHolder);
    }

    @Override
    public int hashCode() {
      int result = placeHolder.hashCode();
      result = 31 * result + (expanded ? 1 : 0);
      return result;
    }

    public boolean getExpanded() {
      return expanded;
    }
  }
}
