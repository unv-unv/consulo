/*
 * Copyright 2013-2023 consulo.io
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
package consulo.sandboxPlugin.lang.inspection;

import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.UnnamedConfigurable;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.localize.LocalizeValue;
import consulo.util.xml.serializer.XmlSerializerUtil;

import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 04/03/2023
 */
public class SandLocalInspectionState implements InspectionToolState<SandLocalInspectionState> {
  private boolean myCheckClass;

  public boolean isCheckClass() {
    return myCheckClass;
  }

  public void setCheckClass(boolean checkClass) {
    myCheckClass = checkClass;
  }

  @Nullable
  @Override
  public UnnamedConfigurable createConfigurable() {
    ConfigurableBuilder builder = ConfigurableBuilder.newBuilder();
    builder.checkBox(LocalizeValue.localizeTODO("Check Class"), this::isCheckClass, this::setCheckClass);
    return builder.buildUnnamed();
  }

  @Nullable
  @Override
  public SandLocalInspectionState getState() {
    return this;
  }

  @Override
  public void loadState(SandLocalInspectionState state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
