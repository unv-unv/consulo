// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ide.impl.idea.openapi.wm.impl.status;

import consulo.application.WriteAction;
import consulo.document.FileDocumentManager;
import consulo.fileEditor.FileEditorsSplitters;
import consulo.fileEditor.event.FileEditorManagerEvent;
import consulo.fileEditor.event.FileEditorManagerListener;
import consulo.fileEditor.internal.FileEditorManagerEx;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.platform.base.localize.ActionLocalize;
import consulo.project.Project;
import consulo.project.ui.wm.StatusBar;
import consulo.project.ui.wm.StatusBarWidget;
import consulo.project.ui.wm.StatusBarWidgetFactory;
import consulo.ui.ex.UIBundle;
import consulo.ui.ex.action.ActionsBundle;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.localize.UILocalize;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.ReadOnlyAttributeUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.function.Consumer;

public final class ToggleReadOnlyAttributePanel implements StatusBarWidget.Multiframe, StatusBarWidget.IconPresentation {
    private final StatusBarWidgetFactory myFactory;
    private StatusBar myStatusBar;

    public ToggleReadOnlyAttributePanel(StatusBarWidgetFactory factory) {
        myFactory = factory;
    }

    @Nonnull
    @Override
    public String getId() {
        return myFactory.getId();
    }

    @Override
    @Nullable
    public Image getIcon() {
        if (!isReadonlyApplicable()) {
            return null;
        }
        VirtualFile virtualFile = getCurrentFile();
        return virtualFile == null || virtualFile.isWritable() ? PlatformIconGroup.ideReadwrite() : PlatformIconGroup.ideReadonly();
    }

    @Override
    public StatusBarWidget copy() {
        return new ToggleReadOnlyAttributePanel(myFactory);
    }

    @Override
    public WidgetPresentation getPresentation() {
        return this;
    }

    @Override
    public void dispose() {
        myStatusBar = null;
    }

    @Override
    public void install(@Nonnull StatusBar statusBar) {
        myStatusBar = statusBar;
        Project project = statusBar.getProject();
        if (project == null) {
            return;
        }

        project.getMessageBus().connect(this).subscribe(FileEditorManagerListener.class, new FileEditorManagerListener() {
            @Override
            public void selectionChanged(@Nonnull FileEditorManagerEvent event) {
                if (myStatusBar != null) {
                    myStatusBar.updateWidget(getId());
                }
            }
        });
    }

    @Override
    public String getTooltipText() {
        VirtualFile virtualFile = getCurrentFile();
        int writable = virtualFile == null || virtualFile.isWritable() ? 1 : 0;
        int readonly = writable == 1 ? 0 : 1;
        return ActionLocalize.actionTogglereadonlyattributeFiles(readonly, 1).get();
    }

    @Override
    public Consumer<MouseEvent> getClickConsumer() {
        return mouseEvent -> {
            VirtualFile file = getCurrentFile();
            if (!isReadOnlyApplicableForFile(file)) {
                return;
            }
            FileDocumentManager.getInstance().saveAllDocuments();

            try {
                WriteAction.run(() -> ReadOnlyAttributeUtil.setReadOnlyAttribute(file, file.isWritable()));
                myStatusBar.updateWidget(getId());
            }
            catch (IOException e) {
                Messages.showMessageDialog(getProject(), e.getMessage(), UILocalize.errorDialogTitle().get(), UIUtil.getErrorIcon());
            }
        };
    }

    private boolean isReadonlyApplicable() {
        VirtualFile file = getCurrentFile();
        return isReadOnlyApplicableForFile(file);
    }

    private static boolean isReadOnlyApplicableForFile(@Nullable VirtualFile file) {
        return file != null && !file.getFileSystem().isReadOnly();
    }

    @Nullable
    private Project getProject() {
        return myStatusBar != null ? myStatusBar.getProject() : null;
    }

    @Nullable
    private VirtualFile getCurrentFile() {
        Project project = getProject();
        if (project == null) {
            return null;
        }
        FileEditorsSplitters splitters = FileEditorManagerEx.getInstanceEx(project).getSplittersFor(myStatusBar.getComponent());
        return splitters.getCurrentFile();
    }
}
