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
package consulo.ide.impl.idea.openapi.command.impl;

import consulo.dataContext.DataManager;
import consulo.application.Application;
import consulo.fileEditor.FileEditor;

import java.awt.*;

/**
 * @author max
 */
public class FocusBasedCurrentEditorProvider implements CurrentEditorProvider {
  @Override
  public FileEditor getCurrentEditor() {
    // TODO [VISTALL] not supported for now
    if (!Application.get().isSwingApplication()) {
      return null;
    }
    // [kirillk] this is a hack, since much of editor-related code was written long before
    // own focus managenent in the platform, so this method should be strictly synchronous
    final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    return DataManager.getInstance().getDataContext(owner).getData(FileEditor.KEY);
  }
}