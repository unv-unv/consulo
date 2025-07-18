/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package consulo.ide.impl.virtualFileSystem;

import consulo.annotation.component.ActionImpl;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.virtualFileSystem.ManagingFS;
import consulo.virtualFileSystem.internal.PersistentFS;
import jakarta.annotation.Nonnull;

/**
 * @author max
 */
@ActionImpl(id = "CheckVfsSanity")
public class CheckSanityAction extends AnAction {
    public CheckSanityAction() {
        super("Check VFS sanity");
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(@Nonnull AnActionEvent e) {
        PersistentFS fs = (PersistentFS) ManagingFS.getInstance();

        fs.checkSanity();
    }
}