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
package consulo.codeEditor.impl.softwrap;

import consulo.codeEditor.SoftWrap;
import consulo.codeEditor.internal.TextChangeImpl;

import jakarta.annotation.Nonnull;

/**
 * {@link SoftWrap} implementation that is built around {@link TextChangeImpl}.
 *
 * @author Denis Zhdanov
 * @since 2010-09-01
 */
public class SoftWrapImpl implements SoftWrap {

  private final TextChangeImpl myChange;
  private final int myIndentInColumns;
  private final int myIndentInPixels;

  public SoftWrapImpl(@Nonnull TextChangeImpl change, int indentInColumns, int indentInPixels) {
    myChange = change;
    myIndentInColumns = indentInColumns;
    myIndentInPixels = indentInPixels;
  }

  @Override
  public int getStart() {
    return myChange.getStart();
  }

  @Override
  public int getEnd() {
    return myChange.getEnd();
  }

  @Nonnull
  @Override
  public CharSequence getText() {
    return myChange.getText();
  }

  @Nonnull
  @Override
  public char[] getChars() {
    return myChange.getChars();
  }

  @Override
  public int getIndentInColumns() {
    return myIndentInColumns;
  }

  @Override
  public int getIndentInPixels() {
    return myIndentInPixels;
  }

  public TextChangeImpl getChange() {
    return myChange;
  }

  public void advance(int diff) {
    myChange.advance(diff);
  }

  @Override
  public int hashCode() {
    int result = myChange.hashCode();
    result = 31 * result + myIndentInColumns;
    return 31 * result + myIndentInPixels;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    
    SoftWrapImpl that = (SoftWrapImpl)o;
    return myIndentInColumns == that.myIndentInColumns && myIndentInPixels == that.myIndentInPixels && myChange.equals(that.myChange);
  }

  @Override
  public String toString() {
    return myChange.toString();
  }
}
