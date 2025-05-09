// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.language.codeStyle;

import consulo.ui.ex.action.AnAction;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiFile;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public interface CodeStyleStatusBarUIContributor {
    /**
     * Checks if any actions are available for the given virtual file without creating them.
     *
     * @param file The current virtual file.
     * @return True if any actions are available, false otherwise.
     */
    boolean areActionsAvailable(@Nonnull VirtualFile file);

    /**
     * @param file The current PSI file
     * @return An array of actions available for the given PSI file or {@code null} if no actions are available.
     */
    @Nullable
    AnAction[] getActions(@Nonnull PsiFile file);

    /**
     * @return A title used for a group of actions opened from the status bar or {@code null} if no title is shown.
     */
    @Nullable
    default String getActionGroupTitle() {
        return null;
    }

    /**
     * @return A status bar tooltip or null for default tooltip.
     */
    @Nullable
    String getTooltip();

    /**
     * Returns a text shown in a popup to drag user's attention to a UI element associated with the current indent options and related actions.
     * The advertisement text may contain basic information about the source of the current indent options so that a user becomes aware of it.
     * The popup is supposed to be shown just once per a case which requires explanation. Subsequent calls to the method may return {@code null}.
     *
     * @param psiFile A PSI file to show the advertisement text for.
     * @return The text to be shown or null for no popup.
     * @deprecated Dropped. The returned text is ignored.
     */
    @Nullable
    @Deprecated
    default String getAdvertisementText(@Nonnull PsiFile psiFile) {
        return null;
    }

    /**
     * Creates an action which can be used to disable the code style source.
     *
     * @param project The project to disable the source in.
     * @return The disable action or null if not available.
     */
    @Nullable
    AnAction createDisableAction(@Nonnull Project project);

    /**
     * Creates an action showing all files related to the code style modification feature.
     *
     * @param project The project to show the files for.
     * @return The "Show all" action or {@code null} if not applicable;
     */
    @Nullable
    default AnAction createShowAllAction(@Nonnull Project project) {
        return null;
    }

    /**
     * @return An icon in the status bar representing a source of changes when modified code style settings are used for a file in editor. By
     * default no icon is shown.
     */
    default Image getIcon() {
        return null;
    }

    /**
     * @param psiFile The currently open {@code PsiFile}.
     * @return A status text to be shown in code style widget for the given {@code PsiFile}
     */
    @Nonnull
    default String getStatusText(@Nonnull PsiFile psiFile) {
        return "*";
    }
}
