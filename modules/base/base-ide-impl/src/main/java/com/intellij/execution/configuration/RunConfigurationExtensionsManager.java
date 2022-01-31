/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.configuration;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.process.ProcessHandler;
import consulo.component.extension.ExtensionPointName;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import consulo.component.persist.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import consulo.component.persist.WriteExternalException;
import consulo.util.collection.SmartList;
import com.intellij.util.containers.ContainerUtil;
import consulo.util.dataholder.Key;
import org.jdom.Element;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author traff
 */
public class RunConfigurationExtensionsManager<U extends RunConfigurationBase, T extends RunConfigurationExtensionBase<U>> {
  private static final Key<List<Element>> RUN_EXTENSIONS = Key.create("run.extension.elements");
  private static final String EXT_ID_ATTR = "ID";
  private static final String EXTENSION_ROOT_ATTR = "EXTENSION";

  private final ExtensionPointName<T> myExtensionPointName;

  public RunConfigurationExtensionsManager(ExtensionPointName<T> extensionPointName) {
    myExtensionPointName = extensionPointName;
  }

  public void readExternal(@Nonnull U configuration, @Nonnull Element parentNode) throws InvalidDataException {
    Map<String, T> extensions = new HashMap<>();
    for (T extension : getApplicableExtensions(configuration)) {
      extensions.put(extension.getSerializationId(), extension);
    }

    List<Element> children = parentNode.getChildren(getExtensionRootAttr());
    // if some of extensions settings weren't found we should just keep it because some plugin with extension
    // may be turned off
    boolean hasUnknownExtension = false;
    for (Element element : children) {
      final T extension = extensions.remove(element.getAttributeValue(getIdAttrName()));
      if (extension == null) {
        hasUnknownExtension = true;
      }
      else {
        extension.readExternal(configuration, element);
      }
    }
    if (hasUnknownExtension) {
      List<Element> copy = children.stream().map(JDOMUtil::internElement).collect(Collectors.toList());
      configuration.putCopyableUserData(RUN_EXTENSIONS, copy);
    }
  }

  @Nonnull
  protected String getIdAttrName() {
    return EXT_ID_ATTR;
  }

  @Nonnull
  protected String getExtensionRootAttr() {
    return EXTENSION_ROOT_ATTR;
  }

  public void writeExternal(@Nonnull U configuration, @Nonnull Element parentNode) {
    Map<String, Element> map = ContainerUtil.newTreeMap();
    final List<Element> elements = configuration.getCopyableUserData(RUN_EXTENSIONS);
    if (elements != null) {
      for (Element element : elements) {
        map.put(element.getAttributeValue(getIdAttrName()), element.clone());
      }
    }

    for (T extension : getApplicableExtensions(configuration)) {
      Element element = new Element(getExtensionRootAttr());
      element.setAttribute(getIdAttrName(), extension.getSerializationId());
      try {
        extension.writeExternal(configuration, element);
      }
      catch (WriteExternalException ignored) {
        continue;
      }

      if (!element.getContent().isEmpty() || element.getAttributes().size() > 1) {
        map.put(extension.getSerializationId(), element);
      }
    }

    for (Element values : map.values()) {
      parentNode.addContent(values);
    }
  }

  public <V extends U> void appendEditors(@Nonnull final U configuration,
                                          @Nonnull final SettingsEditorGroup<V> group) {
    for (T extension : getApplicableExtensions(configuration)) {
      @SuppressWarnings("unchecked")
      final SettingsEditor<V> editor = extension.createEditor((V)configuration);
      if (editor != null) {
        group.addEditor(extension.getEditorTitle(), editor);
      }
    }
  }

  public void validateConfiguration(@Nonnull final U configuration,
                                    final boolean isExecution) throws Exception {
    // only for enabled extensions
    for (T extension : getEnabledExtensions(configuration, null)) {
      extension.validateConfiguration(configuration, isExecution);
    }
  }

  public void extendCreatedConfiguration(@Nonnull final U configuration,
                                         @Nonnull final Location location) {
    for (T extension : getApplicableExtensions(configuration)) {
      extension.extendCreatedConfiguration(configuration, location);
    }
  }

  public void extendTemplateConfiguration(@Nonnull final U configuration) {
    for (T extension : getApplicableExtensions(configuration)) {
      extension.extendTemplateConfiguration(configuration);
    }
  }

  public void patchCommandLine(@Nonnull final U configuration,
                               final RunnerSettings runnerSettings,
                               @Nonnull final GeneralCommandLine cmdLine,
                               @Nonnull final String runnerId) throws ExecutionException {
    // only for enabled extensions
    for (T extension : getEnabledExtensions(configuration, runnerSettings)) {
      extension.patchCommandLine(configuration, runnerSettings, cmdLine, runnerId);
    }
  }

  public void attachExtensionsToProcess(@Nonnull final U configuration,
                                        @Nonnull final ProcessHandler handler,
                                        RunnerSettings runnerSettings) {
    // only for enabled extensions
    for (T extension : getEnabledExtensions(configuration, runnerSettings)) {
      extension.attachToProcess(configuration, handler, runnerSettings);
    }
  }

  @Nonnull
  protected List<T> getApplicableExtensions(@Nonnull U configuration) {
    List<T> extensions = new SmartList<>();
    for (T extension : myExtensionPointName.getExtensionList()) {
      if (extension.isApplicableFor(configuration)) {
        extensions.add(extension);
      }
    }
    return extensions;
  }

  @Nonnull
  protected List<T> getEnabledExtensions(@Nonnull U configuration, @javax.annotation.Nullable RunnerSettings runnerSettings) {
    List<T> extensions = new SmartList<>();
    for (T extension : myExtensionPointName.getExtensionList()) {
      if (extension.isApplicableFor(configuration) && extension.isEnabledFor(configuration, runnerSettings)) {
        extensions.add(extension);
      }
    }
    return extensions;
  }
}
