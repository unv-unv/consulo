// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.execution.internal;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import consulo.platform.Platform;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.util.concurrent.TimeUnit;

public final class LocalFileFinder {
  // if java.io.File.exists() takes more time than this timeout we assume that this is network drive and do not ping it any more
  private static final int FILE_EXISTS_MAX_TIMEOUT_MILLIS = 10;

  private static final Cache<Character, Boolean> windowsDrivesMap = CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).build();

  private LocalFileFinder() {
  }

  /**
   * Behavior is not predictable and result is not guaranteed:
   * 1) result of check cached, but cache entry invalidated on timeout (5 min after write), not on actual change of drive status.
   * 2) even if drive exists, it could be not used due to 10 ms threshold.
   * Method is not generic and is not suitable for all.
   */
  @Nullable
  public static VirtualFile findFile(@Nonnull String path) {
    if (windowsDriveExists(path)) {
      return LocalFileSystem.getInstance().findFileByPath(path);
    }
    return null;
  }

  public static boolean windowsDriveExists(@Nonnull String path) {
    if (!Platform.current().os().isWindows()) {
      return true;
    }

    if (!FileUtil.isWindowsAbsolutePath(path)) {
      return false;
    }

    final char driveLetter = Character.toUpperCase(path.charAt(0));
    final Boolean driveExists = windowsDrivesMap.getIfPresent(driveLetter);
    if (driveExists != null) {
      return driveExists;
    }
    else {
      final long t0 = System.currentTimeMillis();
      boolean exists = new File(driveLetter + ":" + File.separator).exists();
      if (System.currentTimeMillis() - t0 > FILE_EXISTS_MAX_TIMEOUT_MILLIS) {
        exists = false; // may be a slow network drive
      }

      windowsDrivesMap.put(driveLetter, exists);
      return exists;
    }
  }
}