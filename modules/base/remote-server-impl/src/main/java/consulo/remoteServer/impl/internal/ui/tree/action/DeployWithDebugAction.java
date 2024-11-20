// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.remoteServer.impl.internal.ui.tree.action;

import consulo.remoteServer.impl.internal.ui.tree.DeploymentNode;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

import static consulo.remoteServer.impl.internal.util.ApplicationActionUtils.getDeploymentTarget;

public class DeployWithDebugAction extends DumbAwareAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
        DeploymentNode node = getDeploymentTarget(e);
        boolean visible = node != null && node.isDeployActionVisible() && node.isDebugActionVisible();
        e.getPresentation().setVisible(visible);
        e.getPresentation().setEnabled(visible && node.isDeployActionEnabled());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        DeploymentNode node = getDeploymentTarget(e);
        if (node != null) {
            node.deployWithDebug();
        }
    }

//    @Override
//    public @NotNull ActionUpdateThread getActionUpdateThread() {
//        return ActionUpdateThread.BGT;
//    }
}
