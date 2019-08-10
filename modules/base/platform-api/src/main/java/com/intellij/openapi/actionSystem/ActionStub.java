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
package com.intellij.openapi.actionSystem;

import consulo.logging.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.util.ReflectionUtil;
import consulo.ui.RequiredUIAccess;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The main (and single) purpose of this class is provide lazy initialization
 * of the actions. ClassLoader eats a lot of time on startup to load the actions' classes.
 *
 * @author Vladimir Kondratyev
 */
public class ActionStub extends AnAction {
  private static final Logger LOG = Logger.getInstance(ActionStub.class);

  private final String myClassName;
  private final String myId;
  private final String myText;
  private final ClassLoader myLoader;
  private final PluginId myPluginId;
  private final String myIconPath;

  private NullableLazyValue<Class> myClassValue;

  public ActionStub(@Nonnull String actionClass, @Nonnull String id, @Nonnull String text, ClassLoader loader, PluginId pluginId, String iconPath) {
    myLoader = loader;
    myClassName = actionClass;
    LOG.assertTrue(id.length() > 0);
    myId = id;
    myText = text;
    myPluginId = pluginId;
    myIconPath = iconPath;

    myClassValue = NullableLazyValue.of(() -> ReflectionUtil.findClassOrNull(actionClass, loader));
  }

  @Nullable
  public Class resolveClass() {
    return myClassValue.getValue();
  }

  public String getClassName() {
    return myClassName;
  }

  public String getId() {
    return myId;
  }

  public String getText() {
    return myText;
  }

  public ClassLoader getLoader() {
    return myLoader;
  }

  public PluginId getPluginId() {
    return myPluginId;
  }

  public String getIconPath() {
    return myIconPath;
  }

  @RequiredUIAccess
  @Override
  public void actionPerformed(@Nonnull AnActionEvent e) {
    throw new UnsupportedOperationException();
  }

  /**
   * Copies template presentation and shortcuts set to <code>targetAction</code>.
   *
   * @param targetAction cannot be <code>null</code>
   */
  public final void initAction(@Nonnull AnAction targetAction) {
    Presentation sourcePresentation = getTemplatePresentation();
    Presentation targetPresentation = targetAction.getTemplatePresentation();
    if (targetPresentation.getIcon() == null && sourcePresentation.getIcon() != null) {
      targetPresentation.setIcon(sourcePresentation.getIcon());
    }
    if (targetPresentation.getText() == null && sourcePresentation.getText() != null) {
      targetPresentation.setText(sourcePresentation.getText());
    }
    if (targetPresentation.getDescription() == null && sourcePresentation.getDescription() != null) {
      targetPresentation.setDescription(sourcePresentation.getDescription());
    }
    targetAction.setShortcutSet(getShortcutSet());
    targetAction.setCanUseProjectAsDefault(isCanUseProjectAsDefault());
    targetAction.setModuleExtensionIds(getModuleExtensionIds());
  }

}
