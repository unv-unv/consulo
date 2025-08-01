/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.application.util.diff.Diff;
import consulo.logging.Logger;
import consulo.util.collection.primitive.ints.IntSet;
import consulo.util.collection.primitive.ints.IntSets;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

@Deprecated
public class Util {
  private static final Logger LOG = Logger.getInstance(Util.class);
  private static final String DELIMITERS = " \n\r\t(){}[],./?`~!@#$%^&*-=+|\\;:'\"<>";
  public static final IntSet DELIMITERS_SET = IntSets.newHashSet();

  static {
    char[] delimiters = DELIMITERS.toCharArray();
    for (int i = 0; i < delimiters.length; i++) {
      char delimiter = delimiters[i];
      DELIMITERS_SET.add(delimiter);
    }
  }

  @Nonnull
  static String[] splitByWord(@Nonnull String string) {
    BufferedStringList stringList = new BufferedStringList();
    StringTokenizer tokenizer = new StringTokenizer(string, DELIMITERS, true);
    while (tokenizer.hasMoreTokens()) {
      String token = tokenizer.nextToken();
      if (token.length() == 1 && DELIMITERS_SET.contains(token.charAt(0))) {
        char delimiter = token.charAt(0);
        if (delimiter == '\n') {
          stringList.appendToLast(token);
          stringList.flushLast();
          continue;
        }
        if (Character.isWhitespace(delimiter)) {
          stringList.appendToLast(token);
          continue;
        }
      }
      stringList.add(token);
    }
    return stringList.toArray();
  }

  static boolean isSpaceOnly(@Nonnull DiffFragmentOld fragment) {
    return isSpaceOnly(fragment.getText1()) && isSpaceOnly(fragment.getText2());
  }

  private static boolean isSpaceOnly(@Nullable DiffString string) {
    if (string == null) return true;
    return string.isEmptyOrSpaces();
  }

  @Nonnull
  static DiffFragmentOld[] splitByLines(@Nonnull DiffFragmentOld fragment) {
    DiffString[] lines1 = splitByLines(fragment.getText1());
    DiffString[] lines2 = splitByLines(fragment.getText2());
    if (lines1 != null && lines2 != null && lines1.length != lines2.length) {
      LOG.error("1:<" + fragment.getText1() + "> 2:<" + fragment.getText2() + ">");
    }
    int length = lines1 == null ? lines2.length : lines1.length;
    DiffFragmentOld[] lines = new DiffFragmentOld[length];
    for (int i = 0; i < lines.length; i++) {
      lines[i] = new DiffFragmentOld(lines1 == null? null : lines1[i], lines2 == null ? null : lines2[i]);
    }
    return lines;
  }

  @Nullable
  private static DiffString[] splitByLines(@Nullable DiffString string) {
    if (string == null) return null;
    if (string.indexOf('\n') == -1) return new DiffString[]{string};

    return string.tokenize();
  }

  @Nonnull
  public static DiffFragmentOld[][] splitByUnchangedLines(@Nonnull DiffFragmentOld[] fragments) {
    List2D result = new List2D();
    for (int i = 0; i < fragments.length; i++) {
      DiffFragmentOld fragment = fragments[i];
      if (!fragment.isEqual()) {
        result.add(fragment);
        continue;
      }
      DiffString text1 = fragment.getText1();
      DiffString text2 = fragment.getText2();
      assert text1 != null;
      assert text2 != null;
      if (StringUtil.endsWithChar(text1, '\n') && StringUtil.endsWithChar(text2, '\n')) {
        result.add(fragment);
        result.newRow();
        continue;
      }
      while (true) {
        int newLine1 = text1.indexOf('\n');
        int newLine2 = text2.indexOf('\n');
        if (newLine1 == -1 || newLine2 == -1) {
          result.add(DiffFragmentOld.unchanged(text1, text2));
          break;
        }
        result.add(DiffFragmentOld.unchanged(text1.substring(0, newLine1 + 1), text2.substring(0, newLine2 + 1)));
        result.newRow();
        text1 = text1.substring(newLine1 + 1);
        text2 = text2.substring(newLine2 + 1);
        int length1 = text1.length();
        int length2 = text2.length();
        if (length1 == 0 || length2 == 0) {
          if (length1 != 0 || length2 != 0)
            result.add(DiffFragmentOld.unchanged(text1, text2));
          break;
        }
      }
    }
    return result.toArray();
  }

  public static Diff.Change concatEquals(Diff.Change change, @Nonnull Object[] left, @Nonnull Object[] right) {
    MyChange startChange = new MyChange(0, 0, 0, 0);
    MyChange lastChange = startChange;
    while (change != null) {
      if (change.inserted > 0 && change.deleted > 0) {
        lastChange = lastChange.copyNext(change);
      } else if (change.inserted > 0) {
        int shift = calcShift(right, lastChange.getEnd2(), change.line1, change.inserted);
        lastChange = lastChange.copyNext(change, shift);
      } else if (change.deleted > 0) {
        int shift = calcShift(left, lastChange.getEnd1(), change.line0, change.deleted);
        lastChange = lastChange.copyNext(change, shift);
      } else {
        LOG.assertTrue(false);
      }
      change = change.link;
    }
    return concatSingleSide(startChange.link);
  }

  private static Diff.Change concatSingleSide(Diff.Change change) {
    MyChange startChange = new MyChange(0, 0, 0, 0);
    MyChange lastChange = startChange;
    MyChange prevChange = null;
    while (change != null) {
      if (prevChange == null || (change.inserted > 0 && change.deleted > 0)) {
        prevChange = lastChange;
        lastChange = lastChange.copyNext(change);
      } else {
        MyChange newChange = null;
        if (change.deleted == 0 && lastChange.deleted == 0 && change.line1 == lastChange.getEnd2()) {
          newChange = new MyChange(lastChange.line0, lastChange.line1, 0, lastChange.inserted + change.inserted);
        } else if (change.inserted == 0 && lastChange.inserted == 0 && change.line0 == lastChange.getEnd1()) {
          newChange = new MyChange(lastChange.line0, lastChange.line1, lastChange.deleted + change.deleted, 0);
        }
        if (newChange != null) {
          prevChange.setNext(newChange);
          lastChange = newChange;
        } else {
          prevChange = lastChange;
          lastChange = lastChange.copyNext(change);
        }
      }
      change = change.link;
    }
    return startChange.link;
  }

  static int calcShift(@Nonnull Object[] list, int limit, int start, int length) {
    int shift = start - limit;
    for (int i = 0; i < shift; i++) {
      if (!list[limit + i].equals(list[start + length - shift + i])) return 0;
    }
    return -shift;
  }

  @Nonnull
  public static DiffFragmentOld unite(@Nonnull DiffFragmentOld fragment1, @Nonnull DiffFragmentOld fragment2) {
    LOG.assertTrue(isSameType(fragment1, fragment2));
    if (!fragment1.isOneSide()) {
      DiffString unitedText1 = DiffString.concatenateNullable(fragment1.getText1(), fragment2.getText1());
      DiffString unitedText2 = DiffString.concatenateNullable(fragment1.getText2(), fragment2.getText2());
      LOG.assertTrue(fragment1.isEqual() == fragment2.isEqual());
      return fragment1.isEqual() ? DiffFragmentOld.unchanged(unitedText1, unitedText2) :
             new DiffFragmentOld(unitedText1, unitedText2);
    }
    FragmentSide side = FragmentSide.chooseSide(fragment1);
    return side
            .createFragment(DiffString.concatenateNullable(side.getText(fragment1), side.getText(fragment2)), null, fragment1.isModified());
  }

  public static boolean isSameType(@Nonnull DiffFragmentOld fragment1, @Nonnull DiffFragmentOld fragment2) {
    if (fragment1.isEqual()) return fragment2.isEqual();
    if (fragment1.isChange()) return fragment2.isChange();
    if (fragment1.getText1() == null) return fragment2.getText1() == null;
    if (fragment1.getText2() == null) return fragment2.getText2() == null;
    LOG.assertTrue(false);
    return false;
  }

  @Nonnull
  public static DiffString getText(@Nonnull DiffFragmentOld[] fragments, @Nonnull FragmentSide side) {
    DiffString[] data = new DiffString[fragments.length];
    for (int i = 0; i < fragments.length; i++) {
      DiffFragmentOld fragment = fragments[i];
      data[i] = side.getText(fragment);
    }
    return DiffString.concatenate(data);
  }

  @Nonnull
  public static DiffFragmentOld concatenate(@Nonnull DiffFragmentOld[] line) {
    return concatenate(line, 0, line.length);
  }

  @Nonnull
  public static DiffFragmentOld concatenate(@Nonnull DiffFragmentOld[] line, int from, int to) {
    DiffString[] data1 = new DiffString[to - from];
    DiffString[] data2 = new DiffString[to - from];

    boolean isEqual = true;
    for (int i = 0; i < to - from; i++) {
      DiffFragmentOld fragment = line[from + i];
      isEqual &= fragment.isEqual();
      data1[i] = fragment.getText1();
      data2[i] = fragment.getText2();
    }

    DiffString text1 = notEmptyContent(DiffString.concatenate(data1));
    DiffString text2 = notEmptyContent(DiffString.concatenate(data2));
    return isEqual ? DiffFragmentOld.unchanged(text1, text2) : new DiffFragmentOld(text1, text2);
  }

  @Nullable
  private static DiffString notEmptyContent(@Nonnull DiffString string) {
    return string.length() > 0 ? string : null;
  }

  @Nonnull
  public static DiffFragmentOld[][] uniteFormattingOnly(@Nonnull DiffFragmentOld[][] lines) {
    List2D result = new List2D();
    for (int i = 0; i < lines.length; i++) {
      DiffFragmentOld[] line = lines[i];
      if (!areEqual(line) && areEqualOrFormatting(line)) result.addAll(line);
      else {
        result.newRow();
        result.addAll(line);
        result.newRow();
      }
    }
    return result.toArray();
  }

  private static boolean areEqualOrFormatting(@Nonnull DiffFragmentOld[] fragments) {
    for (int i = 0; i < fragments.length; i++) {
      DiffFragmentOld fragment = fragments[i];
      if (fragment.isEqual()) continue;
      for (int side = 0;  side < 2; side++) {
        DiffString text = FragmentSide.fromIndex(side).getText(fragment);
        if (text == null || text.isEmptyOrSpaces()) continue;
        return false;
      }
    }
    return true;
  }

  private static boolean areEqual(@Nonnull DiffFragmentOld[] fragments) {
    for (int i = 0; i < fragments.length; i++) {
      DiffFragmentOld fragment = fragments[i];
      if (!fragment.isEqual()) return false;
    }
    return true;
  }

  @Nonnull
  public static DiffFragmentOld[] cutFirst(@Nonnull DiffFragmentOld[] fragments) {
    fragments = transformHeadInsert(fragments, FragmentSide.SIDE1);
    fragments = transformHeadInsert(fragments, FragmentSide.SIDE2);

    int nullCount = 0;
    for (int sideIndex = 0; sideIndex < 2; sideIndex++) {
      FragmentSide side = FragmentSide.fromIndex(sideIndex);
      for (int i = 0; i < fragments.length; i++) {
        DiffFragmentOld fragment = fragments[i];
        if (fragment == null) continue;
        DiffString text = side.getText(fragment);
        if (text == null || text.isEmpty()) continue;
        text = text.length() > 1 ? text.substring(1) : null;
        DiffString otherText = side.getOtherText(fragment);
        if (otherText == null && text == null) {
          fragments[i] = null;
          nullCount++;
        } else fragments[i] = side.createFragment(text, otherText, fragment.isModified());
        break;
      }
    }
    if (nullCount == 0) return fragments;
    DiffFragmentOld[] result = new DiffFragmentOld[fragments.length - nullCount];
    int dstIndex = 0;
    for (int i = 0; i < fragments.length; i++) {
      DiffFragmentOld fragment = fragments[i];
      if (fragment == null) continue;
      result[dstIndex] = fragment;
      dstIndex++;
    }
    return result;
  }

  @Nonnull
  private static DiffFragmentOld[] transformHeadInsert(@Nonnull DiffFragmentOld[] fragments, @Nonnull FragmentSide side) {
    // transforms {abc}abcd into a{bca}bcd
    if (fragments.length >= 2) {
      DiffFragmentOld first = fragments[0];
      DiffFragmentOld second = fragments[1];
      if (first == null || second == null) {
        return fragments;
      }
      if (side.getText(first) != null) {
        return fragments;
      }
      DiffString rightText = side.getOtherText(first);
      DiffString secondText = side.getText(second);
      if (!Comparing.equal(side.getOtherText(second), secondText)) {
        return fragments;
      }
      if (secondText.charAt(0) == rightText.charAt(0)) {
        List<DiffFragmentOld> result = new ArrayList<DiffFragmentOld>();
        result.add(side.createFragment(rightText.substring(0, 1), rightText.substring(0, 1), false));
        result.add(side.createFragment(null, DiffString.concatenate(rightText.substring(1), secondText.substring(0, 1)), true));
        result.add(side.createFragment(secondText.substring(1), secondText.substring(1), second.isModified()));
        result.addAll(Arrays.asList(fragments).subList(2, fragments.length));
        return result.toArray(new DiffFragmentOld[result.size()]);
      }
    }
    return fragments;
  }

  private static class MyChange extends Diff.Change {
    public MyChange(int line0, int line1, int deleted, int inserted) {
      super(line0, line1, deleted, inserted, null);
    }

    public MyChange copyNext(@Nonnull Diff.Change change) {
      return copyNext(change, 0);
    }

    public MyChange copyNext(@Nonnull Diff.Change change, int shift) {
      MyChange result = new MyChange(change.line0 + shift, change.line1 + shift, change.deleted, change.inserted);
      setNext(result);
      return result;
    }

    public void setNext(MyChange change) {
      link = change;
    }

    public int getEnd1() {
      return line0 + deleted;
    }

    public int getEnd2() {
      return line1 + inserted;
    }
  }
}
