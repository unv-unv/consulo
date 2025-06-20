/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package consulo.language.codeStyle.internal;

import consulo.application.progress.SequentialTask;
import consulo.language.codeStyle.Block;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;

/**
 * Defines common interface for receiving events about formatting progress.
 * 
 * @author Denis Zhdanov
 * @since 2/10/11 3:38 PM
 */
public interface FormattingProgressCallback {

  enum EventType { SUCCESS, CANCEL }

  /**
   * Notifies current indicator that particular {@link Block code block} is wrapped.
   * 
   * @param wrapped   wrapped code block
   * @see FormattingStateId#WRAPPING_BLOCKS
   */
  void afterWrappingBlock(@Nonnull LeafBlockWrapper wrapped);

  /**
   * Notifies current indicator that given {@link LeafBlockWrapper wrapped code block} is processed, i.e. its
   * {@link AbstractBlockWrapper#getWhiteSpace() white space} is adjusted as necessary.
   * 
   * @param block     processed wrapped block which white space if adjusted
   * @see FormattingStateId#PROCESSING_BLOCKS
   */
  void afterProcessingBlock(@Nonnull LeafBlockWrapper block);

  /**
   * Notifies current indicator that changes from the given {@link LeafBlockWrapper wrapped code blocks} are about to be flushed
   * to the underlying document.
   * 
   * @param modifiedBlocks      blocks with modified {@link AbstractBlockWrapper#getWhiteSpace() white spaces} which are about
   *                            to be flushed to the underlying document
   * @see FormattingStateId#APPLYING_CHANGES
   */
  void beforeApplyingFormatChanges(@Nonnull Collection<LeafBlockWrapper> modifiedBlocks);

  /**
   * Notifies current indicator that change from the given {@link LeafBlockWrapper wrapped code block} is successfully flushed
   * to the underlying document.
   * 
   * @param block     {@link LeafBlockWrapper wrapped code block} which change is successfully flushed to the underlying document
   * @see FormattingStateId#APPLYING_CHANGES
   */
  void afterApplyingChange(@Nonnull LeafBlockWrapper block);

  /**
   * Allows to define an actual formatting task to process.
   * <p/>
   * I.e. the general idea is that given indicator is provided with the task which will be executed from EDT
   * {@link SequentialTask#iteration() part by part} until the task {@link SequentialTask#isDone() is finished}.
   * That <code>'part-by-part'</code> processing is assumed to update current indicator (call <code>'beforeXxx()'</code>
   * and <code>'afterXxx()'</code> methods).
   * 
   * @param task    formatter task to process
   */
  void setTask(@Nullable SequentialTask task);

  /**
   * Allows to register callback for the target event type.
   * 
   * @param eventType     target event type
   * @param callback      callback to register for the given event type
   * @return              <code>true</code> if given callback is successfully registered for the given event type;
   *                      <code>false</code> otherwise
   */
  boolean addCallback(@Nonnull EventType eventType, @Nonnull Runnable callback);
  
  /**
   * <a hrep="http://en.wikipedia.org/wiki/Null_Object_pattern">Null object</a> for {@link FormattingProgressCallback}. 
   */
  FormattingProgressCallback EMPTY = new FormattingProgressCallback() {
    @Override
    public void afterWrappingBlock(@Nonnull LeafBlockWrapper wrapped) {
    }

    @Override
    public void afterProcessingBlock(@Nonnull LeafBlockWrapper block) {
    }

    @Override
    public void beforeApplyingFormatChanges(@Nonnull Collection<LeafBlockWrapper> modifiedBlocks) {
    }

    @Override
    public void afterApplyingChange(@Nonnull LeafBlockWrapper block) {
    }

    @Override
    public void setTask(@Nullable SequentialTask task) {
    }

    @Override
    public boolean addCallback(@Nonnull EventType eventType, @Nonnull Runnable callback) {
      return false;
    }
  };
}
