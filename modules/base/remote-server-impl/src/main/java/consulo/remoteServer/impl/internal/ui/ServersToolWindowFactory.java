/*
 * Copyright 2013-2020 consulo.io
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
package consulo.remoteServer.impl.internal.ui;

import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowFactory;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.content.ContentManager;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.ui.ex.toolWindow.ToolWindowAnchor;
import consulo.ui.image.Image;

import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 2020-05-30
 */
@ExtensionImpl
public class ServersToolWindowFactory implements ToolWindowFactory {
  @Nonnull
  @Override
  public String getId() {
    return "Application Servers";
  }

  @RequiredUIAccess
  @Override
  public void createToolWindowContent(@Nonnull Project project, @Nonnull ToolWindow toolWindow) {
    ContentManager contentManager = toolWindow.getContentManager();

    final ServersToolWindowContent serversContent = new ServersToolWindowContent(project);

    Content content = contentManager.getFactory().createContent(serversContent, null, false);
    content.setDisposer(serversContent);
    
    contentManager.addContent(content);
  }

  @Override
  public boolean shouldBeAvailable(@Nonnull Project project) {
    return ServersToolWindowManager.getInstance(project).isAvailable();
  }

  @Nonnull
  @Override
  public ToolWindowAnchor getAnchor() {
    return ToolWindowAnchor.BOTTOM;
  }

  @Nonnull
  @Override
  public Image getIcon() {
    return PlatformIconGroup.remoteserversServerstoolwindow();
  }

  @Nonnull
  @Override
  public LocalizeValue getDisplayName() {
    return LocalizeValue.localizeTODO("Application Servers");
  }
}
