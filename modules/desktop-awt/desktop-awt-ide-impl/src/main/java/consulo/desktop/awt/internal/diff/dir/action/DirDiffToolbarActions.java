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
import consulo.diff.dir.DirDiffModelHolder;
import consulo.ui.ex.action.*;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffToolbarActions extends ActionGroup {
    private final AnAction[] myActions;

    public DirDiffToolbarActions(DirDiffTableModel model, JComponent panel) {
        super("Directory Diff Actions", false);
        final List<AnAction> actions = new ArrayList<AnAction>(Arrays.asList(
            new RefreshDirDiffAction(model),
            AnSeparator.getInstance(),
            new EnableLeft(model),
            new EnableNotEqual(model),
            new EnableEqual(model),
            new EnableRight(model),
            AnSeparator.getInstance(),
            new ChangeCompareModeGroup(model),
            AnSeparator.getInstance()
        ));

        if (model.isOperationsEnabled()) {
            actions.add(new SynchronizeDiff(model, true));
            actions.add(new SynchronizeDiff(model, false));
        }

        for (AnAction action : model.getSettings().getExtraActions()) {
            actions.add(action);
        }

        for (AnAction action : actions) {
            if (action instanceof ShortcutProvider) {
                final ShortcutSet shortcut = ((ShortcutProvider) action).getShortcut();
                if (shortcut != null) {
                    action.registerCustomShortcutSet(shortcut, panel);
                }
            }
            if (action instanceof DirDiffModelHolder) {
                ((DirDiffModelHolder) action).setModel(model);
            }
        }
        myActions = actions.toArray(new AnAction[actions.size()]);
    }

    @Nonnull
    @Override
    public AnAction[] getChildren(@jakarta.annotation.Nullable AnActionEvent e) {
        return myActions;
    }
}
