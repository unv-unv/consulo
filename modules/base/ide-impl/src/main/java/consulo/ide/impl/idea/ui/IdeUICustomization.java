// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ide.impl.idea.ui;

import consulo.ide.localize.IdeLocalize;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Allows to apply IDE-specific customizations to the terms used in platform UI features.
 */
@Deprecated
public class IdeUICustomization {
  private static final IdeUICustomization ourInstance = new IdeUICustomization();

  public static IdeUICustomization getInstance() {
    return ourInstance;
  }

  /**
   * Returns the name to be displayed in the UI for the "project" concept (Rider changes this to "solution").
   */
  public String getProjectConceptName() {
    return "project";
  }

  /**
   * Returns the name to be displayed in the UI for the "Project" concept (Rider changes this to "Solution").
   */
  public String getProjectDisplayName() {
    return StringUtil.capitalize(getProjectConceptName());
  }

  /**
   * Returns the name of the "Close Project" action (with mnemonic if needed).
   */
  public String getCloseProjectActionText() {
    return IdeLocalize.actionCloseProject().get();
  }

  /**
   * Returns the title of the Project view toolwindow.
   */
  public String getProjectViewTitle() {
    return StringUtil.capitalize(getProjectConceptName());
  }

  /**
   * Returns the title of the Project view Select In target.
   */
  public String getProjectViewSelectInTitle() {
    return getProjectViewTitle() + " View";
  }

  /**
   * Returns the title of the "Non-Project Files" scope.
   */
  public String getNonProjectFilesScopeTitle() {
    return "Non-" + StringUtil.capitalize(getProjectConceptName()) + " Files";
  }

  public String getSelectAutopopupByCharsText() {
    return "Insert selected suggestion by pressing space, dot, or other context-dependent keys";
  }

  /**
   * Allows to replace the text of the given action (only for the actions/groups that support this mechanism)
   */
  @Nullable
  public String getActionText(@Nonnull String actionId) {
    return null;
  }
}
