/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package consulo.remoteServer.impl.internal.ui.tree.action;

import consulo.remoteServer.impl.internal.ui.tree.action.ServersTreeActionBase;
import consulo.ui.ex.action.AnActionEvent;
import consulo.remoteServer.impl.internal.ui.ServersToolWindowContent;
import consulo.remoteServer.impl.internal.ui.tree.ServerNode;
import consulo.ui.image.Image;

import jakarta.annotation.Nonnull;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: michael.golubev
 */
public abstract class SingleServerActionBase extends ServersTreeActionBase {
  protected SingleServerActionBase(String text, String description, Image icon) {
    super(text, description, icon);
  }

  @Override
  protected void doActionPerformed(@Nonnull ServersToolWindowContent content) {
    doActionPerformed(content, content.getSelectedServerNodes().iterator().next());
  }

  @Override
  protected boolean isEnabled(@Nonnull ServersToolWindowContent content, AnActionEvent e) {
    Set<ServerNode> serverNodes = content.getSelectedServerNodes();
    return content.getBuilder().getSelectedElements().size() == serverNodes.size() && serverNodes.size() == 1 &&
           isEnabledForServer(serverNodes.iterator().next());
  }

  protected abstract boolean isEnabledForServer(ServerNode serverNode);

  protected abstract void doActionPerformed(@Nonnull ServersToolWindowContent content, @Nonnull ServerNode server);
}
