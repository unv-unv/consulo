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
package consulo.codeEditor;

import consulo.colorScheme.EditorColorKey;
import consulo.colorScheme.TextAttributesKey;

public interface EditorColors {
    EditorColorKey CARET_ROW_COLOR = EditorColorKey.createColorKey("CARET_ROW_COLOR");
    EditorColorKey CARET_COLOR = EditorColorKey.createColorKey("CARET_COLOR");
    EditorColorKey RIGHT_MARGIN_COLOR = EditorColorKey.createColorKey("RIGHT_MARGIN_COLOR");
    EditorColorKey LINE_NUMBERS_COLOR = EditorColorKey.createColorKey("LINE_NUMBERS_COLOR");
    EditorColorKey LINE_NUMBER_ON_CARET_ROW_COLOR = EditorColorKey.createColorKey("LINE_NUMBER_ON_CARET_ROW_COLOR");
    EditorColorKey ANNOTATIONS_COLOR = EditorColorKey.createColorKey("ANNOTATIONS_COLOR");
    EditorColorKey READONLY_BACKGROUND_COLOR = EditorColorKey.createColorKey("READONLY_BACKGROUND");
    EditorColorKey READONLY_FRAGMENT_BACKGROUND_COLOR = EditorColorKey.createColorKey("READONLY_FRAGMENT_BACKGROUND");
    EditorColorKey WHITESPACES_COLOR = EditorColorKey.createColorKey("WHITESPACES");
    EditorColorKey TABS_COLOR = EditorColorKey.createColorKeyWithFallback("TABS", WHITESPACES_COLOR);
    EditorColorKey INDENT_GUIDE_COLOR = EditorColorKey.createColorKey("INDENT_GUIDE");
    EditorColorKey SOFT_WRAP_SIGN_COLOR = EditorColorKey.createColorKey("SOFT_WRAP_SIGN_COLOR");
    EditorColorKey SELECTED_INDENT_GUIDE_COLOR = EditorColorKey.createColorKey("SELECTED_INDENT_GUIDE");
    EditorColorKey SELECTION_BACKGROUND_COLOR = EditorColorKey.createColorKey("SELECTION_BACKGROUND");
    EditorColorKey SELECTION_FOREGROUND_COLOR = EditorColorKey.createColorKey("SELECTION_FOREGROUND");
    EditorColorKey MATCHED_BRACES_INDENT_GUIDE_COLOR = EditorColorKey.createColorKey("MATCHED_BRACES_INDENT_GUIDE_COLOR");

    TextAttributesKey REFERENCE_HYPERLINK_COLOR = TextAttributesKey.of("CTRL_CLICKABLE");

    TextAttributesKey SEARCH_RESULT_ATTRIBUTES = TextAttributesKey.of("SEARCH_RESULT_ATTRIBUTES");
    TextAttributesKey LIVE_TEMPLATE_ATTRIBUTES = TextAttributesKey.of("LIVE_TEMPLATE_ATTRIBUTES");
    TextAttributesKey WRITE_SEARCH_RESULT_ATTRIBUTES = TextAttributesKey.of("WRITE_SEARCH_RESULT_ATTRIBUTES");
    TextAttributesKey IDENTIFIER_UNDER_CARET_ATTRIBUTES = TextAttributesKey.of("IDENTIFIER_UNDER_CARET_ATTRIBUTES");
    TextAttributesKey WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES = TextAttributesKey.of("WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES");
    TextAttributesKey TEXT_SEARCH_RESULT_ATTRIBUTES = TextAttributesKey.of("TEXT_SEARCH_RESULT_ATTRIBUTES");

    TextAttributesKey FOLDED_TEXT_ATTRIBUTES = TextAttributesKey.of("FOLDED_TEXT_ATTRIBUTES");
    EditorColorKey FOLDED_TEXT_BORDER_COLOR = EditorColorKey.createColorKey("FOLDED_TEXT_BORDER_COLOR");
    TextAttributesKey DELETED_TEXT_ATTRIBUTES = TextAttributesKey.of("DELETED_TEXT_ATTRIBUTES");

    EditorColorKey EDITOR_GUTTER_BACKGROUND = EditorColorKey.createColorKey("EDITOR_GUTTER_BACKGROUND");

    EditorColorKey GUTTER_BACKGROUND = EditorColorKey.createColorKey("GUTTER_BACKGROUND");

    EditorColorKey NOTIFICATION_INFORMATION_BACKGROUND = EditorColorKey.createColorKey("NOTIFICATION_INFORMATION_BACKGROUND");
    EditorColorKey NOTIFICATION_WARNING_BACKGROUND = EditorColorKey.createColorKey("NOTIFICATION_WARNING_BACKGROUND");
    EditorColorKey NOTIFICATION_ERROR_BACKGROUND = EditorColorKey.createColorKey("NOTIFICATION_ERROR_BACKGROUND");

    EditorColorKey TEARLINE_COLOR = EditorColorKey.createColorKey("TEARLINE_COLOR");
    EditorColorKey SELECTED_TEARLINE_COLOR = EditorColorKey.createColorKey("SELECTED_TEARLINE_COLOR");

    EditorColorKey ADDED_LINES_COLOR = EditorColorKey.createColorKey("ADDED_LINES_COLOR");
    EditorColorKey MODIFIED_LINES_COLOR = EditorColorKey.createColorKey("MODIFIED_LINES_COLOR");
    EditorColorKey DELETED_LINES_COLOR = EditorColorKey.createColorKey("DELETED_LINES_COLOR");
    EditorColorKey WHITESPACES_MODIFIED_LINES_COLOR = EditorColorKey.createColorKey("WHITESPACES_MODIFIED_LINES_COLOR");
    EditorColorKey BORDER_LINES_COLOR = EditorColorKey.createColorKey("BORDER_LINES_COLOR");

    TextAttributesKey BREADCRUMBS_DEFAULT = TextAttributesKey.of("BREADCRUMBS_DEFAULT");
    TextAttributesKey BREADCRUMBS_HOVERED = TextAttributesKey.of("BREADCRUMBS_HOVERED");
    TextAttributesKey BREADCRUMBS_CURRENT = TextAttributesKey.of("BREADCRUMBS_CURRENT");
    TextAttributesKey BREADCRUMBS_INACTIVE = TextAttributesKey.of("BREADCRUMBS_INACTIVE");

    TextAttributesKey INJECTED_LANGUAGE_FRAGMENT = TextAttributesKey.of("INJECTED_LANGUAGE_FRAGMENT");

    EditorColorKey VISUAL_INDENT_GUIDE_COLOR = EditorColorKey.createColorKey("VISUAL_INDENT_GUIDE");

    EditorColorKey STICKY_LINES_BACKGROUND = EditorColorKey.createColorKey("STICKY_LINES_BACKGROUND");
    EditorColorKey STICKY_LINES_HOVERED_COLOR = EditorColorKey.createColorKeyWithFallback("STICKY_LINES_HOVERED_COLOR", CARET_ROW_COLOR);
    EditorColorKey STICKY_LINES_BORDER_COLOR = EditorColorKey.createColorKeyWithFallback("STICKY_LINES_BORDER_COLOR", RIGHT_MARGIN_COLOR);
}
