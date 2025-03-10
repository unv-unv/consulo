/*
 * Copyright 2013-2023 consulo.io
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
package consulo.platform;

import consulo.platform.os.UnixOperationSystem;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * @author VISTALL
 * @since 25/04/2023
 */
public interface PlatformOperatingSystem {
    @Nonnull
    Collection<ProcessInfo> processes();

    boolean isWindows();

    boolean isMac();

    boolean isLinux();

    boolean isFreeBSD();

    boolean isUnix();

    @Deprecated
    default boolean isXWindow() {
        return this instanceof UnixOperationSystem linux && linux.isXWindow();
    }

    @Nonnull
    default LineSeparator lineSeparator() {
        return LineSeparator.LF;
    }

    @Nonnull
    String name();

    @Nonnull
    String version();

    @Nonnull
    String arch();

    @Nonnull
    Map<String, String> environmentVariables();

    @Nullable
    String getEnvironmentVariable(@Nonnull String key);

    @Nonnull
    String fileNamePrefix();

    @Nullable
    default String getEnvironmentVariable(@Nonnull String key, @Nonnull String defaultValue) {
        String environmentVariable = getEnvironmentVariable(key);
        return environmentVariable == null ? defaultValue : environmentVariable;
    }

    default boolean isEnabledTopMenu() {
        return false;
    }
}
