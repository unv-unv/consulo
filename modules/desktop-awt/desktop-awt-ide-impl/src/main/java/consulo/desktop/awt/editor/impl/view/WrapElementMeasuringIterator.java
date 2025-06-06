// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.desktop.awt.editor.impl.view;

import consulo.codeEditor.FoldRegion;
import consulo.codeEditor.Inlay;
import consulo.ide.impl.idea.openapi.editor.ex.util.EditorUtil;
import consulo.ide.impl.idea.openapi.editor.impl.softwrap.WrapElementIterator;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * {@link WrapElementIterator} extension that also calculates widths of elements.
 */
public final class WrapElementMeasuringIterator extends WrapElementIterator {
    private final EditorViewImpl myView;
    private final List<Inlay<?>> inlineInlays;
    private final List<Inlay<?>> afterLineEndInlays;

    private int inlineInlayIndex;
    private int afterLineEndInlayIndex;

    public WrapElementMeasuringIterator(@Nonnull EditorViewImpl view, int startOffset, int endOffset,
                                        List<Inlay<?>> inlineInlays,
                                        List<Inlay<?>> afterLineEndInlays) {
        super(view.getEditor(), startOffset, endOffset);
        myView = view;
        this.inlineInlays = inlineInlays;
        this.afterLineEndInlays = afterLineEndInlays;
    }

    public float getElementEndX(float startX) {
        FoldRegion fold = getCurrentFold();
        if (fold == null) {
            int codePoint = getCodePoint();
            if (codePoint == '\t') {
                return EditorUtil.nextTabStop(startX + getInlaysPrefixWidth(), myView.getPlainSpaceWidth(), myView.getTabSize()) +
                    getInlaysSuffixWidth();
            }
            else if (codePoint == '\r') { // can only happen when \n part of \r\n line break is folded
                return startX;
            }
            else {
                return startX + getInlaysPrefixWidth() + myView.getCodePointWidth(codePoint, getFontStyle()) + getInlaysSuffixWidth();
            }
        }
        else {
            return startX + myView.getFoldRegionLayout(fold).getWidth();
        }
    }

    private float getInlaysPrefixWidth() {
        return getInlaysWidthForOffset(getElementStartOffset());
    }

    private float getInlaysSuffixWidth() {
        int nextOffset = getElementEndOffset();
        if (nextOffset < myText.length() && "\r\n".indexOf(myText.charAt(nextOffset)) == -1 || nextIsFoldRegion()) {
            return 0;
        }
        int afterLineEndInlaysWidth = getAfterLineEndInlaysWidth(getLogicalLine());
        return getInlaysWidthForOffset(nextOffset) + (afterLineEndInlaysWidth == 0 ? 0 : myView.getPlainSpaceWidth() + afterLineEndInlaysWidth);
    }

    private int getInlaysWidthForOffset(int offset) {
        while (inlineInlayIndex < inlineInlays.size() && inlineInlays.get(inlineInlayIndex).getOffset() < offset) inlineInlayIndex++;
        while (inlineInlayIndex > 0 && inlineInlays.get(inlineInlayIndex - 1).getOffset() >= offset) inlineInlayIndex--;
        int width = 0;
        while (inlineInlayIndex < inlineInlays.size() && inlineInlays.get(inlineInlayIndex).getOffset() == offset) {
            width += inlineInlays.get(inlineInlayIndex++).getWidthInPixels();
        }
        return width;
    }

    private int getAfterLineEndInlaysWidth(int logicalLine) {
        int startOffset = myDocument.getLineStartOffset(logicalLine);
        int endOffset = myDocument.getLineEndOffset(logicalLine);
        while (afterLineEndInlayIndex < afterLineEndInlays.size()
            && afterLineEndInlays.get(afterLineEndInlayIndex).getOffset() < startOffset) {
            afterLineEndInlayIndex++;
        }
        while (afterLineEndInlayIndex > 0 && afterLineEndInlays.get(afterLineEndInlayIndex - 1).getOffset() >= startOffset) {
            afterLineEndInlayIndex--;
        }
        int width = 0;
        while (afterLineEndInlayIndex < afterLineEndInlays.size()) {
            Inlay<?> inlay = afterLineEndInlays.get(afterLineEndInlayIndex);
            int offset = inlay.getOffset();
            if (offset < startOffset || offset > endOffset) {
                break;
            }
            width += inlay.getWidthInPixels();
            afterLineEndInlayIndex++;
        }
        return width;
    }
}
