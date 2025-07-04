/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package consulo.fileEditor.impl.internal;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.AccessRule;
import consulo.application.AccessToken;
import consulo.application.Application;
import consulo.application.concurrent.ApplicationConcurrency;
import consulo.application.dumb.PossiblyDumbAware;
import consulo.application.ui.UISettings;
import consulo.application.ui.event.UISettingsListener;
import consulo.application.ui.wm.ExpirableRunnable;
import consulo.application.ui.wm.IdeFocusManager;
import consulo.application.util.UserHomeFileUtil;
import consulo.application.util.function.ThrowableComputable;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.component.ProcessCanceledException;
import consulo.component.messagebus.MessageBusConnection;
import consulo.component.persist.PersistentStateComponentWithUIState;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.component.util.ActiveRunnable;
import consulo.component.util.BusyObject;
import consulo.component.util.ModificationTracker;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.fileEditor.*;
import consulo.fileEditor.event.FileEditorManagerBeforeListener;
import consulo.fileEditor.event.FileEditorManagerEvent;
import consulo.fileEditor.event.FileEditorManagerListener;
import consulo.fileEditor.history.IdeDocumentHistory;
import consulo.fileEditor.internal.FileEditorDockManager;
import consulo.fileEditor.internal.FileEditorManagerEx;
import consulo.fileEditor.localize.FileEditorLocalize;
import consulo.fileEditor.text.TextEditorProvider;
import consulo.language.file.inject.VirtualFileWindow;
import consulo.logging.Logger;
import consulo.module.content.layer.event.ModuleRootEvent;
import consulo.module.content.layer.event.ModuleRootListener;
import consulo.navigation.OpenFileDescriptor;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.project.event.DumbModeListener;
import consulo.project.event.ProjectManagerListener;
import consulo.project.ui.internal.ProjectIdeFocusManager;
import consulo.project.ui.internal.StatusBarEx;
import consulo.project.ui.wm.MergingQueue;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.project.ui.wm.WindowManager;
import consulo.project.ui.wm.dock.DockContainer;
import consulo.project.ui.wm.dock.DockManager;
import consulo.ui.ModalityState;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.color.ColorValue;
import consulo.ui.ex.ComponentContainer;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.undoRedo.CommandProcessor;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;
import consulo.util.concurrent.ActionCallback;
import consulo.util.concurrent.AsyncResult;
import consulo.util.dataholder.Key;
import consulo.util.lang.NullUtils;
import consulo.util.lang.Pair;
import consulo.util.lang.Trinity;
import consulo.util.lang.ref.SimpleReference;
import consulo.util.lang.ref.SoftReference;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.event.VirtualFileEvent;
import consulo.virtualFileSystem.event.VirtualFileListener;
import consulo.virtualFileSystem.event.VirtualFileMoveEvent;
import consulo.virtualFileSystem.event.VirtualFilePropertyEvent;
import consulo.virtualFileSystem.fileType.FileTypeEvent;
import consulo.virtualFileSystem.fileType.FileTypeListener;
import consulo.virtualFileSystem.status.FileStatus;
import consulo.virtualFileSystem.status.FileStatusListener;
import consulo.virtualFileSystem.status.FileStatusManager;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import kava.beans.PropertyChangeEvent;
import kava.beans.PropertyChangeListener;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * @author Anton Katilin
 * @author Eugene Belyaev
 * @author Vladimir Kondratyev
 */
@State(name = "FileEditorManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public abstract class FileEditorManagerImpl extends FileEditorManagerEx implements PersistentStateComponentWithUIState<Element, Element>, Disposable {
    private static final Logger LOG = Logger.getInstance(FileEditorManagerImpl.class);

    private static final Key<Boolean> DUMB_AWARE = Key.create("DUMB_AWARE");

    private static final FileEditor[] EMPTY_EDITOR_ARRAY = {};
    private static final FileEditorProvider[] EMPTY_PROVIDER_ARRAY = {};

    protected FileEditorsSplitters mySplitters;
    protected final Project myProject;
    private final List<Pair<VirtualFile, FileEditorWindow>> mySelectionHistory = new ArrayList<>();
    private Reference<FileEditorComposite> myLastSelectedComposite = new WeakReference<>(null);

    private MergingQueue<Runnable> myQueue;

    private final BusyObject.Impl.Simple myBusyObject = new BusyObject.Impl.Simple();

    /**
     * Removes invalid myEditor and updates "modified" status.
     */
    private final PropertyChangeListener myEditorPropertyChangeListener = new MyEditorPropertyChangeListener();
    protected final DockManager myDockManager;
    private static final AtomicInteger ourOpenFilesSetModificationCount = new AtomicInteger();

    public static final ModificationTracker OPEN_FILE_SET_MODIFICATION_COUNT = ourOpenFilesSetModificationCount::get;

    public FileEditorManagerImpl(
        @Nonnull Application application,
        @Nonnull ApplicationConcurrency applicationConcurrency,
        @Nonnull Project project,
        DockManager dockManager
    ) {
        myProject = project;
        myDockManager = dockManager;

        if (myProject.isDefault()) {
            return;
        }

        myQueue = new MergingQueue<>(applicationConcurrency, project, 50, this, Runnable::run);

        MessageBusConnection connection = project.getMessageBus().connect();

        if (application.getExtensionPoint(FileEditorAssociateFinder.class).hasAnyExtensions()) {
            connection.subscribe(
                FileEditorManagerListener.class,
                new FileEditorManagerListener() {
                    @Override
                    @RequiredUIAccess
                    public void selectionChanged(@Nonnull FileEditorManagerEvent event) {
                        FileEditorsSplitters splitters = getSplitters();
                        openAssociatedFile(UIAccess.current(), event.getNewFile(), splitters.getCurrentWindow(), splitters);
                    }
                }
            );
        }

        connection.subscribe(
            DumbModeListener.class,
            new DumbModeListener() {
                @Override
                public void exitDumbMode() {
                    // can happen under write action, so postpone to avoid deadlock on FileEditorProviderManager.getProviders()
                    application.invokeLater(() -> {
                        if (!myProject.isDisposed()) {
                            dumbModeFinished(myProject);
                        }
                    });
                }
            }
        );
        connection.subscribe(ProjectManagerListener.class, new ProjectManagerListener() {
            @Override
            public void projectOpened(@Nonnull Project project, @Nonnull UIAccess uiAccess) {
                if (project == myProject) {
                    FileEditorManagerImpl.this.projectOpened(connection);
                }
            }

            @Override
            public void projectClosed(@Nonnull Project project, @Nonnull UIAccess uiAccess) {
                if (project == myProject) {
                    // Dispose created editors. We do not use use closeEditor method because
                    // it fires event and changes history.
                    uiAccess.giveAndWaitIfNeed(() -> closeAllFiles());
                }
            }
        });
    }

    @Nonnull
    protected FileEditorWithProviderComposite createEditorWithProviderComposite(
        @Nonnull VirtualFile file,
        @Nonnull FileEditor[] editors,
        @Nonnull FileEditorProvider[] providers,
        @Nonnull FileEditorManagerEx fileEditorManager
    ) {
        throw new UnsupportedOperationException();
    }

    @RequiredUIAccess
    private void dumbModeFinished(Project project) {
        VirtualFile[] files = getOpenFiles();
        for (VirtualFile file : files) {
            Set<FileEditorProvider> providers = new HashSet<>();
            List<FileEditorWithProviderComposite> composites = getEditorComposites(file);
            for (FileEditorWithProviderComposite composite : composites) {
                providers.addAll(Arrays.asList(composite.getProviders()));
            }
            FileEditorProvider[] newProviders = FileEditorProviderManager.getInstance().getProviders(project, file);
            if (newProviders.length > providers.size()) {
                List<FileEditorProvider> toOpen = new ArrayList<>(Arrays.asList(newProviders));
                toOpen.removeAll(providers);
                // need to open additional non dumb-aware editors
                for (FileEditorWithProviderComposite composite : composites) {
                    for (FileEditorProvider provider : toOpen) {
                        FileEditor editor = provider.createEditor(myProject, file);
                        composite.addEditor(editor, provider);
                    }
                }
            }
        }
    }

    public static boolean isDumbAware(@Nonnull FileEditor editor) {
        return Boolean.TRUE.equals(editor.getUserData(DUMB_AWARE))
            && !(editor instanceof PossiblyDumbAware possiblyDumbAware && !possiblyDumbAware.isDumbAware());
    }

    //-------------------------------------------------------------------------------

    @Nonnull
    public FileEditorsSplitters getMainSplitters() {
        initUI();

        return mySplitters;
    }

    @Nonnull
    @Override
    public Set<FileEditorsSplitters> getAllSplitters() {
        Set<FileEditorsSplitters> all = new LinkedHashSet<>();
        all.add(getMainSplitters());
        Set<DockContainer> dockContainers = myDockManager.getContainers();
        for (DockContainer each : dockContainers) {
            if (each instanceof DockableEditorTabbedContainer dockableEditorTabbedContainer) {
                all.add(dockableEditorTabbedContainer.getSplitters());
            }
        }

        return Collections.unmodifiableSet(all);
    }

    @Nonnull
    private AsyncResult<FileEditorsSplitters> getActiveSplittersAsync() {
        AsyncResult<FileEditorsSplitters> result = new AsyncResult<>();
        IdeFocusManager fm = ProjectIdeFocusManager.getInstance(myProject);
        fm.doWhenFocusSettlesDown(() -> {
            if (myProject.isDisposed()) {
                result.setRejected();
                return;
            }
            Component focusOwner = fm.getFocusOwner();
            DockContainer container = myDockManager.getContainerFor(focusOwner);
            if (container instanceof DockableEditorTabbedContainer dockableEditorTabbedContainer) {
                result.setDone(dockableEditorTabbedContainer.getSplitters());
            }
            else {
                result.setDone(getMainSplitters());
            }
        });
        return result;
    }

    @RequiredUIAccess
    private FileEditorsSplitters getActiveSplittersSync() {
        assertDispatchThread();

        // FIXME [VISTALL] reimpl this
        if (myProject.getApplication().isSwingApplication()) {
            IdeFocusManager fm = ProjectIdeFocusManager.getInstance(myProject);
            Component focusOwner = fm.getFocusOwner();
            if (focusOwner == null) {
                focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            }
            if (focusOwner == null) {
                focusOwner = fm.getLastFocusedFor(fm.getLastFocusedFrame());
            }

            DockContainer container = myDockManager.getContainerFor(focusOwner);
            if (container == null) {
                focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
                container = myDockManager.getContainerFor(focusOwner);
            }

            if (container instanceof DockableEditorTabbedContainer dockableEditorTabbedContainer) {
                return dockableEditorTabbedContainer.getSplitters();
            }
        }
        return getMainSplitters();
    }

    protected void initUI() {
    }

    @Override
    @RequiredUIAccess
    public JComponent getPreferredFocusedComponent() {
        assertReadAccess();
        FileEditorWindow window = getSplitters().getCurrentWindow();
        if (window != null) {
            FileEditorWithProviderComposite editor = window.getSelectedEditor();
            if (editor != null) {
                return editor.getPreferredFocusedComponent();
            }
        }
        return null;
    }

    //-------------------------------------------------------

    /**
     * @return color of the {@code file} which corresponds to the
     * file's status
     */
    public ColorValue getFileColor(@Nonnull VirtualFile file) {
        FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
        ColorValue statusColor = fileStatusManager.getStatus(file).getColor();
        if (statusColor == null) {
            statusColor = TargetAWT.from(UIUtil.getLabelForeground());
        }
        return statusColor;
    }

    public boolean isProblem(@Nonnull VirtualFile file) {
        return false;
    }

    @Nonnull
    public String getFileTooltipText(@Nonnull VirtualFile file) {
        List<EditorTabTitleProvider> availableProviders = DumbService.getDumbAwareExtensions(myProject, EditorTabTitleProvider.EP_NAME);
        for (EditorTabTitleProvider provider : availableProviders) {
            String text = provider.getEditorTabTooltipText(myProject, file);
            if (text != null) {
                return text;
            }
        }
        return UserHomeFileUtil.getLocationRelativeToUserHome(file.getPresentableUrl());
    }

    @Override
    public void updateFilePresentation(@Nonnull VirtualFile file) {
        if (!isFileOpen(file)) {
            return;
        }

        updateFileColor(file);
        updateFileIconAsync(file);
        updateFileName(file);
        updateFileBackgroundColor(file);
    }

    /**
     * Updates tab color for the specified {@code file}. The {@code file}
     * should be opened in the myEditor, otherwise the method throws an assertion.
     */
    private void updateFileColor(@Nonnull VirtualFile file) {
        Set<FileEditorsSplitters> all = getAllSplitters();
        for (FileEditorsSplitters each : all) {
            each.updateFileColor(file);
        }
    }

    private void updateFileBackgroundColor(@Nonnull VirtualFile file) {
        Set<FileEditorsSplitters> all = getAllSplitters();
        for (FileEditorsSplitters each : all) {
            each.updateFileBackgroundColor(file);
        }
    }

    /**
     * Updates tab icon for the specified {@code file}. The {@code file}
     * should be opened in the myEditor, otherwise the method throws an assertion.
     */
    protected void updateFileIconAsync(@Nonnull VirtualFile file) {
        Set<FileEditorsSplitters> all = getAllSplitters();
        for (FileEditorsSplitters each : all) {
            each.updateFileIconAsync(file);
        }
    }

    /**
     * Updates tab title and tab tool tip for the specified {@code file}
     */
    public void updateFileName(@Nullable VirtualFile file) {
        // Queue here is to prevent title flickering when tab is being closed and two events arriving: with component==null and component==next focused tab
        // only the last event makes sense to handle
        myQueue.queue(() -> {
            boolean isExpired = myProject.isDisposed() || !myProject.isOpen() || file != null && !file.isValid();
            if (isExpired) {
                return;
            }

            Set<FileEditorsSplitters> all = getAllSplitters();
            for (FileEditorsSplitters each : all) {
                each.updateFileNameAsync(file);
            }
        });
    }

    //-------------------------------------------------------


    @Override
    public VirtualFile getFile(@Nonnull FileEditor editor) {
        FileEditorWithProviderComposite editorComposite = getEditorComposite(editor);
        if (editorComposite != null) {
            return editorComposite.getFile();
        }
        return null;
    }

    @Override
    @RequiredUIAccess
    public void unsplitWindow() {
        FileEditorWindow currentWindow = getActiveSplittersSync().getCurrentWindow();
        if (currentWindow != null) {
            currentWindow.unsplit(true);
        }
    }

    @Override
    @RequiredUIAccess
    public void unsplitAllWindow() {
        FileEditorWindow currentWindow = getActiveSplittersSync().getCurrentWindow();
        if (currentWindow != null) {
            currentWindow.unsplitAll();
        }
    }

    @Override
    @RequiredUIAccess
    public int getWindowSplitCount() {
        return getActiveSplittersSync().getSplitCount();
    }

    @Override
    @RequiredUIAccess
    public boolean hasSplitOrUndockedWindows() {
        Set<FileEditorsSplitters> splitters = getAllSplitters();
        return splitters.size() > 1 || getWindowSplitCount() > 1;
    }

    @Override
    @Nonnull
    public FileEditorWindow[] getWindows() {
        List<FileEditorWindow> windows = new ArrayList<>();
        Set<FileEditorsSplitters> all = getAllSplitters();
        for (FileEditorsSplitters each : all) {
            FileEditorWindow[] eachList = each.getWindows();
            windows.addAll(Arrays.asList(eachList));
        }

        return windows.toArray(new FileEditorWindow[windows.size()]);
    }

    @Override
    @RequiredUIAccess
    public FileEditorWindow getNextWindow(@Nonnull FileEditorWindow window) {
        FileEditorWindow[] windows = getSplitters().getOrderedWindows();
        for (int i = 0; i != windows.length; ++i) {
            if (windows[i].equals(window)) {
                return windows[(i + 1) % windows.length];
            }
        }
        LOG.error("Not window found");
        return null;
    }

    @Override
    @RequiredUIAccess
    public FileEditorWindow getPrevWindow(@Nonnull FileEditorWindow window) {
        FileEditorWindow[] windows = getSplitters().getOrderedWindows();
        for (int i = 0; i != windows.length; ++i) {
            if (windows[i].equals(window)) {
                return windows[(i + windows.length - 1) % windows.length];
            }
        }
        LOG.error("Not window found");
        return null;
    }

    @Override
    @RequiredUIAccess
    public void createSplitter(int orientation, @Nullable FileEditorWindow window) {
        // window was available from action event, for example when invoked from the tab menu of an editor that is not the 'current'
        if (window != null) {
            window.split(orientation, true, null, false);
        }
        // otherwise we'll split the current window, if any
        else {
            FileEditorWindow currentWindow = getSplitters().getCurrentWindow();
            if (currentWindow != null) {
                currentWindow.split(orientation, true, null, false);
            }
        }
    }

    @Override
    @RequiredUIAccess
    public void changeSplitterOrientation() {
        FileEditorWindow currentWindow = getSplitters().getCurrentWindow();
        if (currentWindow != null) {
            currentWindow.changeOrientation();
        }
    }

    @Override
    @RequiredUIAccess
    public boolean isInSplitter() {
        FileEditorWindow currentWindow = getSplitters().getCurrentWindow();
        return currentWindow != null && currentWindow.inSplitter();
    }

    @Override
    @RequiredUIAccess
    public boolean hasOpenedFile() {
        FileEditorWindow currentWindow = getSplitters().getCurrentWindow();
        return currentWindow != null && currentWindow.getSelectedEditor() != null;
    }

    @Override
    @RequiredUIAccess
    public VirtualFile getCurrentFile() {
        return getActiveSplittersSync().getCurrentFile();
    }

    @Override
    @Nonnull
    public AsyncResult<FileEditorWindow> getActiveWindow() {
        return getActiveSplittersAsync().subResult(FileEditorsSplitters::getCurrentWindow);
    }

    @Override
    @RequiredUIAccess
    public FileEditorWindow getCurrentWindow() {
        if (!UIAccess.isUIThread()) {
            return null;
        }
        FileEditorsSplitters splitters = getActiveSplittersSync();
        return splitters == null ? null : splitters.getCurrentWindow();
    }

    @Override
    @RequiredUIAccess
    public void setCurrentWindow(FileEditorWindow window) {
        getActiveSplittersSync().setCurrentWindow(window, true);
    }

    @RequiredUIAccess
    public void closeFile(@Nonnull VirtualFile file, @Nonnull FileEditorWindow window, boolean transferFocus) {
        assertDispatchThread();
        ourOpenFilesSetModificationCount.incrementAndGet();

        CommandProcessor.getInstance().newCommand()
            .project(myProject)
            .name(FileEditorLocalize.commandCloseActiveEditor())
            .run(() -> {
                if (window.isFileOpen(file)) {
                    window.closeFile(file, true, transferFocus);
                }
            });
        removeSelectionRecord(file, window);
    }

    @Override
    @RequiredUIAccess
    public void closeFile(@Nonnull VirtualFile file, @Nonnull FileEditorWindow window) {
        closeFile(file, window, true);
    }

    //============================= EditorManager methods ================================

    @Override
    @RequiredUIAccess
    public void closeFile(@Nonnull VirtualFile file) {
        closeFile(file, true, false);
    }

    @RequiredUIAccess
    public void closeFile(@Nonnull VirtualFile file, boolean moveFocus, boolean closeAllCopies) {
        assertDispatchThread();

        CommandProcessor.getInstance().newCommand()
            .project(myProject)
            .run(() -> closeFileImpl(file, moveFocus, closeAllCopies));
    }

    @RequiredUIAccess
    private void closeFileImpl(@Nonnull VirtualFile file, boolean moveFocus, boolean closeAllCopies) {
        assertDispatchThread();
        ourOpenFilesSetModificationCount.incrementAndGet();
        runChange(splitters -> splitters.closeFile(file, moveFocus), closeAllCopies ? null : getActiveSplittersSync());
    }

    //-------------------------------------- Open File ----------------------------------------

    @Override
    @Nonnull
    @RequiredUIAccess
    public Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(
        @Nonnull VirtualFile file,
        boolean focusEditor,
        boolean searchForSplitter
    ) {
        if (!file.isValid()) {
            throw new IllegalArgumentException("file is not valid: " + file);
        }
        assertDispatchThread();

        if (isOpenInNewWindow()) {
            return openFileInNewWindow(file);
        }


        FileEditorWindow wndToOpenIn = null;
        if (searchForSplitter) {
            Set<FileEditorsSplitters> all = getAllSplitters();
            FileEditorsSplitters active = getActiveSplittersSync();
            if (active.getCurrentWindow() != null && active.getCurrentWindow().isFileOpen(file)) {
                wndToOpenIn = active.getCurrentWindow();
            }
            else {
                for (FileEditorsSplitters splitters : all) {
                    FileEditorWindow window = splitters.getCurrentWindow();
                    if (window == null) {
                        continue;
                    }

                    if (window.isFileOpen(file)) {
                        wndToOpenIn = window;
                        break;
                    }
                }
            }
        }
        else {
            wndToOpenIn = getSplitters().getCurrentWindow();
        }

        FileEditorsSplitters splitters = getSplitters();

        if (wndToOpenIn == null) {
            wndToOpenIn = splitters.getOrCreateCurrentWindow(file);
        }

        UIAccess uiAccess = UIAccess.current();
        openAssociatedFile(uiAccess, file, wndToOpenIn, splitters);
        return openFileImpl2(uiAccess, wndToOpenIn, file, focusEditor);
    }

    public Pair<FileEditor[], FileEditorProvider[]> openFileInNewWindow(@Nonnull VirtualFile file) {
        return ((FileEditorDockManager)DockManager.getInstance(getProject())).createNewDockContainerFor(file, this);
    }

    protected boolean isOpenInNewWindow() {
        return false;
    }

    @RequiredUIAccess
    private void openAssociatedFile(
        UIAccess uiAccess,
        VirtualFile file,
        FileEditorWindow wndToOpenIn,
        @Nonnull FileEditorsSplitters splitters
    ) {
        FileEditorWindow[] windows = splitters.getWindows();

        if (file != null && windows.length == 2) {
            VirtualFile associatedFile = myProject.getApplication().getExtensionPoint(FileEditorAssociateFinder.class)
                .computeSafeIfAny(finder -> finder.getAssociatedFileToOpen(myProject, file));
            if (associatedFile != null) {
                FileEditorWindow currentWindow = splitters.getCurrentWindow();
                int idx = windows[0] == wndToOpenIn ? 1 : 0;
                openFileImpl2(uiAccess, windows[idx], associatedFile, false);

                if (currentWindow != null) {
                    splitters.setCurrentWindow(currentWindow, false);
                }
            }
        }
    }

    @RequiredUIAccess
    @Nonnull
    @Override
    public Pair<FileEditor[], FileEditorProvider[]> openFileWithProviders(
        @Nonnull VirtualFile file,
        boolean focusEditor,
        @Nonnull FileEditorWindow window
    ) {
        if (!file.isValid()) {
            throw new IllegalArgumentException("file is not valid: " + file);
        }
        assertDispatchThread();

        return openFileImpl2(UIAccess.get(), window, file, focusEditor);
    }

    @Nonnull
    @RequiredUIAccess
    public Pair<FileEditor[], FileEditorProvider[]> openFileImpl2(
        @Nonnull UIAccess uiAccess,
        @Nonnull FileEditorWindow window,
        @Nonnull VirtualFile file,
        boolean focusEditor
    ) {
        SimpleReference<Pair<FileEditor[], FileEditorProvider[]>> result = new SimpleReference<>();
        CommandProcessor.getInstance().newCommand()
            .project(myProject)
            .run(() -> result.set(openFileImpl3(uiAccess, window, file, focusEditor, null, true)));
        return result.get();
    }

    /**
     * @param file  to be opened. Unlike openFile method, file can be
     *              invalid. For example, all file were invalidate and they are being
     *              removed one by one. If we have removed one invalid file, then another
     *              invalid file become selected. That's why we do not require that
     *              passed file is valid.
     * @param entry map between FileEditorProvider and FileEditorState. If this parameter
     */
    @Nonnull
    public Pair<FileEditor[], FileEditorProvider[]> openFileImpl3(
        @Nonnull UIAccess uiAccess,
        @Nonnull FileEditorWindow window,
        @Nonnull VirtualFile file,
        boolean focusEditor,
        @Nullable HistoryEntry entry,
        boolean current
    ) {
        return openFileImpl4(uiAccess, window, file, entry, current, focusEditor, null, -1);
    }

    @Deprecated
    public Pair<FileEditor[], FileEditorProvider[]> openFileImpl4(
        @Nonnull UIAccess uiAccess,
        @Nonnull FileEditorWindow window,
        @Nonnull VirtualFile file,
        @Nullable HistoryEntry entry,
        boolean current,
        boolean focusEditor,
        Boolean pin,
        int index
    ) {
        return openFileImpl4(
            uiAccess,
            window,
            file,
            entry,
            new FileEditorOpenOptions().withCurrentTab(current).withFocusEditor(focusEditor).withPin(pin).withIndex(index)
        );
    }

    /**
     * This method can be invoked from background thread. Of course, UI for returned editors should be accessed from EDT in any case.
     */
    @Nonnull
    public Pair<FileEditor[], FileEditorProvider[]> openFileImpl4(
        @Nonnull UIAccess uiAccess,
        @Nonnull FileEditorWindow window,
        @Nonnull VirtualFile file,
        @Nullable HistoryEntry entry,
        FileEditorOpenOptions options
    ) {
        assert UIAccess.isUIThread() || !myProject.getApplication().isReadAccessAllowed()
            : "must not open files under read action since we are doing a lot of invokeAndWaits here";

        int index = options.getIndex();
        boolean current = options.isCurrentTab();
        boolean focusEditor = options.isFocusEditor();
        Boolean pin = options.getPin();

        SimpleReference<FileEditorWithProviderComposite> compositeRef = new SimpleReference<>();
        if (!options.isReopeningEditorsOnStartup()) {
            uiAccess.giveAndWaitIfNeed(() -> compositeRef.set(window.findFileComposite(file)));
        }

        FileEditorProvider[] newProviders;
        AsyncFileEditorProvider.Builder[] builders;
        if (compositeRef.isNull()) {
            // File is not opened yet. In this case we have to create editors
            // and select the created EditorComposite.
            newProviders = FileEditorProviderManager.getInstance().getProviders(myProject, file);
            if (newProviders.length == 0) {
                return Pair.create(EMPTY_EDITOR_ARRAY, EMPTY_PROVIDER_ARRAY);
            }

            builders = new AsyncFileEditorProvider.Builder[newProviders.length];
            for (int i = 0; i < newProviders.length; i++) {
                try {
                    FileEditorProvider provider = newProviders[i];
                    LOG.assertTrue(
                        provider != null,
                        "Provider for file " + file + " is null. All providers: " + Arrays.asList(newProviders)
                    );
                    ThrowableComputable<AsyncFileEditorProvider.Builder, RuntimeException> action = () -> {
                        if (myProject.isDisposed() || !file.isValid()) {
                            return null;
                        }
                        LOG.assertTrue(provider.accept(myProject, file), "Provider " + provider + " doesn't accept file " + file);
                        return provider instanceof AsyncFileEditorProvider asyncFileEditorProvider
                            ? asyncFileEditorProvider.createEditorAsync(myProject, file)
                            : null;
                    };
                    builders[i] = AccessRule.read(action);
                }
                catch (ProcessCanceledException e) {
                    throw e;
                }
                catch (Exception | AssertionError e) {
                    LOG.error(e);
                }
            }
        }
        else {
            newProviders = null;
            builders = null;
        }
        Runnable runnable = () -> {
            if (myProject.isDisposed() || !file.isValid()) {
                return;
            }

            compositeRef.set(window.findFileComposite(file));
            boolean newEditor = compositeRef.isNull();
            if (newEditor) {
                getProject().getMessageBus().syncPublisher(FileEditorManagerBeforeListener.class).beforeFileOpened(this, file);

                FileEditor[] newEditors = new FileEditor[newProviders.length];
                for (int i = 0; i < newProviders.length; i++) {
                    try {
                        FileEditorProvider provider = newProviders[i];
                        FileEditor editor = builders[i] == null ? provider.createEditor(myProject, file) : builders[i].build();
                        LOG.assertTrue(
                            editor.isValid(),
                            "Invalid editor created by provider " + (provider == null ? null : provider.getClass().getName())
                        );
                        newEditors[i] = editor;
                        // Register PropertyChangeListener into editor
                        editor.addPropertyChangeListener(myEditorPropertyChangeListener);
                        editor.putUserData(DUMB_AWARE, DumbService.isDumbAware(provider));
                    }
                    catch (ProcessCanceledException e) {
                        throw e;
                    }
                    catch (Exception | AssertionError e) {
                        LOG.error(e);
                    }
                }

                // Now we have to create EditorComposite and insert it into the TabbedEditorComponent.
                // After that we have to select opened editor.
                FileEditorWithProviderComposite composite = createComposite(file, newEditors, newProviders);
                if (composite == null) {
                    return;
                }

                if (index >= 0) {
                    composite.getFile().putUserData(FileEditorWindow.INITIAL_INDEX_KEY, index);
                }

                compositeRef.set(composite);
            }

            FileEditorWithProviderComposite composite = compositeRef.get();
            FileEditor[] editors = composite.getEditors();
            FileEditorProvider[] providers = composite.getProviders();

            window.setEditor(composite, current, focusEditor);

            for (int i = 0; i < editors.length; i++) {
                restoreEditorState(file, providers[i], editors[i], entry, newEditor);
            }

            // Restore selected editor
            FileEditorProvider selectedProvider;
            if (entry == null) {
                selectedProvider = ((FileEditorProviderManagerImpl)FileEditorProviderManager.getInstance()).getSelectedFileEditorProvider(
                    EditorHistoryManagerImpl.getInstance(myProject),
                    file,
                    providers
                );
            }
            else {
                selectedProvider = entry.getSelectedProvider();
            }
            if (selectedProvider != null) {
                for (int i = editors.length - 1; i >= 0; i--) {
                    FileEditorProvider provider = providers[i];
                    if (provider.equals(selectedProvider)) {
                        composite.setSelectedEditor(i);
                        break;
                    }
                }
            }

            // Notify editors about selection changes
            window.getOwner().setCurrentWindow(window, focusEditor);
            if (window.getOwner() instanceof FileEditorsSplittersBase fileEditorsSplittersBase) {
                fileEditorsSplittersBase.afterFileOpen(file);
            }
            addSelectionRecord(file, window);

            composite.getSelectedEditor().selectNotify();

            // Transfer focus into editor
            if (!myProject.getApplication().isUnitTestMode() && focusEditor) {
                //myFirstIsActive = myTabbedContainer1.equals(tabbedContainer);
                window.setAsCurrentWindow(true);
                ToolWindowManager.getInstance(myProject).activateEditorComponent();

                window.getOwner().toFront();
            }

            if (newEditor) {
                notifyPublisher(() -> {
                    if (isFileOpen(file)) {
                        getProject().getMessageBus().syncPublisher(FileEditorManagerListener.class).fileOpened(this, file);
                    }
                });
                ourOpenFilesSetModificationCount.incrementAndGet();
            }

            //[jeka] this is a hack to support back-forward navigation
            // previously here was incorrect call to fireSelectionChanged() with a side-effect
            ((IdeDocumentHistoryImpl)IdeDocumentHistory.getInstance(myProject)).onSelectionChanged();

            // Update frame and tab title
            updateFileName(file);

            // Make back/forward work
            IdeDocumentHistory.getInstance(myProject).includeCurrentCommandAsNavigation();

            if (pin != null) {
                window.setFilePinned(file, pin);
            }
        };

        uiAccess.giveAndWaitIfNeed(runnable);

        FileEditorWithProviderComposite composite = compositeRef.get();
        return Pair.create(
            composite == null ? EMPTY_EDITOR_ARRAY : composite.getEditors(),
            composite == null ? EMPTY_PROVIDER_ARRAY : composite.getProviders()
        );
    }

    @Nullable
    private FileEditorWithProviderComposite createComposite(
        @Nonnull VirtualFile file,
        @Nonnull FileEditor[] editors,
        @Nonnull FileEditorProvider[] providers
    ) {
        if (NullUtils.hasNull(editors) || NullUtils.hasNull(providers)) {
            List<FileEditor> editorList = new ArrayList<>(editors.length);
            List<FileEditorProvider> providerList = new ArrayList<>(providers.length);
            for (int i = 0; i < editors.length; i++) {
                FileEditor editor = editors[i];
                FileEditorProvider provider = providers[i];
                if (editor != null && provider != null) {
                    editorList.add(editor);
                    providerList.add(provider);
                }
            }
            if (editorList.isEmpty()) {
                return null;
            }
            editors = editorList.toArray(new FileEditor[editorList.size()]);
            providers = providerList.toArray(new FileEditorProvider[providerList.size()]);
        }
        return createEditorWithProviderComposite(file, editors, providers, this);
    }

    private void restoreEditorState(
        @Nonnull VirtualFile file,
        @Nonnull FileEditorProvider provider,
        @Nonnull FileEditor editor,
        HistoryEntry entry,
        boolean newEditor
    ) {
        FileEditorState state = null;
        if (entry != null) {
            state = entry.getState(provider);
        }
        if (state == null && newEditor) {
            // We have to try to get state from the history only in case
            // if editor is not opened. Otherwise history entry might have a state
            // out of sync with the current editor state.
            state = EditorHistoryManagerImpl.getInstance(myProject).getState(file, provider);
        }
        if (state != null) {
            if (!isDumbAware(editor)) {
                FileEditorState finalState = state;
                DumbService.getInstance(getProject()).runWhenSmart(() -> editor.setState(finalState));
            }
            else {
                editor.setState(state);
            }
        }
    }

    @Nonnull
    @Override
    public ActionCallback notifyPublisher(@Nonnull Runnable runnable) {
        IdeFocusManager focusManager = ProjectIdeFocusManager.getInstance(myProject);
        AsyncResult<Void> done = new AsyncResult<>();
        return myBusyObject.execute(new ActiveRunnable() {
            @Nonnull
            @Override
            public AsyncResult<Void> run() {
                focusManager.doWhenFocusSettlesDown(new ExpirableRunnable.ForProject(myProject) {
                    @Override
                    public void run() {
                        runnable.run();
                        done.setDone();
                    }
                });
                return done;
            }
        });
    }

    @Override
    @RequiredUIAccess
    public void setSelectedEditor(@Nonnull VirtualFile file, @Nonnull String fileEditorProviderId) {
        FileEditorWithProviderComposite composite = getCurrentEditorWithProviderComposite(file);
        if (composite == null) {
            List<FileEditorWithProviderComposite> composites = getEditorComposites(file);

            if (composites.isEmpty()) {
                return;
            }
            composite = composites.get(0);
        }

        FileEditorProvider[] editorProviders = composite.getProviders();
        FileEditorProvider selectedProvider = composite.getSelectedEditorWithProvider().getProvider();

        for (int i = 0; i < editorProviders.length; i++) {
            if (editorProviders[i].getEditorTypeId().equals(fileEditorProviderId) && !selectedProvider.equals(editorProviders[i])) {
                composite.setSelectedEditor(i);
                composite.getSelectedEditor().selectNotify();
            }
        }
    }

    @Nullable
    @RequiredUIAccess
    public FileEditorWithProviderComposite newEditorComposite(VirtualFile file) {
        if (file == null) {
            return null;
        }

        FileEditorProviderManager editorProviderManager = FileEditorProviderManager.getInstance();
        FileEditorProvider[] providers = editorProviderManager.getProviders(myProject, file);
        if (providers.length == 0) {
            return null;
        }
        FileEditor[] editors = new FileEditor[providers.length];
        for (int i = 0; i < providers.length; i++) {
            FileEditorProvider provider = providers[i];
            LOG.assertTrue(provider != null);
            LOG.assertTrue(provider.accept(myProject, file));
            FileEditor editor = provider.createEditor(myProject, file);
            editors[i] = editor;
            LOG.assertTrue(editor.isValid());
            editor.addPropertyChangeListener(myEditorPropertyChangeListener);
        }

        FileEditorWithProviderComposite newComposite = createEditorWithProviderComposite(file, editors, providers, this);
        EditorHistoryManagerImpl editorHistoryManager = EditorHistoryManagerImpl.getInstance(myProject);
        for (int i = 0; i < editors.length; i++) {
            FileEditor editor = editors[i];

            FileEditorProvider provider = providers[i];

// Restore myEditor state
            FileEditorState state = editorHistoryManager.getState(file, provider);
            if (state != null) {
                editor.setState(state);
            }
        }
        return newComposite;
    }

    @Nonnull
    @Override
    @RequiredUIAccess
    public List<FileEditor> openEditor(@Nonnull OpenFileDescriptor descriptor, boolean focusEditor) {
        assertDispatchThread();
        if (descriptor.getFile() instanceof VirtualFileWindow delegate) {
            int hostOffset = delegate.getDocumentWindow().injectedToHost(descriptor.getOffset());
            OpenFileDescriptorImpl realDescriptor =
                new OpenFileDescriptorImpl((Project)descriptor.getProject(), delegate.getDelegate(), hostOffset);
            realDescriptor.setUseCurrentWindow(descriptor.isUseCurrentWindow());
            return openEditor(realDescriptor, focusEditor);
        }

        List<FileEditor> result = new SmartList<>();
        CommandProcessor.getInstance().newCommand().project(myProject).run(() -> {
            VirtualFile file = descriptor.getFile();
            FileEditor[] editors = openFile(file, focusEditor, !descriptor.isUseCurrentWindow());
            ContainerUtil.addAll(result, editors);

            boolean navigated = false;
            for (FileEditor editor : editors) {
                if (editor instanceof NavigatableFileEditor navigatableFileEditor && getSelectedEditor(descriptor.getFile()) == editor) {
                    // try to navigate opened editor
                    navigated = navigateAndSelectEditor(navigatableFileEditor, descriptor);
                    if (navigated) {
                        break;
                    }
                }
            }

            if (!navigated) {
                for (FileEditor editor : editors) {
                    if (editor instanceof NavigatableFileEditor navigatableFileEditor
                        && getSelectedEditor(descriptor.getFile()) != editor
                        && navigateAndSelectEditor(navigatableFileEditor, descriptor)) {
                        break;
                    }
                }
            }
        });

        return result;
    }

    private boolean navigateAndSelectEditor(@Nonnull NavigatableFileEditor editor, @Nonnull OpenFileDescriptor descriptor) {
        if (editor.canNavigateTo(descriptor)) {
            setSelectedEditor(editor);
            editor.navigateTo(descriptor);
            return true;
        }

        return false;
    }

    private void setSelectedEditor(@Nonnull FileEditor editor) {
        FileEditorWithProviderComposite composite = getEditorComposite(editor);
        if (composite == null) {
            return;
        }

        FileEditor[] editors = composite.getEditors();
        for (int i = 0; i < editors.length; i++) {
            FileEditor each = editors[i];
            if (editor == each) {
                composite.setSelectedEditor(i);
                composite.getSelectedEditor().selectNotify();
                break;
            }
        }
    }

    @Override
    @Nonnull
    public Project getProject() {
        return myProject;
    }

    @Override
    @Nullable
    @RequiredUIAccess
    public Editor openTextEditor(@Nonnull OpenFileDescriptor descriptor, boolean focusEditor) {
        Collection<FileEditor> fileEditors = openEditor(descriptor, focusEditor);
        for (FileEditor fileEditor : fileEditors) {
            if (fileEditor instanceof TextEditor textEditor) {
                setSelectedEditor(descriptor.getFile(), TextEditorProvider.getInstance().getEditorTypeId());
                Editor editor = textEditor.getEditor();
                return getOpenedEditor(editor, focusEditor);
            }
        }

        return null;
    }

    protected Editor getOpenedEditor(@Nonnull Editor editor, boolean focusEditor) {
        return editor;
    }

    @Override
    @RequiredUIAccess
    public Editor getSelectedTextEditor(boolean requiredUIThread) {
        if (requiredUIThread) {
            assertDispatchThread();
        }

        FileEditorWindow currentWindow = requiredUIThread ? getMainSplitters().getCurrentWindow() : getSplitters().getCurrentWindow();
        if (currentWindow != null) {
            FileEditorWithProviderComposite selectedEditor = currentWindow.getSelectedEditor();
            if (selectedEditor != null && selectedEditor.getSelectedEditor() instanceof TextEditor textEditor) {
                return textEditor.getEditor();
            }
        }

        return null;
    }

    @Override
    public boolean isFileOpen(@Nonnull VirtualFile file) {
        return !getEditorComposites(file).isEmpty();
    }

    @Override
    @Nonnull
    public VirtualFile[] getOpenFiles() {
        Set<VirtualFile> openFiles = new HashSet<>();
        for (FileEditorsSplitters each : getAllSplitters()) {
            openFiles.addAll(Arrays.asList(each.getOpenFiles()));
        }
        return VirtualFileUtil.toVirtualFileArray(openFiles);
    }

    @Nonnull
    @Override
    @RequiredUIAccess
    public VirtualFile[] getSelectedFiles() {
        Set<VirtualFile> selectedFiles = new LinkedHashSet<>();
        FileEditorsSplitters activeSplitters = getSplitters();
        selectedFiles.addAll(Arrays.asList(activeSplitters.getSelectedFiles()));
        for (FileEditorsSplitters each : getAllSplitters()) {
            if (each != activeSplitters) {
                selectedFiles.addAll(Arrays.asList(each.getSelectedFiles()));
            }
        }
        return VirtualFileUtil.toVirtualFileArray(selectedFiles);
    }

    @Override
    @Nonnull
    public FileEditor[] getSelectedEditors() {
        Set<FileEditor> selectedEditors = new HashSet<>();
        for (FileEditorsSplitters each : getAllSplitters()) {
            selectedEditors.addAll(Arrays.asList(each.getSelectedEditors()));
        }

        return selectedEditors.toArray(new FileEditor[selectedEditors.size()]);
    }

    @Override
    @Nonnull
    @RequiredUIAccess
    public FileEditorsSplitters getSplitters() {
        FileEditorsSplitters active = null;
        if (UIAccess.isUIThread()) {
            active = getActiveSplittersSync();
        }
        return active == null ? getMainSplitters() : active;
    }

    @Nullable
    @Override
    @RequiredUIAccess
    public FileEditor getSelectedEditor(@Nonnull VirtualFile file) {
        FileEditorWithProvider selectedEditorWithProvider = getSelectedEditorWithProvider(file);
        return selectedEditorWithProvider == null ? null : selectedEditorWithProvider.getFileEditor();
    }

    @Nullable
    @Override
    @RequiredUIAccess
    public FileEditorWithProvider getSelectedEditorWithProvider(@Nonnull VirtualFile file) {
        if (file instanceof VirtualFileWindow virtualFileWindow) {
            file = virtualFileWindow.getDelegate();
        }
        FileEditorWithProviderComposite composite = getCurrentEditorWithProviderComposite(file);
        if (composite != null) {
            return composite.getSelectedEditorWithProvider();
        }

        List<FileEditorWithProviderComposite> composites = getEditorComposites(file);
        return composites.isEmpty() ? null : composites.get(0).getSelectedEditorWithProvider();
    }

    @Nonnull
    @Override
    @RequiredUIAccess
    public Pair<FileEditor[], FileEditorProvider[]> getEditorsWithProviders(@Nonnull VirtualFile file) {
        assertReadAccess();

        FileEditorWithProviderComposite composite = getCurrentEditorWithProviderComposite(file);
        if (composite != null) {
            return Pair.create(composite.getEditors(), composite.getProviders());
        }

        List<FileEditorWithProviderComposite> composites = getEditorComposites(file);
        if (!composites.isEmpty()) {
            return Pair.create(composites.get(0).getEditors(), composites.get(0).getProviders());
        }
        return Pair.create(EMPTY_EDITOR_ARRAY, EMPTY_PROVIDER_ARRAY);
    }

    @Override
    @Nonnull
    @RequiredUIAccess
    public FileEditor[] getEditors(@Nonnull VirtualFile file) {
        assertReadAccess();
        if (file instanceof VirtualFileWindow virtualFileWindow) {
            file = virtualFileWindow.getDelegate();
        }

        FileEditorWithProviderComposite composite = getCurrentEditorWithProviderComposite(file);
        if (composite != null) {
            return composite.getEditors();
        }

        List<FileEditorWithProviderComposite> composites = getEditorComposites(file);
        if (!composites.isEmpty()) {
            return composites.get(0).getEditors();
        }
        return EMPTY_EDITOR_ARRAY;
    }

    @Nonnull
    @Override
    public FileEditor[] getAllEditors(@Nonnull VirtualFile file) {
        List<FileEditorWithProviderComposite> editorComposites = getEditorComposites(file);
        if (editorComposites.isEmpty()) {
            return EMPTY_EDITOR_ARRAY;
        }
        List<FileEditor> editors = new ArrayList<>();
        for (FileEditorWithProviderComposite composite : editorComposites) {
            ContainerUtil.addAll(editors, composite.getEditors());
        }
        return editors.toArray(new FileEditor[editors.size()]);
    }

    @Nullable
    @RequiredUIAccess
    private FileEditorWithProviderComposite getCurrentEditorWithProviderComposite(@Nonnull VirtualFile virtualFile) {
        FileEditorWindow editorWindow = getSplitters().getCurrentWindow();
        if (editorWindow != null) {
            return editorWindow.findFileComposite(virtualFile);
        }
        return null;
    }

    @Nonnull
    private List<FileEditorWithProviderComposite> getEditorComposites(@Nonnull VirtualFile file) {
        List<FileEditorWithProviderComposite> result = new ArrayList<>();
        Set<FileEditorsSplitters> all = getAllSplitters();
        for (FileEditorsSplitters each : all) {
            result.addAll(each.findEditorComposites(file));
        }
        return result;
    }

    @Override
    @Nonnull
    @RequiredReadAction
    public FileEditor[] getAllEditors() {
        assertReadAccess();
        List<FileEditor> result = new ArrayList<>();
        Set<FileEditorsSplitters> allSplitters = getAllSplitters();
        for (FileEditorsSplitters splitter : allSplitters) {
            FileEditorWithProviderComposite[] editorsComposites = splitter.getEditorsComposites();
            for (FileEditorWithProviderComposite editorsComposite : editorsComposites) {
                FileEditor[] editors = editorsComposite.getEditors();
                ContainerUtil.addAll(result, editors);
            }
        }
        return result.toArray(new FileEditor[result.size()]);
    }

    @Nonnull
    public List<JComponent> getTopComponents(@Nonnull FileEditor editor) {
        FileEditorWithProviderComposite composite = getEditorComposite(editor);
        return composite != null ? composite.getTopComponents(editor) : Collections.emptyList();
    }

    @Override
    public void addTopComponent(@Nonnull FileEditor editor, @Nonnull JComponent component) {
        FileEditorWithProviderComposite composite = getEditorComposite(editor);
        if (composite != null) {
            composite.addTopComponent(editor, component);
        }
    }

    @Nullable
    @Override
    public Disposable addTopComponent(@Nonnull FileEditor editor, @Nonnull ComponentContainer component) {
        FileEditorWithProviderComposite composite = getEditorComposite(editor);
        if (composite != null) {
            return composite.addTopComponent(editor, component);
        }
        return null;
    }

    @Override
    public void removeTopComponent(@Nonnull FileEditor editor, @Nonnull JComponent component) {
        FileEditorWithProviderComposite composite = getEditorComposite(editor);
        if (composite != null) {
            composite.removeTopComponent(editor, component);
        }
    }

    @Override
    public void addBottomComponent(@Nonnull FileEditor editor, @Nonnull JComponent component) {
        FileEditorWithProviderComposite composite = getEditorComposite(editor);
        if (composite != null) {
            composite.addBottomComponent(editor, component);
        }
    }

    @Override
    public void removeBottomComponent(@Nonnull FileEditor editor, @Nonnull JComponent component) {
        FileEditorWithProviderComposite composite = getEditorComposite(editor);
        if (composite != null) {
            composite.removeBottomComponent(editor, component);
        }
    }

    @Override
    public void addFileEditorManagerListener(
        @Nonnull FileEditorManagerListener listener,
        @Nonnull Disposable parentDisposable
    ) {
        myProject.getMessageBus().connect(parentDisposable).subscribe(FileEditorManagerListener.class, listener);
    }

    protected void projectOpened(@Nonnull MessageBusConnection connection) {
        getMainSplitters().startListeningFocus();

        FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
        /* Updates tabs colors */
        MyFileStatusListener myFileStatusListener = new MyFileStatusListener();
        fileStatusManager.addFileStatusListener(myFileStatusListener, myProject);
        connection.subscribe(FileTypeListener.class, new MyFileTypeListener());
        connection.subscribe(ModuleRootListener.class, new MyRootsListener());

        /* Updates tabs names */
        MyVirtualFileListener myVirtualFileListener = new MyVirtualFileListener();
        VirtualFileManager.getInstance().addVirtualFileListener(myVirtualFileListener, myProject);
        /* Extends/cuts number of opened tabs. Also updates location of tabs. */
        connection.subscribe(UISettingsListener.class, new MyUISettingsListener());
    }

    @RequiredUIAccess
    @Nullable
    @Override
    public Element getStateFromUI() {
        if (mySplitters == null) {
            // do not save if not initialized yet
            return null;
        }

        Element state = new Element("state");
        getMainSplitters().writeExternal(state);
        return state;
    }

    @RequiredWriteAction
    @Nullable
    @Override
    public Element getState(Element element) {
        return element;
    }

    @Override
    public void loadState(Element state) {
        getMainSplitters().readExternal(state);
    }

    @Nullable
    private FileEditorWithProviderComposite getEditorComposite(@Nonnull FileEditor editor) {
        for (FileEditorsSplitters splitters : getAllSplitters()) {
            FileEditorWithProviderComposite[] editorsComposites = splitters.getEditorsComposites();
            for (int i = editorsComposites.length - 1; i >= 0; i--) {
                FileEditorWithProviderComposite composite = editorsComposites[i];
                FileEditor[] editors = composite.getEditors();
                for (int j = editors.length - 1; j >= 0; j--) {
                    FileEditor _editor = editors[j];
                    LOG.assertTrue(_editor != null);
                    if (editor.equals(_editor)) {
                        return composite;
                    }
                }
            }
        }
        return null;
    }

//======================= Misc =====================

    @RequiredUIAccess
    private static void assertDispatchThread() {
        UIAccess.assertIsUIThread();
    }

    @RequiredReadAction
    private static void assertReadAccess() {
        Application.get().assertReadAccessAllowed();
    }

    public void fireSelectionChanged(FileEditorComposite newSelectedComposite) {
        Trinity<VirtualFile, FileEditor, FileEditorProvider> oldData = extract(SoftReference.dereference(myLastSelectedComposite));
        Trinity<VirtualFile, FileEditor, FileEditorProvider> newData = extract(newSelectedComposite);
        myLastSelectedComposite = newSelectedComposite == null ? null : new WeakReference<>(newSelectedComposite);
        boolean filesEqual = oldData.first == null ? newData.first == null : oldData.first.equals(newData.first);
        boolean editorsEqual = oldData.second == null ? newData.second == null : oldData.second.equals(newData.second);
        if (!filesEqual || !editorsEqual) {
            if (oldData.first != null && newData.first != null) {
                boolean newDataNotFound = myProject.getApplication().getExtensionPoint(FileEditorAssociateFinder.class)
                    .noneMatchSafe(finder -> Objects.equals(finder.getAssociatedFileToOpen(myProject, oldData.first), newData.first));
                if (!newDataNotFound) {
                    return;
                }
            }

            FileEditorManagerEvent event = new FileEditorManagerEvent(
                this,
                oldData.first,
                oldData.second,
                oldData.third,
                newData.first,
                newData.second,
                newData.third
            );
            FileEditorManagerListener publisher = getProject().getMessageBus().syncPublisher(FileEditorManagerListener.class);

            if (newData.first != null) {
                DataContext context = DataManager.getInstance().getDataContext(newData.second.getUIComponent());

                FileEditorWindow editorWindow = context.getData(FileEditorWindow.DATA_KEY);
                if (editorWindow != null) {
                    addSelectionRecord(newData.first, editorWindow);
                }
            }
            notifyPublisher(() -> publisher.selectionChanged(event));
        }
    }

    @Nonnull
    private static Trinity<VirtualFile, FileEditor, FileEditorProvider> extract(@Nullable FileEditorComposite composite) {
        VirtualFile file;
        FileEditor editor;
        FileEditorProvider provider;
        if (composite == null || composite.isDisposed()) {
            file = null;
            editor = null;
            provider = null;
        }
        else {
            file = composite.getFile();
            FileEditorWithProvider pair = composite.getSelectedEditorWithProvider();
            editor = pair.getFileEditor();
            provider = pair.getProvider();
        }
        return new Trinity<>(file, editor, provider);
    }

    @Override
    public boolean isChanged(@Nonnull FileEditorComposite editor) {
        FileStatusManager fileStatusManager = FileStatusManager.getInstance(myProject);
        if (fileStatusManager == null) {
            return false;
        }
        FileStatus status = fileStatusManager.getStatus(editor.getFile());
        return status != FileStatus.UNKNOWN && status != FileStatus.NOT_CHANGED;
    }

    @RequiredUIAccess
    public void disposeComposite(@Nonnull FileEditorWithProviderComposite editor) {
        if (getAllEditors().length == 0) {
            setCurrentWindow(null);
        }

        if (editor.equals(getLastSelected())) {
            editor.getSelectedEditor().deselectNotify();
            getSplitters().setCurrentWindow(null, false);
        }

        FileEditor[] editors = editor.getEditors();
        FileEditorProvider[] providers = editor.getProviders();

        FileEditor selectedEditor = editor.getSelectedEditor();
        for (int i = editors.length - 1; i >= 0; i--) {
            FileEditor editor1 = editors[i];
            FileEditorProvider provider = providers[i];
            // we already notified the myEditor (when fire event)
            if (!editor.equals(selectedEditor) && selectedEditor.equals(editor1)) {
                editor1.deselectNotify();
            }
            editor1.removePropertyChangeListener(myEditorPropertyChangeListener);
            provider.disposeEditor(editor1);
        }

        Disposer.dispose(editor);
    }

    @Nullable
    @RequiredUIAccess
    private FileEditorComposite getLastSelected() {
        FileEditorWindow currentWindow = getActiveSplittersSync().getCurrentWindow();
        if (currentWindow != null) {
            return currentWindow.getSelectedEditor();
        }
        return null;
    }

    /**
     * @param splitters - taken getAllSplitters() value if parameter is null
     */
    public void runChange(@Nonnull Consumer<FileEditorsSplitters> change, @Nullable FileEditorsSplitters splitters) {
        Set<FileEditorsSplitters> target = new HashSet<>();
        if (splitters == null) {
            target.addAll(getAllSplitters());
        }
        else {
            target.add(splitters);
        }

        for (FileEditorsSplitters each : target) {
            AccessToken token = each.increaseChange();
            try {
                change.accept(each);
            }
            finally {
                token.finish();
            }
        }
    }

    //================== Listeners =====================

    /**
     * Closes deleted files. Closes file which are in the deleted directories.
     */
    private final class MyVirtualFileListener implements VirtualFileListener {
        @Override
        @RequiredUIAccess
        public void beforeFileDeletion(@Nonnull VirtualFileEvent e) {
            assertDispatchThread();

            VirtualFile file = e.getFile();
            VirtualFile[] openFiles = getOpenFiles();
            for (int i = openFiles.length - 1; i >= 0; i--) {
                if (VirtualFileUtil.isAncestor(file, openFiles[i], false)) {
                    closeFile(openFiles[i], true, true);
                }
            }
        }

        @Override
        @RequiredUIAccess
        public void propertyChanged(@Nonnull VirtualFilePropertyEvent e) {
            if (VirtualFile.PROP_NAME.equals(e.getPropertyName())) {
                assertDispatchThread();
                VirtualFile file = e.getFile();
                if (isFileOpen(file)) {
                    updateFileName(file);
                    updateFileIconAsync(file); // file type can change after renaming
                    updateFileBackgroundColor(file);
                }
            }
            else if (VirtualFile.PROP_WRITABLE.equals(e.getPropertyName()) || VirtualFile.PROP_ENCODING.equals(e.getPropertyName())) {
                // TODO: message bus?
                updateIconAndStatusBar(e);
            }
        }

        @RequiredUIAccess
        private void updateIconAndStatusBar(VirtualFilePropertyEvent e) {
            assertDispatchThread();
            VirtualFile file = e.getFile();
            if (isFileOpen(file)) {
                updateFileIconAsync(file);
                if (file.equals(getSelectedFiles()[0])) { // update "write" status
                    StatusBarEx statusBar = (StatusBarEx)WindowManager.getInstance().getStatusBar(myProject);
                    assert statusBar != null;
                    statusBar.updateWidgets();
                }
            }
        }

        @Override
        public void fileMoved(@Nonnull VirtualFileMoveEvent e) {
            VirtualFile file = e.getFile();
            VirtualFile[] openFiles = getOpenFiles();
            for (VirtualFile openFile : openFiles) {
                if (VirtualFileUtil.isAncestor(file, openFile, false)) {
                    updateFileName(openFile);
                    updateFileBackgroundColor(openFile);
                }
            }
        }
    }

    @Override
    @RequiredUIAccess
    public boolean isInsideChange() {
        return getSplitters().isInsideChange();
    }

    private final class MyEditorPropertyChangeListener implements PropertyChangeListener {
        @Override
        @RequiredUIAccess
        public void propertyChange(@Nonnull PropertyChangeEvent e) {
            assertDispatchThread();

            String propertyName = e.getPropertyName();
            if (FileEditor.PROP_MODIFIED.equals(propertyName)) {
                FileEditor editor = (FileEditor)e.getSource();
                FileEditorWithProviderComposite composite = getEditorComposite(editor);
                if (composite != null) {
                    updateFileIconAsync(composite.getFile());
                }
            }
            else if (FileEditor.PROP_VALID.equals(propertyName)) {
                boolean valid = (Boolean)e.getNewValue();
                if (!valid) {
                    FileEditor editor = (FileEditor)e.getSource();
                    LOG.assertTrue(editor != null);
                    FileEditorWithProviderComposite composite = getEditorComposite(editor);
                    if (composite != null) {
                        closeFile(composite.getFile());
                    }
                }
            }
        }
    }


    /**
     * Gets events from VCS and updates color of myEditor tabs
     */
    private final class MyFileStatusListener implements FileStatusListener {
        @Override
        @RequiredUIAccess
        public void fileStatusesChanged() { // update color of all open files
            assertDispatchThread();
            LOG.debug("FileEditorManagerImpl.MyFileStatusListener.fileStatusesChanged()");
            VirtualFile[] openFiles = getOpenFiles();
            for (int i = openFiles.length - 1; i >= 0; i--) {
                VirtualFile file = openFiles[i];
                LOG.assertTrue(file != null);
                myProject.getApplication().invokeLater(
                    () -> {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("updating file status in tab for " + file.getPath());
                        }
                        updateFileStatus(file);
                    },
                    ModalityState.nonModal(),
                    myProject.getDisposed()
                );
            }
        }

        @Override
        @RequiredUIAccess
        public void fileStatusChanged(@Nonnull VirtualFile file) { // update color of the file (if necessary)
            assertDispatchThread();
            if (isFileOpen(file)) {
                updateFileStatus(file);
            }
        }

        private void updateFileStatus(VirtualFile file) {
            updateFileColor(file);
            updateFileIconAsync(file);
        }
    }

    /**
     * Gets events from FileTypeManager and updates icons on tabs
     */
    private final class MyFileTypeListener implements FileTypeListener {
        @Override
        @RequiredUIAccess
        public void fileTypesChanged(@Nonnull FileTypeEvent event) {
            assertDispatchThread();
            VirtualFile[] openFiles = getOpenFiles();
            for (int i = openFiles.length - 1; i >= 0; i--) {
                VirtualFile file = openFiles[i];
                LOG.assertTrue(file != null);
                updateFileIconAsync(file);
            }
        }
    }

    private class MyRootsListener implements ModuleRootListener {
        private boolean myScheduled;

        @Override
        @RequiredUIAccess
        public void rootsChanged(ModuleRootEvent event) {
            if (myScheduled) {
                return;
            }
            myScheduled = true;

            UIAccess uiAccess = UIAccess.current();
            DumbService.getInstance(myProject).runWhenSmart(() -> {
                myScheduled = false;
                handleRootChange(uiAccess);
            });
        }

        @RequiredUIAccess
        private void handleRootChange(UIAccess uiAccess) {
            List<EditorFileSwapper> swappers = EditorFileSwapper.EP_NAME.getExtensionList();

            for (FileEditorWindow eachWindow : getWindows()) {
                FileEditorWithProviderComposite selected = eachWindow.getSelectedEditor();
                FileEditorWithProviderComposite[] editors = eachWindow.getEditors();
                for (int i = 0; i < editors.length; i++) {
                    FileEditorWithProviderComposite editor = editors[i];
                    VirtualFile file = editor.getFile();
                    if (!file.isValid()) {
                        continue;
                    }

                    Pair<VirtualFile, Integer> newFilePair = null;

                    for (EditorFileSwapper each : swappers) {
                        newFilePair = each.getFileToSwapTo(myProject, editor);
                        if (newFilePair != null) {
                            break;
                        }
                    }

                    if (newFilePair == null) {
                        continue;
                    }

                    VirtualFile newFile = newFilePair.first;
                    if (newFile == null) {
                        continue;
                    }

                    // already open
                    if (eachWindow.findFileIndex(newFile) != -1) {
                        continue;
                    }

                    try {
                        newFile.putUserData(FileEditorWindow.INITIAL_INDEX_KEY, i);
                        Pair<FileEditor[], FileEditorProvider[]> pair =
                            openFileImpl2(uiAccess, eachWindow, newFile, editor == selected);

                        if (newFilePair.second != null) {
                            TextEditor openedEditor = EditorFileSwapper.findSinglePsiAwareEditor(pair.first);
                            if (openedEditor != null) {
                                openedEditor.getEditor().getCaretModel().moveToOffset(newFilePair.second);
                                openedEditor.getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);
                            }
                        }
                    }
                    finally {
                        newFile.putUserData(FileEditorWindow.INITIAL_INDEX_KEY, null);
                    }
                    closeFile(file, eachWindow);
                }
            }
        }
    }

    /**
     * Gets notifications from UISetting component to track changes of RECENT_FILES_LIMIT
     * and EDITOR_TAB_LIMIT, etc values.
     */
    private final class MyUISettingsListener implements UISettingsListener {
        @Override
        @RequiredUIAccess
        public void uiSettingsChanged(UISettings uiSettings) {
            assertDispatchThread();
            getMainSplitters().revalidate();

            for (FileEditorsSplitters each : getAllSplitters()) {
                each.setTabsPlacement(uiSettings.getEditorTabPlacement());
                each.trimToSize(uiSettings.getEditorTabLimit());

                // Tab layout policy
                if (uiSettings.getScrollTabLayoutInEditor()) {
                    each.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
                }
                else {
                    each.setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
                }
            }

            // "Mark modified files with asterisk"
            VirtualFile[] openFiles = getOpenFiles();
            for (int i = openFiles.length - 1; i >= 0; i--) {
                VirtualFile file = openFiles[i];
                updateFileIconAsync(file);
                updateFileName(file);
                updateFileBackgroundColor(file);
            }
        }
    }

    @Override
    @RequiredUIAccess
    public void closeAllFiles() {
        VirtualFile[] openFiles = getSplitters().getOpenFiles();
        for (VirtualFile openFile : openFiles) {
            closeFile(openFile);
        }
    }

    @Override
    @Nonnull
    public VirtualFile[] getSiblings(@Nonnull VirtualFile file) {
        return getOpenFiles();
    }

    public void queueUpdateFile(@Nonnull VirtualFile file) {
        myQueue.queue(() -> {
            if (isFileOpen(file)) {
                updateFileIconAsync(file);
                updateFileColor(file);
                updateFileBackgroundColor(file);
            }
        });
    }

    @Override
    public FileEditorsSplitters getSplittersFor(Component c) {
        FileEditorsSplitters splitters = null;
        DockContainer dockContainer = myDockManager.getContainerFor(c);
        if (dockContainer instanceof DockableEditorTabbedContainer dockableEditorTabbedContainer) {
            splitters = dockableEditorTabbedContainer.getSplitters();
        }

        if (splitters == null) {
            splitters = getMainSplitters();
        }

        return splitters;
    }

    @Nonnull
    public List<Pair<VirtualFile, FileEditorWindow>> getSelectionHistory() {
        List<Pair<VirtualFile, FileEditorWindow>> copy = new ArrayList<>();
        for (Pair<VirtualFile, FileEditorWindow> pair : mySelectionHistory) {
            if (pair.second.getFiles().length == 0) {
                FileEditorWindow[] windows = pair.second.getOwner().getWindows();
                if (windows.length > 0 && windows[0] != null && windows[0].getFiles().length > 0) {
                    Pair<VirtualFile, FileEditorWindow> p = Pair.create(pair.first, windows[0]);
                    if (!copy.contains(p)) {
                        copy.add(p);
                    }
                }
            }
            else if (!copy.contains(pair)) {
                copy.add(pair);
            }
        }
        mySelectionHistory.clear();
        mySelectionHistory.addAll(copy);
        return mySelectionHistory;
    }

    public void addSelectionRecord(@Nonnull VirtualFile file, @Nonnull FileEditorWindow window) {
        Pair<VirtualFile, FileEditorWindow> record = Pair.create(file, window);
        mySelectionHistory.remove(record);
        mySelectionHistory.add(0, record);
    }

    public void removeSelectionRecord(@Nonnull VirtualFile file, @Nonnull FileEditorWindow window) {
        mySelectionHistory.remove(Pair.create(file, window));
        updateFileName(file);
    }

    @Nonnull
    @Override
    public AsyncResult<Void> getReady(@Nonnull Object requestor) {
        return myBusyObject.getReady(requestor);
    }
}
