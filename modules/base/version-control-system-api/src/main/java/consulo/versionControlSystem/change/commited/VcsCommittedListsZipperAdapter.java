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
package consulo.versionControlSystem.change.commited;

import consulo.util.collection.MultiMap;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.RepositoryLocation;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.versionBrowser.CommittedChangeList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public abstract class VcsCommittedListsZipperAdapter implements VcsCommittedListsZipper {
  private final GroupCreator myGroupCreator;

  public interface GroupCreator {
    Object createKey(final RepositoryLocation location);
    RepositoryLocationGroup createGroup(final Object key, final Collection<RepositoryLocation> locations);
  }

  protected VcsCommittedListsZipperAdapter(final GroupCreator groupCreator) {
    myGroupCreator = groupCreator;
  }

  @Override
  public Pair<List<RepositoryLocationGroup>, List<RepositoryLocation>> groupLocations(final List<RepositoryLocation> in) {
    final List<RepositoryLocationGroup> groups = new ArrayList<RepositoryLocationGroup>();
    final List<RepositoryLocation> singles = new ArrayList<RepositoryLocation>();

    final MultiMap<Object, RepositoryLocation> map = new MultiMap<Object, RepositoryLocation>();

    for (RepositoryLocation location : in) {
      final Object key = myGroupCreator.createKey(location);
      map.putValue(key, location);
    }

    final Set<Object> keys = map.keySet();
    for (Object key : keys) {
      final Collection<RepositoryLocation> locations = map.get(key);
      if (locations.size() == 1) {
        singles.addAll(locations);
      } else {
        final RepositoryLocationGroup group = myGroupCreator.createGroup(key, locations);
        groups.add(group);
      }
    }

    return Pair.create(groups, singles);
  }

  @Override
  public CommittedChangeList zip(final RepositoryLocationGroup group, final List<CommittedChangeList> lists) {
    if (lists.size() == 1) {
      return lists.get(0);
    }
    final CommittedChangeList result = lists.get(0);
    for (int i = 1; i < lists.size(); i++) {
      final CommittedChangeList list = lists.get(i);
      for (Change change : list.getChanges()) {
        final Collection<Change> resultChanges = result.getChanges();
        if (! resultChanges.contains(change)) {
          resultChanges.add(change);
        }
      }
    }
    return result;
  }

  @Override
  public long getNumber(final CommittedChangeList list) {
    return list.getNumber();
  }
}
