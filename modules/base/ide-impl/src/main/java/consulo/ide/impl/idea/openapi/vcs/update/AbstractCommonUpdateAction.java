/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package consulo.ide.impl.idea.openapi.vcs.update;

import consulo.application.Application;
import consulo.application.progress.EmptyProgressIndicator;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.component.ProcessCanceledException;
import consulo.configurable.UnnamedConfigurable;
import consulo.content.scope.NamedScope;
import consulo.ide.impl.idea.openapi.vcs.actions.AbstractVcsAction;
import consulo.ide.impl.idea.openapi.vcs.actions.DescindingFilesFilter;
import consulo.ide.impl.idea.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.localHistory.Label;
import consulo.localHistory.LocalHistory;
import consulo.localHistory.LocalHistoryAction;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.StoreReloadManager;
import consulo.project.ui.notification.Notification;
import consulo.project.ui.notification.NotificationService;
import consulo.project.util.WaitForProgressToShow;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionUpdateThread;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.awt.OptionsDialog;
import consulo.ui.ex.errorTreeView.HotfixData;
import consulo.util.collection.MultiMap;
import consulo.util.lang.ref.Ref;
import consulo.versionControlSystem.*;
import consulo.versionControlSystem.action.VcsContext;
import consulo.versionControlSystem.change.VcsAnnotationRefresher;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import consulo.versionControlSystem.impl.internal.change.RemoteRevisionsCache;
import consulo.versionControlSystem.impl.internal.change.commited.CommittedChangesCache;
import consulo.versionControlSystem.impl.internal.update.RestoreUpdateTree;
import consulo.versionControlSystem.internal.ProjectLevelVcsManagerEx;
import consulo.versionControlSystem.localize.VcsLocalize;
import consulo.versionControlSystem.ui.UpdateOrStatusOptionsDialog;
import consulo.versionControlSystem.ui.ViewUpdateInfoNotification;
import consulo.versionControlSystem.update.*;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.util.*;

import static consulo.util.lang.ObjectUtil.notNull;
import static consulo.util.lang.StringUtil.pluralize;
import static consulo.versionControlSystem.VcsNotifier.STANDARD_NOTIFICATION;

public abstract class AbstractCommonUpdateAction extends AbstractVcsAction {
    private final boolean myAlwaysVisible;
    private final static Logger LOG = Logger.getInstance(AbstractCommonUpdateAction.class);

    private final ActionInfo myActionInfo;
    private final ScopeInfo myScopeInfo;

    protected AbstractCommonUpdateAction(ActionInfo actionInfo, ScopeInfo scopeInfo, boolean alwaysVisible) {
        myActionInfo = actionInfo;
        myScopeInfo = scopeInfo;
        myAlwaysVisible = alwaysVisible;
    }

    @Nonnull
    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private String getCompleteActionName(VcsContext dataContext) {
        return myActionInfo.getActionName(myScopeInfo.getScopeName(dataContext, myActionInfo));
    }

    @Override
    @RequiredUIAccess
    protected void actionPerformed(@Nonnull VcsContext context) {
        Project project = context.getProject();

        boolean showUpdateOptions = myActionInfo.showOptions(project);

        LOG.debug(String.format("project: %s, show update options: %s", project, showUpdateOptions));

        if (project != null) {
            try {
                FilePath[] filePaths = myScopeInfo.getRoots(context, myActionInfo);
                FilePath[] roots = DescindingFilesFilter.filterDescindingFiles(filterRoots(filePaths, context), project);
                if (roots.length == 0) {
                    LOG.debug("No roots found.");
                    return;
                }

                Map<AbstractVcs, Collection<FilePath>> vcsToVirtualFiles = createVcsToFilesMap(roots, project);

                for (AbstractVcs vcs : vcsToVirtualFiles.keySet()) {
                    UpdateEnvironment updateEnvironment = myActionInfo.getEnvironment(vcs);
                    if ((updateEnvironment != null) && (!updateEnvironment.validateOptions(vcsToVirtualFiles.get(vcs)))) {
                        // messages already shown
                        LOG.debug("Options not valid for files: " + vcsToVirtualFiles);
                        return;
                    }
                }

                if (showUpdateOptions || OptionsDialog.shiftIsPressed(context.getModifiers())) {
                    showOptionsDialog(vcsToVirtualFiles, project, context);
                }

                Application application = project.getApplication();
                if (application.isDispatchThread()) {
                    application.saveAll();
                }
                Task.Backgroundable task = new Updater(project, roots, vcsToVirtualFiles);
                if (application.isUnitTestMode()) {
                    task.run(new EmptyProgressIndicator());
                }
                else {
                    ProgressManager.getInstance().run(task);
                }
            }
            catch (ProcessCanceledException ignored) {
            }
        }
    }

    private boolean canGroupByChangelist(Set<AbstractVcs> abstractVcses) {
        if (myActionInfo.canGroupByChangelist()) {
            for (AbstractVcs vcs : abstractVcses) {
                if (vcs.getCachingCommittedChangesProvider() != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean someSessionWasCanceled(List<UpdateSession> updateSessions) {
        for (UpdateSession updateSession : updateSessions) {
            if (updateSession.isCanceled()) {
                return true;
            }
        }
        return false;
    }

    private static String getAllFilesAreUpToDateMessage(FilePath[] roots) {
        if (roots.length == 1 && !roots[0].isDirectory()) {
            return VcsLocalize.messageTextFileIsUpToDate().get();
        }
        else {
            return VcsLocalize.messageTextAllFilesAreUpToDate().get();
        }
    }

    @RequiredUIAccess
    private void showOptionsDialog(
        Map<AbstractVcs, Collection<FilePath>> updateEnvToVirtualFiles,
        Project project,
        VcsContext dataContext
    ) {
        LinkedHashMap<UnnamedConfigurable, AbstractVcs> envToConfMap = createConfigurableToEnvMap(updateEnvToVirtualFiles);
        LOG.debug("configurables map: " + envToConfMap);
        if (!envToConfMap.isEmpty()) {
            UpdateOrStatusOptionsDialog dialogOrStatus = myActionInfo.createOptionsDialog(
                project,
                envToConfMap,
                myScopeInfo.getScopeName(dataContext, myActionInfo)
            );
            if (!dialogOrStatus.showAndGet()) {
                throw new ProcessCanceledException();
            }
        }
    }

    private LinkedHashMap<UnnamedConfigurable, AbstractVcs> createConfigurableToEnvMap(Map<AbstractVcs, Collection<FilePath>> updateEnvToVirtualFiles) {
        LinkedHashMap<UnnamedConfigurable, AbstractVcs> envToConfMap = new LinkedHashMap<>();
        for (AbstractVcs vcs : updateEnvToVirtualFiles.keySet()) {
            UnnamedConfigurable configurable = myActionInfo.getEnvironment(vcs).createConfigurable(updateEnvToVirtualFiles.get(vcs));
            if (configurable != null) {
                envToConfMap.put(configurable, vcs);
            }
        }
        return envToConfMap;
    }

    private Map<AbstractVcs, Collection<FilePath>> createVcsToFilesMap(@Nonnull FilePath[] roots, @Nonnull Project project) {
        MultiMap<AbstractVcs, FilePath> resultPrep = MultiMap.createSet();
        for (FilePath file : roots) {
            AbstractVcs vcs = VcsUtil.getVcsFor(project, file);
            if (vcs != null) {
                UpdateEnvironment updateEnvironment = myActionInfo.getEnvironment(vcs);
                if (updateEnvironment != null) {
                    resultPrep.putValue(vcs, file);
                }
            }
        }

        Map<AbstractVcs, Collection<FilePath>> result = new HashMap<>();
        for (Map.Entry<AbstractVcs, Collection<FilePath>> entry : resultPrep.entrySet()) {
            AbstractVcs<?> vcs = entry.getKey();
            result.put(vcs, vcs.filterUniqueRoots(new ArrayList<>(entry.getValue()), FilePath::getVirtualFile));
        }
        return result;
    }

    @Nonnull
    private FilePath[] filterRoots(FilePath[] roots, VcsContext vcsContext) {
        List<FilePath> result = new ArrayList<>();
        Project project = vcsContext.getProject();
        assert project != null;
        for (FilePath file : roots) {
            AbstractVcs vcs = VcsUtil.getVcsFor(project, file);
            if (vcs != null) {
                if (!myScopeInfo.filterExistsInVcs() || AbstractVcs.fileInVcsByFileStatus(project, file)) {
                    UpdateEnvironment updateEnvironment = myActionInfo.getEnvironment(vcs);
                    if (updateEnvironment != null) {
                        result.add(file);
                    }
                }
                else {
                    VirtualFile virtualFile = file.getVirtualFile();
                    if (virtualFile != null && virtualFile.isDirectory()) {
                        VirtualFile[] vcsRoots = ProjectLevelVcsManager.getInstance(vcsContext.getProject()).getAllVersionedRoots();
                        for (VirtualFile vcsRoot : vcsRoots) {
                            if (VfsUtilCore.isAncestor(virtualFile, vcsRoot, false)) {
                                result.add(file);
                            }
                        }
                    }
                }
            }
        }
        return result.toArray(new FilePath[result.size()]);
    }

    protected abstract boolean filterRootsBeforeAction();

    @Override
    protected void update(@Nonnull VcsContext vcsContext, @Nonnull Presentation presentation) {
        Project project = vcsContext.getProject();

        if (project != null) {
            ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
            boolean underVcs = vcsManager.hasActiveVcss();
            if (!underVcs) {
                presentation.setVisible(false);
                return;
            }

            String actionName = getCompleteActionName(vcsContext);
            if (myActionInfo.showOptions(project) || OptionsDialog.shiftIsPressed(vcsContext.getModifiers())) {
                actionName += "...";
            }

            presentation.setText(actionName);

            presentation.setEnabledAndVisible(true);

            if (supportingVcsesAreEmpty(vcsManager, myActionInfo)) {
                presentation.setVisible(myAlwaysVisible);
                presentation.setEnabled(false);
                return;
            }

            if (filterRootsBeforeAction()) {
                FilePath[] roots = filterRoots(myScopeInfo.getRoots(vcsContext, myActionInfo), vcsContext);
                if (roots.length == 0) {
                    presentation.setVisible(myAlwaysVisible);
                    presentation.setEnabled(false);
                    return;
                }
            }

            if (presentation.isVisible() && presentation.isEnabled() &&
                vcsManager.isBackgroundVcsOperationRunning()) {
                presentation.setEnabled(false);
            }
        }
        else {
            presentation.setEnabledAndVisible(false);
        }
    }

    private static boolean supportingVcsesAreEmpty(ProjectLevelVcsManager vcsManager, ActionInfo actionInfo) {
        AbstractVcs[] allActiveVcss = vcsManager.getAllActiveVcss();
        for (AbstractVcs activeVcs : allActiveVcss) {
            if (actionInfo.getEnvironment(activeVcs) != null) {
                return false;
            }
        }
        return true;
    }

    private class Updater extends Task.Backgroundable {
        private final LocalizeValue LOCAL_HISTORY_ACTION = VcsLocalize.localHistoryUpdateFromVcs();

        private final Project myProject;
        private final ProjectLevelVcsManagerImpl myProjectLevelVcsManager;
        private UpdatedFiles myUpdatedFiles;
        private final FilePath[] myRoots;
        private final Map<AbstractVcs, Collection<FilePath>> myVcsToVirtualFiles;
        private final Map<HotfixData, List<VcsException>> myGroupedExceptions;
        private final List<UpdateSession> myUpdateSessions;
        private int myUpdateNumber;

        // vcs name, context object
        private final Map<AbstractVcs, SequentialUpdatesContext> myContextInfo;
        private final VcsDirtyScopeManager myDirtyScopeManager;

        private Label myBefore;
        private Label myAfter;
        private LocalHistoryAction myLocalHistoryAction;

        public Updater(Project project, FilePath[] roots, Map<AbstractVcs, Collection<FilePath>> vcsToVirtualFiles) {
            super(project, getTemplatePresentation().getTextValue(), true, VcsConfiguration.getInstance(project).getUpdateOption());
            myProject = project;
            myProjectLevelVcsManager = (ProjectLevelVcsManagerImpl) ProjectLevelVcsManagerEx.getInstanceEx(project);
            myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
            myRoots = roots;
            myVcsToVirtualFiles = vcsToVirtualFiles;

            myUpdatedFiles = UpdatedFiles.create();
            myGroupedExceptions = new HashMap<>();
            myUpdateSessions = new ArrayList<>();

            // create from outside without any context; context is created by vcses
            myContextInfo = new HashMap<>();
            myUpdateNumber = 1;
        }

        private void reset() {
            myUpdatedFiles = UpdatedFiles.create();
            myGroupedExceptions.clear();
            myUpdateSessions.clear();
            ++myUpdateNumber;
        }

        @Override
        public void run(@Nonnull ProgressIndicator indicator) {
            runImpl();
        }

        private void runImpl() {
            StoreReloadManager storeReloadManager = StoreReloadManager.getInstance((Project) getProject());

            storeReloadManager.blockReloadingProjectOnExternalChanges();
            myProjectLevelVcsManager.startBackgroundVcsOperation();

            myBefore = LocalHistory.getInstance().putSystemLabel(myProject, LocalizeValue.localizeTODO("Before update"));
            myLocalHistoryAction = LocalHistory.getInstance().startAction(LOCAL_HISTORY_ACTION);
            ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();

            try {
                int toBeProcessed = myVcsToVirtualFiles.size();
                int processed = 0;
                for (AbstractVcs vcs : myVcsToVirtualFiles.keySet()) {
                    UpdateEnvironment updateEnvironment = myActionInfo.getEnvironment(vcs);
                    updateEnvironment.fillGroups(myUpdatedFiles);
                    Collection<FilePath> files = myVcsToVirtualFiles.get(vcs);

                    SequentialUpdatesContext context = myContextInfo.get(vcs);
                    Ref<SequentialUpdatesContext> refContext = new Ref<>(context);

                    // actual update
                    UpdateSession updateSession =
                        updateEnvironment.updateDirectories(files.toArray(new FilePath[files.size()]), myUpdatedFiles, progressIndicator, refContext);

                    myContextInfo.put(vcs, refContext.get());
                    processed++;
                    if (progressIndicator != null) {
                        progressIndicator.setFraction((double) processed / (double) toBeProcessed);
                        progressIndicator.setText2Value(LocalizeValue.empty());
                    }
                    List<VcsException> exceptionList = updateSession.getExceptions();
                    gatherExceptions(vcs, exceptionList);
                    myUpdateSessions.add(updateSession);
                }
            }
            finally {
                try {
                    ProgressManager.progress(VcsLocalize.progressTextSynchronizingFiles());
                    doVfsRefresh();
                }
                finally {
                    myProjectLevelVcsManager.stopBackgroundVcsOperation();
                    if (!myProject.isDisposed()) {
                        myProject.getMessageBus().syncPublisher(UpdatedFilesListener.class).
                            consume(UpdatedFilesReverseSide.getPathsFromUpdatedFiles(myUpdatedFiles));
                    }
                }
            }
        }

        private void gatherExceptions(AbstractVcs vcs, List<VcsException> exceptionList) {
            VcsExceptionsHotFixer fixer = vcs.getVcsExceptionsHotFixer();
            if (fixer == null) {
                putExceptions(null, exceptionList);
            }
            else {
                putExceptions(fixer.groupExceptions(ActionType.update, exceptionList));
            }
        }

        private void putExceptions(Map<HotfixData, List<VcsException>> map) {
            for (Map.Entry<HotfixData, List<VcsException>> entry : map.entrySet()) {
                putExceptions(entry.getKey(), entry.getValue());
            }
        }

        private void putExceptions(HotfixData key, @Nonnull List<VcsException> list) {
            if (list.isEmpty()) {
                return;
            }
            myGroupedExceptions.computeIfAbsent(key, k -> new ArrayList<>()).addAll(list);
        }

        private void doVfsRefresh() {
            LOG.info("Calling refresh files after update for roots: " + Arrays.toString(myRoots));
            RefreshVFsSynchronously.updateAllChanged(myUpdatedFiles);
            notifyAnnotations();
        }

        private void notifyAnnotations() {
            VcsAnnotationRefresher refresher = myProject.getMessageBus().syncPublisher(VcsAnnotationRefresher.class);
            UpdateFilesHelper.iterateFileGroupFilesDeletedOnServerFirst(myUpdatedFiles, (filePath, groupId) -> refresher.dirty(filePath));
        }

        @Nonnull
        private Notification.Builder prepareNotification(@Nonnull UpdateInfoTreeImpl tree, boolean someSessionWasCancelled) {
            int allFiles = getUpdatedFilesCount();

            NotificationService notificationService = NotificationService.getInstance();
            if (someSessionWasCancelled) {
                return notificationService.newWarn(STANDARD_NOTIFICATION)
                    .title(LocalizeValue.localizeTODO("Project Partially Updated"))
                    .content(LocalizeValue.localizeTODO(allFiles + " " + pluralize("file", allFiles) + " updated"));
            }
            else {
                return notificationService.newInfo(STANDARD_NOTIFICATION)
                    .title(LocalizeValue.localizeTODO(allFiles + " Project " + pluralize("File", allFiles) + " Updated"))
                    .content(LocalizeValue.ofNullable(prepareScopeUpdatedText(tree)));
            }
        }

        private int getUpdatedFilesCount() {
            return myUpdatedFiles.getTopLevelGroups().stream().mapToInt(this::getFilesCount).sum();
        }

        private int getFilesCount(@Nonnull FileGroup group) {
            return group.getFiles().size() + group.getChildren().stream().mapToInt(this::getFilesCount).sum();
        }

        @Nullable
        private String prepareScopeUpdatedText(@Nonnull UpdateInfoTreeImpl tree) {
            String scopeText = null;
            NamedScope scopeFilter = tree.getFilterScope();
            if (scopeFilter != null) {
                int filteredFiles = tree.getFilteredFilesCount();
                String filterName = scopeFilter.getName();
                if (filteredFiles == 0) {
                    scopeText = filterName + " wasn't modified";
                }
                else {
                    scopeText = filteredFiles + " in " + filterName;
                }
            }
            return scopeText;
        }

        @Override
        @RequiredUIAccess
        public void onSuccess() {
            onSuccessImpl(false);
        }

        private void onSuccessImpl(boolean wasCanceled) {
            StoreReloadManager storeReloadManager = StoreReloadManager.getInstance((Project) getProject());
            if (!myProject.isOpen() || myProject.isDisposed()) {
                storeReloadManager.unblockReloadingProjectOnExternalChanges();
                LocalHistory.getInstance().putSystemLabel(myProject, LOCAL_HISTORY_ACTION); // TODO check why this label is needed
                return;
            }
            boolean continueChain = false;
            for (SequentialUpdatesContext context : myContextInfo.values()) {
                continueChain |= (context != null) && (context.shouldFail());
            }
            boolean continueChainFinal = continueChain;

            boolean someSessionWasCancelled = wasCanceled || someSessionWasCanceled(myUpdateSessions);
            // here text conflicts might be interactively resolved
            for (UpdateSession updateSession : myUpdateSessions) {
                updateSession.onRefreshFilesCompleted();
            }
            // only after conflicts are resolved, put a label
            if (myLocalHistoryAction != null) {
                myLocalHistoryAction.finish();
            }
            myAfter = LocalHistory.getInstance().putSystemLabel(myProject, LocalizeValue.localizeTODO("After update"));

            if (myActionInfo.canChangeFileStatus()) {
                List<VirtualFile> files = new ArrayList<>();
                RemoteRevisionsCache revisionsCache = RemoteRevisionsCache.getInstance(myProject);
                revisionsCache.invalidate(myUpdatedFiles);
                UpdateFilesHelper.iterateFileGroupFiles(myUpdatedFiles, (filePath, groupId) -> {
                    String path = VfsUtilCore.pathToUrl(filePath.replace(File.separatorChar, '/'));
                    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(path);
                    if (file != null) {
                        files.add(file);
                    }
                });
                myDirtyScopeManager.filesDirty(files, null);
            }

            boolean updateSuccess = !someSessionWasCancelled && myGroupedExceptions.isEmpty();

            WaitForProgressToShow.runOrInvokeLaterAboveProgress(
                () -> {
                    if (myProject.isDisposed()) {
                        storeReloadManager.unblockReloadingProjectOnExternalChanges();
                        return;
                    }

                    if (!myGroupedExceptions.isEmpty()) {
                        if (continueChainFinal) {
                            gatherContextInterruptedMessages();
                        }
                        AbstractVcsHelper.getInstance(myProject).showErrors(
                            myGroupedExceptions,
                            VcsLocalize.messageTitleVcsUpdateErrors(getTemplatePresentation().getText())
                        );
                    }
                    else if (someSessionWasCancelled) {
                        ProgressManager.progress(VcsLocalize.progressTextUpdatingCanceled());
                    }
                    else {
                        ProgressManager.progress(VcsLocalize.progressTextUpdatingDone());
                    }

                    boolean noMerged = myUpdatedFiles.getGroupById(FileGroup.MERGED_WITH_CONFLICT_ID).isEmpty();
                    if (myUpdatedFiles.isEmpty() && myGroupedExceptions.isEmpty()) {
                        NotificationService notificationService = NotificationService.getInstance();
                        if (someSessionWasCancelled) {
                            notificationService.newWarn(STANDARD_NOTIFICATION)
                                .content(VcsLocalize.progressTextUpdatingCanceled())
                                .notify(myProject);
                        }
                        else {
                            notificationService.newInfo(STANDARD_NOTIFICATION)
                                .content(LocalizeValue.localizeTODO(getAllFilesAreUpToDateMessage(myRoots)))
                                .notify(myProject);
                        }
                    }
                    else if (!myUpdatedFiles.isEmpty()) {
                        UpdateInfoTreeImpl tree =
                            showUpdateTree(continueChainFinal && updateSuccess && noMerged, someSessionWasCancelled);
                        CommittedChangesCache cache = CommittedChangesCache.getInstance(myProject);
                        cache.processUpdatedFiles(myUpdatedFiles, tree::setChangeLists);

                        prepareNotification(tree, someSessionWasCancelled)
                            .addAction(notification -> new ViewUpdateInfoNotification(
                                myProject,
                                tree,
                                LocalizeValue.localizeTODO("View"),
                                notification
                            ))
                            .notify(myProject);
                    }

                    storeReloadManager.unblockReloadingProjectOnExternalChanges();

                    if (continueChainFinal && updateSuccess) {
                        if (!noMerged) {
                            showContextInterruptedError();
                        }
                        else {
                            // trigger next update; for CVS when updating from several branches simultaneously
                            reset();
                            ProgressManager.getInstance().run(this);
                        }
                    }
                },
                null,
                myProject
            );
        }


        private void showContextInterruptedError() {
            gatherContextInterruptedMessages();
            AbstractVcsHelper.getInstance(myProject).showErrors(
                myGroupedExceptions,
                VcsLocalize.messageTitleVcsUpdateErrors(getTemplatePresentation().getText())
            );
        }

        private void gatherContextInterruptedMessages() {
            for (Map.Entry<AbstractVcs, SequentialUpdatesContext> entry : myContextInfo.entrySet()) {
                SequentialUpdatesContext context = entry.getValue();
                if ((context == null) || (!context.shouldFail())) {
                    continue;
                }
                VcsException exception = new VcsException(context.getMessageWhenInterruptedBeforeStart());
                gatherExceptions(entry.getKey(), Collections.singletonList(exception));
            }
        }

        @Nonnull
        @RequiredUIAccess
        private UpdateInfoTreeImpl showUpdateTree(boolean willBeContinued, boolean wasCanceled) {
            RestoreUpdateTree restoreUpdateTree = RestoreUpdateTree.getInstance(myProject);
            restoreUpdateTree.registerUpdateInformation(myUpdatedFiles, myActionInfo);
            String text = getTemplatePresentation().getText() + ((willBeContinued || (myUpdateNumber > 1)) ? ("#" + myUpdateNumber) : "");
            UpdateInfoTreeImpl updateInfoTree =
                notNull(myProjectLevelVcsManager.showUpdateProjectInfo(myUpdatedFiles, text, myActionInfo, wasCanceled));
            updateInfoTree.setBefore(myBefore);
            updateInfoTree.setAfter(myAfter);
            updateInfoTree.setCanGroupByChangeList(canGroupByChangelist(myVcsToVirtualFiles.keySet()));
            return updateInfoTree;
        }

        @Override
        @RequiredUIAccess
        public void onCancel() {
            onSuccessImpl(true);
        }
    }
}
