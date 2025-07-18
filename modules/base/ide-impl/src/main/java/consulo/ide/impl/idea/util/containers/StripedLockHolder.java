/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package consulo.ide.impl.idea.util.containers;

import jakarta.annotation.Nonnull;

import java.lang.reflect.Array;

/**
 * @author cdr
 */
public abstract class StripedLockHolder<T> {
  private static final int NUM_LOCKS = 256;
  protected final T[] ourLocks;
  private int ourLockAllocationCounter = 0;

  @SuppressWarnings("unchecked")
  protected StripedLockHolder(@Nonnull Class<T> aClass) {
    ourLocks = (T[])Array.newInstance(aClass, NUM_LOCKS);
    for (int i = 0; i < ourLocks.length; i++) {
      ourLocks[i] = create();
    }
  }

  @Nonnull
  protected abstract T create();

  @Nonnull
  public T allocateLock() {
    return ourLocks[allocateLockIndex()];
  }

  public int allocateLockIndex() {
    return ourLockAllocationCounter = (ourLockAllocationCounter + 1) % NUM_LOCKS;
  }
}
