/*
 * Copyright 2013-2019 consulo.io
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
package consulo.ui.ex.awt.internal;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.project.ui.wm.IdeFrame;
import consulo.ui.ex.awt.FrameWrapper;
import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;

/**
* @author VISTALL
* @since 2019-02-15
 *
 * Hack for extract desktop dep to desktop module
 * TODO [VISTALL] drop this class when FrameWrapper removed
*/
@ServiceAPI(ComponentScope.APPLICATION)
public interface FrameWrapperPeerFactory {
  JFrame createJFrame(FrameWrapper owner, IdeFrame parent);

  JDialog createJDialog(FrameWrapper owner, IdeFrame parent);

  void updateWindowIcon(@Nonnull Window window, boolean dark);
}
