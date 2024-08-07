/*
 * Copyright 2013-2024 consulo.io
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
package consulo.desktop.awt.internal.versionControlSystem.patch;

import consulo.annotation.component.ServiceImpl;
import consulo.desktop.awt.internal.versionControlSystem.change.shelf.ApplyPatchDifferentiatedDialog;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.ide.impl.idea.openapi.vcs.changes.patch.ApplyPatchDifferentiatedDialogFactory;
import consulo.ide.impl.idea.openapi.vcs.changes.patch.ApplyPatchExecutor;
import consulo.ide.impl.idea.openapi.vcs.changes.patch.ApplyPatchMode;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * @author VISTALL
 * @since 06-Jul-24
 */
@Singleton
@ServiceImpl
public class ApplyPatchDifferentiatedDialogFactoryImpl implements ApplyPatchDifferentiatedDialogFactory {
  @Override
  public DialogWrapper create(Project project,
                              ApplyPatchExecutor callback,
                              List<ApplyPatchExecutor> executors,
                              @Nonnull ApplyPatchMode applyPatchMode,
                              @Nonnull VirtualFile patchFile) {
    return new ApplyPatchDifferentiatedDialog(project, callback, executors, applyPatchMode, patchFile);
  }

  @Override
  public FileChooserDescriptor createSelectPatchDescriptor() {
    return ApplyPatchDifferentiatedDialog.createSelectPatchDescriptor();
  }
}
