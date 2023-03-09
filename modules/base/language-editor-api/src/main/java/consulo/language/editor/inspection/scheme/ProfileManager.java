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
package consulo.language.editor.inspection.scheme;

import consulo.content.scope.NamedScopesHolder;

import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * User: anna
 * Date: 09-Dec-2005
 *
 * TODO [VISTALL] move all methods to InspectionProfileManager and drop
 */
@Deprecated
public interface ProfileManager {
  NamedScopesHolder getScopesManager();

  @Nonnull
  Collection<? extends Profile> getProfiles();

  Profile getProfile(@Nonnull String name, boolean returnRootProfileIfNamedIsAbsent);

  Profile getProfile(@Nonnull String name);

  void updateProfile(@Nonnull Profile profile);

  @Nonnull
  String[] getAvailableProfileNames();

  void deleteProfile(String name);
}
