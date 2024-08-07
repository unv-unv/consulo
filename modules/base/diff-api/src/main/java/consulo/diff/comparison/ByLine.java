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
package consulo.diff.comparison;

import consulo.application.progress.ProgressIndicator;
import consulo.application.util.registry.Registry;
import consulo.diff.comparison.iterable.FairDiffIterable;
import consulo.diff.util.MergeRange;
import consulo.diff.util.Range;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.collection.primitive.ints.IntLists;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

import static consulo.diff.comparison.ComparisonPolicy.IGNORE_WHITESPACES;
import static consulo.diff.comparison.TrimUtil.trimEnd;
import static consulo.diff.comparison.TrimUtil.trimStart;
import static consulo.diff.comparison.iterable.DiffIterableUtil.*;
import static consulo.util.lang.StringUtil.isWhiteSpace;

public class ByLine {
  @Nonnull
  public static FairDiffIterable compare(@Nonnull List<? extends CharSequence> lines1,
                                         @Nonnull List<? extends CharSequence> lines2,
                                         @Nonnull ComparisonPolicy policy,
                                         @Nonnull ProgressIndicator indicator) {
    indicator.checkCanceled();
    return doCompare(getLines(lines1, policy), getLines(lines2, policy), policy, indicator);
  }

  @Nonnull
  public static List<MergeRange> compare(@Nonnull List<? extends CharSequence> lines1,
                                         @Nonnull List<? extends CharSequence> lines2,
                                         @Nonnull List<? extends CharSequence> lines3,
                                         @Nonnull ComparisonPolicy policy,
                                         @Nonnull ProgressIndicator indicator) {
    indicator.checkCanceled();
    return doCompare(getLines(lines1, policy), getLines(lines2, policy), getLines(lines3, policy), policy, indicator);
  }

  //
  // Impl
  //

  @Nonnull
  static FairDiffIterable doCompare(@Nonnull List<Line> lines1,
                                    @Nonnull List<Line> lines2,
                                    @Nonnull ComparisonPolicy policy,
                                    @Nonnull ProgressIndicator indicator) {
    indicator.checkCanceled();

    if (policy == IGNORE_WHITESPACES) {
      FairDiffIterable changes = compareSmart(lines1, lines2, indicator);
      changes = optimizeLineChunks(lines1, lines2, changes, indicator);
      return expandRanges(lines1, lines2, changes);
    }
    else {
      List<Line> iwLines1 = convertMode(lines1, IGNORE_WHITESPACES);
      List<Line> iwLines2 = convertMode(lines2, IGNORE_WHITESPACES);

      FairDiffIterable iwChanges = compareSmart(iwLines1, iwLines2, indicator);
      iwChanges = optimizeLineChunks(lines1, lines2, iwChanges, indicator);
      return correctChangesSecondStep(lines1, lines2, iwChanges);
    }
  }

  @Nonnull
  static List<MergeRange> doCompare(@Nonnull List<Line> lines1,
                                    @Nonnull List<Line> lines2,
                                    @Nonnull List<Line> lines3,
                                    @Nonnull ComparisonPolicy policy,
                                    @Nonnull ProgressIndicator indicator) {
    indicator.checkCanceled();

    List<Line> iwLines1 = convertMode(lines1, IGNORE_WHITESPACES);
    List<Line> iwLines2 = convertMode(lines2, IGNORE_WHITESPACES);
    List<Line> iwLines3 = convertMode(lines3, IGNORE_WHITESPACES);

    FairDiffIterable iwChanges1 = compareSmart(iwLines2, iwLines1, indicator);
    iwChanges1 = optimizeLineChunks(lines2, lines1, iwChanges1, indicator);
    FairDiffIterable iterable1 = correctChangesSecondStep(lines2, lines1, iwChanges1);

    FairDiffIterable iwChanges2 = compareSmart(iwLines2, iwLines3, indicator);
    iwChanges2 = optimizeLineChunks(lines2, lines3, iwChanges2, indicator);
    FairDiffIterable iterable2 = correctChangesSecondStep(lines2, lines3, iwChanges2);

    return ComparisonMergeUtil.buildFair(iterable1, iterable2, indicator);
  }

  @Nonnull
  private static FairDiffIterable correctChangesSecondStep(@Nonnull final List<Line> lines1,
                                                           @Nonnull final List<Line> lines2,
                                                           @Nonnull final FairDiffIterable changes) {
    /*
     * We want to fix invalid matching here:
     *
     * .{        ..{
     * ..{   vs  ...{
     * ...{
     *
     * first step will return matching (0,2)-(0,2). And we should adjust it to (1,3)-(0,2)
     *
     *
     * From the other hand, we don't want to reduce number of IW-matched lines.
     *
     * .{         ...{
     * ..{    vs  ..{
     * ...{       .{
     *
     * first step will return (0,3)-(0,3) and 'correcting' it to (0,1)-(2,3) is wrong (and it will break ByWord highlighting).
     *
     *
     * Idea:
     * 1. lines are matched at first step and equal -> match them
     * 2. lines are not matched at first step -> do not match them
     * 3. lines are matched at first step and not equal ->
     *   a. find all IW-equal lines in the same unmatched block
     *   b. find a maximum matching between them, maximising amount of equal pairs in it
     *   c. match equal lines using result of the previous step
     */

    final ExpandChangeBuilder builder = new ExpandChangeBuilder(lines1, lines2);
    new Object() {
      private CharSequence sample = null;
      private int last1 = 0;
      private int last2 = 0;

      public void run() {
        for (Range range : changes.iterateUnchanged()) {
          int count = range.end1 - range.start1;
          for (int i = 0; i < count; i++) {
            int index1 = range.start1 + i;
            int index2 = range.start2 + i;
            Line line1 = lines1.get(index1);
            Line line2 = lines2.get(index2);

            if (!StringUtil.equalsIgnoreWhitespaces(sample, line1.getContent())) {
              if (line1.equals(line2)) {
                flush(index1, index2);
                builder.markEqual(index1, index2);
              }
              else {
                flush(index1, index2);
                sample = line1.getContent();
              }
            }
          }
        }
        flush(changes.getLength1(), changes.getLength2());
      }

      private void flush(int line1, int line2) {
        if (sample == null) return;

        int start1 = Math.max(last1, builder.getIndex1());
        int start2 = Math.max(last2, builder.getIndex2());

        IntList subLines1 = IntLists.newArrayList();
        IntList subLines2 = IntLists.newArrayList();
        for (int i = start1; i < line1; i++) {
          if (StringUtil.equalsIgnoreWhitespaces(sample, lines1.get(i).getContent())) {
            subLines1.add(i);
            last1 = i + 1;
          }
        }
        for (int i = start2; i < line2; i++) {
          if (StringUtil.equalsIgnoreWhitespaces(sample, lines2.get(i).getContent())) {
            subLines2.add(i);
            last2 = i + 1;
          }
        }

        assert subLines1.size() > 0 && subLines2.size() > 0;
        alignExactMatching(subLines1, subLines2);

        sample = null;
      }

      private void alignExactMatching(IntList subLines1, IntList subLines2) {
        int n = Math.max(subLines1.size(), subLines2.size());
        boolean skipAligning = n > 10 || // we use brute-force algorithm (C_n_k). This will limit search space by ~250 cases.
                               subLines1.size() == subLines2.size(); // nothing to do

        if (skipAligning) {
          int count = Math.min(subLines1.size(), subLines2.size());
          for (int i = 0; i < count; i++) {
            int index1 = subLines1.get(i);
            int index2 = subLines2.get(i);
            if (lines1.get(index1).equals(lines2.get(index2))) {
              builder.markEqual(index1, index2);
            }
          }
          return;
        }

        if (subLines1.size() < subLines2.size()) {
          int[] matching = getBestMatchingAlignment(subLines1, subLines2, lines1, lines2);
          for (int i = 0; i < subLines1.size(); i++) {
            int index1 = subLines1.get(i);
            int index2 = subLines2.get(matching[i]);
            if (lines1.get(index1).equals(lines2.get(index2))) {
              builder.markEqual(index1, index2);
            }
          }
        }
        else {
          int[] matching = getBestMatchingAlignment(subLines2, subLines1, lines2, lines1);
          for (int i = 0; i < subLines2.size(); i++) {
            int index1 = subLines1.get(matching[i]);
            int index2 = subLines2.get(i);
            if (lines1.get(index1).equals(lines2.get(index2))) {
              builder.markEqual(index1, index2);
            }
          }
        }
      }
    }.run();

    return fair(builder.finish());
  }

  @Nonnull
  private static int[] getBestMatchingAlignment(@Nonnull final IntList subLines1,
                                                @Nonnull final IntList subLines2,
                                                @Nonnull final List<Line> lines1,
                                                @Nonnull final List<Line> lines2) {
    assert subLines1.size() < subLines2.size();
    final int size = subLines1.size();

    final int[] comb = new int[size];
    final int[] best = new int[size];
    for (int i = 0; i < size; i++) {
      best[i] = i;
    }

    // find a combination with maximum weight (maximum number of equal lines)
    new Object() {
      int bestWeight = 0;

      public void run() {
        combinations(0, subLines2.size() - 1, 0);
      }

      private void combinations(int start, int n, int k) {
        if (k == size) {
          processCombination();
          return;
        }

        for (int i = start; i <= n; i++) {
          comb[k] = i;
          combinations(i + 1, n, k + 1);
        }
      }

      private void processCombination() {
        int weight = 0;
        for (int i = 0; i < size; i++) {
          int index1 = subLines1.get(i);
          int index2 = subLines2.get(comb[i]);
          if (lines1.get(index1).equals(lines2.get(index2))) weight++;
        }

        if (weight > bestWeight) {
          bestWeight = weight;
          System.arraycopy(comb, 0, best, 0, comb.length);
        }
      }
    }.run();

    return best;
  }

  @Nonnull
  private static FairDiffIterable optimizeLineChunks(@Nonnull List<Line> lines1,
                                                     @Nonnull List<Line> lines2,
                                                     @Nonnull FairDiffIterable iterable,
                                                     @Nonnull ProgressIndicator indicator) {
    return new ChunkOptimizer.LineChunkOptimizer(lines1, lines2, iterable, indicator).build();
  }

  /*
   * Compare lines in two steps:
   *  - compare ignoring "unimportant" lines
   *  - correct changes (compare all lines gaps between matched chunks)
   */
  @Nonnull
  private static FairDiffIterable compareSmart(@Nonnull List<Line> lines1,
                                               @Nonnull List<Line> lines2,
                                               @Nonnull ProgressIndicator indicator) {
    int threshold = Registry.intValue("diff.unimportant.line.char.count");
    if (threshold == 0) return diff(lines1, lines2, indicator);

    Pair<List<Line>, IntList> bigLines1 = getBigLines(lines1, threshold);
    Pair<List<Line>, IntList> bigLines2 = getBigLines(lines2, threshold);

    FairDiffIterable changes = diff(bigLines1.first, bigLines2.first, indicator);
    return new ChangeCorrector.SmartLineChangeCorrector(bigLines1.second, bigLines2.second, lines1, lines2, changes, indicator).build();
  }

  @Nonnull
  private static Pair<List<Line>, IntList> getBigLines(@Nonnull List<Line> lines, int threshold) {
    List<Line> bigLines = new ArrayList<>(lines.size());
    IntList indexes = IntLists.newArrayList(lines.size());

    for (int i = 0; i < lines.size(); i++) {
      Line line = lines.get(i);
      if (line.getNonSpaceChars() > threshold) {
        bigLines.add(line);
        indexes.add(i);
      }
    }
    return Pair.create(bigLines, indexes);
  }

  @Nonnull
  private static FairDiffIterable expandRanges(@Nonnull List<Line> lines1,
                                               @Nonnull List<Line> lines2,
                                               @Nonnull FairDiffIterable iterable) {
    List<Range> changes = new ArrayList<>();

    for (Range ch : iterable.iterateChanges()) {
      Range expanded = TrimUtil.expand(lines1, lines2, ch.start1, ch.start2, ch.end1, ch.end2);
      if (!expanded.isEmpty()) changes.add(expanded);
    }

    return fair(create(changes, lines1.size(), lines2.size()));
  }

  //
  // Lines
  //

  @Nonnull
  private static List<Line> getLines(@Nonnull List<? extends CharSequence> text, @Nonnull ComparisonPolicy policy) {
    return ContainerUtil.map(text, (line) -> new Line(line, policy));
  }

  @Nonnull
  private static List<Line> convertMode(@Nonnull List<Line> original, @Nonnull ComparisonPolicy policy) {
    List<Line> result = new ArrayList<>(original.size());
    for (Line line : original) {
      result.add(new Line(line.getContent(), policy));
    }
    return result;
  }

  static class Line {
    @Nonnull
    private final CharSequence myText;
    @Nonnull
    private final ComparisonPolicy myPolicy;
    private final int myHash;
    private final int myNonSpaceChars;

    public Line(@Nonnull CharSequence text, @Nonnull ComparisonPolicy policy) {
      myText = text;
      myPolicy = policy;
      myHash = hashCode(text, policy);
      myNonSpaceChars = countNonSpaceChars(text);
    }

    @Nonnull
    public CharSequence getContent() {
      return myText;
    }

    public int getNonSpaceChars() {
      return myNonSpaceChars;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Line line = (Line)o;
      assert myPolicy == line.myPolicy;

      if (hashCode() != line.hashCode()) return false;

      return equals(getContent(), line.getContent(), myPolicy);
    }

    @Override
    public int hashCode() {
      return myHash;
    }

    private static int countNonSpaceChars(@Nonnull CharSequence text) {
      int nonSpace = 0;

      int len = text.length();
      int offset = 0;

      while (offset < len) {
        char c = text.charAt(offset);
        if (!isWhiteSpace(c)) nonSpace++;
        offset++;
      }

      return nonSpace;
    }

    private static boolean equals(@Nonnull CharSequence text1, @Nonnull CharSequence text2, @Nonnull ComparisonPolicy policy) {
      switch (policy) {
        case DEFAULT:
          return StringUtil.equals(text1, text2);
        case TRIM_WHITESPACES:
          return StringUtil.equalsTrimWhitespaces(text1, text2);
        case IGNORE_WHITESPACES:
          return StringUtil.equalsIgnoreWhitespaces(text1, text2);
        default:
          throw new IllegalArgumentException(policy.toString());
      }
    }

    private static int hashCode(@Nonnull CharSequence text, @Nonnull ComparisonPolicy policy) {
      switch (policy) {
        case DEFAULT:
          return StringUtil.stringHashCode(text);
        case TRIM_WHITESPACES:
          int offset1 = trimStart(text, 0, text.length());
          int offset2 = trimEnd(text, offset1, text.length());
          return StringUtil.stringHashCode(text, offset1, offset2);
        case IGNORE_WHITESPACES:
          return StringUtil.stringHashCodeIgnoreWhitespaces(text);
        default:
          throw new IllegalArgumentException(policy.name());
      }
    }
  }
}
