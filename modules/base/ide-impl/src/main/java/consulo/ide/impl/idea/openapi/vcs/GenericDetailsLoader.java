/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package consulo.ide.impl.idea.openapi.vcs;

import consulo.application.ApplicationManager;
import consulo.disposer.Disposable;
import consulo.util.lang.Comparing;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.function.PairConsumer;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @author irengrig
 *         Date: 6/29/11
 *         Time: 11:38 PM
 */
public class GenericDetailsLoader<Id, Data> implements Details<Id,Data>, Disposable {
  private final Consumer<Id> myLoader;
  private final ValueConsumer<Id, Data> myValueConsumer;
  private final AtomicReference<Id> myCurrentlySelected;
  private boolean myIsDisposed;

  /**
   * @param loader - is called in AWT. Should call {@link #take} with data when ready. Also in AWT
   * @param valueConsumer - is called in AWT. passive, just benefits from details loading
   */
  public GenericDetailsLoader(final Consumer<Id> loader, final PairConsumer<Id, Data> valueConsumer) {
    myLoader = loader;
    myValueConsumer = new ValueConsumer<Id, Data>(valueConsumer);
    myCurrentlySelected = new AtomicReference<Id>(null);
  }

  @RequiredUIAccess
  public void updateSelection(@jakarta.annotation.Nullable final Id id, boolean force) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myIsDisposed) return;
    myValueConsumer.setId(id);

    final Id wasId = myCurrentlySelected.getAndSet(id);
    if (force || ! Comparing.equal(id, wasId)) {
      myLoader.accept(id);
    }
  }

  public void setCacheConsumer(final PairConsumer<Id, Data>  cacheConsumer) {
    myValueConsumer.setCacheConsumer(cacheConsumer);
  }

  @RequiredUIAccess
  @Override
  public void take(Id id, Data data) throws AlreadyDisposedException {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myIsDisposed) throw new AlreadyDisposedException();
    myValueConsumer.consume(id, data);
  }

  public void resetValueConsumer() {
    myValueConsumer.reset();
  }

  @Override
  public Id getCurrentlySelected() {
    return myCurrentlySelected.get();
  }

  @Override
  public void dispose() {
    myIsDisposed = true;
  }
}
