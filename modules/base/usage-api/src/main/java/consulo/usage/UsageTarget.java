/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package consulo.usage;

import consulo.navigation.NavigationItem;
import consulo.fileEditor.FileEditor;
import consulo.virtualFileSystem.VirtualFile;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author max
 */
public interface UsageTarget extends NavigationItem {
    UsageTarget[] EMPTY_ARRAY = new UsageTarget[0];

    /**
     * Should open usage view and look for usages
     */
    void findUsages();

    /**
     * Should look for usages in one specific editor. This typicaly shows other kind of dialog and doesn't
     * result in usage view display.
     */
    void findUsagesInEditor(@Nonnull FileEditor editor);

    void highlightUsages(@Nonnull PsiFile file, @Nonnull Editor editor, boolean clearHighlights);

    boolean isValid();

    boolean isReadOnly();

    /**
     * @return the files this usage target is in. Might be null if usage target is not file-based
     */
    @Nullable
    VirtualFile[] getFiles();

    void update();
}
