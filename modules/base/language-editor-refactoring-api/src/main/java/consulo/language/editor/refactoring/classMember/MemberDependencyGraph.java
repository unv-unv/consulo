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
package consulo.language.editor.refactoring.classMember;

import consulo.language.psi.PsiElement;

import java.util.Set;

/**
 * @author dsl
 * @since 2002-07-08
 */
public interface MemberDependencyGraph<T extends PsiElement, M extends MemberInfoBase<T>> {
  /**
   * Call this to notify that a new member info have been added
   * or a state of some memberInfo have been changed.
   * @param memberInfo
   */
  void memberChanged(M memberInfo);

  /**
   * Returns class members that are dependent on checked MemberInfos.
   * @return set of PsiMembers
   */
  Set<? extends T> getDependent();

  /**
   * Returns PsiMembers of checked MemberInfos that member depends on.
   * member should belong to getDependent()
   * @param member
   * @return set of PsiMembers
   */
  Set<? extends T> getDependenciesOf(T member);
}
