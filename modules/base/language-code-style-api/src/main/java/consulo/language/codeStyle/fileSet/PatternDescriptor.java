// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.language.codeStyle.fileSet;

import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.regex.Pattern;

/**
 * Describes a set of files specified by a directory and/or by a file mask.
 * <p>
 * Examples:
 * <ul>
 * <li>
 * {@code *.test}<br>
 * A file ending with .test in any directory
 * </li>
 * <li>
 * {@code /foo/*.test}<br>
 * A file with *.test extension immediately under <i>project-dir</i>/foo directory.
 * </li>
 * <li>
 * {@code foo/*.test}<br>
 * A file with .test extension if their parent directory is "foo", equivalent to &#47;**&#47;foo&#47;*.test
 * </li>
 * <li>
 * {@code /*.test}
 * A file with .test extension directly under project root directory.
 * </li>
 * </ul>
 */
public class PatternDescriptor implements FileSetDescriptor {

  public static final String PATTERN_TYPE = "pattern";
  private
  @Nullable
  String myRawPattern;
  private
  @Nullable
  Pattern myPathPattern;
  private
  @Nullable
  Pattern myFileNamePattern;
  private final static String FORBIDDEN_CHARS = "<>:\"\\;";

  public PatternDescriptor(@Nonnull String pattern) {
    myRawPattern = pattern;
    compileSpec(myRawPattern);
  }

  private void compileSpec(@Nonnull String spec) {
    String pathSpec = "";
    String fileSpec;
    int lastSlashPos = spec.lastIndexOf('/');
    if (lastSlashPos >= 0) {
      fileSpec = spec.substring(lastSlashPos + 1);
      pathSpec = spec.substring(0, lastSlashPos + 1);
      if (pathSpec.length() > 0 && pathSpec.charAt(0) != '/') {
        pathSpec = "/**/" + pathSpec;
      }
    }
    else {
      fileSpec = spec;
    }
    if (!pathSpec.isEmpty()) {
      myPathPattern = Pattern.compile(specToRegexp(pathSpec, true));
    }
    if (!fileSpec.isEmpty()) {
      myFileNamePattern = Pattern.compile(specToRegexp(fileSpec, false));
    }
  }

  private static String specToRegexp(@Nonnull String spec, boolean isPathSpec) {
    StringBuilder sb = new StringBuilder();
    char[] chars = spec.toCharArray();
    int i = 0;
    while (i < chars.length) {
      char c = chars[i];
      switch (c) {
        case '*':
          if (isPathSpec && i < chars.length - 1 && chars[i + 1] == '*') {
            sb.append("([^/]*/)*");
            i++;
            if (i < chars.length - 1 && chars[i + 1] == '/') {
              i++;
            }
          }
          else {
            sb.append("[^/]*");
          }
          break;
        case '?':
          sb.append("[^/]");
          break;
        default:
          if (isRegexSpecialChar(c)) {
            sb.append('\\').append(c);
          }
          else {
            sb.append(c);
          }
      }
      i++;
    }
    return sb.toString();
  }


  private static boolean isRegexSpecialChar(char c) {
    return "^${}[]().*+-&".indexOf(c) >= 0;
  }

  public boolean matches(@Nonnull Project project, @Nonnull VirtualFile virtualFile) {
    if (myFileNamePattern == null && myPathPattern == null) return false; // Empty spec matches nothing
    String name = virtualFile.getName();
    VirtualFile parent = virtualFile.getParent();
    String path = getRelativePath(project, parent) + "/";
    return patternMatches(myPathPattern, path) && patternMatches(myFileNamePattern, name);
  }

  @Override
  public boolean matches(@Nonnull PsiFile psiFile) {
    if (psiFile.isValid()) {
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile != null) {
        return matches(psiFile.getProject(), virtualFile);
      }
    }
    return false;
  }

  @Nonnull
  private static String getRelativePath(@Nonnull Project project, @Nullable VirtualFile parent) {
    VirtualFile projectDir = project.getBaseDir();
    String projectPath = projectDir.getPath();
    if (parent != null) {
      String parentPath = parent.getPath();
      if (parentPath.startsWith(projectPath)) {
        return parentPath.substring(projectPath.length());
      }
      else {
        return parentPath;
      }
    }
    return "";
  }

  private static boolean patternMatches(@Nullable Pattern pattern, @Nonnull String str) {
    return pattern == null || pattern.matcher(str).matches();
  }

  @Override
  public String getPattern() {
    return myRawPattern;
  }

  @Override
  public void setPattern(@Nullable String pattern) {
    myRawPattern = pattern;
    if (pattern != null) {
      compileSpec(pattern);
    }
    else {
      myPathPattern = null;
      myFileNamePattern = null;
    }
  }

  @Override
  @SuppressWarnings("EqualsHashCode")
  public boolean equals(Object obj) {
    return obj instanceof PatternDescriptor && Comparing.equal(myRawPattern, ((PatternDescriptor)obj).getPattern());
  }

  public static boolean isValidPattern(@Nonnull String pattern) {
    for (int i = 0; i < pattern.length(); i++) {
      if (FORBIDDEN_CHARS.indexOf(pattern.charAt(i)) >= 0) return false;
    }
    return true;
  }

  @Nonnull
  @Override
  public String getType() {
    return PATTERN_TYPE;
  }

  @Override
  public String toString() {
    return getType() + ": " + getPattern();
  }
}
