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
package consulo.language.codeStyle.impl.internal.arrangement;

import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 11/21/12 12:54 PM
 */
public interface ArrangementCallback {

  /**
   * Callback to be notified when arrangement has been performed.
   * 
   * @param moveInfos  information about the changes performed during arrangement
   */
  void afterArrangement(@Nonnull List<ArrangementMoveInfo> moveInfos);
}
