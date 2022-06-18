/*
 * Copyright 2013-2018 consulo.io
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
package consulo.desktop.awt.fileChooser.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.annotation.component.Orderable;
import consulo.component.ComponentManager;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserDialog;
import consulo.fileChooser.PathChooserDialog;
import consulo.fileChooser.provider.FileChooseDialogProvider;
import consulo.fileChooser.provider.FileOperateDialogProvider;
import consulo.project.Project;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;

/**
 * @author VISTALL
 * @since 2018-06-28
 */
@ExtensionImpl
@Orderable(id = FileOperateDialogProvider.APPLICATION_ID, order = "last")
public class DesktopFileChooseDialogProvider implements FileChooseDialogProvider {
  @Nonnull
  @Override
  public String getId() {
    return APPLICATION_ID;
  }

  @Nonnull
  @Override
  public String getName() {
    return "application";
  }

  @Nonnull
  @Override
  public FileChooserDialog createFileChooser(@Nonnull FileChooserDescriptor descriptor, @Nullable ComponentManager project, @Nullable Component parent) {
    if (parent != null) {
      return new FileChooserDialogImpl(descriptor, parent, (Project)project);
    }
    else {
      return new FileChooserDialogImpl(descriptor, (Project)project);
    }
  }

  @Nonnull
  @Override
  public PathChooserDialog createPathChooser(@Nonnull FileChooserDescriptor descriptor, @Nullable ComponentManager project, @Nullable Component parent) {
    if (parent != null) {
      return new FileChooserDialogImpl(descriptor, parent, (Project)project);
    }
    else {
      return new FileChooserDialogImpl(descriptor, (Project)project);
    }
  }
}
