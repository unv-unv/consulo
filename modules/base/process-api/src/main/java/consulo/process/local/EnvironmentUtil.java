// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.process.local;

import consulo.component.util.NativeFileLoader;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.platform.PlatformOperatingSystem;
import consulo.platform.os.UnixOperationSystem;
import consulo.process.internal.UnixProcessManager;
import consulo.process.internal.WinProcessManager;
import consulo.process.io.BaseOutputReader;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.HashingStrategy;
import consulo.util.collection.Maps;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static java.util.Collections.emptyMap;

public final class EnvironmentUtil {
  private static final Logger LOG = Logger.getInstance(EnvironmentUtil.class);

  /**
   * The default time-out to read the environment, in milliseconds.
   */
  private static final long DEFAULT_SHELL_ENV_READING_TIMEOUT_MILLIS = 20_000L;

  private static final String LANG = "LANG";
  private static final String LC_ALL = "LC_ALL";
  private static final String LC_CTYPE = "LC_CTYPE";

  private static final String DESKTOP_STARTUP_ID = "DESKTOP_STARTUP_ID";

  public static final String BASH_EXECUTABLE_NAME = "bash";
  public static final String SHELL_VARIABLE_NAME = "SHELL";
  private static final String SHELL_INTERACTIVE_ARGUMENT = "-i";
  public static final String SHELL_LOGIN_ARGUMENT = "-l";
  public static final String SHELL_COMMAND_ARGUMENT = "-c";
  public static final String SHELL_SOURCE_COMMAND = "source";

  /**
   * Holds the number of shell levels the current shell is running on top of.
   * Tested with bash/zsh/fish/tcsh/csh/ksh.
   */
  private static final String SHLVL = "SHLVL";

  private static final AtomicReference<CompletableFuture<Map<String, String>>> ourEnvGetter = new AtomicReference<>();

  private EnvironmentUtil() {
  }

  /**
   * <p>A wrapper layer around {@link System#getenv()}.</p>
   *
   * <p>On Windows, the returned map is case-insensitive (i.e. {@code map.get("Path") == map.get("PATH")} holds).</p>
   *
   * <p>On macOS, things are complicated.<br/>
   * An app launched by a GUI launcher (Finder, Dock, Spotlight etc.) receives a pretty empty and useless environment,
   * since standard Unix ways of setting variables via e.g. ~/.profile do not work. What's more important, there are no
   * sane alternatives. This causes a lot of user complaints about tools working in a terminal not working when launched
   * from the IDE. To ease their pain, the IDE loads a shell environment (see {@link #getShellEnv} for gory details)
   * and returns it as the result.<br/>
   * And one more thing (c): locale variables on macOS are usually set by a terminal app - meaning they are missing
   * even from a shell environment above. This again causes user complaints about tools being unable to output anything
   * outside ASCII range when launched from the IDE. Resolved by adding LC_CTYPE variable to the map if it doesn't contain
   * explicitly set locale variables (LANG/LC_ALL/LC_CTYPE). See {@link #setCharsetVar} for details.</p>
   *
   * @return unmodifiable map of the process environment.
   */
  @Nonnull
  public static Map<String, String> getEnvironmentMap() {
    CompletableFuture<Map<String, String>> getter = ourEnvGetter.get();
    if (getter == null) {
      getter = CompletableFuture.completedFuture(getSystemEnv());
      if (!ourEnvGetter.compareAndSet(null, getter)) {
        getter = ourEnvGetter.get();
      }
    }
    try {
      return getter.join();
    }
    catch (Throwable t) {
      // unknown state; is not expected to happen
      throw new AssertionError(t);
    }
  }

  @Nullable
  public static Boolean loadEnvironment(@Nonnull Runnable activity) {
    if (!shouldLoadShellEnv()) {
      ourEnvGetter.set(CompletableFuture.completedFuture(getSystemEnv()));
      return null;
    }

    CompletableFuture<Map<String, String>> envFuture = new CompletableFuture<>();
    ourEnvGetter.set(envFuture);
    Boolean result = Boolean.TRUE;
    try {
      Map<String, String> env = getShellEnv();
      setCharsetVar(env);
      envFuture.complete(Collections.unmodifiableMap(env));
    }
    catch (Throwable t) {
      result = Boolean.FALSE;
      LOG.warn("can't get shell environment", t);
    }
    finally {
      activity.run();
    }

    // execution time of handlers of envFuture should be not included into load env activity
    if (Objects.equals(result, Boolean.FALSE)) {
      envFuture.complete(getSystemEnv());
    }
    return result;
  }

  private static boolean shouldLoadShellEnv() {
    if (!Platform.current().os().isMac()) {
      return false;
    }

    // The method is called too early when the IDE starts up, at this point the registry values have not been loaded yet from the service.
    // Using a system property is a good alternative.
    if (!Boolean.parseBoolean(System.getProperty("ij.load.shell.env", "true"))) {
      LOG.info("loading shell env is turned off");
      return false;
    }

    // On macOS, login shell session is not run when a user logs in, thus "SHLVL > 0" likely means that IDE is run from a terminal.
    String shLvl = System.getenv(SHLVL);
    if (shLvl != null) {
      LOG.info("loading shell env is skipped: IDE has been launched from a terminal (" + SHLVL + '=' + shLvl + ')');
      return false;
    }

    return true;
  }

  @Nonnull
  private static Map<String, String> getSystemEnv() {
    PlatformOperatingSystem os = Platform.current().os();
    if (os.isWindows()) {
      return Collections.unmodifiableMap(Maps.newHashMap(System.getenv(), HashingStrategy.caseInsensitive()));
    }
    else if (os instanceof UnixOperationSystem unix && unix.isXWindow()) {
      // DESKTOP_STARTUP_ID variable can be set by an application launcher in X Window environment.
      // It shouldn't be passed to child processes as per 'Startup notification protocol'
      // (https://specifications.freedesktop.org/startup-notification-spec/startup-notification-latest.txt).
      // Ideally, JDK should clear this variable, and it actually does, but the snapshot of the environment variables,
      // returned by System.getenv(), is captured before the removal.
      Map<String, String> env = System.getenv();
      if (env.containsKey(DESKTOP_STARTUP_ID)) {
        env = new HashMap<>(env);
        env.remove(DESKTOP_STARTUP_ID);
        env = Collections.unmodifiableMap(env);
      }
      return env;
    }
    else {
      return System.getenv();
    }
  }

  /**
   * Same as {@code getEnvironmentMap().get(name)}.
   * Returns value for the passed environment variable name, or null if no such variable found.
   *
   * @see #getEnvironmentMap()
   */
  @Nullable
  public static String getValue(@Nonnull String name) {
    return getEnvironmentMap().get(name);
  }

  /**
   * Validates environment variable name in accordance to
   * {@code ProcessEnvironment#validateVariable} ({@code ProcessEnvironment#validateName} on Windows).
   *
   * @see #isValidValue(String)
   * @see <a href="http://pubs.opengroup.org/onlinepubs/000095399/basedefs/xbd_chap08.html">Environment Variables in Unix</a>
   * @see <a href="https://docs.microsoft.com/en-us/windows/desktop/ProcThread/environment-variables">Environment Variables in Windows</a>
   */
  @Contract(value = "null -> false", pure = true)
  public static boolean isValidName(@Nullable String name) {
    return name != null && !name.isEmpty() && name.indexOf('\0') == -1 && name.indexOf('=', Platform.current().os().isWindows() ? 1 : 0) == -1;
  }

  /**
   * Validates environment variable value in accordance to {@code ProcessEnvironment#validateValue}.
   *
   * @see #isValidName(String)
   */
  @Contract(value = "null -> false", pure = true)
  public static boolean isValidValue(@Nullable String value) {
    return value != null && value.indexOf('\0') == -1;
  }

  public static final String DISABLE_OMZ_AUTO_UPDATE = "DISABLE_AUTO_UPDATE";
  private static final String INTELLIJ_ENVIRONMENT_READER = "INTELLIJ_ENVIRONMENT_READER";

  @Nonnull
  private static Map<String, String> getShellEnv() throws IOException {
    return new ShellEnvReader().readShellEnv(null, null);
  }

  public static class ShellEnvReader {
    private final long myTimeoutMillis;

    /**
     * Creates an instance with the default time-out value of {@value #DEFAULT_SHELL_ENV_READING_TIMEOUT_MILLIS} milliseconds.
     *
     * @see #ShellEnvReader(long)
     */
    public ShellEnvReader() {
      this(DEFAULT_SHELL_ENV_READING_TIMEOUT_MILLIS);
    }

    /**
     * @param timeoutMillis the time-out (in milliseconds) for reading environment variables.
     * @see #ShellEnvReader()
     */
    public ShellEnvReader(long timeoutMillis) {
      myTimeoutMillis = timeoutMillis;
    }

    @Nonnull
    public final Map<String, String> readShellEnv(@Nullable Path file, @Nullable Map<String, String> additionalEnvironment) throws IOException {
      Platform platform = Platform.current();
      String fileName = platform.os().isMac() ? platform.mapExecutableName("printenv") : "printenv.py";
      Path executablePath = NativeFileLoader.findExecutablePath(fileName);

      Path envFile = Files.createTempFile("consulo-shell-env.", ".tmp");
      StringBuilder readerCmd = new StringBuilder();
      if (file != null) {
        if (!Files.exists(file)) {
          throw new NoSuchFileException(file.toString());
        }
        readerCmd.append(SHELL_SOURCE_COMMAND).append(" \"").append(file).append("\" && ");
      }

      readerCmd.append("'").append(executablePath.toAbsolutePath()).append("' '").append(envFile.toAbsolutePath()).append("'");

      try {
        List<String> command = getShellProcessCommand();
        int idx = command.indexOf(SHELL_COMMAND_ARGUMENT);
        if (idx >= 0) {
          // if there is already a command append command to the end
          command.set(idx + 1, command.get(idx + 1) + ';' + readerCmd);
        }
        else {
          command.add(SHELL_COMMAND_ARGUMENT);
          command.add(readerCmd.toString());
        }

        LOG.info("loading shell env: " + String.join(" ", command));
        return runProcessAndReadOutputAndEnvs(command, null, additionalEnvironment, envFile).getValue();
      }
      finally {
        try {
          Files.delete(envFile);
        }
        catch (NoSuchFileException ignore) {
        }
        catch (IOException e) {
          LOG.warn("Cannot delete temporary file", e);
        }
      }
    }

    /**
     * @throws IOException if the process fails to start, exits with a non-zero
     *                     code, produces no output or the file used to store the output can't be
     *                     read.
     * @see #runProcessAndReadOutputAndEnvs(List, Path, Map, Path)
     * @see #runProcessAndReadOutputAndEnvs(List, Path, Consumer, Path)
     */
    protected final
    @Nonnull
    Map.Entry<String, Map<String, String>> runProcessAndReadOutputAndEnvs(@Nonnull List<String> command, @Nullable Path workingDir, @Nonnull Path envFile) throws IOException {
      return runProcessAndReadOutputAndEnvs(command, workingDir, emptyMap(), envFile);
    }

    /**
     * @param scriptEnvironment the extra environment to be added to the
     *                          environment of the new process. If {@code null},
     *                          the process environment won't be modified.
     * @throws IOException if the process fails to start, exits with a non-zero
     *                     code, produces no output or the file used to store the output can't be
     *                     read.
     * @see #runProcessAndReadOutputAndEnvs(List, Path, Path)
     * @see #runProcessAndReadOutputAndEnvs(List, Path, Consumer, Path)
     */
    protected final
    @Nonnull
    Map.Entry<String, Map<String, String>> runProcessAndReadOutputAndEnvs(@Nonnull List<String> command,
                                                                          @Nullable Path workingDir,
                                                                          @Nullable Map<String, String> scriptEnvironment,
                                                                          @Nonnull Path envFile) throws IOException {
      return runProcessAndReadOutputAndEnvs(command, workingDir, (it) -> {
        if (scriptEnvironment != null) {
          // we might need default environment for the process to launch correctly
          it.putAll(scriptEnvironment);
        }
      }, envFile);
    }

    /**
     * @param scriptEnvironmentProcessor the block which accepts the environment
     *                                   of the new process, allowing to add and
     *                                   remove environment variables.
     * @throws IOException if the process fails to start, exits with a non-zero
     *                     code, produces no output or the file used to store the output can't be
     *                     read.
     * @see #runProcessAndReadOutputAndEnvs(List, Path, Path)
     * @see #runProcessAndReadOutputAndEnvs(List, Path, Map, Path)
     */
    @Nonnull
    protected final Map.Entry<String, Map<String, String>> runProcessAndReadOutputAndEnvs(@Nonnull List<String> command,
                                                                                          @Nullable Path workingDir,
                                                                                          @Nonnull Consumer<Map<String, String>> scriptEnvironmentProcessor,
                                                                                          @Nonnull Path envFile) throws IOException {
      final ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);

      /*
       * Add, remove or change the environment variables.
       */
      scriptEnvironmentProcessor.accept(builder.environment());

      if (workingDir != null) {
        builder.directory(workingDir.toFile());
      }
      builder.environment().put(DISABLE_OMZ_AUTO_UPDATE, "true");
      builder.environment().put(INTELLIJ_ENVIRONMENT_READER, "true");
      final Process process = builder.start();
      final StreamGobbler gobbler = new StreamGobbler(process.getInputStream());
      final int exitCode = waitAndTerminateAfter(process, myTimeoutMillis);
      gobbler.stop();

      final String lines = new String(Files.readAllBytes(envFile), StandardCharsets.UTF_8);
      if (exitCode != 0 || lines.isEmpty()) {
        throw new IOException("command " + command + "\n\texit code:" + exitCode + " text:" + lines.length() + " out:" + gobbler.getText().trim());
      }
      return new AbstractMap.SimpleImmutableEntry<>(gobbler.getText(), parseEnv(lines));
    }

    protected
    @Nonnull
    List<String> getShellProcessCommand() {
      String shellScript = getShell();
      if (shellScript == null || shellScript.isEmpty()) {
        throw new RuntimeException("empty $SHELL");
      }
      if (!Files.isExecutable(Paths.get(shellScript))) {
        throw new RuntimeException("$SHELL points to a missing or non-executable file: " + shellScript);
      }
      return buildShellProcessCommand(shellScript, true, true, false);
    }

    protected
    @Nullable
    String getShell() {
      return System.getenv(SHELL_VARIABLE_NAME);
    }
  }

  /**
   * Builds a login shell command list from the {@code shellScript} path.
   *
   * @param shellScript   path to the shell script, probably taken from environment variable {@code SHELL}
   * @param isLogin       true iff it should be login shell, usually {@code -l} parameter
   * @param isInteractive true iff it should be interactive shell, usually {@code -i} parameter
   * @param isCommand     true iff command should accept a command, instead of script name, usually {@code -c} parameter
   * @return list of commands for starting a process, e.g. {@code /bin/bash -l -i -c}
   */
  @Nonnull
  public static List<String> buildShellProcessCommand(@Nonnull String shellScript, boolean isLogin, boolean isInteractive, boolean isCommand) {
    List<String> commands = new ArrayList<>();
    commands.add(shellScript);
    if (isLogin && !(shellScript.endsWith("/tcsh") || shellScript.endsWith("/csh"))) {
      // *csh do not allow to use -l with any other options
      commands.add(SHELL_LOGIN_ARGUMENT);
    }
    if (isInteractive && !shellScript.endsWith("/fish")) {
      // Fish uses a single config file with conditions
      commands.add(SHELL_INTERACTIVE_ARGUMENT);
    }
    if (isCommand) {
      commands.add(SHELL_COMMAND_ARGUMENT);
    }
    return commands;
  }

  @SuppressWarnings("SSBasedInspection")
  @Nonnull
  public static Map<String, String> parseEnv(@Nonnull String[] lines) {
    Set<String> toIgnore = new HashSet<>(Arrays.asList("_", "PWD", "SHLVL", DISABLE_OMZ_AUTO_UPDATE, INTELLIJ_ENVIRONMENT_READER));
    Map<String, String> env = System.getenv();
    Map<String, String> newEnv = new HashMap<>();

    for (String line : lines) {
      int pos = line.indexOf('=');
      if (pos <= 0) {
        throw new RuntimeException("malformed:" + line);
      }
      String name = line.substring(0, pos);
      if (!toIgnore.contains(name)) {
        newEnv.put(name, line.substring(pos + 1));
      }
      else if (env.containsKey(name)) {
        newEnv.put(name, env.get(name));
      }
    }

    LOG.info("shell environment loaded (" + newEnv.size() + " vars)");
    return newEnv;
  }

  private static
  @Nonnull
  Map<String, String> parseEnv(@Nonnull String text) {
    return parseEnv(text.split("\0"));
  }

  /**
   * @param timeoutMillis the time-out (in milliseconds) for {@code process} to terminate.
   */
  private static int waitAndTerminateAfter(@Nonnull Process process, final long timeoutMillis) {
    Integer exitCode = waitFor(process, timeoutMillis);
    if (exitCode != null) {
      return exitCode;
    }
    LOG.warn("shell env loader is timed out");

    PlatformOperatingSystem os = Platform.current().os();

    // First, try to interrupt 'softly' (we know how to do it only on *nix)
    if (!os.isWindows()) {
      UnixProcessManager.sendSigIntToProcessTree(process);
      exitCode = waitFor(process, 1000L);
      if (exitCode != null) {
        return exitCode;
      }
      LOG.warn("failed to terminate shell env loader process gracefully, terminating forcibly");
    }

    if (os.isWindows()) {
      WinProcessManager.kill(process, true);
    }
    else {
      UnixProcessManager.sendSigKillToProcessTree(process);
    }
    exitCode = waitFor(process, 1000L);
    if (exitCode != null) {
      return exitCode;
    }
    LOG.warn("failed to kill shell env loader");
    return -1;
  }

  /**
   * @param timeoutMillis the time-out (in milliseconds) for {@code process} to terminate.
   * @return the exit code of the process if it has already terminated, or it has terminated within the timeout; or {@code null} otherwise
   */
  private static
  @Nullable
  Integer waitFor(@Nonnull Process process, final long timeoutMillis) {
    try {
      if (process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
        return process.exitValue();
      }
    }
    catch (InterruptedException e) {
      LOG.info("Interrupted while waiting for process", e);
    }
    return null;
  }

  private static void setCharsetVar(@Nonnull Map<String, String> env) {
    if (!isCharsetVarDefined(env)) {
      String value = setLocaleEnv(env, Charset.defaultCharset());
      LOG.info("LC_CTYPE=" + value);
    }
  }

  private static boolean checkIfLocaleAvailable(String candidateLanguageTerritory) {
    Locale[] available = Locale.getAvailableLocales();
    for (Locale l : available) {
      if (Objects.equals(l.toString(), candidateLanguageTerritory)) {
        return true;
      }
    }
    return false;
  }

  public static
  @Nonnull
  String setLocaleEnv(@Nonnull Map<String, String> env, @Nonnull Charset charset) {
    Locale locale = Locale.getDefault();
    String language = locale.getLanguage();
    String country = locale.getCountry();

    String languageTerritory = "en_US";
    if (!language.isEmpty() && !country.isEmpty()) {
      String languageTerritoryFromLocale = language + '_' + country;
      if (checkIfLocaleAvailable(languageTerritoryFromLocale)) {
        languageTerritory = languageTerritoryFromLocale;
      }
    }

    String result = languageTerritory + '.' + charset.name();
    env.put(LC_CTYPE, result);
    return result;
  }

  private static boolean isCharsetVarDefined(@Nonnull Map<String, String> env) {
    return !env.isEmpty() && (env.containsKey(LANG) || env.containsKey(LC_ALL) || env.containsKey(LC_CTYPE));
  }

  public static void inlineParentOccurrences(@Nonnull Map<String, String> envs) {
    inlineParentOccurrences(envs, getEnvironmentMap());
  }

  public static void inlineParentOccurrences(@Nonnull Map<String, String> envs, @Nonnull Map<String, String> parentEnv) {
    for (Map.Entry<String, String> entry : envs.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (value != null) {
        String parentVal = parentEnv.get(key);
        if (parentVal != null && containsEnvKeySubstitution(key, value)) {
          envs.put(key, value.replace("$" + key + "$", parentVal));
        }
      }
    }
  }

  public static boolean containsEnvKeySubstitution(final String envKey, final String val) {
    return ArrayUtil.find(val.split(File.pathSeparator), "$" + envKey + "$") != -1;
  }

  @TestOnly
  static Map<String, String> testLoader() throws IOException {
    return getShellEnv();
  }

  @TestOnly
  static Map<String, String> testParser(@Nonnull String lines) {
    try {
      return parseEnv(lines);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static final class StreamGobbler extends BaseOutputReader {
    private static final Options OPTIONS = new Options() {
      @Override
      public SleepingPolicy policy() {
        return SleepingPolicy.BLOCKING;
      }

      @Override
      public boolean splitToLines() {
        return false;
      }
    };

    private final StringBuffer myBuffer;

    StreamGobbler(@Nonnull InputStream stream) {
      super(stream, Charset.defaultCharset(), OPTIONS);
      myBuffer = new StringBuffer();
      startWithoutChangingThreadName();
    }

    @Override
    protected
    @Nonnull
    Future<?> executeOnPooledThread(@Nonnull Runnable runnable) {
      return ForkJoinPool.commonPool().submit(runnable);
    }

    @Override
    protected void onTextAvailable(@Nonnull String text) {
      myBuffer.append(text);
    }

    public
    @Nonnull
    String getText() {
      return myBuffer.toString();
    }
  }
}