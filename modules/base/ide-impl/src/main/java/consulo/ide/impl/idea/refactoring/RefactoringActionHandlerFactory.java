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
package consulo.ide.impl.idea.refactoring;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.Service;
import consulo.ide.ServiceManager;
import consulo.dataContext.DataContext;
import consulo.language.editor.refactoring.RefactoringActionHandler;
import consulo.language.psi.PsiElement;
import consulo.project.Project;

/**
 * Use this project component to create {@link RefactoringActionHandler}s for various
 * IntelliJ IDEA refactorings.
 * @author dsl
 */
@Service(ComponentScope.APPLICATION)
public abstract class RefactoringActionHandlerFactory {
  public static RefactoringActionHandlerFactory getInstance() {
    return ServiceManager.getService(RefactoringActionHandlerFactory.class);
  }


  /**
   * Creates handler for Safe Delete refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(Project, PsiElement[], DataContext)}
   * accepts a list of {@link PsiElement}s to delete.
   * @return
   */
  public abstract RefactoringActionHandler createSafeDeleteHandler();


  public abstract RefactoringActionHandler createMoveHandler();

  public abstract RefactoringActionHandler createRenameHandler();
}
