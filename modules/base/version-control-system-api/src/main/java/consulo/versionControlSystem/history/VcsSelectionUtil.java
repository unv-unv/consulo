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
package consulo.versionControlSystem.history;

import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.codeEditor.SelectionModel;
import consulo.versionControlSystem.action.VcsContext;
import jakarta.annotation.Nullable;

/**
 * @author yole
 */
public class VcsSelectionUtil {
  private VcsSelectionUtil() {
  }

  @Nullable
  public static VcsSelection getSelection(VcsContext context) {
    VcsSelection selectionFromEditor = getSelectionFromEditor(context);
    if (selectionFromEditor != null) {
      return selectionFromEditor;
    }

    return Application.get().getExtensionPoint(VcsSelectionProvider.class).computeSafeIfAny(p -> p.getSelection(context));
  }

  @Nullable
  private static VcsSelection getSelectionFromEditor(VcsContext context) {
    Editor editor = context.getEditor();
    if (editor == null) return null;
    SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      return null;
    }
    return new VcsSelection(editor.getDocument(), selectionModel);
  }
}