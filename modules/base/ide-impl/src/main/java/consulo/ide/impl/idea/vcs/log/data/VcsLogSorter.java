/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package consulo.ide.impl.idea.vcs.log.data;

import consulo.versionControlSystem.log.TimedVcsCommit;
import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class VcsLogSorter {

  @Nonnull
  public static <Commit extends TimedVcsCommit> List<Commit> sortByDateTopoOrder(@Nonnull Collection<Commit> commits) {
    return new VcsLogJoiner.NewCommitIntegrator<>(new ArrayList<>(), commits).getResultList();
  }
}
