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
package consulo.usage;

import consulo.colorScheme.TextAttributesKey;

public interface UsageTreeColors {
    TextAttributesKey INVALID_PREFIX = TextAttributesKey.of("$INVALID_PREFIX");
    TextAttributesKey READONLY_PREFIX = TextAttributesKey.of("$READ_ONLY_PREFIX");
    TextAttributesKey HAS_READ_ONLY_CHILD = TextAttributesKey.of("$HAS_READ_ONLY_CHILD");
    TextAttributesKey TEXT_NODE = TextAttributesKey.of("$TEXT_NODE");
    TextAttributesKey NUMBER_OF_USAGES = TextAttributesKey.of("$NUMBER_OF_USAGES");
    TextAttributesKey USAGE_LOCATION = TextAttributesKey.of("$NUMBER_OF_USAGES");
    TextAttributesKey OCCURENCE = TextAttributesKey.of("$OCCURENCE");
    TextAttributesKey SELECTED_OCCURENCE = TextAttributesKey.of("$SELECTED_OCCURENCE");
    TextAttributesKey EXCLUDED_NODE = TextAttributesKey.of("$EXCLUDED_NODE");
}