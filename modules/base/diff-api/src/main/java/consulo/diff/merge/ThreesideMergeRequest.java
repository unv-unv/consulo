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
package consulo.diff.merge;

import consulo.diff.content.DiffContent;
import jakarta.annotation.Nonnull;

import java.util.List;

public abstract class ThreesideMergeRequest extends MergeRequest {
  /**
   * 3 contents: left - middle - right (local - base - server)
   */
  @Nonnull
  public abstract List<? extends DiffContent> getContents();

  @Nonnull
  public abstract DiffContent getOutputContent();

  /**
   * @return contents names. Should have same length as {@link #getContents()}
   * Titles could be null.
   */
  @Nonnull
  public abstract List<String> getContentTitles();
}
