// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.openapi.vfs.encoding.ChangeFileEncodingAction;
import com.intellij.openapi.vfs.encoding.EncodingManagerImpl;
import com.intellij.openapi.vfs.encoding.EncodingManagerListener;
import com.intellij.openapi.vfs.encoding.EncodingUtil;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.util.ObjectUtils;
import consulo.component.messagebus.MessageBusConnection;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import java.nio.charset.Charset;

public class EncodingPanel extends EditorBasedStatusBarPopup {
  public EncodingPanel(@Nonnull Project project) {
    super(project, true);
  }

  @Nonnull
  @Override
  protected WidgetState getWidgetState(@Nullable VirtualFile file) {
    if (file == null) {
      return WidgetState.HIDDEN;
    }

    Pair<Charset, String> check = EncodingUtil.getCharsetAndTheReasonTooltip(file);
    String failReason = Pair.getSecond(check);
    Charset charset = ObjectUtils.notNull(Pair.getFirst(check), file.getCharset());
    String charsetName = ObjectUtils.notNull(charset.displayName(), "n/a");
    String toolTipText = failReason == null ? "File Encoding: " + charsetName : StringUtil.capitalize(failReason) + ".";
    return new WidgetState(toolTipText, charsetName, failReason == null);
  }

  @Nullable
  @Override
  protected ListPopup createPopup(DataContext context) {
    ChangeFileEncodingAction action = new ChangeFileEncodingAction();
    action.getTemplatePresentation().setText("File Encoding");
    return action.createPopup(context);
  }

  @Override
  protected void registerCustomListeners() {
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(this);

    // should update to reflect encoding-from-content
    connection.subscribe(EncodingManagerListener.ENCODING_MANAGER_CHANGES, new EncodingManagerListener() {
      @Override
      public void propertyChanged(@Nullable Document document, @Nonnull String propertyName, Object oldValue, Object newValue) {
        if (propertyName.equals(EncodingManagerImpl.PROP_CACHED_ENCODING_CHANGED)) {
          updateForDocument(document);
        }
      }
    });

    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(new VirtualFileListener() {
      @Override
      public void propertyChanged(@Nonnull VirtualFilePropertyEvent event) {
        if (VirtualFile.PROP_ENCODING.equals(event.getPropertyName())) {
          updateForFile(event.getFile());
        }
      }
    }));
  }

  @Nonnull
  @Override
  protected StatusBarWidget createInstance(@Nonnull Project project) {
    return new EncodingPanel(project);
  }

  @Override
  @Nonnull
  public String ID() {
    return StatusBar.StandardWidgets.ENCODING_PANEL;
  }
}
