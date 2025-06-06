/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import consulo.language.Language;

import jakarta.annotation.Nullable;

/**
 * @author Denis Zhdanov
 * @since 2012-06-26
 */
public interface BlockEx extends Block {
    /**
     * @return current block's language (is used to decide on what code style settings should be used for it)
     */
    @Nullable
    Language getLanguage();
}
