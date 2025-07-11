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
package consulo.language.codeStyle.template;

import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.ASTBlock;
import consulo.language.codeStyle.Block;
import consulo.language.codeStyle.Indent;
import consulo.language.codeStyle.Spacing;
import consulo.util.lang.Pair;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexey Chmutov
 * @since 2009-07-03
 */
class BlockUtil {
  private BlockUtil() {
  }

  public static List<DataLanguageBlockWrapper> buildChildWrappers(@Nonnull final Block parent) {
    assert !(parent instanceof DataLanguageBlockWrapper) : parent.getClass();
    List<Block> children = parent.getSubBlocks();
    if (children.size() == 0) return Collections.emptyList();
    ArrayList<DataLanguageBlockWrapper> result = new ArrayList<DataLanguageBlockWrapper>(children.size());
    DataLanguageBlockWrapper prevWrapper = null;
    for (Block child : children) {
      DataLanguageBlockWrapper currWrapper = createAndAddBlock(result, child, null);
      if(currWrapper != null && prevWrapper != null) {
        Spacing spacing = parent.getSpacing(prevWrapper.getOriginal(), currWrapper.getOriginal());
        prevWrapper.setRightHandSpacing(currWrapper, spacing);
      }
      prevWrapper = currWrapper;
    }
    return result;
  }

  public static Pair<List<DataLanguageBlockWrapper>, List<DataLanguageBlockWrapper>> splitBlocksByRightBound(@Nonnull Block parent, @Nonnull TextRange bounds) {
    final List<Block> subBlocks = parent.getSubBlocks();
    if (subBlocks.size() == 0) return new Pair<List<DataLanguageBlockWrapper>, List<DataLanguageBlockWrapper>>(Collections.<DataLanguageBlockWrapper>emptyList(), Collections.<DataLanguageBlockWrapper>emptyList());
    final ArrayList<DataLanguageBlockWrapper> before = new ArrayList<DataLanguageBlockWrapper>(subBlocks.size() / 2);
    final ArrayList<DataLanguageBlockWrapper> after = new ArrayList<DataLanguageBlockWrapper>(subBlocks.size() / 2);
    splitByRightBoundAndCollectBlocks(subBlocks, before, after, bounds);
    return new Pair<List<DataLanguageBlockWrapper>, List<DataLanguageBlockWrapper>>(before, after);
  }

  private static void splitByRightBoundAndCollectBlocks(@Nonnull List<Block> blocks,
                                                        @Nonnull List<DataLanguageBlockWrapper> before,
                                                        @Nonnull List<DataLanguageBlockWrapper> after,
                                                        @Nonnull TextRange bounds) {
    for (Block block : blocks) {
      final TextRange textRange = block.getTextRange();
      if (bounds.contains(textRange)) {
        createAndAddBlock(before, block, null);
      }
      else if (bounds.getEndOffset() <= textRange.getStartOffset()) {
        createAndAddBlock(after, block, null);
      }
      else {
        //assert block.getSubBlocks().size() != 0 : "Block " + block.getTextRange() + " doesn't contain subblocks!";
        splitByRightBoundAndCollectBlocks(block.getSubBlocks(), before, after, bounds);
      }
    }
  }

  @Nullable
  private static DataLanguageBlockWrapper createAndAddBlock(List<DataLanguageBlockWrapper> list, Block block, @Nullable final Indent indent) {
    DataLanguageBlockWrapper wrapper = DataLanguageBlockWrapper.create(block, indent);
    if (wrapper != null) {
      list.add(wrapper);
    }
    return wrapper;
  }


  public static List<Block> mergeBlocks(@Nonnull List<TemplateLanguageBlock> tlBlocks, @Nonnull List<DataLanguageBlockWrapper> foreignBlocks) {
    ArrayList<Block> result = new ArrayList<Block>(tlBlocks.size() + foreignBlocks.size());
    int vInd = 0;
    int fInd = 0;
    while (vInd < tlBlocks.size() && fInd < foreignBlocks.size()) {
      final TemplateLanguageBlock v = tlBlocks.get(vInd);
      final DataLanguageBlockWrapper f = foreignBlocks.get(fInd);
      final TextRange vRange = v.getTextRange();
      final TextRange fRange = f.getTextRange();
      if (vRange.getStartOffset() >= fRange.getEndOffset()) {
        // add leading foreign blocks
        result.add(f);
        fInd++;
      }
      else if (vRange.getEndOffset() <= fRange.getStartOffset()) {
        // add leading TL blocks
        result.add(v);
        vInd++;
      }
      else if (vRange.getStartOffset() < fRange.getStartOffset() ||
               vRange.getStartOffset() == fRange.getStartOffset() && vRange.getEndOffset() >= fRange.getEndOffset()) {
        // add including TL blocks and split intersecting foreign blocks
        result.add(v);
        while (fInd < foreignBlocks.size() && vRange.contains(foreignBlocks.get(fInd).getTextRange())) {
          v.addForeignChild(foreignBlocks.get(fInd++));
        }
        if (fInd < foreignBlocks.size()) {
          final DataLanguageBlockWrapper notContainedF = foreignBlocks.get(fInd);
          if (vRange.intersectsStrict(notContainedF.getTextRange())) {
            Pair<List<DataLanguageBlockWrapper>, List<DataLanguageBlockWrapper>> splitBlocks = splitBlocksByRightBound(notContainedF.getOriginal(), vRange);
            v.addForeignChildren(splitBlocks.getFirst());
            foreignBlocks.remove(fInd);
            if (splitBlocks.getSecond().size() > 0) {
              foreignBlocks.addAll(fInd, splitBlocks.getSecond());
            }
          }
        }
        vInd++;
      }
      else if (vRange.getStartOffset() > fRange.getStartOffset() ||
               vRange.getStartOffset() == fRange.getStartOffset() && vRange.getEndOffset() < fRange.getEndOffset()) {
        // add including foreign blocks or split them if needed
        int lastContainedTlInd = vInd;
        while (lastContainedTlInd < tlBlocks.size() && fRange.intersectsStrict(tlBlocks.get(lastContainedTlInd).getTextRange())) {
          lastContainedTlInd++;
        }
        if (fRange.contains(tlBlocks.get(lastContainedTlInd - 1).getTextRange())) {
          result.add(f);
          fInd++;
          while (vInd < lastContainedTlInd) {
            f.addTlChild(tlBlocks.get(vInd++));
          }
        }
        else {
          foreignBlocks.remove(fInd);
          foreignBlocks.addAll(fInd, buildChildWrappers(f.getOriginal()));
        }
      }
    }
    while (vInd < tlBlocks.size()) {
      result.add(tlBlocks.get(vInd++));
    }
    while (fInd < foreignBlocks.size()) {
      result.add(foreignBlocks.get(fInd++));
    }
    return result;
  }

  @Nonnull
  public static List<DataLanguageBlockWrapper> filterBlocksByRange(@Nonnull List<DataLanguageBlockWrapper> list, @Nonnull TextRange textRange) {
    int i = 0;
    while (i < list.size()) {
      final DataLanguageBlockWrapper wrapper = list.get(i);
      final TextRange range = wrapper.getTextRange();
      if (textRange.contains(range)) {
        i++;
      }
      else if (range.intersectsStrict(textRange)) {
        list.remove(i);
        list.addAll(i, buildChildWrappers(wrapper.getOriginal()));
      }
      else {
        list.remove(i);
      }
    }
    return list;
  }

  static List<Block> splitBlockIntoFragments(@Nonnull Block block, @Nonnull List<TemplateLanguageBlock> subBlocks) {
    final List<Block> children = new ArrayList<Block>(5);
    final TextRange range = block.getTextRange();
    int childStartOffset = range.getStartOffset();
    TemplateLanguageBlock lastTLBlock = null;
    for (TemplateLanguageBlock tlBlock : subBlocks) {
      final TextRange tlTextRange = tlBlock.getTextRange();
      if (tlTextRange.getStartOffset() > childStartOffset) {
        TextRange dataBlockTextRange = new TextRange(childStartOffset, tlTextRange.getStartOffset());
        if (tlBlock.isRequiredRange(dataBlockTextRange)) {
          children.add(new DataLanguageBlockFragmentWrapper(block, dataBlockTextRange));
        }
      }
      children.add(tlBlock);
      lastTLBlock = tlBlock;
      childStartOffset = tlTextRange.getEndOffset();
    }
    if (range.getEndOffset() > childStartOffset) {
      TextRange dataBlockTextRange = new TextRange(childStartOffset, range.getEndOffset());
      if (lastTLBlock == null || lastTLBlock.isRequiredRange(dataBlockTextRange) ) {
        children.add(new DataLanguageBlockFragmentWrapper(block, dataBlockTextRange));
      }
    }
    return children;
  }

  static void printBlocks(@Nullable TextRange textRange, @Nonnull List<Block> list) {
    StringBuilder sb = new StringBuilder(String.valueOf(textRange)).append(": ");
    for (Block block : list) {
      ASTNode node = block instanceof ASTBlock ? ((ASTBlock)block).getNode() : null;
      TextRange r = block.getTextRange();
      sb.append(" [").append(node != null ? node.getElementType() : null)//.append(" ").append(((BlockWithParent)block).getParent() != null)
          .append(r).append(block.getIndent()).append(block.getAlignment()).append("] ");
    }
    System.out.println(sb);
  }

  static List<Block> setParent(List<Block> children, BlockWithParent parent) {
    for (Block block : children) {
      if (block instanceof BlockWithParent) ((BlockWithParent)block).setParent(parent);
    }
    return children;
  }
}
