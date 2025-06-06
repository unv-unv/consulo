// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ide.impl.idea.ide.actions.searcheverywhere;

public interface SearchEverywhereToggleAction {
    boolean isEverywhere();

    void setEverywhere(boolean everywhere);

    boolean canToggleEverywhere();
}
