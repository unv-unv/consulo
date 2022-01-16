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
package com.intellij.lang;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.util.NotNullLazyValue;
import consulo.component.extension.KeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;

import javax.annotation.Nonnull;

/**
 * @author yole
 */
public class LanguageExtensionPoint<T> extends AbstractExtensionPointBean implements KeyedLazyInstance<T> {

  // these must be public for scrambling compatibility
  @Attribute("language")
  public String language;

  @Attribute("implementationClass")
  public String implementationClass;

  private final NotNullLazyValue<T> myHandler = new NotNullLazyValue<T>() {
    @Override
    @Nonnull
    protected T compute() {
      try {
        return instantiate(implementationClass, Application.get().getInjectingContainer());
      }
      catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    }
  };

  @Override
  public T getInstance() {
    return myHandler.getValue();
  }

  @Override
  public String getKey() {
    return language;
  }
}