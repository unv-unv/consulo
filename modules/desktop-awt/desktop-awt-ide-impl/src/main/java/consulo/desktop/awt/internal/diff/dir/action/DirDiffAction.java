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
package consulo.desktop.awt.internal.diff.dir.action;

import consulo.desktop.awt.internal.diff.dir.DirDiffTableModel;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.ShortcutProvider;
import consulo.ui.ex.action.ShortcutSet;
import consulo.ui.ex.action.ToggleAction;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public abstract class DirDiffAction extends ToggleAction implements ShortcutProvider {
    private final DirDiffTableModel myModel;

    protected DirDiffAction(DirDiffTableModel model) {
        myModel = model;
    }

    public DirDiffTableModel getModel() {
        return myModel;
    }

    protected abstract void updateState(boolean state);

    @Override
    public final void setSelected(AnActionEvent e, boolean state) {
        updateState(state);
        if (isReloadNeeded()) {
            if (isFullReload()) {
                getModel().reloadModel(true);
            }
            else {
                if (state) {
                    getModel().applySettings();
                }
                else {
                    getModel().applyRemove();
                }
            }
        }
        getModel().updateFromUI();
    }

    protected boolean isFullReload() {
        return false;
    }

    protected boolean isReloadNeeded() {
        return true;
    }

    @Override
    public void update(@Nonnull AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabled(!getModel().isUpdating());
    }

    @Nullable
    @Override
    public ShortcutSet getShortcut() {
        return getShortcutSet();
    }
}
