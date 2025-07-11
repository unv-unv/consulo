/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package consulo.ide.impl.idea.vcsUtil;

/**
 * @author Irina.Chernushina
 * @since 2012-07-06
 */
public abstract class AbstractActionStateConsumer implements ActionStateConsumer {
  protected boolean myVisible;
  protected boolean myEnabled;

  protected AbstractActionStateConsumer() {
    myVisible = true;
    myEnabled = true;
  }

  @Override
  public void hide() {
    myVisible = false;
    myEnabled = false;
  }

  @Override
  public void disable() {
    myVisible = true;
    myEnabled = false;
  }

  @Override
  public void enable() {
    myVisible = true;
    myEnabled = true;
  }
}
