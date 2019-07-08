/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.util.io;

import com.intellij.openapi.diagnostic.LoggerRt;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nullable;
import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Stripped-down version of {@code com.intellij.openapi.util.io.FileUtil}.
 * Intended to use by external (out-of-IDE-process) runners and helpers so it should not contain any library dependencies.
 *
 * @since 12.0
 */
@SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
public class FileUtilRt {
  private static final int KILOBYTE = 1024;
  public static final int MEGABYTE = KILOBYTE * KILOBYTE;

  private static final int DEFAULT_INTELLISENSE_LIMIT = 2500 * KILOBYTE;

  public static final int LARGE_FOR_CONTENT_LOADING = Math.max(20 * MEGABYTE, getUserFileSizeLimit());
  public static final int LARGE_FILE_PREVIEW_SIZE = Math.min(getLargeFilePreviewSize(), LARGE_FOR_CONTENT_LOADING);

  private static final int MAX_FILE_IO_ATTEMPTS = 10;
  private static final boolean USE_FILE_CHANNELS = "true".equalsIgnoreCase(System.getProperty("idea.fs.useChannels"));

  public static final FileFilter ALL_FILES = new FileFilter() {
    @Override
    public boolean accept(File file) {
      return true;
    }
  };
  public static final FileFilter ALL_DIRECTORIES = new FileFilter() {
    @Override
    public boolean accept(File file) {
      return file.isDirectory();
    }
  };

  protected static final ThreadLocal<byte[]> BUFFER = new ThreadLocal<byte[]>() {
    @Override
    protected byte[] initialValue() {
      return new byte[1024 * 20];
    }
  };

  private static String ourCanonicalTempPathCache = null;

  protected static final class NIOReflect {
    // NIO-reflection initialization placed in a separate class for lazy loading
    static final boolean IS_AVAILABLE;

    // todo: replace reflection with normal code after migration to JDK 1.8
    private static Method ourFilesDeleteIfExistsMethod;
    private static Method ourFilesWalkMethod;
    private static Method ourFileToPathMethod;
    private static Method ourPathToFileMethod;
    private static Object ourDeletionVisitor;
    private static Class ourNoSuchFileExceptionClass;
    private static Class ourAccessDeniedExceptionClass;

    static {
      boolean initSuccess = false;
      try {
        final Class<?> pathClass = Class.forName("java.nio.file.Path");
        final Class<?> visitorClass = Class.forName("java.nio.file.FileVisitor");
        final Class<?> filesClass = Class.forName("java.nio.file.Files");
        ourNoSuchFileExceptionClass = Class.forName("java.nio.file.NoSuchFileException");
        ourAccessDeniedExceptionClass = Class.forName("java.nio.file.AccessDeniedException");

        ourFileToPathMethod = Class.forName("java.io.File").getMethod("toPath");
        ourPathToFileMethod = pathClass.getMethod("toFile");
        ourFilesWalkMethod = filesClass.getMethod("walkFileTree", pathClass, visitorClass);
        ourFilesDeleteIfExistsMethod = filesClass.getMethod("deleteIfExists", pathClass);
        final Object Result_Continue = Class.forName("java.nio.file.FileVisitResult").getDeclaredField("CONTINUE").get(null);
        ourDeletionVisitor = Proxy.newProxyInstance(FileUtilRt.class.getClassLoader(), new Class[]{visitorClass}, new InvocationHandler() {
          @Override
          public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (args.length == 2) {
              final Object second = args[1];
              if (second instanceof Throwable) {
                throw (Throwable)second;
              }
              final String methodName = method.getName();
              if ("visitFile".equals(methodName) || "postVisitDirectory".equals(methodName)) {
                if (!performDelete(args[0])) {
                  throw new IOException("Failed to delete " + args[0]) {
                    // optimization: the stacktrace is not needed: the exception is used to terminate tree walkup and to pass the result
                    @Override
                    public synchronized Throwable fillInStackTrace() {
                      return this;
                    }
                  };
                }
              }
            }
            return Result_Continue;
          }

          private boolean performDelete(@Nonnull final Object fileObject) {
            Boolean result = doIOOperation(new RepeatableIOOperation<Boolean, RuntimeException>() {
              @Override
              public Boolean execute(boolean lastAttempt) {
                try {
                  //Files.deleteIfExists(file);
                  ourFilesDeleteIfExistsMethod.invoke(null, fileObject);
                  return Boolean.TRUE;
                }
                catch (InvocationTargetException e) {
                  final Throwable cause = e.getCause();
                  if (!(cause instanceof IOException)) {
                    return Boolean.FALSE;
                  }
                  if (ourAccessDeniedExceptionClass.isInstance(cause)) {
                    // file is read-only: fallback to standard java.io API
                    try {
                      final File file = (File)ourPathToFileMethod.invoke(fileObject);
                      if (file == null) {
                        return Boolean.FALSE;
                      }
                      if (file.delete() || !file.exists()) {
                        return Boolean.TRUE;
                      }
                    }
                    catch (Throwable ignored) {
                      return Boolean.FALSE;
                    }
                  }
                }
                catch (IllegalAccessException e) {
                  return Boolean.FALSE;
                }
                return lastAttempt ? Boolean.FALSE : null;
              }
            });
            return Boolean.TRUE.equals(result);
          }
        });
        initSuccess = true;
      }
      catch (Throwable ignored) {
        logger().info("Was not able to detect NIO API");
        ourFileToPathMethod = null;
        ourFilesWalkMethod = null;
        ourFilesDeleteIfExistsMethod = null;
        ourDeletionVisitor = null;
        ourNoSuchFileExceptionClass = null;
      }
      IS_AVAILABLE = initSuccess;
    }
  }

  @Nonnull
  public static String getExtension(@Nonnull String fileName) {
    int index = fileName.lastIndexOf('.');
    if (index < 0) return "";
    return fileName.substring(index + 1);
  }

  @Nonnull
  public static CharSequence getExtension(@Nonnull CharSequence fileName) {
    int index = StringUtilRt.lastIndexOf(fileName, '.', 0, fileName.length());
    if (index < 0) return "";
    return fileName.subSequence(index + 1, fileName.length());
  }

  public static boolean extensionEquals(@Nonnull String filePath, @Nonnull String extension) {
    int extLen = extension.length();
    if (extLen == 0) {
      int lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
      return filePath.indexOf('.', lastSlash + 1) == -1;
    }
    int extStart = filePath.length() - extLen;
    return extStart >= 1 &&
           filePath.charAt(extStart - 1) == '.' &&
           filePath.regionMatches(!SystemInfoRt.isFileSystemCaseSensitive, extStart, extension, 0, extLen);
  }

  @Nonnull
  public static String toSystemDependentName(@NonNls @Nonnull String fileName) {
    return toSystemDependentName(fileName, File.separatorChar);
  }

  @Nonnull
  public static String toSystemDependentName(@NonNls @Nonnull String fileName, final char separatorChar) {
    return fileName.replace('/', separatorChar).replace('\\', separatorChar);
  }

  @Nonnull
  public static String toSystemIndependentName(@NonNls @Nonnull String fileName) {
    return fileName.replace('\\', '/');
  }

  @Nullable
  public static String getRelativePath(File base, File file) {
    if (base == null || file == null) return null;

    if (!base.isDirectory()) {
      base = base.getParentFile();
      if (base == null) return null;
    }

    //noinspection FileEqualsUsage
    if (base.equals(file)) return ".";

    final String filePath = file.getAbsolutePath();
    String basePath = base.getAbsolutePath();
    return getRelativePath(basePath, filePath, File.separatorChar);
  }

  @Nullable
  public static String getRelativePath(@Nonnull String basePath, @Nonnull String filePath, char separator) {
    return getRelativePath(basePath, filePath, separator, SystemInfoRt.isFileSystemCaseSensitive);
  }

  @Nullable
  public static String getRelativePath(@Nonnull String basePath, @Nonnull String filePath, char separator, boolean caseSensitive) {
    basePath = ensureEnds(basePath, separator);

    if (caseSensitive ? basePath.equals(ensureEnds(filePath, separator)) : basePath.equalsIgnoreCase(ensureEnds(filePath, separator))) {
      return ".";
    }

    int len = 0;
    int lastSeparatorIndex = 0; // need this for cases like this: base="/temp/abc/base" and file="/temp/ab"
    CharComparingStrategy strategy = caseSensitive ? CharComparingStrategy.IDENTITY : CharComparingStrategy.CASE_INSENSITIVE;
    while (len < filePath.length() && len < basePath.length() && strategy.charsEqual(filePath.charAt(len), basePath.charAt(len))) {
      if (basePath.charAt(len) == separator) {
        lastSeparatorIndex = len;
      }
      len++;
    }

    if (len == 0) return null;

    StringBuilder relativePath = new StringBuilder();
    for (int i = len; i < basePath.length(); i++) {
      if (basePath.charAt(i) == separator) {
        relativePath.append("..");
        relativePath.append(separator);
      }
    }
    relativePath.append(filePath.substring(lastSeparatorIndex + 1));

    return relativePath.toString();
  }

  private static String ensureEnds(@Nonnull String s, final char endsWith) {
    return StringUtilRt.endsWithChar(s, endsWith) ? s : s + endsWith;
  }

  @Nonnull
  public static String getNameWithoutExtension(@Nonnull String name) {
    int i = name.lastIndexOf('.');
    if (i != -1) {
      name = name.substring(0, i);
    }
    return name;
  }

  @Nonnull
  public static File createTempDirectory(@Nonnull @NonNls String prefix, @Nullable @NonNls String suffix) throws IOException {
    return createTempDirectory(prefix, suffix, true);
  }

  @Nonnull
  public static File createTempDirectory(@Nonnull @NonNls String prefix, @Nullable @NonNls String suffix, boolean deleteOnExit) throws IOException {
    final File dir = new File(getTempDirectory());
    return createTempDirectory(dir, prefix, suffix, deleteOnExit);
  }

  @Nonnull
  public static File createTempDirectory(@Nonnull File dir, @Nonnull @NonNls String prefix, @Nullable @NonNls String suffix) throws IOException {
    return createTempDirectory(dir, prefix, suffix, true);
  }

  @Nonnull
  public static File createTempDirectory(@Nonnull File dir, @Nonnull @NonNls String prefix, @Nullable @NonNls String suffix, boolean deleteOnExit)
          throws IOException {
    File file = doCreateTempFile(dir, prefix, suffix, true);
    if (deleteOnExit) {
      //file.deleteOnExit();
      // default deleteOnExit does not remove dirs if they are not empty
      FilesToDeleteHolder.ourFilesToDelete.add(file.getPath());
    }
    if (!file.isDirectory()) {
      throw new IOException("Cannot create directory: " + file);
    }
    return file;
  }

  private static class FilesToDeleteHolder {
    private static final Queue<String> ourFilesToDelete = createFilesToDelete();

    private static Queue<String> createFilesToDelete() {
      final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<String>();
      Runtime.getRuntime().addShutdownHook(new Thread("FileUtil deleteOnExit") {
        @Override
        public void run() {
          String name = queue.poll();
          while (name != null) {
            delete(new File(name));
            name = queue.poll();
          }
        }
      });
      return queue;
    }
  }

  @Nonnull
  public static File createTempFile(@Nonnull @NonNls String prefix, @Nullable @NonNls String suffix) throws IOException {
    return createTempFile(prefix, suffix, false); //false until TeamCity fixes its plugin
  }

  @Nonnull
  public static File createTempFile(@Nonnull @NonNls String prefix, @Nullable @NonNls String suffix, boolean deleteOnExit) throws IOException {
    final File dir = new File(getTempDirectory());
    return createTempFile(dir, prefix, suffix, true, deleteOnExit);
  }

  @Nonnull
  public static File createTempFile(@NonNls File dir, @Nonnull @NonNls String prefix, @Nullable @NonNls String suffix) throws IOException {
    return createTempFile(dir, prefix, suffix, true, true);
  }

  @Nonnull
  public static File createTempFile(@NonNls File dir, @Nonnull @NonNls String prefix, @Nullable @NonNls String suffix, boolean create) throws IOException {
    return createTempFile(dir, prefix, suffix, create, true);
  }

  @Nonnull
  public static File createTempFile(@NonNls File dir, @Nonnull @NonNls String prefix, @Nullable @NonNls String suffix, boolean create, boolean deleteOnExit)
          throws IOException {
    File file = doCreateTempFile(dir, prefix, suffix, false);
    if (deleteOnExit) {
      //noinspection SSBasedInspection
      file.deleteOnExit();
    }
    if (!create) {
      if (!file.delete() && file.exists()) {
        throw new IOException("Cannot delete file: " + file);
      }
    }
    return file;
  }

  private static final Random RANDOM = new Random();

  @Nonnull
  private static File doCreateTempFile(@Nonnull File dir, @Nonnull @NonNls String prefix, @Nullable @NonNls String suffix, boolean isDirectory)
          throws IOException {
    //noinspection ResultOfMethodCallIgnored
    dir.mkdirs();

    if (prefix.length() < 3) {
      prefix = (prefix + "___").substring(0, 3);
    }
    if (suffix == null) {
      suffix = "";
    }
    // normalize and use only the file name from the prefix
    prefix = new File(prefix).getName();

    int exceptionsCount = 0;
    int i = 0;
    int maxFileNumber = 10;
    while (true) {
      try {
        File f = calcName(dir, prefix, suffix, i);

        boolean success = isDirectory ? f.mkdir() : f.createNewFile();
        if (!success) {
          List<String> list = Arrays.asList(f.getParentFile().list());
          maxFileNumber = Math.max(10, list.size() * 10); // if too many files are in tmp dir, we need a bigger random range than meager 10
          throw new IOException(
                  "Unable to create temporary file " + f + "\nDirectory '" + f.getParentFile() + "' list (" + list.size() + " children): " + list);
        }

        return normalizeFile(f);
      }
      catch (IOException e) { // Win32 createFileExclusively access denied
        if (++exceptionsCount >= 100) {
          throw e;
        }
      }
      i++; // for some reason the file1 can't be created (previous file1 was deleted but got locked by anti-virus?). try file2.
      if (i > 2) {
        i = 2 + RANDOM.nextInt(maxFileNumber); // generate random suffix if too many failures
      }
    }
  }

  @Nonnull
  private static File calcName(@Nonnull File dir, @Nonnull String prefix, @Nonnull String suffix, int i) throws IOException {
    prefix += i == 0 ? "" : i;
    if (prefix.endsWith(".") && suffix.startsWith(".")) {
      prefix = prefix.substring(0, prefix.length() - 1);
    }
    String name = prefix + suffix;
    File f = new File(dir, name);
    if (!name.equals(f.getName())) {
      throw new IOException("Unable to create temporary file " + f + " for name " + name);
    }
    return f;
  }

  @Nonnull
  private static File normalizeFile(@Nonnull File temp) throws IOException {
    final File canonical = temp.getCanonicalFile();
    return SystemInfoRt.isWindows && canonical.getAbsolutePath().contains(" ") ? temp.getAbsoluteFile() : canonical;
  }

  @Nonnull
  public static String getTempDirectory() {
    if (ourCanonicalTempPathCache == null) {
      ourCanonicalTempPathCache = calcCanonicalTempPath();
    }
    return ourCanonicalTempPathCache;
  }

  @Nonnull
  private static String calcCanonicalTempPath() {
    final File file = new File(System.getProperty("java.io.tmpdir"));
    try {
      final String canonical = file.getCanonicalPath();
      if (!SystemInfoRt.isWindows || !canonical.contains(" ")) {
        return canonical;
      }
    }
    catch (IOException ignore) {
    }
    return file.getAbsolutePath();
  }

  @TestOnly
  public static void resetCanonicalTempPathCache(final String tempPath) {
    ourCanonicalTempPathCache = tempPath;
  }

  @Nonnull
  public static File generateRandomTemporaryPath() throws IOException {
    File file = new File(getTempDirectory(), UUID.randomUUID().toString());
    int i = 0;
    while (file.exists() && i < 5) {
      file = new File(getTempDirectory(), UUID.randomUUID().toString());
      ++i;
    }
    if (file.exists()) {
      throw new IOException("Couldn't generate unique random path.");
    }
    return normalizeFile(file);
  }

  /**
   * Set executable attribute, it makes sense only on non-windows platforms.
   *
   * @param path           the path to use
   * @param executableFlag new value of executable attribute
   * @throws IOException if there is a problem with setting the flag
   */
  public static void setExecutableAttribute(@Nonnull String path, boolean executableFlag) throws IOException {
    try {
      File file = new File(path);
      //noinspection Since15
      if (!file.setExecutable(executableFlag) && file.canExecute() != executableFlag) {
        logger().warn("Can't set executable attribute of '" + path + "' to " + executableFlag);
      }
    }
    catch (LinkageError ignored) {
    }
  }

  @Nonnull
  public static String loadFile(@Nonnull File file) throws IOException {
    return loadFile(file, null, false);
  }

  @Nonnull
  public static String loadFile(@Nonnull File file, boolean convertLineSeparators) throws IOException {
    return loadFile(file, null, convertLineSeparators);
  }

  @Nonnull
  public static String loadFile(@Nonnull File file, @Nullable @NonNls String encoding) throws IOException {
    return loadFile(file, encoding, false);
  }

  @Nonnull
  public static String loadFile(@Nonnull File file, @Nullable @NonNls String encoding, boolean convertLineSeparators) throws IOException {
    final String s = new String(loadFileText(file, encoding));
    return convertLineSeparators ? StringUtilRt.convertLineSeparators(s) : s;
  }

  @Nonnull
  public static char[] loadFileText(@Nonnull File file) throws IOException {
    return loadFileText(file, (String)null);
  }

  @Nonnull
  public static char[] loadFileText(@Nonnull File file, @Nullable @NonNls String encoding) throws IOException {
    InputStream stream = new FileInputStream(file);
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") Reader reader =
            encoding == null ? new InputStreamReader(stream) : new InputStreamReader(stream, encoding);
    try {
      return loadText(reader, (int)file.length());
    }
    finally {
      reader.close();
    }
  }

  @Nonnull
  public static char[] loadFileText(@Nonnull File file, @Nonnull @NonNls Charset encoding) throws IOException {
    Reader reader = new InputStreamReader(new FileInputStream(file), encoding);
    try {
      return loadText(reader, (int)file.length());
    }
    finally {
      reader.close();
    }
  }

  @Nonnull
  public static char[] loadText(@Nonnull Reader reader, int length) throws IOException {
    char[] chars = new char[length];
    int count = 0;
    while (count < chars.length) {
      int n = reader.read(chars, count, chars.length - count);
      if (n <= 0) break;
      count += n;
    }
    if (count == chars.length) {
      return chars;
    }
    else {
      char[] newChars = new char[count];
      System.arraycopy(chars, 0, newChars, 0, count);
      return newChars;
    }
  }

  @Nonnull
  public static List<String> loadLines(@Nonnull File file) throws IOException {
    return loadLines(file.getPath());
  }

  @Nonnull
  public static List<String> loadLines(@Nonnull File file, @Nullable @NonNls String encoding) throws IOException {
    return loadLines(file.getPath(), encoding);
  }

  @Nonnull
  public static List<String> loadLines(@Nonnull String path) throws IOException {
    return loadLines(path, null);
  }

  @Nonnull
  public static List<String> loadLines(@Nonnull String path, @Nullable @NonNls String encoding) throws IOException {
    InputStream stream = new FileInputStream(path);
    try {
      @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") InputStreamReader in =
              encoding == null ? new InputStreamReader(stream) : new InputStreamReader(stream, encoding);
      BufferedReader reader = new BufferedReader(in);
      try {
        return loadLines(reader);
      }
      finally {
        reader.close();
      }
    }
    finally {
      stream.close();
    }
  }

  @Nonnull
  public static List<String> loadLines(@Nonnull BufferedReader reader) throws IOException {
    List<String> lines = new ArrayList<String>();
    String line;
    while ((line = reader.readLine()) != null) {
      lines.add(line);
    }
    return lines;
  }

  @Nonnull
  public static byte[] loadBytes(@Nonnull InputStream stream) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    copy(stream, buffer);
    return buffer.toByteArray();
  }

  public static boolean isTooLarge(long len) {
    return len > LARGE_FOR_CONTENT_LOADING;
  }

  @Nonnull
  public static byte[] loadBytes(@Nonnull InputStream stream, int length) throws IOException {
    if (length == 0) {
      return ArrayUtilRt.EMPTY_BYTE_ARRAY;
    }
    byte[] bytes = new byte[length];
    int count = 0;
    while (count < length) {
      int n = stream.read(bytes, count, length - count);
      if (n <= 0) break;
      count += n;
    }
    return bytes;
  }

  /**
   * Get parent for the file. The method correctly
   * processes "." and ".." in file names. The name
   * remains relative if was relative before.
   *
   * @param file a file to analyze
   * @return a parent or the null if the file has no parent.
   */
  @Nullable
  public static File getParentFile(@Nonnull File file) {
    int skipCount = 0;
    File parentFile = file;
    while (true) {
      parentFile = parentFile.getParentFile();
      if (parentFile == null) {
        return null;
      }
      if (".".equals(parentFile.getName())) {
        continue;
      }
      if ("..".equals(parentFile.getName())) {
        skipCount++;
        continue;
      }
      if (skipCount > 0) {
        skipCount--;
        continue;
      }
      return parentFile;
    }
  }

  /**
   * Warning! this method is _not_ symlinks-aware. Consider using com.intellij.openapi.util.io.FileUtil.delete()
   *
   * @param file file or directory to delete
   * @return true if the file did not exist or was successfully deleted
   */
  public static boolean delete(@Nonnull File file) {
    if (NIOReflect.IS_AVAILABLE) {
      return deleteRecursivelyNIO(file);
    }
    return deleteRecursively(file);
  }

  protected static boolean deleteRecursivelyNIO(File file) {
    try {
      /*
      Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.deleteIfExists(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          Files.deleteIfExists(dir);
          return FileVisitResult.CONTINUE;
        }
      });
      */
      final Object pathObject = NIOReflect.ourFileToPathMethod.invoke(file);
      NIOReflect.ourFilesWalkMethod.invoke(null, pathObject, NIOReflect.ourDeletionVisitor);
    }
    catch (InvocationTargetException e) {
      final Throwable cause = e.getCause();
      if (cause == null || !NIOReflect.ourNoSuchFileExceptionClass.isInstance(cause)) {
        logger().info(e);
        return false;
      }
    }
    catch (Exception e) {
      logger().info(e);
      return false;
    }
    return true;
  }

  private static boolean deleteRecursively(@Nonnull File file) {
    File[] files = file.listFiles();
    if (files != null) {
      for (File child : files) {
        if (!deleteRecursively(child)) return false;
      }
    }

    return deleteFile(file);
  }

  public interface RepeatableIOOperation<T, E extends Throwable> {
    @Nullable
    T execute(boolean lastAttempt) throws E;
  }

  @Nullable
  public static <T, E extends Throwable> T doIOOperation(@Nonnull RepeatableIOOperation<T, E> ioTask) throws E {
    for (int i = MAX_FILE_IO_ATTEMPTS; i > 0; i--) {
      T result = ioTask.execute(i == 1);
      if (result != null) return result;

      try {
        //noinspection BusyWait
        Thread.sleep(10);
      }
      catch (InterruptedException ignored) {
      }
    }
    return null;
  }

  protected static boolean deleteFile(@Nonnull final File file) {
    Boolean result = doIOOperation(new RepeatableIOOperation<Boolean, RuntimeException>() {
      @Override
      public Boolean execute(boolean lastAttempt) {
        if (file.delete() || !file.exists()) {
          return Boolean.TRUE;
        }
        else if (lastAttempt) {
          return Boolean.FALSE;
        }
        else {
          return null;
        }
      }
    });
    return Boolean.TRUE.equals(result);
  }

  public static boolean ensureCanCreateFile(@Nonnull File file) {
    if (file.exists()) return file.canWrite();
    if (!createIfNotExists(file)) return false;
    return delete(file);
  }

  public static boolean createIfNotExists(@Nonnull File file) {
    if (file.exists()) return true;
    try {
      if (!createParentDirs(file)) return false;

      OutputStream s = new FileOutputStream(file);
      s.close();
      return true;
    }
    catch (IOException e) {
      logger().info(e);
      return false;
    }
  }

  public static boolean createParentDirs(@Nonnull File file) {
    if (!file.exists()) {
      final File parentFile = file.getParentFile();
      if (parentFile != null) {
        return createDirectory(parentFile);
      }
    }
    return true;
  }

  public static boolean createDirectory(@Nonnull File path) {
    return path.isDirectory() || path.mkdirs();
  }

  @SuppressWarnings("Duplicates")
  public static void copy(@Nonnull File fromFile, @Nonnull File toFile) throws IOException {
    if (!ensureCanCreateFile(toFile)) {
      return;
    }

    FileOutputStream fos = new FileOutputStream(toFile);
    try {
      FileInputStream fis = new FileInputStream(fromFile);
      try {
        copy(fis, fos);
      }
      finally {
        fis.close();
      }
    }
    finally {
      fos.close();
    }

    long timeStamp = fromFile.lastModified();
    if (timeStamp < 0) {
      logger().warn("Invalid timestamp " + timeStamp + " of '" + fromFile + "'");
    }
    else if (!toFile.setLastModified(timeStamp)) {
      logger().warn("Unable to set timestamp " + timeStamp + " to '" + toFile + "'");
    }
  }

  public static void copy(@Nonnull InputStream inputStream, @Nonnull OutputStream outputStream) throws IOException {
    if (USE_FILE_CHANNELS && inputStream instanceof FileInputStream && outputStream instanceof FileOutputStream) {
      final FileChannel fromChannel = ((FileInputStream)inputStream).getChannel();
      try {
        final FileChannel toChannel = ((FileOutputStream)outputStream).getChannel();
        try {
          fromChannel.transferTo(0, Long.MAX_VALUE, toChannel);
        }
        finally {
          toChannel.close();
        }
      }
      finally {
        fromChannel.close();
      }
    }
    else {
      final byte[] buffer = getThreadLocalBuffer();
      while (true) {
        int read = inputStream.read(buffer);
        if (read < 0) break;
        outputStream.write(buffer, 0, read);
      }
    }
  }

  @Nonnull
  public static byte[] getThreadLocalBuffer() {
    return BUFFER.get();
  }


  protected interface SymlinkResolver {
    @Nonnull
    String resolveSymlinksAndCanonicalize(@Nonnull String path, char separatorChar, boolean removeLastSlash);

    boolean isSymlink(@Nonnull CharSequence path);
  }

  /**
   * Converts given path to canonical representation by eliminating '.'s, traversing '..'s, and omitting duplicate separators.
   * Please note that this method is symlink-unfriendly (i.e. result of "/path/to/link/../next" most probably will differ from
   * what {@link File#getCanonicalPath()} will return), so if the path may contain symlinks, consider using {@link com.intellij.openapi.util.io.FileUtil#toCanonicalPath(String, boolean)} instead.
   */
  @Contract("null, _, _ -> null")
  public static String toCanonicalPath(@Nullable String path, char separatorChar, boolean removeLastSlash) {
    return toCanonicalPath(path, separatorChar, removeLastSlash, null);
  }

  @Contract("null, _, _, _ -> null")
  protected static String toCanonicalPath(@Nullable String path, final char separatorChar, final boolean removeLastSlash, final @Nullable SymlinkResolver resolver) {
    if (path == null || path.length() == 0) {
      return path;
    }
    if (path.charAt(0) == '.') {
      if (path.length() == 1) {
        return "";
      }
      char c = path.charAt(1);
      if (c == '/' || c == separatorChar) {
        path = path.substring(2);
      }
    }

    path = path.replace(separatorChar, '/');
    // trying to speedup the common case when there are no "//" or "/."
    int index = -1;
    do {
      index = path.indexOf('/', index + 1);
      char next = index == path.length() - 1 ? 0 : path.charAt(index + 1);
      if (next == '.' || next == '/') {
        break;
      }
    }
    while (index != -1);
    if (index == -1) {
      if (removeLastSlash) {
        int start = processRoot(path, NullAppendable.INSTANCE);
        int slashIndex = path.lastIndexOf('/');
        return slashIndex != -1 && slashIndex > start && slashIndex == path.length() - 1 ? path.substring(0, path.length() - 1) : path;
      }
      return path;
    }

    StringBuilder result = new StringBuilder(path.length());
    int start = processRoot(path, result);
    int dots = 0;
    boolean separator = true;

    for (int i = start; i < path.length(); ++i) {
      char c = path.charAt(i);
      if (c == '/') {
        if (!separator) {
          if (!processDots(result, dots, start, resolver)) {
            return resolver.resolveSymlinksAndCanonicalize(path, separatorChar, removeLastSlash);
          }
          dots = 0;
        }
        separator = true;
      }
      else if (c == '.') {
        if (separator || dots > 0) {
          ++dots;
        }
        else {
          result.append('.');
        }
        separator = false;
      }
      else {
        while (dots > 0) {
          result.append('.');
          dots--;
        }
        result.append(c);
        separator = false;
      }
    }

    if (dots > 0) {
      if (!processDots(result, dots, start, resolver)) {
        return resolver.resolveSymlinksAndCanonicalize(path, separatorChar, removeLastSlash);
      }
    }

    int lastChar = result.length() - 1;
    if (removeLastSlash && lastChar >= 0 && result.charAt(lastChar) == '/' && lastChar > start) {
      result.deleteCharAt(lastChar);
    }

    return result.toString();
  }

  private static int processRoot(@Nonnull String path, @Nonnull Appendable result) {
    try {
      if (SystemInfoRt.isWindows && path.length() > 1 && path.charAt(0) == '/' && path.charAt(1) == '/') {
        result.append("//");

        int hostStart = 2;
        while (hostStart < path.length() && path.charAt(hostStart) == '/') hostStart++;
        if (hostStart == path.length()) return hostStart;
        int hostEnd = path.indexOf('/', hostStart);
        if (hostEnd < 0) hostEnd = path.length();
        result.append(path, hostStart, hostEnd);
        result.append('/');

        int shareStart = hostEnd;
        while (shareStart < path.length() && path.charAt(shareStart) == '/') shareStart++;
        if (shareStart == path.length()) return shareStart;
        int shareEnd = path.indexOf('/', shareStart);
        if (shareEnd < 0) shareEnd = path.length();
        result.append(path, shareStart, shareEnd);
        result.append('/');

        return shareEnd;
      }
      if (path.length() > 0 && path.charAt(0) == '/') {
        result.append('/');
        return 1;
      }
      if (path.length() > 2 && path.charAt(1) == ':' && path.charAt(2) == '/') {
        result.append(path, 0, 3);
        return 3;
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return 0;
  }

  @Contract("_, _, _, null -> true")
  private static boolean processDots(@Nonnull StringBuilder result, int dots, int start, SymlinkResolver symlinkResolver) {
    if (dots == 2) {
      int pos = -1;
      if (!StringUtilRt.endsWith(result, "/../") && !"../".contentEquals(result)) {
        pos = StringUtilRt.lastIndexOf(result, '/', start, result.length() - 1);
        if (pos >= 0) {
          ++pos;  // separator found, trim to next char
        }
        else if (start > 0) {
          pos = start;  // path is absolute, trim to root ('/..' -> '/')
        }
        else if (result.length() > 0) {
          pos = 0;  // path is relative, trim to default ('a/..' -> '')
        }
      }
      if (pos >= 0) {
        if (symlinkResolver != null && symlinkResolver.isSymlink(result)) {
          return false;
        }
        result.delete(pos, result.length());
      }
      else {
        result.append("../");  // impossible to traverse, keep as-is
      }
    }
    else if (dots != 1) {
      for (int i = 0; i < dots; i++) {
        result.append('.');
      }
      result.append('/');
    }
    return true;
  }

  public static int getUserFileSizeLimit() {
    try {
      return Integer.parseInt(System.getProperty("idea.max.intellisense.filesize")) * KILOBYTE;
    }
    catch (NumberFormatException e) {
      return 2500 * KILOBYTE;
    }
  }

  private static int getLargeFilePreviewSize() {
    try {
      return Integer.parseInt(System.getProperty("idea.max.content.load.large.preview.size")) * KILOBYTE;
    }
    catch (NumberFormatException e) {
      return DEFAULT_INTELLISENSE_LIMIT;
    }
  }

  private interface CharComparingStrategy {
    CharComparingStrategy IDENTITY = new CharComparingStrategy() {
      @Override
      public boolean charsEqual(char ch1, char ch2) {
        return ch1 == ch2;
      }
    };
    CharComparingStrategy CASE_INSENSITIVE = new CharComparingStrategy() {
      @Override
      public boolean charsEqual(char ch1, char ch2) {
        return StringUtilRt.charsEqualIgnoreCase(ch1, ch2);
      }
    };

    boolean charsEqual(char ch1, char ch2);
  }

  private static LoggerRt logger() {
    return LoggerRt.getInstance("#com.intellij.openapi.util.io.FileUtilRt");
  }
}