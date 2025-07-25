/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package consulo.ide.impl.idea.openapi.editor.actions;

public class DeleteToWordStartAction extends TextComponentEditorAction {
    public DeleteToWordStartAction() {
        super(new DeleteToWordBoundaryHandler(true, false));
    }
}
