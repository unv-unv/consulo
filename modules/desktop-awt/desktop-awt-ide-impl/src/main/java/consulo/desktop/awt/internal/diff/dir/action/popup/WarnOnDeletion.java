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
package consulo.desktop.awt.internal.diff.dir.action.popup;

import consulo.ide.impl.idea.ide.util.PropertiesComponent;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.ToggleAction;
import consulo.application.dumb.DumbAware;

/**
 * @author Konstantin Bulenkov
 */
public class WarnOnDeletion extends ToggleAction implements DumbAware {
  private static final String PROPERTY_NAME = "dir.diff.do.not.show.warnings.when.deleting";

  @Override
  public boolean isSelected(AnActionEvent e) {
    return isWarnWhenDeleteItems();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    setWarnWhenDeleteItems(state);
  }

  public static boolean isWarnWhenDeleteItems() {
    return !PropertiesComponent.getInstance().isTrueValue(PROPERTY_NAME);
  }

  public static void setWarnWhenDeleteItems(boolean warn) {
    PropertiesComponent.getInstance().setValue(PROPERTY_NAME, Boolean.valueOf(!warn).toString());
  }
}
