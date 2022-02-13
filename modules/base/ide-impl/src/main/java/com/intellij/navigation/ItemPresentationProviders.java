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
package com.intellij.navigation;

import consulo.application.extension.ClassExtension;
import consulo.container.plugin.PluginIds;
import consulo.navigation.ItemPresentation;
import consulo.navigation.NavigationItem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author yole
 */
public class ItemPresentationProviders extends ClassExtension<ItemPresentationProvider> {
  public static final ItemPresentationProviders INSTANCE = new ItemPresentationProviders();

  private ItemPresentationProviders() {
    super(PluginIds.CONSULO_BASE + ".itemPresentationProvider");
  }

  @Nullable
  public static <T extends NavigationItem> ItemPresentationProvider<T> getItemPresentationProvider(@Nonnull T element) {
    @SuppressWarnings({"unchecked"}) final ItemPresentationProvider<T> provider = INSTANCE.forClass(element.getClass());
    return provider;
  }

  @Nullable
  public static ItemPresentation getItemPresentation(NavigationItem element) {
    final ItemPresentationProvider<NavigationItem> provider = getItemPresentationProvider(element);
    return provider != null ? provider.getPresentation(element) : null;
  }
}
