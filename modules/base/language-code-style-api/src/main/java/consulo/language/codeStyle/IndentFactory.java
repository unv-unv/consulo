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
package consulo.language.codeStyle;

import jakarta.annotation.Nonnull;

/**
 * Internal interface for creating indent instances.
 * <p/>
 * Methods of this interface define contract for implementing {@link Indent} factory methods, so, feel free to check
 * their contracts.
 */
public interface IndentFactory {
    Indent getNormalIndent(boolean relativeToDirectParent);

    Indent getNoneIndent();

    Indent getAbsoluteNoneIndent();

    Indent getAbsoluteLabelIndent();

    Indent getLabelIndent();

    Indent getContinuationIndent(boolean relativeToDirectParent);

    Indent getContinuationWithoutFirstIndent(boolean relativeToDirectParent);

    Indent getSpaceIndent(int spaces, boolean relativeToDirectParent);

    Indent getIndent(@Nonnull Indent.Type type, boolean relativeToDirectParent, boolean enforceIndentToChildren);

    Indent getIndent(@Nonnull Indent.Type type, int spaces, boolean relativeToDirectParent, boolean enforceIndentToChildren);

    Indent getSmartIndent(@Nonnull Indent.Type type);

    Indent getSmartIndent(@Nonnull Indent.Type type, boolean relativeToDirectParent);
}
