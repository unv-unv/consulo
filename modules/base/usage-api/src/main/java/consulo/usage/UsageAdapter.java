/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import consulo.fileEditor.FileEditorLocation;

import jakarta.annotation.Nonnull;

public class UsageAdapter implements Usage {
    @Override
    @Nonnull
    public UsagePresentation getPresentation() {
        throw new IllegalAccessError();
    }

    @Override
    public boolean isValid() {
        return false;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public FileEditorLocation getLocation() {
        return null;
    }

    @Override
    public void selectInEditor() {
    }

    @Override
    public void highlightInEditor() {
    }

    @Override
    public void navigate(boolean requestFocus) {
    }

    @Override
    public boolean canNavigate() {
        return false;
    }

    @Override
    public boolean canNavigateToSource() {
        return false;
    }
}
