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
package consulo.ide.impl.idea.openapi.vcs.changes;

import consulo.document.util.TextRange;
import consulo.language.psi.search.TodoPattern;

/**
 * @author Irina.Chernushina
 * @since 2012-11-29
 */
public class TodoItemData {
  private final int myStartOffset;
  private final int myEndOffset;
  private final TodoPattern myPattern;

  public TodoItemData(int startOffset, int endOffset, TodoPattern pattern) {
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myPattern = pattern;
  }

  public int getStartOffset() {
    return myStartOffset;
  }

  public int getEndOffset() {
    return myEndOffset;
  }

  public TodoPattern getPattern() {
    return myPattern;
  }

  public TextRange getTextRange() {
    return new TextRange(myStartOffset, myEndOffset);
  }
}
