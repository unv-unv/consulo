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

package consulo.ide.impl.idea.ide.hierarchy;

import consulo.ui.ex.tree.NodeDescriptor;
import consulo.project.ui.view.tree.SmartElementDescriptor;
import consulo.colorScheme.TextAttributes;
import consulo.project.Project;
import consulo.ide.impl.idea.openapi.roots.ui.util.CompositeAppearance;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.usage.UsageTreeColors;
import consulo.usage.UsageTreeColorsScheme;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public abstract class HierarchyNodeDescriptor extends SmartElementDescriptor {
    protected CompositeAppearance myHighlightedText;
    private Object[] myCachedChildren = null;
    protected final boolean myIsBase;

    protected HierarchyNodeDescriptor(
        @Nonnull Project project,
        NodeDescriptor parentDescriptor,
        @Nonnull PsiElement element,
        boolean isBase
    ) {
        super(project, parentDescriptor, element);
        myHighlightedText = new CompositeAppearance();
        myName = "";
        myIsBase = isBase;
    }

    @Override
    public final Object getElement() {
        return this;
    }

    @Nullable
    public PsiFile getContainingFile() {
        PsiElement element = getPsiElement();
        return element != null ? element.getContainingFile() : null;
    }

    public boolean isValid() {
        return getPsiElement() != null;
    }

    public final Object[] getCachedChildren() {
        return myCachedChildren;
    }

    public final void setCachedChildren(Object[] cachedChildren) {
        myCachedChildren = cachedChildren;
    }

    @Override
    protected final boolean isMarkReadOnly() {
        return true;
    }

    @Override
    protected final boolean isMarkModified() {
        return true;
    }

    public final CompositeAppearance getHighlightedText() {
        return myHighlightedText;
    }

    protected static TextAttributes getInvalidPrefixAttributes() {
        return UsageTreeColorsScheme.getInstance().getScheme().getAttributes(UsageTreeColors.INVALID_PREFIX);
    }

    protected static TextAttributes getUsageCountPrefixAttributes() {
        return UsageTreeColorsScheme.getInstance().getScheme().getAttributes(UsageTreeColors.NUMBER_OF_USAGES);
    }

    protected static TextAttributes getPackageNameAttributes() {
        return getUsageCountPrefixAttributes();
    }

    @Override
    public boolean expandOnDoubleClick() {
        return false;
    }
}
