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

package consulo.ide.impl.idea.execution.impl;

import consulo.ide.impl.idea.openapi.util.text.StringUtil;
import consulo.language.psi.PsiManager;
import consulo.logging.Logger;
import consulo.project.Project;
import org.jetbrains.annotations.NonNls;

public class DisposedPsiManagerCheck {
  private static final Logger LOG = Logger.getInstance(DisposedPsiManagerCheck.class);
  private final Throwable myAllocationPlace;
  private final Project myProject;

  public DisposedPsiManagerCheck(final Project project) {
    myProject = project;
    myAllocationPlace = new Throwable();
  }

  public void performCheck() {
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    if (psiManager == null)
      log("Is null");
    else if (psiManager.isDisposed())
      log("Disposed");
  }

  private void log(@NonNls final String message) {
    LOG.error(message + "\n" + StringUtil.getThrowableText(myAllocationPlace));
  }
}
