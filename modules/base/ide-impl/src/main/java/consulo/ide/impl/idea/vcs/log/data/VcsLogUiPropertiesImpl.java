/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import consulo.component.persist.PersistentStateComponent;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.ide.impl.idea.vcs.log.graph.PermanentGraph;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * Stores UI configuration based on user activity and preferences.
 */
public abstract class VcsLogUiPropertiesImpl implements PersistentStateComponent<VcsLogUiPropertiesImpl.State>, MainVcsLogUiProperties {
  private static final int RECENTLY_FILTERED_VALUES_LIMIT = 10;
  private static final Set<VcsLogUiProperty> SUPPORTED_PROPERTIES = ContainerUtil.newHashSet(
    MainVcsLogUiProperties.SHOW_DETAILS,
    MainVcsLogUiProperties.SHOW_LONG_EDGES,
    MainVcsLogUiProperties.BEK_SORT_TYPE,
    MainVcsLogUiProperties.SHOW_ROOT_NAMES,
    MainVcsLogUiProperties.COMPACT_REFERENCES_VIEW,
    MainVcsLogUiProperties.SHOW_TAG_NAMES,
    MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE,
    MainVcsLogUiProperties.TEXT_FILTER_REGEX
  );
  private final Set<VcsLogUiPropertiesListener> myListeners = new LinkedHashSet<>();

  public static class State {
    public boolean SHOW_DETAILS_IN_CHANGES = true;
    public boolean LONG_EDGES_VISIBLE = false;
    public int BEK_SORT_TYPE = 0;
    public boolean SHOW_ROOT_NAMES = false;
    public Deque<UserGroup> RECENTLY_FILTERED_USER_GROUPS = new ArrayDeque<>();
    public Deque<UserGroup> RECENTLY_FILTERED_BRANCH_GROUPS = new ArrayDeque<>();
    public Map<String, Boolean> HIGHLIGHTERS = new TreeMap<>();
    public Map<String, List<String>> FILTERS = new TreeMap<>();
    public boolean COMPACT_REFERENCES_VIEW = true;
    public boolean SHOW_TAG_NAMES = false;
    public TextFilterSettings TEXT_FILTER_SETTINGS = new TextFilterSettings();
  }

  @Nonnull
  @Override
  public abstract State getState();

  @SuppressWarnings("unchecked")
  @Nonnull
  @Override
  public <T> T get(@Nonnull VcsLogUiProperty<T> property) {
    if (SHOW_DETAILS.equals(property)) {
      return (T)Boolean.valueOf(getState().SHOW_DETAILS_IN_CHANGES);
    }
    else if (SHOW_LONG_EDGES.equals(property)) {
      return (T)Boolean.valueOf(getState().LONG_EDGES_VISIBLE);
    }
    else if (SHOW_ROOT_NAMES.equals(property)) {
      return (T)Boolean.valueOf(getState().SHOW_ROOT_NAMES);
    }
    else if (COMPACT_REFERENCES_VIEW.equals(property)) {
      return (T)Boolean.valueOf(getState().COMPACT_REFERENCES_VIEW);
    }
    else if (SHOW_TAG_NAMES.equals(property)) {
      return (T)Boolean.valueOf(getState().SHOW_TAG_NAMES);
    }
    else if (BEK_SORT_TYPE.equals(property)) {
      return (T)PermanentGraph.SortType.values()[getState().BEK_SORT_TYPE];
    }
    else if (TEXT_FILTER_MATCH_CASE.equals(property)) {
      return (T)Boolean.valueOf(getTextFilterSettings().MATCH_CASE);
    }
    else if (TEXT_FILTER_REGEX.equals(property)) {
      return (T)Boolean.valueOf(getTextFilterSettings().REGEX);
    }
    else if (property instanceof VcsLogHighlighterProperty vcsLogHighlighterProperty) {
      Boolean result = getState().HIGHLIGHTERS.get(vcsLogHighlighterProperty.getId());
      if (result == null) return (T)Boolean.TRUE;
      return (T)result;
    }
    throw new UnsupportedOperationException("Property " + property + " does not exist");
  }

  @Override
  public <T> void set(@Nonnull VcsLogUiProperty<T> property, @Nonnull T value) {
    if (SHOW_DETAILS.equals(property)) {
      getState().SHOW_DETAILS_IN_CHANGES = (Boolean)value;
    }
    else if (SHOW_LONG_EDGES.equals(property)) {
      getState().LONG_EDGES_VISIBLE = (Boolean)value;
    }
    else if (SHOW_ROOT_NAMES.equals(property)) {
      getState().SHOW_ROOT_NAMES = (Boolean)value;
    }
    else if (COMPACT_REFERENCES_VIEW.equals(property)) {
      getState().COMPACT_REFERENCES_VIEW = (Boolean)value;
    }
    else if (SHOW_TAG_NAMES.equals(property)) {
      getState().SHOW_TAG_NAMES = (Boolean)value;
    }
    else if (BEK_SORT_TYPE.equals(property)) {
      getState().BEK_SORT_TYPE = ((PermanentGraph.SortType)value).ordinal();
    }
    else if (TEXT_FILTER_REGEX.equals(property)) {
      getTextFilterSettings().REGEX = (Boolean)value;
    }
    else if (TEXT_FILTER_MATCH_CASE.equals(property)) {
      getTextFilterSettings().MATCH_CASE = (Boolean)value;
    }
    else if (property instanceof VcsLogHighlighterProperty vcsLogHighlighterProperty) {
      getState().HIGHLIGHTERS.put(vcsLogHighlighterProperty.getId(), (Boolean)value);
    }
    else {
      throw new UnsupportedOperationException("Property " + property + " does not exist");
    }
    myListeners.forEach(l -> l.onPropertyChanged(property));
  }

  @Override
  public <T> boolean exists(@Nonnull VcsLogUiProperty<T> property) {
    return SUPPORTED_PROPERTIES.contains(property) || property instanceof VcsLogHighlighterProperty;
  }

  @Nonnull
  private TextFilterSettings getTextFilterSettings() {
    TextFilterSettings settings = getState().TEXT_FILTER_SETTINGS;
    if (settings == null) {
      settings = new TextFilterSettings();
      getState().TEXT_FILTER_SETTINGS = settings;
    }
    return settings;
  }

  @Override
  public void addRecentlyFilteredUserGroup(@Nonnull List<String> usersInGroup) {
    addRecentGroup(usersInGroup, getState().RECENTLY_FILTERED_USER_GROUPS);
  }

  @Override
  public void addRecentlyFilteredBranchGroup(@Nonnull List<String> valuesInGroup) {
    addRecentGroup(valuesInGroup, getState().RECENTLY_FILTERED_BRANCH_GROUPS);
  }

  private static void addRecentGroup(@Nonnull List<String> valuesInGroup, @Nonnull Deque<UserGroup> stateField) {
    UserGroup group = new UserGroup();
    group.users = valuesInGroup;
    if (stateField.contains(group)) {
      return;
    }
    stateField.addFirst(group);
    if (stateField.size() > RECENTLY_FILTERED_VALUES_LIMIT) {
      stateField.removeLast();
    }
  }

  @Override
  @Nonnull
  public List<List<String>> getRecentlyFilteredUserGroups() {
    return getRecentGroup(getState().RECENTLY_FILTERED_USER_GROUPS);
  }

  @Override
  @Nonnull
  public List<List<String>> getRecentlyFilteredBranchGroups() {
    return getRecentGroup(getState().RECENTLY_FILTERED_BRANCH_GROUPS);
  }

  @Nonnull
  private static List<List<String>> getRecentGroup(Deque<UserGroup> stateField) {
    return ContainerUtil.map2List(stateField, group -> group.users);
  }

  @Override
  public void saveFilterValues(@Nonnull String filterName, @Nullable List<String> values) {
    if (values != null) {
      getState().FILTERS.put(filterName, values);
    }
    else {
      getState().FILTERS.remove(filterName);
    }
  }

  @Nullable
  @Override
  public List<String> getFilterValues(@Nonnull String filterName) {
    return getState().FILTERS.get(filterName);
  }

  @Override
  public void addChangeListener(@Nonnull VcsLogUiPropertiesListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeChangeListener(@Nonnull VcsLogUiPropertiesListener listener) {
    myListeners.remove(listener);
  }

  public static class UserGroup {
    public List<String> users = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      UserGroup group = (UserGroup)o;
      return users.equals(group.users);
    }

    @Override
    public int hashCode() {
      return users.hashCode();
    }
  }

  private static class TextFilterSettings {
    public boolean REGEX = false;
    public boolean MATCH_CASE = false;
  }

  public abstract static class MainVcsLogUiPropertiesListener implements VcsLogUiPropertiesListener {
    public abstract void onShowDetailsChanged();

    public abstract void onShowLongEdgesChanged();

    public abstract void onBekChanged();

    public abstract void onShowRootNamesChanged();

    public abstract void onCompactReferencesViewChanged();

    public abstract void onShowTagNamesChanged();

    public abstract void onTextFilterSettingsChanged();

    public abstract void onHighlighterChanged();

    @Override
    public <T> void onPropertyChanged(@Nonnull VcsLogUiProperty<T> property) {
      if (SHOW_DETAILS.equals(property)) {
        onShowDetailsChanged();
      }
      else if (SHOW_LONG_EDGES.equals(property)) {
        onShowLongEdgesChanged();
      }
      else if (SHOW_ROOT_NAMES.equals(property)) {
        onShowRootNamesChanged();
      }
      else if (COMPACT_REFERENCES_VIEW.equals(property)) {
        onCompactReferencesViewChanged();
      }
      else if (SHOW_TAG_NAMES.equals(property)) {
        onShowTagNamesChanged();
      }
      else if (BEK_SORT_TYPE.equals(property)) {
        onBekChanged();
      }
      else if (TEXT_FILTER_REGEX.equals(property) || TEXT_FILTER_MATCH_CASE.equals(property)) {
        onTextFilterSettingsChanged();
      }
      else if (property instanceof VcsLogHighlighterProperty) {
        onHighlighterChanged();
      }
      else {
        throw new UnsupportedOperationException("Property " + property + " does not exist");
      }
    }
  }
}
