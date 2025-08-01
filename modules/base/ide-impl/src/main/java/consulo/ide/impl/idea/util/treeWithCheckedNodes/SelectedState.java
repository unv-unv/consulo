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
package consulo.ide.impl.idea.util.treeWithCheckedNodes;

import consulo.application.util.function.Processor;
import consulo.util.collection.SLRUMap;

import jakarta.annotation.Nonnull;
import java.util.*;

/**
 * We can have only limited number of nodes selected in a tree;
 * and children of selected nodes (any level) cannot change their state
 * used together with {@link SelectionManager}
 *
 * @author irengrig
 * @since 2011-02-07
 */
public class SelectedState<T> {
  private final Set<T> mySelected;
  private final SLRUMap<T, TreeNodeState> myCache;
  private final int mySelectedSize;

  public SelectedState(final int selectedSize, final int queueSize) {
    mySelectedSize = selectedSize;
    assert queueSize > 0;
    mySelected = new HashSet<>();
    myCache = new SLRUMap<>(queueSize, queueSize);
  }

  @jakarta.annotation.Nullable
  public TreeNodeState get(final T node) {
    if (mySelected.contains(node)) return TreeNodeState.SELECTED;
    return myCache.get(node);
  }

  public void clear(final T node) {
    myCache.remove(node);
    mySelected.remove(node);
  }

  public void clearAllCachedMatching(final Processor<T> processor) {
    final Set<Map.Entry<T, TreeNodeState>> entries = myCache.entrySet();
    for (Map.Entry<T, TreeNodeState> entry: entries){
      if(processor.process(entry.getKey())) {
        myCache.remove(entry.getKey());
      }
    }
  }

  public void remove(final T node) {
    mySelected.remove(node);
    myCache.remove(node);
  }

  @Nonnull
  public TreeNodeState putAndPass(final T node, @Nonnull final TreeNodeState state) {
    if (TreeNodeState.SELECTED.equals(state)) {
      mySelected.add(node);
      myCache.remove(node);
    } else {
      mySelected.remove(node);
      myCache.put(node, state);
    }
    return state;
  }

  public boolean canAddSelection() {
    return mySelected.size() < mySelectedSize;
  }

  public Set<T> getSelected() {
    return Collections.unmodifiableSet(mySelected);
  }

  public void setSelection(Collection<T> files) {
    mySelected.clear();
    mySelected.addAll(files);
  }
}
