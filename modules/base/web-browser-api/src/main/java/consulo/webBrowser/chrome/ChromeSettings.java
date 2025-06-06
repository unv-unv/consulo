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
package consulo.webBrowser.chrome;

import consulo.process.cmd.ParametersListUtil;
import consulo.util.io.FileUtil;
import consulo.util.io.PathUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.annotation.Tag;
import consulo.webBrowser.BrowserSpecificSettings;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;

public final class ChromeSettings extends BrowserSpecificSettings {
    public static final String USER_DATA_DIR_ARG = "--user-data-dir=";
    private @Nullable
    String myCommandLineOptions;
    private @Nullable
    String myUserDataDirectoryPath;
    private boolean myUseCustomProfile;

    public ChromeSettings() {
    }

    @Nullable
    @Tag("user-data-dir")
    public String getUserDataDirectoryPath() {
        return myUserDataDirectoryPath;
    }

    @Tag("use-custom-profile")
    public boolean isUseCustomProfile() {
        return myUseCustomProfile;
    }

    @Nullable
    @Tag("command-line-options")
    public String getCommandLineOptions() {
        return myCommandLineOptions;
    }

    public void setCommandLineOptions(@Nullable String value) {
        myCommandLineOptions = StringUtil.nullize(value);
    }

    public void setUserDataDirectoryPath(@Nullable String value) {
        myUserDataDirectoryPath = PathUtil.toSystemIndependentName(StringUtil.nullize(value));
    }

    public void setUseCustomProfile(boolean useCustomProfile) {
        myUseCustomProfile = useCustomProfile;
    }

    @Nonnull
    @Override
    public List<String> getAdditionalParameters() {
        if (myCommandLineOptions == null) {
            if (myUseCustomProfile && myUserDataDirectoryPath != null) {
                return Collections.singletonList(USER_DATA_DIR_ARG + FileUtil.toSystemDependentName(myUserDataDirectoryPath));
            }
            else {
                return Collections.emptyList();
            }
        }

        List<String> cliOptions = ParametersListUtil.parse(myCommandLineOptions);
        if (myUseCustomProfile && myUserDataDirectoryPath != null) {
            cliOptions.add(USER_DATA_DIR_ARG + FileUtil.toSystemDependentName(myUserDataDirectoryPath));
        }
        return cliOptions;
    }

    @Nonnull
    @Override
    public ChromeSettingsConfigurable createConfigurable() {
        return new ChromeSettingsConfigurable(this);
    }

    @Override
    @SuppressWarnings("EqualsHashCode")
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ChromeSettings settings = (ChromeSettings)o;
        return myUseCustomProfile == settings.myUseCustomProfile
            && Comparing.equal(myCommandLineOptions, settings.myCommandLineOptions)
            && (!myUseCustomProfile || Comparing.equal(myUserDataDirectoryPath, settings.myUserDataDirectoryPath));
    }
}
