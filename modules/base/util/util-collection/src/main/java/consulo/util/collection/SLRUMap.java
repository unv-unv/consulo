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
package consulo.util.collection;

import consulo.util.collection.impl.map.LinkedHashMap;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author max
 */
public class SLRUMap<K,V> {
  protected final Map<K,V> myProtectedQueue;
  protected final Map<K,V> myProbationalQueue;

  private final int myProtectedQueueSize;
  private final int myProbationalQueueSize;

  private int probationalHits = 0;
  private int protectedHits = 0;
  private int misses = 0;
  private static final int FACTOR = Integer.getInteger("idea.slru.factor", 1);

  public SLRUMap(final int protectedQueueSize, final int probationalQueueSize) {
    this(protectedQueueSize, probationalQueueSize, HashingStrategy.canonical());
  }

  public SLRUMap(final int protectedQueueSize, final int probationalQueueSize, HashingStrategy<K> hashingStrategy) {
    myProtectedQueueSize = protectedQueueSize * FACTOR;
    myProbationalQueueSize = probationalQueueSize * FACTOR;

    myProtectedQueue = new LinkedHashMap<K, V>(10, 0.6f, hashingStrategy) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<K, V> eldest, K key, V value) {
        if (size() > myProtectedQueueSize) {
          myProbationalQueue.put(key, value);
          return true;
        }

        return false;
      }
    };

    myProbationalQueue = new LinkedHashMap<K, V>(10, 0.6f, hashingStrategy) {
      protected boolean removeEldestEntry(final Map.Entry<K, V> eldest, K key, V value) {
        if (size() > myProbationalQueueSize) {
          onDropFromCache(key, value);
          return true;
        }
        return false;
      }
    };
  }

  @Nullable
  public V get(K key) {
    V value = myProtectedQueue.get(key);
    if (value != null) {
      protectedHits++;
      return value;
    }

    value = myProbationalQueue.remove(key);
    if (value != null) {
      probationalHits++;
      putToProtectedQueue(key, value);
      return value;
    }

    misses++;
    return null;
  }

  protected void putToProtectedQueue(K key, V value) {
    myProtectedQueue.put(getStableKey(key), value);
  }

  public void put(K key, @Nonnull V value) {
    V oldValue = myProtectedQueue.remove(key);
    if (oldValue != null) {
      onDropFromCache(key, oldValue);
    }

    oldValue = myProbationalQueue.put(getStableKey(key), value);
    if (oldValue != null) {
      onDropFromCache(key, oldValue);
    }
  }

  protected void onDropFromCache(K key, V value) {}

  public boolean remove(K key) {
    V value = myProtectedQueue.remove(key);
    if (value != null) {
      onDropFromCache(key, value);
      return true;
    }

    value = myProbationalQueue.remove(key);
    if (value != null) {
      onDropFromCache(key, value);
      return true;
    }

    return false;
  }

  public void iterateKeys(final Consumer<K> keyConsumer) {
    for (K key : myProtectedQueue.keySet()) {
      keyConsumer.accept(key);
    }
    for (K key : myProbationalQueue.keySet()) {
      keyConsumer.accept(key);
    }
  }

  public Set<Map.Entry<K, V>> entrySet() {
    Set<Map.Entry<K, V>> set = new HashSet<Map.Entry<K,V>>(myProtectedQueue.entrySet());
    set.addAll(myProbationalQueue.entrySet());
    return set;
  }

  public void clear() {
    for (Map.Entry<K, V> entry : myProtectedQueue.entrySet()) {
      onDropFromCache(entry.getKey(), entry.getValue());
    }
    myProtectedQueue.clear();

    for (Map.Entry<K, V> entry : myProbationalQueue.entrySet()) {
      onDropFromCache(entry.getKey(), entry.getValue());
    }
    myProbationalQueue.clear();
  }

  protected K getStableKey(K key) {
    if (key instanceof ShareableKey) {
      return (K)((ShareableKey)key).getStableCopy();
    }

    return key;
  }
}
