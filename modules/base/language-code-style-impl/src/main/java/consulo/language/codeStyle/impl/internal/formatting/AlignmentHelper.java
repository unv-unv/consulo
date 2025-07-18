/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package consulo.language.codeStyle.impl.internal.formatting;

import consulo.annotation.access.RequiredReadAction;
import consulo.document.Document;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.Alignment;
import consulo.language.codeStyle.Block;
import consulo.language.codeStyle.internal.AbstractBlockWrapper;
import consulo.language.codeStyle.internal.AlignmentImpl;
import consulo.language.codeStyle.internal.LeafBlockWrapper;
import consulo.logging.Logger;
import consulo.logging.util.LoggerUtil;
import consulo.util.collection.MultiMap;

import java.util.*;

public class AlignmentHelper {
    private static final Logger LOG = Logger.getInstance(AlignmentHelper.class);

    private static final Map<Alignment.Anchor, BlockAlignmentProcessor> ALIGNMENT_PROCESSORS = new EnumMap<>(Alignment.Anchor.class);

    static {
        ALIGNMENT_PROCESSORS.put(Alignment.Anchor.LEFT, new LeftEdgeAlignmentProcessor());
        ALIGNMENT_PROCESSORS.put(Alignment.Anchor.RIGHT, new RightEdgeAlignmentProcessor());
    }

    private final Set<Alignment> myAlignmentsToSkip = new HashSet<>();
    private final Document myDocument;
    private final BlockIndentOptions myBlockIndentOptions;

    private final AlignmentCyclesDetector myCyclesDetector;

    private final Map<LeafBlockWrapper, Set<LeafBlockWrapper>> myBackwardShiftedAlignedBlocks = new HashMap<>();
    private final Map<AbstractBlockWrapper, Set<AbstractBlockWrapper>> myAlignmentMappings = new HashMap<>();

    public AlignmentHelper(Document document, MultiMap<Alignment, Block> blocksToAlign, BlockIndentOptions options) {
        myDocument = document;
        myBlockIndentOptions = options;
        int totalBlocks = blocksToAlign.values().size();
        myCyclesDetector = new AlignmentCyclesDetector(totalBlocks);
    }

    @RequiredReadAction
    private static void reportAlignmentProcessingError(BlockAlignmentProcessor.Context context) {
        ASTNode node = context.targetBlock.getNode();
        Language language = node != null ? node.getPsi().getLanguage() : null;
        LoggerUtil.error(
            LOG,
            (language != null ? language.getID() + ": " : "") +
                "Can't align block " + context.targetBlock, context.document.getText()
        );
    }

    @RequiredReadAction
    public LeafBlockWrapper applyAlignment(AlignmentImpl alignment, LeafBlockWrapper currentBlock) {
        BlockAlignmentProcessor alignmentProcessor = ALIGNMENT_PROCESSORS.get(alignment.getAnchor());
        if (alignmentProcessor == null) {
            LOG.error(String.format("Can't find alignment processor for alignment anchor %s", alignment.getAnchor()));
            return null;
        }

        BlockAlignmentProcessor.Context context = new BlockAlignmentProcessor.Context(
            myDocument,
            alignment,
            currentBlock,
            myAlignmentMappings,
            myBackwardShiftedAlignedBlocks,
            myBlockIndentOptions.getIndentOptions(currentBlock)
        );
        LeafBlockWrapper offsetResponsibleBlock = alignment.getOffsetRespBlockBefore(currentBlock);
        myCyclesDetector.registerOffsetResponsibleBlock(offsetResponsibleBlock);
        BlockAlignmentProcessor.Result result = alignmentProcessor.applyAlignment(context);
        switch (result) {
            case TARGET_BLOCK_PROCESSED_NOT_ALIGNED:
                return null;
            case TARGET_BLOCK_ALIGNED:
                storeAlignmentMapping(currentBlock);
                return null;
            case BACKWARD_BLOCK_ALIGNED:
                if (offsetResponsibleBlock == null) {
                    return null;
                }
                Set<LeafBlockWrapper> blocksCausedRealignment = new HashSet<>();
                myBackwardShiftedAlignedBlocks.clear();
                myBackwardShiftedAlignedBlocks.put(offsetResponsibleBlock, blocksCausedRealignment);
                blocksCausedRealignment.add(currentBlock);
                storeAlignmentMapping(currentBlock, offsetResponsibleBlock);
                if (myCyclesDetector.isCycleDetected()) {
                    reportAlignmentProcessingError(context);
                    return null;
                }
                myCyclesDetector.registerBlockRollback(currentBlock);
                return offsetResponsibleBlock.getNextBlock();
            case RECURSION_DETECTED:
                myAlignmentsToSkip.add(alignment);
                return offsetResponsibleBlock; // Fall through to the 'register alignment to skip'.
            case UNABLE_TO_ALIGN_BACKWARD_BLOCK:
                myAlignmentsToSkip.add(alignment);
                return null;
            default:
                return null;
        }
    }

    public boolean shouldSkip(AlignmentImpl alignment) {
        return myAlignmentsToSkip.contains(alignment);
    }

    private void storeAlignmentMapping(AbstractBlockWrapper block1, AbstractBlockWrapper block2) {
        doStoreAlignmentMapping(block1, block2);
        doStoreAlignmentMapping(block2, block1);
    }

    private void doStoreAlignmentMapping(AbstractBlockWrapper key, AbstractBlockWrapper value) {
        Set<AbstractBlockWrapper> wrappers = myAlignmentMappings.get(key);
        if (wrappers == null) {
            myAlignmentMappings.put(key, wrappers = new HashSet<>());
        }
        wrappers.add(value);
    }

    private void storeAlignmentMapping(LeafBlockWrapper currentBlock) {
        AlignmentImpl alignment = null;
        AbstractBlockWrapper block = currentBlock;
        while (alignment == null && block != null) {
            alignment = block.getAlignment();
            block = block.getParent();
        }
        if (alignment != null) {
            block = alignment.getOffsetRespBlockBefore(currentBlock);
            if (block != null) {
                storeAlignmentMapping(currentBlock, block);
            }
        }
    }

    public void reset() {
        myBackwardShiftedAlignedBlocks.clear();
        myAlignmentMappings.clear();
    }
}
