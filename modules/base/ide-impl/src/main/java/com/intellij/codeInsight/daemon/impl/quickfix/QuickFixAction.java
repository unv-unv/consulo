// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl.quickfix;

import consulo.language.editor.highlight.HighlightDisplayKey;
import consulo.language.editor.highlight.impl.HighlightInfoImpl;
import consulo.language.editor.intention.IntentionAction;
import consulo.document.util.TextRange;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.List;

/**
 * @author Alexey Kudravtsev
 */
public final class QuickFixAction {
  private QuickFixAction() {
  }

  public static void registerQuickFixAction(@Nullable HighlightInfoImpl info, @Nullable IntentionAction action, @Nullable HighlightDisplayKey key) {
    registerQuickFixAction(info, null, action, key);
  }

  public static void registerQuickFixAction(@Nullable HighlightInfoImpl info, @Nullable IntentionAction action) {
    registerQuickFixAction(info, null, action);
  }

  /**
   * @deprecated This is used by TeamCity plugin
   */
  @Deprecated
  public static void registerQuickFixAction(@Nullable HighlightInfoImpl info, @Nullable IntentionAction action, @Nullable List<IntentionAction> options, @Nullable @Nls String displayName) {
    if (info == null) return;
    info.registerFix(action, options, displayName, null, null);
  }

  public static void registerQuickFixAction(@Nullable HighlightInfoImpl info, @Nullable TextRange fixRange, @Nullable IntentionAction action, @Nullable HighlightDisplayKey key) {
    if (info == null) return;
    info.registerFix(action, null, HighlightDisplayKey.getDisplayNameByKey(key), fixRange, key);
  }

  public static void registerQuickFixAction(@Nullable HighlightInfoImpl info, @Nullable TextRange fixRange, @Nullable IntentionAction action) {
    if (info == null) return;
    info.registerFix(action, null, null, fixRange, null);
  }

  public static void registerQuickFixActions(@Nullable HighlightInfoImpl info, @Nullable TextRange fixRange, @Nonnull Iterable<? extends IntentionAction> actions) {
    for (IntentionAction action : actions) {
      registerQuickFixAction(info, fixRange, action);
    }
  }
}
