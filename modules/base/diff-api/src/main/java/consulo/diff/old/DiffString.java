/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import consulo.annotation.DeprecationInfo;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Deprecated
@DeprecationInfo("Old diff-impl")
public class DiffString implements CharSequence {
  @Nonnull
  public static final DiffString EMPTY = new DiffString(new char[0], 0, 0);

  @Nonnull
  private final char[] myData;
  private final int myStart;
  private final int myLength;
  private int myHash;

  @Nullable
  public static DiffString createNullable(@Nullable String string) {
    if (string == null) return null;
    return create(string);
  }

  @Nonnull
  public static DiffString create(@Nonnull String string) {
    if (string.isEmpty()) return EMPTY;
    return create(string.toCharArray());
  }

  @Nonnull
  static DiffString create(@Nonnull char[] data) {
    return create(data, 0, data.length);
  }

  @Nonnull
  static DiffString create(@Nonnull char[] data, int start, int length) {
    if (length == 0) return EMPTY;
    checkBounds(start, length, data.length);
    return new DiffString(data, start, length);
  }

  private DiffString(@Nonnull char[] data, int start, int length) {
    myData = data;
    myStart = start;
    myLength = length;
  }

  @Override
  public int length() {
    return myLength;
  }

  public boolean isEmpty() {
    return myLength == 0;
  }

  @Override
  public char charAt(int index) {
    if (index < 0 || index >= myLength) {
      throw new StringIndexOutOfBoundsException(index);
    }
    return data(index);
  }

  public char data(int index) {
    return myData[myStart + index];
  }

  @Nonnull
  public DiffString substring(int start) {
    return substring(start, myLength);
  }

  @Nonnull
  public DiffString substring(int start, int end) {
    if (start == 0 && end == myLength) return this;
    checkBounds(start, end - start, myLength);
    return create(myData, myStart + start, end - start);
  }

  @Override
  public DiffString subSequence(int start, int end) {
    return substring(start, end);
  }

  @Nonnull
  @Override
  public String toString() {
    return new String(myData, myStart, myLength);
  }

  @Nonnull
  public DiffString copy() {
    return create(Arrays.copyOfRange(myData, myStart, myStart + myLength));
  }

  public void copyData(@Nonnull char[] dst, int start) {
    checkBounds(start, myLength, dst.length);
    System.arraycopy(myData, myStart, dst, start, myLength);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DiffString that = (DiffString)o;

    if (myLength != that.myLength) return false;
    if (hashCode() != that.hashCode()) return false;
    for (int i = 0; i < myLength; i++) {
      if (data(i) != that.data(i)) return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int h = myHash;
    if (h == 0) {
      h = StringUtil.stringHashCode(myData, myStart, myStart + myLength);
      if (h == 0) h = 1;
      myHash = h;
    }
    return h;
  }

  @Nullable
  public static DiffString concatenateNullable(@Nullable DiffString s1, @Nullable DiffString s2) {
    if (s1 == null || s2 == null) {
      if (s1 != null) return s1;
      if (s2 != null) return s2;
      return null;
    }

    return concatenate(s1, s2);
  }

  @Nonnull
  public static DiffString concatenate(@Nonnull DiffString s1, @Nonnull DiffString s2) {
    if (s1.isEmpty()) return s2;
    if (s2.isEmpty()) return s1;

    if (s1.myData == s2.myData && s1.myStart + s1.myLength == s2.myStart) {
      return create(s1.myData, s1.myStart, s1.myLength + s2.myLength);
    }

    char[] data = new char[s1.myLength + s2.myLength];
    System.arraycopy(s1.myData, s1.myStart, data, 0, s1.myLength);
    System.arraycopy(s2.myData, s2.myStart, data, s1.myLength, s2.myLength);
    return create(data);
  }

  public static boolean canInplaceConcatenate(@Nonnull DiffString s1, @Nonnull DiffString s2) {
    if (s1.isEmpty()) return true;
    if (s2.isEmpty()) return true;

    if (s1.myData == s2.myData && s1.myStart + s1.myLength == s2.myStart) {
      return true;
    }

    return false;
  }

  @Nonnull
  public static DiffString concatenateCopying(@Nonnull DiffString[] strings) {
    return concatenateCopying(strings, 0, strings.length);
  }

  @Nonnull
  public static DiffString concatenateCopying(@Nonnull DiffString[] strings, int start, int length) {
    checkBounds(start, length, strings.length);

    int len = 0;
    for (int i = 0; i < length; i++) {
      DiffString string = strings[start + i];
      len += string == null ? 0 : string.myLength;
    }

    if (len == 0) return EMPTY;

    char[] data = new char[len];
    int index = 0;
    for (int i = 0; i < length; i++) {
      DiffString string = strings[start + i];
      if (string == null || string.isEmpty()) continue;
      System.arraycopy(string.myData, string.myStart, data, index, string.myLength);
      index += string.myLength;
    }
    return create(data);
  }

  @Nonnull
  public static DiffString concatenate(@Nonnull DiffString s, char c) {
    if (s.myStart + s.myLength < s.myData.length && s.data(s.myLength) == c) {
      return create(s.myData, s.myStart, s.myLength + 1);
    }

    char[] data = new char[s.myLength + 1];
    System.arraycopy(s.myData, s.myStart, data, 0, s.myLength);
    data[s.myLength] = c;
    return create(data);
  }

  @Nonnull
  public static DiffString concatenate(char c, @Nonnull DiffString s) {
    if (s.myStart > 0 && s.data(-1) == c) {
      return create(s.myData, s.myStart - 1, s.myLength + 1);
    }

    char[] data = new char[s.myLength + 1];
    System.arraycopy(s.myData, s.myStart, data, 1, s.myLength);
    data[0] = c;
    return create(data);
  }

  @Nonnull
  public static DiffString concatenate(@Nonnull DiffString[] strings) {
    return concatenate(strings, 0, strings.length);
  }

  @Nonnull
  public static DiffString concatenate(@Nonnull DiffString[] strings, int start, int length) {
    checkBounds(start, length, strings.length);

    char[] data = null;
    int startIndex = 0;
    int endIndex = 0;

    boolean linearized = true;
    for (int i = 0; i < length; i++) {
      DiffString string = strings[start + i];
      if (string == null || string.isEmpty()) continue;
      if (data == null) {
        data = string.myData;
        startIndex = string.myStart;
        endIndex = string.myStart + string.myLength;
        continue;
      }
      if (data != string.myData || string.myStart != endIndex) {
        linearized = false;
        break;
      }
      endIndex += string.myLength;
    }

    if (linearized) {
      if (data == null) return EMPTY;
      return create(data, startIndex, endIndex - startIndex);
    }

    return concatenateCopying(strings, start, length);
  }

  @Nonnull
  public DiffString append(char c) {
    return concatenate(this, c);
  }

  @Nonnull
  public DiffString preappend(char c) {
    return concatenate(c, this);
  }

  public static boolean isWhiteSpace(char c) {
    return StringUtil.isWhiteSpace(c);
  }

  public boolean isEmptyOrSpaces() {
    if (isEmpty()) return true;

    for (int i = 0; i < myLength; i++) {
      if (!isWhiteSpace(data(i))) return false;
    }
    return true;
  }

  @Nonnull
  public DiffString trim() {
    int start = 0;
    int end = myLength;

    while (start < end && isWhiteSpace(data(start))) start++;
    while (end > start && isWhiteSpace(data(end - 1))) end--;

    return substring(start, end);
  }

  @Nonnull
  public DiffString trimLeading() {
    int i = 0;

    while (i < myLength && isWhiteSpace(data(i))) i++;

    return substring(i, myLength);
  }

  @Nonnull
  public DiffString trimTrailing() {
    int end = myLength;

    while (end > 0 && isWhiteSpace(data(end - 1))) end--;

    return substring(0, end);
  }

  @Nonnull
  public DiffString getLeadingSpaces() {
    int i = 0;

    while (i < myLength && data(i) == ' ') i++;

    return substring(0, i);
  }

  @Nonnull
  public DiffString skipSpaces() {
    DiffString s = trim();
    int count = 0;
    for (int i = 0; i < s.myLength; i++) {
      if (isWhiteSpace(s.data(i))) count++;
    }
    if (count == 0) return s;

    char[] data = new char[s.myLength - count];
    int index = 0;
    for (int i = 0; i < s.myLength; i++) {
      if (isWhiteSpace(s.data(i))) continue;
      data[index] = s.data(i);
      index++;
    }
    return create(data);
  }

  public int indexOf(char c) {
    return StringUtil.indexOf(this, c);
  }

  public boolean endsWith(char c) {
    if (isEmpty()) return false;
    return data(myLength - 1) == c;
  }

  public static void checkBounds(int start, int length, int maxLength) {
    if (start < 0) {
      throw new StringIndexOutOfBoundsException(start);
    }
    if (length < 0) {
      throw new StringIndexOutOfBoundsException(length);
    }
    if (start + length > maxLength) {
      throw new StringIndexOutOfBoundsException(start + length);
    }
  }

  @Nonnull
  public DiffString[] tokenize() {
    return new LineTokenizer(this).execute();
  }

  public static class LineTokenizer extends LineTokenizerBase<DiffString> {
    @Nonnull
    private final DiffString myText;

    public LineTokenizer(@Nonnull DiffString text) {
      myText = text;
    }

    @Nonnull
    public DiffString[] execute() {
      ArrayList<DiffString> lines = new ArrayList<>();
      doExecute(lines);
      return lines.toArray(new DiffString[lines.size()]);
    }

    @Override
    protected void addLine(List<DiffString> lines, int start, int end, boolean appendNewLine) {
      if (appendNewLine) {
        lines.add(myText.substring(start, end).append('\n'));
      }
      else {
        lines.add(myText.substring(start, end));
      }
    }

    @Override
    protected char charAt(int index) {
      return myText.data(index);
    }

    @Override
    protected int length() {
      return myText.length();
    }

    @Nonnull
    @Override
    protected String substring(int start, int end) {
      return myText.substring(start, end).toString();
    }
  }
}
