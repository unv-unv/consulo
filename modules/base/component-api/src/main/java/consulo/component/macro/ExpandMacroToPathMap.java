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
package consulo.component.macro;

import consulo.util.lang.StringUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 * @since 2004-12-06
 */
public class ExpandMacroToPathMap extends PathMacroMap {
  private final Map<String, String> myPlainMap = new LinkedHashMap<>();
  private final Map<String, String> myMacroExpands = new LinkedHashMap<>();

  public void addMacroExpand(String macroName, String path) {
    myMacroExpands.put(macroName, PathMacroMap.quotePath(path));
  }

  public void put(String fromText, String toText) {
    myPlainMap.put(fromText, toText);
  }

  public void putAll(ExpandMacroToPathMap another) {
    myPlainMap.putAll(another.myPlainMap);
    myMacroExpands.putAll(another.myMacroExpands);
  }

  @Override
  public String substitute(String text, boolean caseSensitive) {
    if (text == null) {
      //noinspection ConstantConditions
      return null;
    }
    if (text.indexOf('$') < 0) {
      return text;
    }
    for (Map.Entry<String, String> entry : myPlainMap.entrySet()) {
      // when replacing macros with actual paths the replace utility may be used as always 'case-sensitive'
      // for case-insensitive file systems there will be no unnecessary toLowerCase() transforms.
      text = StringUtil.replace(text, entry.getKey(), entry.getValue(), false);
    }

    for (Map.Entry<String, String> entry : myMacroExpands.entrySet()) {
      text = replaceMacro(text, entry.getKey(), entry.getValue());
    }

    return text;
  }

  private static String replaceMacro(String text, String macroName, String replacement) {
    while (true) {
      int start = findMacroIndex(text, macroName);
      if (start < 0) {
        break;
      }

      int end = start + macroName.length() + 2;
      int slashCount = getSlashCount(text, end);
      String actualReplacement = slashCount > 0 && !replacement.endsWith("/") ? replacement + "/" : replacement;
      text = StringUtil.replaceSubstring(text, start, end + slashCount, actualReplacement);
    }
    return text;
  }

  private static int getSlashCount(String text, int pos) {
    return StringUtil.isChar(text, pos, '/') ? StringUtil.isChar(text, pos + 1, '/') ? 2 : 1 : 0;
  }

  private static int findMacroIndex(String text, String macroName) {
    int i = -1;
    while (true) {
      i = text.indexOf('$', i + 1);
      if (i < 0) {
        return -1;
      }
      if (StringUtil.startsWith(text, i + 1, macroName) && StringUtil.isChar(text, i + macroName.length() + 1, '$')) {
        return i;
      }
    }
  }

  @Override
  public int hashCode() {
    return myPlainMap.hashCode() + myMacroExpands.hashCode();
  }
}
