/*
 * Copyright 2013-2025 consulo.io
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
package consulo.credentialStorage.impl.internal.windows;

import consulo.annotation.component.ExtensionImpl;
import consulo.credentialStorage.CredentialStore;
import consulo.credentialStorage.impl.internal.NativeCredentialStoreWrapper;
import consulo.credentialStorage.internal.CredentialStoreFactory;
import consulo.platform.Platform;
import consulo.util.jna.JnaLoader;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 2025-04-04
 */
@ExtensionImpl
public class WindowsCredentialStoreFactory implements CredentialStoreFactory {
    @Nullable
    @Override
    public CredentialStore create(@Nonnull Platform platform) {
        if (platform.os().isWindows() && JnaLoader.isLoaded()) {
            return new NativeCredentialStoreWrapper(new WindowsCredentialStore());
        }
        return null;
    }
}
