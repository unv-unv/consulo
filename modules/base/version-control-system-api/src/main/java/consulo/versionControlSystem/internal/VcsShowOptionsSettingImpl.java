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
package consulo.versionControlSystem.internal;

import consulo.versionControlSystem.VcsConfiguration;
import consulo.versionControlSystem.VcsShowSettingOption;

public class VcsShowOptionsSettingImpl extends VcsAbstractSetting implements VcsShowSettingOption {
  private boolean myValue = true;


  public VcsShowOptionsSettingImpl(final String displayName) {
    super(displayName);
  }

  public VcsShowOptionsSettingImpl(VcsConfiguration.StandardOption option) {
    this(option.getId());
  }

  public boolean getValue(){
    return myValue;
  }

  public void setValue(final boolean value) {
    myValue = value;
  }

}
