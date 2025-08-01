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
package consulo.diff.old;

import consulo.document.util.TextRange;
import consulo.application.util.diff.Diff;
import consulo.annotation.DeprecationInfo;
import consulo.logging.Logger;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;

/**
 * Builds a sequence of DiffFragment objects thus diffing 2 files
 * Parses the output of CVS 'diff' command assumed to be in the RCS Normal Format
 * Format of the output chunks for the command: 'diff file1 file2':
 * change-command
 * < from-file-line
 * < from-file-line...
 * ---
 * > to-file-line
 * > to-file-line...
 *
 * Where:
 * Change-Command -> Line a Range
 * Change-Command -> Range c Range
 * Change-Command -> Range d Line
 * Range -> Line , Line
 * Range -> Line
 * Line -> number-of-line
 *
 * The commands are:
 * a: append a range of lines from the file2 after line Line of the file1
 * c: change the range of lines in the file1 to the range from file2
 * d: Delete the lines in range Range from the file1; line Line is where they would have appeared in the file2 had they not been deleted
 *
 * Class DiffFragmentBuilder
 * @author Jeka
 */
@Deprecated(forRemoval = true)
@DeprecationInfo("Old diff impl, must be removed")
public class DiffFragmentBuilder {

  private static final Logger LOG = Logger.getInstance(DiffFragmentBuilder.class);

  @Nonnull
  private final DiffString[] mySource1;
  @Nonnull
  private final DiffString[] mySource2;
  private int myLastLine1 = 1;
  private int myLastLine2 = 1;
  @Nonnull
  private final List<DiffFragmentOld> myData = new LinkedList<DiffFragmentOld>();

  public DiffFragmentBuilder(@Nonnull DiffString[] source1, @Nonnull DiffString[] source2) {
    mySource1 = source1;
    mySource2 = source2;
    init();
  }

  @Nonnull
  private List<DiffFragmentOld> getFragments() {
    return myData;
  }

  private void finish() {
    DiffString text1 = null;
    DiffString text2 = null;
    if (myLastLine1 <= mySource1.length) {
      text1 = concatenate(mySource1, myLastLine1, mySource1.length);
    }
    if (myLastLine2 <= mySource2.length) {
      text2 = concatenate((mySource2), myLastLine2, mySource2.length);
    }
    if (text1 != null || text2 != null) {
      myData.add(DiffFragmentOld.unchanged(text1, text2));
    }
  }

  private void init() {
    myData.clear();
    myLastLine1 = myLastLine2 = 1;
  }

  private void append(int line, @Nonnull TextRange range) {
    LOG.debug("DiffFragmentBuilder.append(" + line + "," + range + "), modified:");
    DiffString text1 = null;
    DiffString text2 = null;
    int start = range.getStartOffset();
    int end = range.getEndOffset();
    if (myLastLine1 <= line) {
      text1 = concatenate(mySource1, myLastLine1, line);
    }
    if (myLastLine2 < start) {
      text2 = concatenate(mySource2, myLastLine2, start - 1);
    }
    if (text1 != null || text2 != null) {
      myData.add(DiffFragmentOld.unchanged(text1, text2));
    }
    myData.add(new DiffFragmentOld(null, concatenate(mySource2, start, end)));
    myLastLine1 = line + 1;
    myLastLine2 = end + 1;
  }

  private void change(@Nonnull TextRange range1, @Nonnull TextRange range2) {
    LOG.debug("DiffFragmentBuilder.change(" + range1 + "," + range2 + ")");

    DiffString text1 = null, text2 = null;
    int start1 = range1.getStartOffset();
    int end1 = range1.getEndOffset();
    int start2 = range2.getStartOffset();
    int end2 = range2.getEndOffset();
    if (myLastLine1 < start1) {
      text1 = concatenate(mySource1, myLastLine1, start1 - 1);
    }
    if (myLastLine2 < start2) {
      text2 = concatenate(mySource2, myLastLine2, start2 - 1);
    }
    if (text1 != null || text2 != null) {
      myData.add(DiffFragmentOld.unchanged(text1, text2));
    }
    myData.add(new DiffFragmentOld(concatenate(mySource1, start1, end1),
                                concatenate(mySource2, start2, end2)));
    myLastLine1 = end1 + 1;
    myLastLine2 = end2 + 1;
  }

  private void delete(@Nonnull TextRange range, int line) {
    LOG.debug("DiffFragmentBuilder.delete(" + range + "," + line + ")");

    DiffString text1 = null;
    DiffString text2 = null;
    int start = range.getStartOffset();
    int end = range.getEndOffset();
    if (myLastLine1 < start) {
      text1 = concatenate(mySource1, myLastLine1, start - 1);
    }
    if (myLastLine2 <= line) {
      text2 = concatenate(mySource2, myLastLine2, line);
    }
    if (text1 != null || text2 != null) {
      myData.add(DiffFragmentOld.unchanged(text1, text2));
    }
    myData.add(new DiffFragmentOld(concatenate(mySource1, start, end), null));
    myLastLine1 = end + 1;
    myLastLine2 = line + 1;
  }

  @Nonnull
  private static DiffString concatenate(@Nonnull DiffString[] strings, int start, int end) {
    return DiffString.concatenate(strings, start - 1, end - start + 1);
  }

  @Nonnull
  public DiffFragmentOld[] buildFragments(@Nullable Diff.Change change) {
    while (change != null) {
      if (change.inserted > 0 && change.deleted > 0) {
        change(
                new TextRange(change.line0 + 1, change.line0 + change.deleted),
                new TextRange(change.line1 + 1, change.line1 + change.inserted)
        );
      }
      else if (change.inserted > 0) {
        append(change.line0, new TextRange(change.line1 + 1, change.line1 + change.inserted));
      }
      else if (change.deleted > 0) {
        delete(new TextRange(change.line0 + 1, change.line0 + change.deleted), change.line1);
      }
      change = change.link;
    }
    finish();

    final List<DiffFragmentOld> fragments = getFragments();
    return fragments.toArray(new DiffFragmentOld[myData.size()]);
  }
}
