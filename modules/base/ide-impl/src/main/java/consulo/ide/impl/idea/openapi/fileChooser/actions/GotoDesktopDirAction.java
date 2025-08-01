/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import consulo.application.util.NullableLazyValue;
import consulo.application.util.SystemInfo;
import consulo.ide.impl.idea.openapi.fileChooser.FileSystemTree;
import consulo.platform.Platform;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.local.ExecUtil;
import consulo.ui.ex.action.AnActionEvent;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;

public class GotoDesktopDirAction extends FileChooserAction {
  private final NullableLazyValue<VirtualFile> myDesktopDirectory = new NullableLazyValue<VirtualFile>() {
    @Nullable
    @Override
    protected VirtualFile compute() {
      return getDesktopDirectory();
    }
  };

  @Override
  protected void actionPerformed(final FileSystemTree tree, @Nonnull AnActionEvent e) {
    final VirtualFile dir = myDesktopDirectory.getValue();
    if (dir != null) {
      tree.select(dir, new Runnable() {
        @Override
        public void run() {
          tree.expand(dir, null);
        }
      });
    }
  }

  @Override
  protected void update(@Nonnull FileSystemTree tree, @Nonnull AnActionEvent e) {
    VirtualFile dir = myDesktopDirectory.getValue();
    e.getPresentation().setEnabled(dir != null && tree.isUnderRoots(dir));
  }

  @Nullable
  private static VirtualFile getDesktopDirectory() {
    File desktop = new File(Platform.current().user().homePath().toFile(), "Desktop");

    if (!desktop.isDirectory() && SystemInfo.hasXdgOpen()) {
      String path = ExecUtil.execAndReadLine(new GeneralCommandLine("xdg-user-dir", "DESKTOP"));
      if (path != null) {
        desktop = new File(path);
      }
    }

    return desktop.isDirectory() ? LocalFileSystem.getInstance().refreshAndFindFileByIoFile(desktop) : null;
  }
}
