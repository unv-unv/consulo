// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.openapi.Disposable;
import consulo.logging.Logger;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.InlayModel;
import com.intellij.openapi.editor.VisualPosition;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.List;

public class InlayModelWindow implements InlayModel {
  private static final Logger LOG = Logger.getInstance(InlayModelWindow.class);

  @Nullable
  @Override
  public Inlay addInlineElement(int offset, boolean relatesToPrecedingText, @Nonnull EditorCustomElementRenderer renderer) {
    logUnsupported();
    return null;
  }

  @Nonnull
  @Override
  public List<Inlay> getInlineElementsInRange(int startOffset, int endOffset) {
    logUnsupported();
    return Collections.emptyList();
  }

  @Override
  public boolean hasInlineElementAt(int offset) {
    logUnsupported();
    return false;
  }

  @Nullable
  @Override
  public Inlay getInlineElementAt(@Nonnull VisualPosition visualPosition) {
    logUnsupported();
    return null;
  }

  @Nullable
  @Override
  public Inlay getElementAt(@Nonnull Point point) {
    logUnsupported();
    return null;
  }

  @Override
  public void addListener(@Nonnull Listener listener, @Nonnull Disposable disposable) {
    logUnsupported();
  }

  private static void logUnsupported() {
    LOG.error("Inlay operations are not supported for injected editors");
  }
}
