/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package consulo.ide.impl.idea.openapi.roots.ui.configuration.libraryEditor;

import consulo.content.OrderRootType;
import consulo.content.internal.LibraryEx;
import consulo.content.internal.LibraryKindRegistry;
import consulo.content.library.*;
import consulo.disposer.Disposable;
import consulo.content.base.BinariesOrderRootType;
import consulo.disposer.Disposer;
import consulo.ide.setting.module.event.LibraryEditorListener;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.Collection;

public class ExistingLibraryEditor extends LibraryEditorBase implements Disposable {
  private final LibraryEx myLibrary;
  private final LibraryEditorListener myListener;
  private String myLibraryName = null;
  private LibraryProperties myLibraryProperties;
  private LibraryProperties myDetectedLibraryProperties;
  private LibraryEx.ModifiableModelEx myModel = null;
  private LibraryType<?> myDetectedType;
  private boolean myDetectedTypeComputed;

  public ExistingLibraryEditor(@Nonnull Library library, @Nullable LibraryEditorListener listener) {
    myLibrary = (LibraryEx)library;
    myListener = listener;
  }

  public Library getLibrary() {
    return myLibrary;
  }

  @Override
  public String getName() {
    if (myLibraryName != null) {
      return myLibraryName;
    }
    return myLibrary.getName();
  }

  @Override
  public LibraryType<?> getType() {
    final LibraryKind kind = myLibrary.getKind();
    if (kind != null) {
      return LibraryKindRegistry.getInstance().findLibraryTypeByKindId(kind.getKindId());
    }
    return detectType();
  }

  @Override
  public void setType(@Nonnull LibraryType<?> type) {
    getModel().setKind(type.getKind());
  }

  private LibraryType detectType() {
    if (!myDetectedTypeComputed) {
      final Pair<LibraryType<?>,LibraryProperties<?>> pair = LibraryDetectionManager.getInstance().detectType(Arrays.asList(getFiles(
              BinariesOrderRootType.getInstance())));
      if (pair != null) {
        myDetectedType = pair.getFirst();
        myDetectedLibraryProperties = pair.getSecond();
      }
      myDetectedTypeComputed = true;
    }
    return myDetectedType;
  }

  @Override
  public LibraryProperties getProperties() {
    final LibraryType type = getType();
    if (type == null) return null;

    if (myDetectedType != null) {
      return myDetectedLibraryProperties;
    }

    if (myLibraryProperties == null) {
      myLibraryProperties = type.getKind().createDefaultProperties();
      //noinspection unchecked
      myLibraryProperties.loadState(getOriginalProperties().getState());
    }
    return myLibraryProperties;
  }

  @Override
  public void setProperties(LibraryProperties properties) {
    myLibraryProperties = properties;
  }

  private LibraryProperties getOriginalProperties() {
    return myLibrary.getProperties();
  }

  @Override
  public void dispose() {
    if (myModel != null) {
      // dispose if wasn't committed
      Disposer.dispose(myModel);
    }
  }

  @Override
  public String[] getUrls(OrderRootType rootType) {
    if (myModel != null) {
      return myModel.getUrls(rootType);
    }
    return myLibrary.getUrls(rootType);
  }

  @Override
  public VirtualFile[] getFiles(OrderRootType rootType) {
    if (myModel != null) {
      return myModel.getFiles(rootType);
    }
    return myLibrary.getFiles(rootType);
  }

  @Override
  public String[] getExcludedRootUrls() {
    if (myModel != null) {
      return myModel.getExcludedRootUrls();
    }
    return myLibrary.getExcludedRootUrls();
  }

  @Override
  public void setName(String name) {
    String oldName = getModel().getName();
    myLibraryName = name;
    getModel().setName(name);
    if (myListener != null) {
      myListener.libraryRenamed(myLibrary, oldName, name);
    }
  }

  @Override
  public void addRoot(VirtualFile file, OrderRootType rootType) {
    getModel().addRoot(file, rootType);
  }

  @Override
  public void addRoot(String url, OrderRootType rootType) {
    getModel().addRoot(url, rootType);
  }

  @Override
  public void addExcludedRoot(@Nonnull String url) {
    getModel().addExcludedRoot(url);
  }

  @Override
  public void addJarDirectory(VirtualFile file, boolean recursive, OrderRootType rootType) {
    getModel().addJarDirectory(file, recursive, rootType);
  }

  @Override
  public void addJarDirectory(String url, boolean recursive, OrderRootType rootType) {
    getModel().addJarDirectory(url, recursive, rootType);
  }

  @Override
  public void removeRoot(String url, OrderRootType rootType) {
    boolean removed;
    do {
      removed = getModel().removeRoot(url, rootType);
    }
    while (removed);
  }

  @Override
  public void removeExcludedRoot(@Nonnull String url) {
    getModel().removeExcludedRoot(url);
  }

  public void commit() {
    if (myModel != null) {
      if (myLibraryProperties != null) {
        myModel.setProperties(myLibraryProperties);
      }
      myModel.commit();
      myModel = null;
      myLibraryName = null;
      myLibraryProperties = null;
    }
  }

  public LibraryEx.ModifiableModelEx getModel() {
    if (myModel == null) {
      myModel = myLibrary.getModifiableModel();
    }
    return myModel;
  }

  @Override
  public boolean hasChanges() {
    if (myModel != null && myModel.isChanged()) {
      return true;
    }
    return myLibraryProperties != null && !myLibraryProperties.equals(getOriginalProperties());
  }

  @Override
  public boolean isJarDirectory(String url, OrderRootType rootType) {
    if (myModel != null) {
      return myModel.isJarDirectory(url, rootType);
    }
    return myLibrary.isJarDirectory(url, rootType);
  }

  @Override
  public boolean isValid(final String url, final OrderRootType orderRootType) {
    if (myModel != null) {
      return myModel.isValid(url, orderRootType);
    }
    return myLibrary.isValid(url, orderRootType);
  }

  @Override
  public Collection<OrderRootType> getOrderRootTypes() {
    return OrderRootType.getAllTypes();
  }
}
