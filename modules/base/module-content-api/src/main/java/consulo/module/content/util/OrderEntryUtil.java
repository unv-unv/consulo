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
package consulo.module.content.util;

import consulo.content.OrderRootType;
import consulo.content.bundle.Sdk;
import consulo.content.library.Library;
import consulo.content.library.LibraryTable;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.module.content.layer.ModuleRootModel;
import consulo.module.content.layer.orderEntry.*;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.function.Predicate;

/**
 * @author cdr
 */
public class OrderEntryUtil {
  private OrderEntryUtil() {
  }

  @Nullable
  public static LibraryOrderEntry findLibraryOrderEntry(@Nonnull ModuleRootModel model, @Nullable Library library) {
    if (library == null) return null;
    for (OrderEntry orderEntry : model.getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry && library.equals(((LibraryOrderEntry)orderEntry).getLibrary())) {
        return (LibraryOrderEntry)orderEntry;
      }
    }

    return null;
  }

  @Nullable
  public static LibraryOrderEntry findLibraryOrderEntry(@Nonnull ModuleRootModel model, @Nonnull String libraryName) {
    for (OrderEntry orderEntry : model.getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry) {
        final String libName = ((LibraryOrderEntry)orderEntry).getLibraryName();
        if (libraryName.equals(libName)) {
          return (LibraryOrderEntry)orderEntry;
        }
      }
    }
    return null;
  }

  @Nullable
  public static ModuleOrderEntry findModuleOrderEntry(@Nonnull ModuleRootModel model, @Nullable Module module) {
    if (module == null) return null;

    for (OrderEntry orderEntry : model.getOrderEntries()) {
      if (orderEntry instanceof ModuleOrderEntry && module.equals(((ModuleOrderEntry)orderEntry).getModule())) {
        return (ModuleOrderEntry)orderEntry;
      }
    }
    return null;
  }

  @Nullable
  @Deprecated
  public static ModuleExtensionWithSdkOrderEntry findJdkOrderEntry(@Nonnull ModuleRootModel model, @Nullable Sdk sdk) {
    return findModuleExtensionWithSdkOrderEntry(model, sdk);
  }

  @Nullable
  public static ModuleExtensionWithSdkOrderEntry findModuleExtensionWithSdkOrderEntry(@Nonnull ModuleRootModel model, @Nullable Sdk sdk) {
    if (sdk == null) return null;

    for (OrderEntry orderEntry : model.getOrderEntries()) {
      if (orderEntry instanceof ModuleExtensionWithSdkOrderEntry && sdk.equals(((ModuleExtensionWithSdkOrderEntry)orderEntry).getSdk())) {
        return (ModuleExtensionWithSdkOrderEntry)orderEntry;
      }
    }
    return null;
  }

  public static boolean equals(OrderEntry orderEntry1, OrderEntry orderEntry2) {
    if (orderEntry1 instanceof ModuleExtensionWithSdkOrderEntry && orderEntry2 instanceof ModuleExtensionWithSdkOrderEntry) {
      final ModuleExtensionWithSdkOrderEntry sdkOrderEntry1 = (ModuleExtensionWithSdkOrderEntry)orderEntry1;
      final ModuleExtensionWithSdkOrderEntry sdkOrderEntry2 = (ModuleExtensionWithSdkOrderEntry)orderEntry2;
      return Comparing.equal(sdkOrderEntry1.getSdk(), sdkOrderEntry2.getSdk()) && Comparing.strEqual(sdkOrderEntry1.getSdkName(),
                                                                                                     sdkOrderEntry2.getSdkName());
    }
    if (orderEntry1 instanceof LibraryOrderEntry && orderEntry2 instanceof LibraryOrderEntry) {
      final LibraryOrderEntry jdkOrderEntry1 = (LibraryOrderEntry)orderEntry1;
      final LibraryOrderEntry jdkOrderEntry2 = (LibraryOrderEntry)orderEntry2;
      return Comparing.equal(jdkOrderEntry1.getLibrary(), jdkOrderEntry2.getLibrary());
    }
    if (orderEntry1 instanceof ModuleSourceOrderEntry && orderEntry2 instanceof ModuleSourceOrderEntry) {
      final ModuleSourceOrderEntry jdkOrderEntry1 = (ModuleSourceOrderEntry)orderEntry1;
      final ModuleSourceOrderEntry jdkOrderEntry2 = (ModuleSourceOrderEntry)orderEntry2;
      return Comparing.equal(jdkOrderEntry1.getOwnerModule(), jdkOrderEntry2.getOwnerModule());
    }
    if (orderEntry1 instanceof ModuleOrderEntry && orderEntry2 instanceof ModuleOrderEntry) {
      final ModuleOrderEntry jdkOrderEntry1 = (ModuleOrderEntry)orderEntry1;
      final ModuleOrderEntry jdkOrderEntry2 = (ModuleOrderEntry)orderEntry2;
      return Comparing.equal(jdkOrderEntry1.getModule(), jdkOrderEntry2.getModule());
    }
    return false;
  }

  public static boolean equals(Library library1, Library library2) {
    if (library1 == library2) return true;
    if (library1 == null || library2 == null) return false;

    final LibraryTable table = library1.getTable();
    if (table != null) {
      if (library2.getTable() != table) return false;
      final String name = library1.getName();
      return name != null && name.equals(library2.getName());
    }

    if (library2.getTable() != null) return false;

    for (OrderRootType type : OrderRootType.getAllTypes()) {
      if (!Comparing.equal(library1.getUrls(type), library2.getUrls(type))) {
        return false;
      }
    }
    return true;
  }

  public static void addLibraryToRoots(final LibraryOrderEntry libraryOrderEntry, final Module module) {
    Library library = libraryOrderEntry.getLibrary();
    if (library == null) return;
    addLibraryToRoots(module, library);
  }

  public static void addLibraryToRoots(@Nonnull Module module, @Nonnull Library library) {
    final ModuleRootManager manager = ModuleRootManager.getInstance(module);
    final ModifiableRootModel rootModel = manager.getModifiableModel();

    if (library.getTable() == null) {
      final Library jarLibrary = rootModel.getModuleLibraryTable().createLibrary();
      final Library.ModifiableModel libraryModel = jarLibrary.getModifiableModel();
      for (OrderRootType orderRootType : OrderRootType.getAllTypes()) {
        VirtualFile[] files = library.getFiles(orderRootType);
        for (VirtualFile jarFile : files) {
          libraryModel.addRoot(jarFile, orderRootType);
        }
      }
      libraryModel.commit();
    }
    else {
      rootModel.addLibraryEntry(library);
    }
    rootModel.commit();
  }

  public static void replaceLibrary(@Nonnull ModifiableRootModel model, @Nonnull Library oldLibrary, @Nonnull Library newLibrary) {
    OrderEntry[] entries = model.getOrderEntries();
    for (int i = 0; i < entries.length; i++) {
      OrderEntry orderEntry = entries[i];
      if (orderEntry instanceof LibraryOrderEntry && oldLibrary.equals(((LibraryOrderEntry)orderEntry).getLibrary())) {
        model.removeOrderEntry(orderEntry);
        final LibraryOrderEntry newEntry = model.addLibraryEntry(newLibrary);
        final OrderEntry[] newEntries = new OrderEntry[entries.length];
        System.arraycopy(entries, 0, newEntries, 0, i);
        newEntries[i] = newEntry;
        System.arraycopy(entries, i, newEntries, i + 1, entries.length - i - 1);
        model.rearrangeOrderEntries(newEntries);
        return;
      }
    }
  }

  public static <T extends OrderEntry> void processOrderEntries(@Nonnull Module module,
                                                                @Nonnull Class<T> orderEntryClass,
                                                                @Nonnull Predicate<T> processor) {
    OrderEntry[] orderEntries = ModuleRootManager.getInstance(module).getOrderEntries();
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntryClass.isInstance(orderEntry)) {
        if (!processor.test(orderEntryClass.cast(orderEntry))) {
          break;
        }
      }
    }
  }

}
