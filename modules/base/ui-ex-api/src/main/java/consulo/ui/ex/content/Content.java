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
package consulo.ui.ex.content;

import consulo.annotation.DeprecationInfo;
import consulo.component.util.BusyObject;
import consulo.disposer.Disposable;
import consulo.ui.Component;
import consulo.ui.color.ColorValue;
import consulo.ui.ex.ComponentContainer;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.image.Image;
import consulo.util.dataholder.Key;
import consulo.util.dataholder.UserDataHolder;
import jakarta.annotation.Nullable;
import kava.beans.PropertyChangeListener;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

/**
 * Represents a tab or pane displayed in a toolwindow or in another content manager.
 *
 * @see ContentFactory#createUIContent(Component, String, boolean)
 */
public interface Content extends UserDataHolder, ComponentContainer {
  String PROP_DISPLAY_NAME = "displayName";
  String PROP_ICON = "icon";
  String PROP_ACTIONS = "actions";
  String PROP_DESCRIPTION = "description";
  String PROP_COMPONENT = "component";
  String IS_CLOSABLE = "isClosable";
  String PROP_PINNED = "pinned";

  Key<Content> KEY = Key.create(Content.class);
  Key<Content[]> KEY_OF_ARRAY = Key.create(Content[].class);

  Key<Boolean> TABBED_CONTENT_KEY = Key.create("tabbedContent");
  Key<String> TAB_GROUP_NAME_KEY = Key.create("tabbedGroupName");
  Key<ComponentOrientation> TAB_LABEL_ORIENTATION_KEY = Key.create("tabLabelComponentOrientation");

  String PROP_ALERT = "alerting";

  default void setUIComponent(@Nullable Component component) {
    throw new AbstractMethodError();
  }

  default void setUIPreferredFocusableComponent(Component component) {
    throw new AbstractMethodError();
  }

  default void setUIPreferredFocusedComponent(Supplier<Component> computable) {
    throw new AbstractMethodError();
  }

  void setIcon(Image icon);

  @Nullable
  Image getIcon();

  void setDisplayName(String displayName);

  String getDisplayName();

  void setTabName(String tabName);

  String getTabName();

  void setToolwindowTitle(String toolwindowTitle);

  String getToolwindowTitle();

  Disposable getDisposer();

  /**
   * @param disposer a Disposable object which dispose() method will be invoked upon this content release.
   */
  void setDisposer(Disposable disposer);

  @Deprecated(forRemoval = true)
  @DeprecationInfo("Since components can't not be extended by user, this methods can't not be used")
  default void setShouldDisposeContent(boolean value) {
  }

  @Deprecated(forRemoval = true)
  @DeprecationInfo("Since components can't not be extended by user, this methods can't not be used")
  default boolean shouldDisposeContent() {
    return false;
  }

  String getDescription();

  void setDescription(String description);

  void addPropertyChangeListener(PropertyChangeListener l);

  void removePropertyChangeListener(PropertyChangeListener l);

  ContentManager getManager();

  boolean isSelected();

  void release();

  boolean isValid();

  boolean isPinned();

  void setPinned(boolean pinned);

  boolean isPinnable();

  void setPinnable(boolean pinnable);

  boolean isCloseable();

  void setCloseable(boolean closeable);

  void setActions(ActionGroup actions, String place, @Nullable JComponent contextComponent);

  void setSearchComponent(@Nullable JComponent comp);

  ActionGroup getActions();

  @Nullable
  JComponent getSearchComponent();

  String getPlace();

  JComponent getActionsContextComponent();

  void setAlertIcon(@Nullable AlertIcon icon);

  @Nullable
  AlertIcon getAlertIcon();

  void fireAlert();

  @Nullable
  BusyObject getBusyObject();

  void setBusyObject(BusyObject object);

  String getSeparator();

  void setSeparator(String separator);

  void setPopupIcon(@Nullable Image icon);

  @Nullable
  Image getPopupIcon();

  /**
   * @param executionId supposed to identify group of contents (for example "Before Launch" tasks and the main Run Configuration)
   */
  void setExecutionId(long executionId);

  long getExecutionId();

  default void setHelpId(String helpId) {
  }

  @Nullable
  default String getHelpId() {
    return null;
  }

  default void setTabColor(@Nullable ColorValue color) {
  }

  @Nullable
  default ColorValue getTabColor() {
    return null;
  }

  // TODO [VISTALL] AWT & Swing dependency

  // region AWT & Swing dependency
  @Deprecated
  default void setComponent(javax.swing.JComponent component) {
    throw new AbstractMethodError();
  }

  default void setPreferredFocusableComponent(javax.swing.JComponent component) {
    throw new AbstractMethodError();
  }

  default void setPreferredFocusedComponent(Supplier<JComponent> computable) {
    throw new AbstractMethodError();
  }
  // endregion
}
