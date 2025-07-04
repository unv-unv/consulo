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
package consulo.codeEditor.impl;

import consulo.codeEditor.IndentGuideDescriptor;
import consulo.codeEditor.IndentsModel;
import consulo.codeEditor.LogicalPosition;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author max
 */
public class IndentsModelImpl implements IndentsModel {

  private final Map<IntPair, IndentGuideDescriptor> myIndentsByLines = new HashMap<>();
  private List<IndentGuideDescriptor> myIndents = new ArrayList<>();
  @Nonnull
  private final CodeEditorBase myEditor;

  public IndentsModelImpl(@Nonnull CodeEditorBase editor) {
    myEditor = editor;
  }

  @Nonnull
  public List<IndentGuideDescriptor> getIndents() {
    return myIndents;
  }

  @Override
  public IndentGuideDescriptor getCaretIndentGuide() {
    final LogicalPosition pos = myEditor.getCaretModel().getLogicalPosition();
    final int column = pos.column;
    final int line = pos.line;

    if (column > 0) {
      for (IndentGuideDescriptor indent : myIndents) {
        if (column == indent.indentLevel && line >= indent.startLine && line < indent.endLine) {
          return indent;
        }
      }
    }
    return null;
  }

  @Override
  public IndentGuideDescriptor getDescriptor(int startLine, int endLine) {
    return myIndentsByLines.get(new IntPair(startLine, endLine));
  }

  @Override
  public void assumeIndents(@Nonnull List<IndentGuideDescriptor> descriptors) {
    myIndents = descriptors;
    myIndentsByLines.clear();
    for (IndentGuideDescriptor descriptor : myIndents) {
      myIndentsByLines.put(new IntPair(descriptor.startLine, descriptor.endLine), descriptor);
    }
  }

  private static class IntPair {

    private final int start;
    private final int end;

    IntPair(int start, int end) {
      this.start = start;
      this.end = end;
    }

    @Override
    public int hashCode() {
      int result = start;
      return 31 * result + end;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      IntPair that = (IntPair)o;
      return start == that.start && end == that.end;
    }

    @Override
    public String toString() {
      return "start=" + start + ", end=" + end;
    }
  }
}
