/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package consulo.document.util;

import java.util.Comparator;

/**
 * @author cdr
 */
public interface Segment {
  Segment[] EMPTY_ARRAY = new Segment[0];
  int getStartOffset();
  int getEndOffset();

  Comparator<Segment> BY_START_OFFSET_THEN_END_OFFSET = (r1, r2) -> {
    int result = r1.getStartOffset() - r2.getStartOffset();
    if (result == 0) result = r1.getEndOffset() - r2.getEndOffset();
    return result;
  };
}
