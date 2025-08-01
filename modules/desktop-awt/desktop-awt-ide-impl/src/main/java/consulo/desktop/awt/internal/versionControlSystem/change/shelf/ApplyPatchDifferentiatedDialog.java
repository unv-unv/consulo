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
package consulo.desktop.awt.internal.versionControlSystem.change.shelf;

import consulo.application.AllIcons;
import consulo.application.Application;
import consulo.application.impl.internal.IdeaModalityState;
import consulo.application.progress.ProgressIndicator;
import consulo.application.util.function.ThrowableComputable;
import consulo.component.ProcessCanceledException;
import consulo.diff.DiffDialogHints;
import consulo.diff.DiffManager;
import consulo.diff.chain.DiffRequestChain;
import consulo.diff.chain.DiffRequestProducer;
import consulo.diff.chain.DiffRequestProducerException;
import consulo.diff.request.DiffRequest;
import consulo.disposer.Disposer;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.IdeaFileChooser;
import consulo.ide.impl.idea.diff.actions.impl.GoToChangePopupBuilder;
import consulo.ide.impl.idea.ide.util.PropertiesComponent;
import consulo.ide.impl.idea.openapi.diff.impl.patch.*;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.versionControlSystem.change.patch.FilePatch;
import consulo.versionControlSystem.change.patch.TextFilePatch;
import consulo.versionControlSystem.impl.internal.util.ZipperUpdater;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.ide.impl.idea.openapi.vcs.changes.actions.diff.ChangeGoToChangePopupAction;
import consulo.ide.impl.idea.openapi.vcs.changes.patch.*;
import consulo.ide.impl.idea.openapi.vcs.changes.shelf.ShelvedBinaryFilePatch;
import consulo.ide.impl.idea.openapi.vcs.changes.ui.*;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.language.plain.PlainTextFileType;
import consulo.logging.Logger;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.ui.ex.awt.util.Alarm;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.PopupStep;
import consulo.util.collection.MultiMap;
import consulo.util.dataholder.UserDataHolder;
import consulo.util.dataholder.UserDataHolderBase;
import consulo.util.lang.Couple;
import consulo.util.lang.EmptyRunnable;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.versionControlSystem.change.ChangesUtil;
import consulo.versionControlSystem.change.LocalChangeList;
import consulo.versionControlSystem.impl.internal.change.ui.awt.*;
import consulo.versionControlSystem.localize.VcsLocalize;
import consulo.versionControlSystem.util.ObjectsConvertor;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.event.VirtualFileAdapter;
import consulo.virtualFileSystem.event.VirtualFileEvent;
import consulo.virtualFileSystem.status.FileStatus;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static consulo.ui.ex.SimpleTextAttributes.STYLE_PLAIN;

public class ApplyPatchDifferentiatedDialog extends DialogWrapper {

    private static final Logger LOG = Logger.getInstance(ApplyPatchDifferentiatedDialog.class);
    private final ZipperUpdater myLoadQueue;
    private final TextFieldWithBrowseButton myPatchFile;

    private final List<AbstractFilePatchInProgress> myPatches;
    private final List<ShelvedBinaryFilePatch> myBinaryShelvedPatches;
    @Nonnull
    private final MyChangeTreeList myChangesTreeList;
    @Nullable
    private final Collection<Change> myPreselectedChanges;
    private final boolean myUseProjectRootAsPredefinedBase;

    private JComponent myCenterPanel;
    protected final Project myProject;

    private final AtomicReference<FilePresentationModel> myRecentPathFileChange;
    private final ApplyPatchDifferentiatedDialog.MyUpdater myUpdater;
    private final Runnable myReset;
    private final ChangeListChooserPanel myChangeListChooser;
    private final ChangesLegendCalculator myInfoCalculator;
    private final CommitLegendPanel myCommitLegendPanel;
    private final ApplyPatchExecutor myCallback;
    private final List<ApplyPatchExecutor> myExecutors;

    private boolean myContainBasedChanges;
    private JLabel myPatchFileLabel;
    private PatchReader myReader;
    private VirtualFileAdapter myListener;
    private final boolean myCanChangePatchFile;
    private String myHelpId = "reference.dialogs.vcs.patch.apply";
    private final boolean myShouldUpdateChangeListName;

    public ApplyPatchDifferentiatedDialog(
        final Project project,
        final ApplyPatchExecutor callback,
        final List<ApplyPatchExecutor> executors,
        @Nonnull final ApplyPatchMode applyPatchMode,
        @Nonnull final VirtualFile patchFile
    ) {
        this(project, callback, executors, applyPatchMode, patchFile, null, null, null, null, null, false);
    }

    public ApplyPatchDifferentiatedDialog(
        final Project project,
        final ApplyPatchExecutor callback,
        final List<ApplyPatchExecutor> executors,
        @Nonnull final ApplyPatchMode applyPatchMode,
        @Nonnull final List<TextFilePatch> patches,
        @Nullable final LocalChangeList defaultList
    ) {
        this(project, callback, executors, applyPatchMode, null, patches, defaultList, null, null, null, false);
    }

    public ApplyPatchDifferentiatedDialog(
        final Project project,
        final ApplyPatchExecutor callback,
        final List<ApplyPatchExecutor> executors,
        @Nonnull final ApplyPatchMode applyPatchMode,
        @Nullable final VirtualFile patchFile,
        @Nullable final List<TextFilePatch> patches,
        @Nullable final LocalChangeList defaultList,
        @Nullable List<ShelvedBinaryFilePatch> binaryShelvedPatches,
        @Nullable Collection<Change> preselectedChanges,
        @Nullable String externalCommitMessage,
        boolean useProjectRootAsPredefinedBase
    ) {
        super(project, true);
        myCallback = callback;
        myExecutors = executors;
        myUseProjectRootAsPredefinedBase = useProjectRootAsPredefinedBase;
        setModal(false);
        setTitle(applyPatchMode.getTitle());

        final FileChooserDescriptor descriptor = createSelectPatchDescriptor();
        descriptor.withTitleValue(VcsLocalize.patchApplySelectTitle());

        myProject = project;
        myPatches = new LinkedList<>();
        myRecentPathFileChange = new AtomicReference<>();
        myBinaryShelvedPatches = binaryShelvedPatches;
        myPreselectedChanges = preselectedChanges;
        myChangesTreeList = new MyChangeTreeList(project, Collections.emptyList(), new Runnable() {
            @Override
            public void run() {
                final NamedLegendStatuses includedNameStatuses = new NamedLegendStatuses();
                final Collection<AbstractFilePatchInProgress.PatchChange> includedChanges = myChangesTreeList.getIncludedChanges();
                final Set<Couple<String>> set = new HashSet<>();
                for (AbstractFilePatchInProgress.PatchChange change : includedChanges) {
                    final FilePatch patch = change.getPatchInProgress().getPatch();
                    final Couple<String> pair = Couple.of(patch.getBeforeName(), patch.getAfterName());
                    if (set.contains(pair)) {
                        continue;
                    }
                    set.add(pair);
                    acceptChange(includedNameStatuses, change);
                }
                myInfoCalculator.setIncluded(includedNameStatuses);
                myCommitLegendPanel.update();
                updateOkActions();
            }
        }, new MyChangeNodeDecorator());
        myChangesTreeList.setDoubleClickHandler(() -> {
            List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
            if (selectedChanges.size() == 1 && !selectedChanges.get(0).isValid()) {
                myChangesTreeList.handleInvalidChangesAndToggle();
            }
            new MyShowDiff().showDiff();
        });
        myShouldUpdateChangeListName = defaultList == null && externalCommitMessage == null;
        myUpdater = new MyUpdater();
        myPatchFile = new TextFieldWithBrowseButton();
        myPatchFile.addBrowseFolderListener(VcsLocalize.patchApplySelectTitle().get(), "", project, descriptor);
        myPatchFile.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                setPathFileChangeDefault();
                queueRequest();
            }
        });

        myLoadQueue = new ZipperUpdater(500, Alarm.ThreadToUse.POOLED_THREAD, getDisposable());
        myCanChangePatchFile = applyPatchMode.isCanChangePatchFile();
        myReset = myCanChangePatchFile ? this::reset : EmptyRunnable.getInstance();

        myChangeListChooser = new ChangeListChooserPanel(project, errorMessage -> {
            setOKActionEnabled(errorMessage == null && isChangeTreeEnabled());
            setErrorText(errorMessage);
        });
        ChangeListManager changeListManager = ChangeListManager.getInstance(project);
        myChangeListChooser.setChangeLists(changeListManager.getChangeListsCopy());
        if (defaultList != null) {
            myChangeListChooser.setDefaultSelection(defaultList);
        }
        else if (externalCommitMessage != null) {
            myChangeListChooser.setSuggestedName(externalCommitMessage);
        }
        myChangeListChooser.init();

        myInfoCalculator = new ChangesLegendCalculator();
        myCommitLegendPanel = new CommitLegendPanel(myInfoCalculator) {
            @Override
            public void update() {
                super.update();
                final int inapplicable = myInfoCalculator.getInapplicable();
                if (inapplicable > 0) {
                    appendSpace();
                    appendText(inapplicable, inapplicable, FileStatus.MERGED_WITH_CONFLICTS, "Missing Base:");
                }
            }
        };

        init();

        if (patchFile != null && patchFile.isValid()) {
            init(patchFile);
        }
        else if (patches != null) {
            init(patches);
        }

        myPatchFileLabel.setVisible(myCanChangePatchFile);
        myPatchFile.setVisible(myCanChangePatchFile);

        if (myCanChangePatchFile) {
            myListener = new VirtualFileAdapter() {
                @Override
                @RequiredUIAccess
                public void contentsChanged(@Nonnull VirtualFileEvent event) {
                    syncUpdatePatchFileAndScheduleReloadIfNeeded(event.getFile());
                }
            };
            final VirtualFileManager fileManager = VirtualFileManager.getInstance();
            fileManager.addVirtualFileListener(myListener);
            Disposer.register(getDisposable(), () -> fileManager.removeVirtualFileListener(myListener));
        }
        updateOkActions();
    }

    private void updateOkActions() {
        boolean changeTreeEnabled = isChangeTreeEnabled();
        setOKActionEnabled(changeTreeEnabled);
        if (changeTreeEnabled) {
            myChangeListChooser.updateEnabled();
        }
    }

    private boolean isChangeTreeEnabled() {
        return !myChangesTreeList.getIncludedChanges().isEmpty();
    }

    private void queueRequest() {
        paintBusy(true);
        myLoadQueue.queue(myUpdater);
    }

    private void init(List<? extends FilePatch> patches) {
        final List<AbstractFilePatchInProgress> matchedPatches =
            new MatchPatchPaths(myProject).execute(patches, myUseProjectRootAsPredefinedBase);
        //todo add shelved binary patches
        myProject.getApplication().invokeLater(() -> {
            myPatches.clear();
            myPatches.addAll(matchedPatches);
            updateTree(true);
        });
    }

    public static FileChooserDescriptor createSelectPatchDescriptor() {
        return new FileChooserDescriptor(true, false, false, false, false, false) {
            @Override
            @RequiredUIAccess
            public boolean isFileSelectable(VirtualFile file) {
                return file.getFileType() == PatchFileType.INSTANCE || file.getFileType() == PlainTextFileType.INSTANCE;
            }
        };
    }

    @Nonnull
    @Override
    protected Action[] createActions() {
        if (myExecutors.isEmpty()) {
            return super.createActions();
        }
        final List<Action> actions = new ArrayList<>(4);
        actions.add(getOKAction());
        for (int i = 0; i < myExecutors.size(); i++) {
            final ApplyPatchExecutor executor = myExecutors.get(i);
            final int finalI = i;
            actions.add(new AbstractAction(executor.getName()) {
                @Override
                @RequiredUIAccess
                public void actionPerformed(ActionEvent e) {
                    runExecutor(executor);
                    close(NEXT_USER_EXIT_CODE + finalI);
                }
            });
        }
        actions.add(getCancelAction());
        actions.add(getHelpAction());
        return actions.toArray(new Action[actions.size()]);
    }

    @RequiredUIAccess
    private void runExecutor(ApplyPatchExecutor executor) {
        final Collection<AbstractFilePatchInProgress> included = getIncluded();
        if (included.isEmpty()) {
            return;
        }
        final MultiMap<VirtualFile, AbstractFilePatchInProgress> patchGroups = new MultiMap<>();
        for (AbstractFilePatchInProgress patchInProgress : included) {
            patchGroups.putValue(patchInProgress.getBase(), patchInProgress);
        }
        final LocalChangeList selected = getSelectedChangeList();
        FilePresentationModel presentation = myRecentPathFileChange.get();
        VirtualFile vf = presentation != null ? presentation.getVf() : null;
        executor.apply(
            getOriginalRemaining(),
            patchGroups,
            selected,
            vf == null ? null : vf.getName(),
            myReader == null ? null : myReader.getAdditionalInfo(ApplyPatchDefaultExecutor.pathsFromGroups(patchGroups))
        );
    }

    @Nonnull
    private List<FilePatch> getOriginalRemaining() {
        Collection<AbstractFilePatchInProgress> notIncluded = ContainerUtil.subtract(myPatches, getIncluded());
        List<FilePatch> remainingOriginal = new ArrayList<>();
        for (AbstractFilePatchInProgress progress : notIncluded) {
            progress.reset();
            remainingOriginal.add(progress.getPatch());
        }
        return remainingOriginal;
    }

    @Override
    @NonNls
    protected String getDimensionServiceKey() {
        return "vcs.ApplyPatchDifferentiatedDialog";
    }

    @Override
    protected String getHelpId() {
        return myHelpId;
    }

    @Nullable
    @Override
    @RequiredUIAccess
    public JComponent getPreferredFocusedComponent() {
        return myChangeListChooser.getPreferredFocusedComponent();
    }

    private void setPathFileChangeDefault() {
        myRecentPathFileChange.set(new FilePresentationModel(myPatchFile.getText()));
    }

    private void init(@Nonnull final VirtualFile patchFile) {
        myPatchFile.setText(patchFile.getPresentableUrl());
        myRecentPathFileChange.set(new FilePresentationModel(patchFile));
    }

    public void setHelpId(String s) {
        myHelpId = s;
    }

    private class MyUpdater implements Runnable {
        @Override
        public void run() {
            final FilePresentationModel filePresentationModel = myRecentPathFileChange.get();
            final VirtualFile file = filePresentationModel != null ? filePresentationModel.getVf() : null;
            if (file == null) {
                myProject.getApplication().invokeLater(myReset, IdeaModalityState.stateForComponent(myCenterPanel));
                return;
            }

            final PatchReader patchReader = loadPatches(file);
            List<FilePatch> filePatches =
                patchReader != null ? ContainerUtil.newArrayList(patchReader.getPatches()) : Collections.emptyList();
            if (!ContainerUtil.isEmpty(myBinaryShelvedPatches)) {
                filePatches.addAll(myBinaryShelvedPatches);
            }
            final List<AbstractFilePatchInProgress> matchedPatches =
                new MatchPatchPaths(myProject).execute(filePatches, myUseProjectRootAsPredefinedBase);

            myProject.getApplication().invokeLater(
                () -> {
                    if (myShouldUpdateChangeListName) {
                        myChangeListChooser.setSuggestedName(file.getNameWithoutExtension().replace('_', ' ').trim());
                    }
                    myPatches.clear();
                    myPatches.addAll(matchedPatches);
                    myReader = patchReader;
                    updateTree(true);
                    paintBusy(false);
                    updateOkActions();
                },
                IdeaModalityState.stateForComponent(myCenterPanel)
            );
        }
    }

    @Nullable
    private static PatchReader loadPatches(@Nonnull VirtualFile patchFile) {
        PatchReader reader;
        patchFile.refresh(false, false);
        try {
            reader = Application.get().runReadAction(
                (ThrowableComputable<PatchReader, IOException>) () -> PatchVirtualFileReader.create(patchFile)
            );
        }
        catch (IOException e) {
            LOG.warn("Can't read patchFile: " + patchFile.getPresentableName(), e);
            return null;
        }
        try {
            reader.parseAllPatches();
        }
        catch (PatchSyntaxException e) {
            return null;
        }

        return reader;
    }

    @RequiredUIAccess
    private void syncUpdatePatchFileAndScheduleReloadIfNeeded(@Nullable VirtualFile eventFile) {
        // if dialog is modal and refresh called not from dispatch thread then
        // fireEvents in RefreshQueueImpl will not be triggered because of wrong modality state inside those thread -> defaultMS == NON_MODAL
        final FilePresentationModel filePresentationModel = myRecentPathFileChange.get();
        VirtualFile filePresentationVf = filePresentationModel != null ? filePresentationModel.getVf() : null;
        if (filePresentationVf != null && (eventFile == null || filePresentationVf.equals(eventFile))) {
            filePresentationVf.refresh(false, false);
            queueRequest();
        }
    }

    private static class FilePresentationModel {
        @Nonnull
        private final String myPath;
        @Nullable
        private VirtualFile myVf;

        private FilePresentationModel(@Nonnull String path) {
            myPath = path;
            myVf = null; // don't try to find vf for each typing; only when requested
        }

        public FilePresentationModel(@Nonnull VirtualFile file) {
            myPath = file.getPath();
            myVf = file;
        }

        @Nullable
        public VirtualFile getVf() {
            if (myVf == null) {
                final VirtualFile file = VfsUtil.findFileByIoFile(new File(myPath), true);
                myVf = file != null && !file.isDirectory() ? file : null;
            }
            return myVf;
        }
    }

    private void reset() {
        myPatches.clear();
        myChangesTreeList.setChangesToDisplay(Collections.emptyList());
        myChangesTreeList.repaint();
        myContainBasedChanges = false;
        paintBusy(false);
    }

    @Override
    protected JComponent createCenterPanel() {
        if (myCenterPanel == null) {
            myCenterPanel = new JPanel(new GridBagLayout());
            final GridBagConstraints gb =
                new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, JBUI.insets(1), 0, 0);

            myPatchFileLabel = new JLabel(VcsLocalize.patchApplyFileNameField().get());
            myPatchFileLabel.setLabelFor(myPatchFile);
            myCenterPanel.add(myPatchFileLabel, gb);

            gb.fill = GridBagConstraints.HORIZONTAL;
            ++gb.gridy;
            myCenterPanel.add(myPatchFile, gb);

            final DefaultActionGroup group = new DefaultActionGroup();
            final AnAction[] treeActions = myChangesTreeList.getTreeActions();
            group.addAll(treeActions);
            group.add(new MapDirectory());

            final MyShowDiff diffAction = new MyShowDiff();
            diffAction.registerCustomShortcutSet(CommonShortcuts.getDiff(), getRootPane());
            group.add(diffAction);

            group.add(new StripUp());
            group.add(new StripDown());
            group.add(new ResetStrip());
            group.add(new ZeroStrip());
            if (myCanChangePatchFile) {
                group.add(
                    new AnAction(CommonLocalize.actionRefresh(), CommonLocalize.actionRefresh(), AllIcons.Actions.Refresh) {
                        @Override
                        @RequiredUIAccess
                        public void actionPerformed(@Nonnull AnActionEvent e) {
                            syncUpdatePatchFileAndScheduleReloadIfNeeded(null);
                        }
                    }
                );
            }

            final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("APPLY_PATCH", group, true);
            ++gb.gridy;
            gb.fill = GridBagConstraints.HORIZONTAL;
            myCenterPanel.add(toolbar.getComponent(), gb);

            ++gb.gridy;
            gb.weighty = 1;
            gb.fill = GridBagConstraints.BOTH;
            myCenterPanel.add(ScrollPaneFactory.createScrollPane(myChangesTreeList), gb);

            ++gb.gridy;
            gb.weighty = 0;
            gb.fill = GridBagConstraints.NONE;
            gb.insets.bottom = UIUtil.DEFAULT_VGAP;
            myCenterPanel.add(myCommitLegendPanel.getComponent(), gb);

            ++gb.gridy;
            gb.fill = GridBagConstraints.HORIZONTAL;
            myCenterPanel.add(myChangeListChooser, gb);
        }
        return myCenterPanel;
    }

    private void paintBusy(final boolean requestPut) {
        if (requestPut) {
            myChangesTreeList.setPaintBusy(true);
        }
        else {
            myChangesTreeList.setPaintBusy(!myLoadQueue.isEmpty());
        }
    }

    private class MyChangeTreeList extends ChangesTreeList<AbstractFilePatchInProgress.PatchChange> {
        private MyChangeTreeList(
            Project project,
            Collection<AbstractFilePatchInProgress.PatchChange> initiallyIncluded,
            @Nullable Runnable inclusionListener,
            @Nullable ChangeNodeDecorator decorator
        ) {
            super(project, initiallyIncluded, true, false, inclusionListener, decorator);
        }

        @Override
        protected DefaultTreeModel buildTreeModel(
            List<AbstractFilePatchInProgress.PatchChange> changes,
            ChangeNodeDecorator changeNodeDecorator
        ) {
            TreeModelBuilder builder = new TreeModelBuilder(myProject, isShowFlatten());
            return builder.buildModel(ObjectsConvertor.convert(changes, o -> o), changeNodeDecorator);
        }

        @Override
        protected List<AbstractFilePatchInProgress.PatchChange> getSelectedObjects(
            ChangesBrowserNode<AbstractFilePatchInProgress.PatchChange> node
        ) {
            final List<Change> under = node.getAllChangesUnder();
            return ObjectsConvertor.convert(under, o -> (AbstractFilePatchInProgress.PatchChange) o);
        }

        @Override
        protected AbstractFilePatchInProgress.PatchChange getLeadSelectedObject(ChangesBrowserNode node) {
            final Object o = node.getUserObject();
            return o instanceof AbstractFilePatchInProgress.PatchChange patchChange ? patchChange : null;
        }

        @Override
        protected boolean isNodeEnabled(ChangesBrowserNode node) {
            boolean enabled = super.isNodeEnabled(node);
            Object value = node.getUserObject();
            if (value instanceof AbstractFilePatchInProgress.PatchChange patchChange) {
                enabled &= patchChange.isValid();
            }
            return enabled;
        }

        @Nonnull
        private List<AbstractFilePatchInProgress.PatchChange> getOnlyValidChanges(
            @Nonnull Collection<AbstractFilePatchInProgress.PatchChange> changes
        ) {
            return ContainerUtil.filter(changes, AbstractFilePatchInProgress.PatchChange::isValid);
        }

        @Override
        protected void toggleChanges(Collection<AbstractFilePatchInProgress.PatchChange> changes) {
            if (changes.size() == 1 && !changes.iterator().next().isValid()) {
                handleInvalidChangesAndToggle();
            }
            else {
                super.toggleChanges(getOnlyValidChanges(changes));
            }
        }

        private void handleInvalidChangesAndToggle() {
            new NewBaseSelector(false).run();
            super.toggleChanges(getOnlyValidChanges(getSelectedChanges()));
        }
    }

    private class MapDirectory extends AnAction {
        private final NewBaseSelector myNewBaseSelector;

        private MapDirectory() {
            super("Map base directory", "Map base directory", AllIcons.Vcs.MapBase);
            myNewBaseSelector = new NewBaseSelector();
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(@Nonnull AnActionEvent e) {
            final List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
            if ((selectedChanges.size() >= 1) && (sameBase(selectedChanges))) {
                final AbstractFilePatchInProgress.PatchChange patchChange = selectedChanges.get(0);
                final AbstractFilePatchInProgress patch = patchChange.getPatchInProgress();
                final List<VirtualFile> autoBases = patch.getAutoBasesCopy();
                if (autoBases.isEmpty() || (autoBases.size() == 1 && autoBases.get(0).equals(patch.getBase()))) {
                    myNewBaseSelector.run();
                }
                else {
                    autoBases.add(null);
                    final MapPopup step = new MapPopup(autoBases, myNewBaseSelector);
                    JBPopupFactory.getInstance().createListPopup(step).showCenteredInCurrentWindow(myProject);
                }
            }
        }

        @Override
        public void update(@Nonnull AnActionEvent e) {
            final List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
            e.getPresentation().setEnabled(selectedChanges.size() >= 1 && sameBase(selectedChanges));
        }
    }

    private static boolean sameBase(final List<AbstractFilePatchInProgress.PatchChange> selectedChanges) {
        VirtualFile base = null;
        for (AbstractFilePatchInProgress.PatchChange change : selectedChanges) {
            final VirtualFile changeBase = change.getPatchInProgress().getBase();
            if (base == null) {
                base = changeBase;
            }
            else if (!base.equals(changeBase)) {
                return false;
            }
        }
        return true;
    }

    private void updateTree(boolean doInitCheck) {
        final List<AbstractFilePatchInProgress> patchesToSelect = changes2patches(myChangesTreeList.getSelectedChanges());
        final List<AbstractFilePatchInProgress.PatchChange> changes = getAllChanges();
        final Collection<AbstractFilePatchInProgress.PatchChange> included = getIncluded(doInitCheck, changes);

        myChangesTreeList.setChangesToDisplay(changes);
        myChangesTreeList.setIncludedChanges(included);
        if (doInitCheck) {
            myChangesTreeList.expandAll();
        }
        myChangesTreeList.repaint();
        if ((!doInitCheck) && patchesToSelect != null) {
            final List<AbstractFilePatchInProgress.PatchChange> toSelect = new ArrayList<>(patchesToSelect.size());
            for (AbstractFilePatchInProgress.PatchChange change : changes) {
                if (patchesToSelect.contains(change.getPatchInProgress())) {
                    toSelect.add(change);
                }
            }
            myChangesTreeList.select(toSelect);
        }

        myContainBasedChanges = false;
        for (AbstractFilePatchInProgress patch : myPatches) {
            if (patch.baseExistsOrAdded()) {
                myContainBasedChanges = true;
                break;
            }
        }
    }

    private List<AbstractFilePatchInProgress.PatchChange> getAllChanges() {
        return ObjectsConvertor.convert(myPatches, AbstractFilePatchInProgress::getChange);
    }

    private static void acceptChange(final NamedLegendStatuses nameStatuses, final AbstractFilePatchInProgress.PatchChange change) {
        final AbstractFilePatchInProgress patchInProgress = change.getPatchInProgress();
        if (FilePatchStatus.ADDED.equals(patchInProgress.getStatus())) {
            nameStatuses.plusAdded();
        }
        else if (FilePatchStatus.DELETED.equals(patchInProgress.getStatus())) {
            nameStatuses.plusDeleted();
        }
        else {
            nameStatuses.plusModified();
        }
        if (!patchInProgress.baseExistsOrAdded()) {
            nameStatuses.plusInapplicable(); // may be deleted or modified, but still not applicable
        }
    }

    private Collection<AbstractFilePatchInProgress.PatchChange> getIncluded(
        boolean doInitCheck,
        List<AbstractFilePatchInProgress.PatchChange> changes
    ) {
        final NamedLegendStatuses totalNameStatuses = new NamedLegendStatuses();
        final NamedLegendStatuses includedNameStatuses = new NamedLegendStatuses();

        final Collection<AbstractFilePatchInProgress.PatchChange> included = new LinkedList<>();
        if (doInitCheck) {
            for (AbstractFilePatchInProgress.PatchChange change : changes) {
                acceptChange(totalNameStatuses, change);
                final AbstractFilePatchInProgress abstractFilePatchInProgress = change.getPatchInProgress();
                if (abstractFilePatchInProgress.baseExistsOrAdded() && (myPreselectedChanges == null || myPreselectedChanges.contains(change))) {
                    acceptChange(includedNameStatuses, change);
                    included.add(change);
                }
            }
        }
        else {
            // todo maybe written pretty
            final Collection<AbstractFilePatchInProgress.PatchChange> includedNow = myChangesTreeList.getIncludedChanges();
            final Set<AbstractFilePatchInProgress> toBeIncluded = new HashSet<>();
            for (AbstractFilePatchInProgress.PatchChange change : includedNow) {
                final AbstractFilePatchInProgress patch = change.getPatchInProgress();
                toBeIncluded.add(patch);
            }
            for (AbstractFilePatchInProgress.PatchChange change : changes) {
                final AbstractFilePatchInProgress patch = change.getPatchInProgress();
                acceptChange(totalNameStatuses, change);
                if (toBeIncluded.contains(patch) && patch.baseExistsOrAdded()) {
                    acceptChange(includedNameStatuses, change);
                    included.add(change);
                }
            }
        }
        myInfoCalculator.setTotal(totalNameStatuses);
        myInfoCalculator.setIncluded(includedNameStatuses);
        myCommitLegendPanel.update();
        return included;
    }

    private class NewBaseSelector implements Runnable {
        final boolean myDirectorySelector;

        public NewBaseSelector() {
            this(true);
        }

        public NewBaseSelector(boolean directorySelector) {
            myDirectorySelector = directorySelector;
        }

        @Override
        public void run() {
            final FileChooserDescriptor descriptor = myDirectorySelector
                ? FileChooserDescriptorFactory.createSingleFolderDescriptor()
                : FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
            descriptor.setTitle(String.format("Select %s Base", myDirectorySelector ? "Directory" : "File"));
            VirtualFile selectedFile = IdeaFileChooser.chooseFile(descriptor, myProject, null);
            if (selectedFile == null) {
                return;
            }

            final List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
            if (selectedChanges.size() >= 1) {
                for (AbstractFilePatchInProgress.PatchChange patchChange : selectedChanges) {
                    final AbstractFilePatchInProgress patch = patchChange.getPatchInProgress();
                    if (myDirectorySelector) {
                        patch.setNewBase(selectedFile);
                    }
                    else {
                        final FilePatch filePatch = patch.getPatch();
                        //if file was renamed in the patch but applied on another or already renamed local one then we shouldn't apply this rename/move
                        filePatch.setAfterName(selectedFile.getName());
                        filePatch.setBeforeName(selectedFile.getName());
                        patch.setNewBase(selectedFile.getParent());
                    }
                }
                updateTree(false);
            }
        }
    }

    private static List<AbstractFilePatchInProgress> changes2patches(final List<AbstractFilePatchInProgress.PatchChange> selectedChanges) {
        return ObjectsConvertor.convert(selectedChanges, AbstractFilePatchInProgress.PatchChange::getPatchInProgress);
    }

    private class MapPopup extends BaseListPopupStep<VirtualFile> {
        private final Runnable myNewBaseSelector;

        private MapPopup(final @Nonnull List<? extends VirtualFile> aValues, Runnable newBaseSelector) {
            super("Select base directory for a path", aValues);
            myNewBaseSelector = newBaseSelector;
        }

        @Override
        public boolean isSpeedSearchEnabled() {
            return true;
        }

        @Override
        public PopupStep onChosen(final VirtualFile selectedValue, boolean finalChoice) {
            if (selectedValue == null) {
                myNewBaseSelector.run();
                return null;
            }
            final List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
            if (selectedChanges.size() >= 1) {
                for (AbstractFilePatchInProgress.PatchChange patchChange : selectedChanges) {
                    final AbstractFilePatchInProgress patch = patchChange.getPatchInProgress();
                    patch.setNewBase(selectedValue);
                }
                updateTree(false);
            }
            return null;
        }

        @Nonnull
        @Override
        public String getTextFor(VirtualFile value) {
            return value == null ? "Select base for a path" : value.getPath();
        }
    }

    private static class NamedLegendStatuses {
        private int myAdded;
        private int myModified;
        private int myDeleted;
        private int myInapplicable;

        public NamedLegendStatuses() {
            myAdded = 0;
            myModified = 0;
            myDeleted = 0;
            myInapplicable = 0;
        }

        public void plusAdded() {
            ++myAdded;
        }

        public void plusModified() {
            ++myModified;
        }

        public void plusDeleted() {
            ++myDeleted;
        }

        public void plusInapplicable() {
            ++myInapplicable;
        }

        public int getAdded() {
            return myAdded;
        }

        public int getModified() {
            return myModified;
        }

        public int getDeleted() {
            return myDeleted;
        }

        public int getInapplicable() {
            return myInapplicable;
        }
    }

    private static class ChangesLegendCalculator implements CommitLegendPanel.InfoCalculator {
        private NamedLegendStatuses myTotal;
        private NamedLegendStatuses myIncluded;

        private ChangesLegendCalculator() {
            myTotal = new NamedLegendStatuses();
            myIncluded = new NamedLegendStatuses();
        }

        public void setTotal(final NamedLegendStatuses nameStatuses) {
            myTotal = nameStatuses;
        }

        public void setIncluded(final NamedLegendStatuses nameStatuses) {
            myIncluded = nameStatuses;
        }

        @Override
        public int getNew() {
            return myTotal.getAdded();
        }

        @Override
        public int getModified() {
            return myTotal.getModified();
        }

        @Override
        public int getDeleted() {
            return myTotal.getDeleted();
        }

        @Override
        public int getUnversioned() {
            return 0;
        }

        public int getInapplicable() {
            return myTotal.getInapplicable();
        }

        @Override
        public int getIncludedNew() {
            return myIncluded.getAdded();
        }

        @Override
        public int getIncludedModified() {
            return myIncluded.getModified();
        }

        @Override
        public int getIncludedDeleted() {
            return myIncluded.getDeleted();
        }

        @Override
        public int getIncludedUnversioned() {
            return 0;
        }
    }

    private class MyChangeNodeDecorator implements ChangeNodeDecorator {
        @Override
        @NonNls
        public void decorate(Change change, SimpleColoredComponent component, boolean isShowFlatten) {
            if (change instanceof AbstractFilePatchInProgress.PatchChange patchChange) {
                final AbstractFilePatchInProgress patchInProgress = patchChange.getPatchInProgress();
                if (patchInProgress.getCurrentStrip() > 0) {
                    component.append(" stripped " + patchInProgress.getCurrentStrip(), SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
                }
                final String text;
                if (FilePatchStatus.ADDED.equals(patchInProgress.getStatus())) {
                    text = "Added";
                }
                else if (FilePatchStatus.DELETED.equals(patchInProgress.getStatus())) {
                    text = "Deleted";
                }
                else {
                    text = "Modified";
                }
                component.append("   ");
                component.append(text, SimpleTextAttributes.GRAY_ATTRIBUTES);
                if (!patchInProgress.baseExistsOrAdded()) {
                    component.append("  ");
                    component.append(
                        "Select missing base",
                        new SimpleTextAttributes(STYLE_PLAIN, JBCurrentTheme.Link.linkColor()),
                        (Runnable) myChangesTreeList::handleInvalidChangesAndToggle
                    );
                }
                else {
                    if (!patchInProgress.getStatus().equals(FilePatchStatus.ADDED) && basePathWasChanged(patchInProgress)) {
                        component.append("  ");
                        component.append("New base detected", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
                        component.setToolTipText(
                            String.format(
                                "was: %s (base dir: %s)",
                                patchInProgress.getOriginalBeforePath(),
                                myProject.getBasePath()
                            ) + "<br/>" + String.format(
                                "now: %s (base dir: %s)",
                                patchInProgress.getPatch().getBeforeName(),
                                patchInProgress.getBase().getPath()
                            )
                        );
                    }
                }
            }
        }

        @Override
        public void preDecorate(Change change, ChangesBrowserNodeRenderer renderer, boolean showFlatten) {
        }
    }

    private boolean basePathWasChanged(@Nonnull AbstractFilePatchInProgress patchInProgress) {
        return !FileUtil.filesEqual(
            patchInProgress.getIoCurrentBase(),
            new File(myProject.getBasePath(), patchInProgress.getOriginalBeforePath())
        );
    }

    private Collection<AbstractFilePatchInProgress> getIncluded() {
        return ObjectsConvertor.convert(
            myChangesTreeList.getIncludedChanges(),
            AbstractFilePatchInProgress.PatchChange::getPatchInProgress
        );
    }

    @Nullable
    private LocalChangeList getSelectedChangeList() {
        return myChangeListChooser.getSelectedList(myProject);
    }

    @Override
    @RequiredUIAccess
    protected void doOKAction() {
        super.doOKAction();
        runExecutor(myCallback);
    }

    private class ZeroStrip extends AnAction {
        private ZeroStrip() {
            super("Remove Directories", "Remove Directories", AllIcons.Vcs.StripNull);
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(@Nonnull AnActionEvent e) {
            final List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
            for (AbstractFilePatchInProgress.PatchChange change : selectedChanges) {
                change.getPatchInProgress().setZero();
            }
            updateTree(false);
        }
    }

    private class StripDown extends AnAction {
        private StripDown() {
            super(
                LocalizeValue.localizeTODO("Restore Directory"),
                LocalizeValue.localizeTODO("Restore Directory"),
                PlatformIconGroup.vcsStripdown()
            );
        }

        @Override
        public void update(@Nonnull AnActionEvent e) {
            e.getPresentation().setEnabled(isEnabled());
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(@Nonnull AnActionEvent e) {
            if (!isEnabled()) {
                return;
            }
            final List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
            for (AbstractFilePatchInProgress.PatchChange change : selectedChanges) {
                change.getPatchInProgress().down();
            }
            updateTree(false);
        }

        private boolean isEnabled() {
            final List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
            if (selectedChanges.isEmpty()) {
                return false;
            }
            for (AbstractFilePatchInProgress.PatchChange change : selectedChanges) {
                if (!change.getPatchInProgress().canDown()) {
                    return false;
                }
            }
            return true;
        }
    }

    private class StripUp extends AnAction {
        private StripUp() {
            super(
                LocalizeValue.localizeTODO("Strip Directory"),
                LocalizeValue.localizeTODO("Strip Directory"),
                PlatformIconGroup.vcsStripup()
            );
        }

        @Override
        public void update(@Nonnull AnActionEvent e) {
            e.getPresentation().setEnabled(isEnabled());
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(@Nonnull AnActionEvent e) {
            if (!isEnabled()) {
                return;
            }
            final List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
            for (AbstractFilePatchInProgress.PatchChange change : selectedChanges) {
                change.getPatchInProgress().up();
            }
            updateTree(false);
        }

        private boolean isEnabled() {
            final List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
            if (selectedChanges.isEmpty()) {
                return false;
            }
            for (AbstractFilePatchInProgress.PatchChange change : selectedChanges) {
                if (!change.getPatchInProgress().canUp()) {
                    return false;
                }
            }
            return true;
        }
    }

    private class ResetStrip extends AnAction {
        private ResetStrip() {
            super(
                LocalizeValue.localizeTODO("Reset Directories"),
                LocalizeValue.localizeTODO("Reset Directories"),
                PlatformIconGroup.vcsResetstrip()
            );
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(@Nonnull AnActionEvent e) {
            final List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();
            for (AbstractFilePatchInProgress.PatchChange change : selectedChanges) {
                change.getPatchInProgress().reset();
            }
            updateTree(false);
        }
    }

    private class MyShowDiff extends AnAction {
        private final MyChangeComparator myMyChangeComparator;

        private MyShowDiff() {
            super(LocalizeValue.localizeTODO("Show Diff"), LocalizeValue.localizeTODO("Show Diff"), PlatformIconGroup.actionsDiff());
            myMyChangeComparator = new MyChangeComparator();
        }

        @Override
        public void update(@Nonnull AnActionEvent e) {
            e.getPresentation().setEnabled((!myPatches.isEmpty()) && myContainBasedChanges);
        }

        @Override
        @RequiredUIAccess
        public void actionPerformed(@Nonnull AnActionEvent e) {
            showDiff();
        }

        @RequiredUIAccess
        private void showDiff() {
            if (ChangeListManager.getInstance(myProject).isFreezedWithNotification(null)) {
                return;
            }
            if (myPatches.isEmpty() || (!myContainBasedChanges)) {
                return;
            }
            final List<AbstractFilePatchInProgress.PatchChange> changes = getAllChanges();
            Collections.sort(changes, myMyChangeComparator);
            List<AbstractFilePatchInProgress.PatchChange> selectedChanges = myChangesTreeList.getSelectedChanges();

            int selectedIdx = 0;
            final List<DiffRequestProducer> diffRequestPresentableList = new ArrayList<>(changes.size());
            if (selectedChanges.isEmpty()) {
                selectedChanges = changes;
            }
            if (!selectedChanges.isEmpty()) {
                final AbstractFilePatchInProgress.PatchChange c = selectedChanges.get(0);
                for (AbstractFilePatchInProgress.PatchChange change : changes) {
                    final AbstractFilePatchInProgress patchInProgress = change.getPatchInProgress();
                    if (!patchInProgress.baseExistsOrAdded()) {
                        diffRequestPresentableList.add(createBaseNotFoundErrorRequest(patchInProgress));
                    }
                    else {
                        diffRequestPresentableList.add(patchInProgress.getDiffRequestProducers(myProject, myReader));
                    }
                    if (change.equals(c)) {
                        selectedIdx = diffRequestPresentableList.size() - 1;
                    }
                }
            }
            if (diffRequestPresentableList.isEmpty()) {
                return;
            }
            MyDiffRequestChain chain = new MyDiffRequestChain(diffRequestPresentableList, changes, selectedIdx);
            DiffManager.getInstance().showDiff(myProject, chain, DiffDialogHints.DEFAULT);
        }
    }

    @Nonnull
    private static DiffRequestProducer createBaseNotFoundErrorRequest(@Nonnull final AbstractFilePatchInProgress patchInProgress) {
        final String beforePath = patchInProgress.getPatch().getBeforeName();
        final String afterPath = patchInProgress.getPatch().getAfterName();
        return new DiffRequestProducer() {
            @Nonnull
            @Override
            public String getName() {
                final File ioCurrentBase = patchInProgress.getIoCurrentBase();
                return ioCurrentBase == null ? patchInProgress.getCurrentPath() : ioCurrentBase.getPath();
            }

            @Nonnull
            @Override
            public DiffRequest process(@Nonnull UserDataHolder context, @Nonnull ProgressIndicator indicator)
                throws DiffRequestProducerException, ProcessCanceledException {
                throw new DiffRequestProducerException("Cannot find base for '" + (beforePath != null ? beforePath : afterPath) + "'");
            }
        };
    }

    private static class MyDiffRequestChain extends UserDataHolderBase implements DiffRequestChain, GoToChangePopupBuilder.Chain {
        private final List<DiffRequestProducer> myRequests;
        private final List<? extends Change> myChanges;
        private int myIndex;

        public MyDiffRequestChain(@Nonnull List<DiffRequestProducer> requests, @Nonnull List<? extends Change> changes, int index) {
            myRequests = requests;
            myChanges = changes;

            myIndex = index >= 0 ? index : 0;
        }

        @Nonnull
        @Override
        public List<? extends DiffRequestProducer> getRequests() {
            return myRequests;
        }

        @Override
        public int getIndex() {
            return myIndex;
        }

        @Override
        public void setIndex(int index) {
            assert index >= 0 && index < myRequests.size();
            myIndex = index;
        }

        @Nonnull
        @Override
        public AnAction createGoToChangeAction(@Nonnull Consumer<Integer> onSelected) {
            return new ChangeGoToChangePopupAction.Fake<>(this, myIndex, onSelected) {
                @Nonnull
                @Override
                protected FilePath getFilePath(int index) {
                    return ChangesUtil.getFilePath(myChanges.get(index));
                }

                @Nonnull
                @Override
                protected FileStatus getFileStatus(int index) {
                    return myChanges.get(index).getFileStatus();
                }
            };
        }
    }

    private class MyChangeComparator implements Comparator<AbstractFilePatchInProgress.PatchChange> {
        @Override
        public int compare(AbstractFilePatchInProgress.PatchChange o1, AbstractFilePatchInProgress.PatchChange o2) {
            if (PropertiesComponent.getInstance(myProject).isTrueValue("ChangesBrowser.SHOW_FLATTEN")) {
                return o1.getPatchInProgress().getIoCurrentBase().getName().compareTo(o2.getPatchInProgress().getIoCurrentBase().getName());
            }
            return FileUtil.compareFiles(o1.getPatchInProgress().getIoCurrentBase(), o2.getPatchInProgress().getIoCurrentBase());
        }
    }
}
