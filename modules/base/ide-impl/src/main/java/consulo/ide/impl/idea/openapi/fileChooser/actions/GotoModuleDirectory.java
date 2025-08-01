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

package consulo.ide.impl.idea.openapi.fileChooser.actions;

import consulo.ui.ex.action.AnActionEvent;
import consulo.language.editor.LangDataKeys;
import consulo.ui.ex.action.Presentation;
import consulo.ide.impl.idea.openapi.fileChooser.FileSystemTree;
import consulo.module.Module;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public final class GotoModuleDirectory extends FileChooserAction {
  @Override
  protected void actionPerformed(final FileSystemTree fileSystemTree, @Nonnull AnActionEvent e) {
    final VirtualFile moduleDir = getModuleDir(e);
    if (moduleDir != null) {
      fileSystemTree.select(moduleDir, new Runnable() {
        @Override
        public void run() {
          fileSystemTree.expand(moduleDir, null);
        }
      });
    }
  }

  @Override
  protected void update(@Nonnull FileSystemTree fileSystemTree, @Nonnull AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final VirtualFile moduleDir = getModuleDir(e);
    presentation.setEnabled(moduleDir != null && fileSystemTree.isUnderRoots(moduleDir));
  }

  @Nullable
  private static VirtualFile getModuleDir(final AnActionEvent e) {
    Module module = e.getData(LangDataKeys.MODULE_CONTEXT);
    if (module == null) {
      module = e.getData(LangDataKeys.MODULE);
    }

    if (module != null && !module.isDisposed()) {
      return module.getModuleDir();
    }

    return null;
  }
}
