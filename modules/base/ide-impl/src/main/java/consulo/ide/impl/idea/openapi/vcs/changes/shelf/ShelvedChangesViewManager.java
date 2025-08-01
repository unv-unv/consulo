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
package consulo.ide.impl.idea.openapi.vcs.changes.shelf;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.impl.internal.IdeaModalityState;
import consulo.application.util.DateFormatUtil;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.dataContext.DataSink;
import consulo.dataContext.TypeSafeDataProvider;
import consulo.ide.impl.idea.ide.actions.EditSourceAction;
import consulo.versionControlSystem.change.patch.FilePatch;
import consulo.ide.impl.idea.openapi.diff.impl.patch.PatchSyntaxException;
import consulo.versionControlSystem.impl.internal.change.ui.issueLink.IssueLinkRenderer;
import consulo.ui.ex.awt.tree.TreeLinkMouseListener;
import consulo.ide.impl.idea.openapi.vcs.changes.patch.PatchFileType;
import consulo.ide.impl.idea.openapi.vcs.changes.patch.RelativePathCalculator;
import consulo.ide.impl.idea.openapi.vcs.changes.ui.ChangesViewContentI;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.language.file.FileTypeManager;
import consulo.localize.LocalizeValue;
import consulo.navigation.Navigatable;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.ui.ex.DeleteProvider;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.CommonShortcuts;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.PopupHandler;
import consulo.ui.ex.awt.ScrollPaneFactory;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.event.DoubleClickListener;
import consulo.ui.ex.awt.speedSearch.TreeSpeedSearch;
import consulo.ui.ex.awt.tree.ColoredTreeCellRenderer;
import consulo.ui.ex.awt.tree.Tree;
import consulo.ui.ex.awt.tree.TreeState;
import consulo.ui.ex.awt.tree.TreeUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.content.ContentFactory;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.util.dataholder.Key;
import consulo.util.lang.Couple;
import consulo.versionControlSystem.AbstractVcsHelper;
import consulo.versionControlSystem.VcsDataKeys;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.VcsToolWindow;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.CommitContext;
import consulo.versionControlSystem.localize.VcsLocalize;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.status.FileStatus;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author yole
 * @since 2006-11-23
 */
@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class ShelvedChangesViewManager {
  private final ChangesViewContentI myContentManager;
  private final ShelveChangesManager myShelveChangesManager;
  private final Project myProject;
  private final Tree myTree;
  private Content myContent = null;
  private final ShelvedChangeDeleteProvider myDeleteProvider = new ShelvedChangeDeleteProvider();
  private boolean myUpdatePending = false;
  private Runnable myPostUpdateRunnable = null;

  public static Key<ShelvedChangeList[]> SHELVED_CHANGELIST_KEY = Key.create("ShelveChangesManager.ShelvedChangeListData");
  public static Key<ShelvedChangeList[]> SHELVED_RECYCLED_CHANGELIST_KEY = Key.create("ShelveChangesManager.ShelvedRecycledChangeListData");
  public static Key<List<ShelvedChange>> SHELVED_CHANGE_KEY = Key.create("ShelveChangesManager.ShelvedChange");
  public static Key<List<ShelvedBinaryFile>> SHELVED_BINARY_FILE_KEY = Key.create("ShelveChangesManager.ShelvedBinaryFile");
  private static final Object ROOT_NODE_VALUE = new Object();
  private DefaultMutableTreeNode myRoot;
  private final Map<Couple<String>, String> myMoveRenameInfo;

  public static ShelvedChangesViewManager getInstance(Project project) {
    return project.getComponent(ShelvedChangesViewManager.class);
  }

  @Inject
  public ShelvedChangesViewManager(Project project, ChangesViewContentI contentManager, ShelveChangesManager shelveChangesManager) {
    myProject = project;
    myContentManager = contentManager;
    myShelveChangesManager = shelveChangesManager;
    myProject.getMessageBus().connect().subscribe(ShelveChangesListener.class, manager -> {
      myUpdatePending = true;
      myProject.getApplication().invokeLater(() -> updateChangesContent(), IdeaModalityState.nonModal());
    });
    myMoveRenameInfo = new HashMap<>();

    myTree = new ShelfTree();
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.setCellRenderer(new ShelfTreeCellRenderer(project, myMoveRenameInfo));
    new TreeLinkMouseListener(new ShelfTreeCellRenderer(project, myMoveRenameInfo)).installOn(myTree);

    final AnAction showDiffAction = ActionManager.getInstance().getAction("ShelvedChanges.Diff");
    showDiffAction.registerCustomShortcutSet(CommonShortcuts.getDiff(), myTree);
    final EditSourceAction editSourceAction = new EditSourceAction();
    editSourceAction.registerCustomShortcutSet(CommonShortcuts.getEditSource(), myTree);

    PopupHandler.installPopupHandler(myTree, "ShelvedChangesPopupMenu", ActionPlaces.UNKNOWN);

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        DiffShelvedChangesAction.showShelvedChangesDiff(DataManager.getInstance().getDataContext(myTree));
        return true;
      }
    }.installOn(myTree);

    new TreeSpeedSearch(myTree, o -> {
      final Object lc = o.getLastPathComponent();
      final Object lastComponent = lc == null ? null : ((DefaultMutableTreeNode)lc).getUserObject();
      if (lastComponent instanceof ShelvedChangeList shelvedChangeList) {
        return shelvedChangeList.DESCRIPTION;
      }
      else if (lastComponent instanceof ShelvedChange shelvedChange) {
        return shelvedChange.getBeforeFileName() == null ? shelvedChange.getAfterFileName() : shelvedChange.getBeforeFileName();
      }
      else if (lastComponent instanceof ShelvedBinaryFile sbf) {
        final String value = sbf.BEFORE_PATH == null ? sbf.AFTER_PATH : sbf.BEFORE_PATH;
        int idx = value.lastIndexOf("/");
        idx = (idx == -1) ? value.lastIndexOf("\\") : idx;
        return idx > 0 ? value.substring(idx + 1) : value;
      }
      return null;
    }, true);
  }

  public void updateChangesContent() {
    myUpdatePending = false;
    final List<ShelvedChangeList> changeLists = new ArrayList<>(myShelveChangesManager.getShelvedChangeLists());
    changeLists.addAll(myShelveChangesManager.getRecycledShelvedChangeLists());
    if (changeLists.size() == 0) {
      if (myContent != null) {
        myContentManager.removeContent(myContent);
        myContentManager.selectContent("Local");
      }
      myContent = null;
    }
    else {
      if (myContent == null) {
        JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
        scrollPane.setBorder(null);
        myContent = ContentFactory.getInstance().createContent(scrollPane, VcsLocalize.shelfTab().get(), false);
        myContent.setCloseable(false);
        myContentManager.addContent(myContent);
      }
      TreeState state = TreeState.createOn(myTree);
      myTree.setModel(buildChangesModel());
      state.applyTo(myTree);
      if (myPostUpdateRunnable != null) {
        myPostUpdateRunnable.run();
      }
    }
    myPostUpdateRunnable = null;
  }

  private TreeModel buildChangesModel() {
    myRoot = new DefaultMutableTreeNode(ROOT_NODE_VALUE);   // not null for TreeState matching to work
    DefaultTreeModel model = new DefaultTreeModel(myRoot);
    final List<ShelvedChangeList> changeLists = new ArrayList<>(myShelveChangesManager.getShelvedChangeLists());
    Collections.sort(changeLists, ChangelistComparator.getInstance());
    if (myShelveChangesManager.isShowRecycled()) {
      ArrayList<ShelvedChangeList> recycled = new ArrayList<>(myShelveChangesManager.getRecycledShelvedChangeLists());
      Collections.sort(recycled, ChangelistComparator.getInstance());
      changeLists.addAll(recycled);
    }
    myMoveRenameInfo.clear();

    for (ShelvedChangeList changeList : changeLists) {
      DefaultMutableTreeNode node = new DefaultMutableTreeNode(changeList);
      model.insertNodeInto(node, myRoot, myRoot.getChildCount());

      final List<Object> shelvedFilesNodes = new ArrayList<>();
      List<ShelvedChange> changes = changeList.getChanges(myProject);
      for (ShelvedChange change : changes) {
        putMovedMessage(change.getBeforePath(), change.getAfterPath());
        shelvedFilesNodes.add(change);
      }
      List<ShelvedBinaryFile> binaryFiles = changeList.getBinaryFiles();
      for (ShelvedBinaryFile file : binaryFiles) {
        putMovedMessage(file.BEFORE_PATH, file.AFTER_PATH);
        shelvedFilesNodes.add(file);
      }
      Collections.sort(shelvedFilesNodes, ShelvedFilePatchComparator.getInstance());
      for (int i = 0; i < shelvedFilesNodes.size(); i++) {
        final Object filesNode = shelvedFilesNodes.get(i);
        final DefaultMutableTreeNode pathNode = new DefaultMutableTreeNode(filesNode);
        model.insertNodeInto(pathNode, node, i);
      }
    }
    return model;
  }

  private static class ChangelistComparator implements Comparator<ShelvedChangeList> {
    private final static ChangelistComparator ourInstance = new ChangelistComparator();

    public static ChangelistComparator getInstance() {
      return ourInstance;
    }

    @Override
    public int compare(ShelvedChangeList o1, ShelvedChangeList o2) {
      return o2.DATE.compareTo(o1.DATE);
    }
  }

  private void putMovedMessage(final String beforeName, final String afterName) {
    final String movedMessage = RelativePathCalculator.getMovedString(beforeName, afterName);
    if (movedMessage != null) {
      myMoveRenameInfo.put(Couple.of(beforeName, afterName), movedMessage);
    }
  }

  public void activateView(final ShelvedChangeList list) {
    Runnable runnable = () -> {
      if (list != null) {
        TreeUtil.selectNode(myTree, TreeUtil.findNodeWithObject(myRoot, list));
      }
      myContentManager.setSelectedContent(myContent);
      ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(VcsToolWindow.ID);
      if (!window.isVisible()) {
        window.activate(null);
      }
    };
    if (myUpdatePending) {
      myPostUpdateRunnable = runnable;
    }
    else {
      runnable.run();
    }
  }

  private class ShelfTree extends Tree implements TypeSafeDataProvider {
    @Override
    public void calcData(Key<?> key, DataSink sink) {
      if (key == SHELVED_CHANGELIST_KEY) {
        final Set<ShelvedChangeList> changeLists = getSelectedLists(false);

        if (changeLists.size() > 0) {
          sink.put(SHELVED_CHANGELIST_KEY, changeLists.toArray(new ShelvedChangeList[changeLists.size()]));
        }
      }
      else if (key == SHELVED_RECYCLED_CHANGELIST_KEY) {
        final Set<ShelvedChangeList> changeLists = getSelectedLists(true);

        if (changeLists.size() > 0) {
          sink.put(SHELVED_RECYCLED_CHANGELIST_KEY, changeLists.toArray(new ShelvedChangeList[changeLists.size()]));
        }
      }
      else if (key == SHELVED_CHANGE_KEY) {
        sink.put(SHELVED_CHANGE_KEY, TreeUtil.collectSelectedObjectsOfType(this, ShelvedChange.class));
      }
      else if (key == SHELVED_BINARY_FILE_KEY) {
        sink.put(SHELVED_BINARY_FILE_KEY, TreeUtil.collectSelectedObjectsOfType(this, ShelvedBinaryFile.class));
      }
      else if (key == VcsDataKeys.HAVE_SELECTED_CHANGES) {
        sink.put(VcsDataKeys.HAVE_SELECTED_CHANGES, getSelectionCount() > 0);
        /*List<ShelvedChange> shelvedChanges = TreeUtil.collectSelectedObjectsOfType(this, ShelvedChange.class);
        final List<ShelvedChangeList> changeLists = TreeUtil.collectSelectedObjectsOfType(this, ShelvedChangeList.class);*/
      }
      else if (key == VcsDataKeys.CHANGES) {
        List<ShelvedChange> shelvedChanges = TreeUtil.collectSelectedObjectsOfType(this, ShelvedChange.class);
        final List<ShelvedBinaryFile> shelvedBinaryFiles = TreeUtil.collectSelectedObjectsOfType(this, ShelvedBinaryFile.class);
        if (!shelvedChanges.isEmpty() || !shelvedBinaryFiles.isEmpty()) {
          final List<Change> changes = new ArrayList<>(shelvedChanges.size() + shelvedBinaryFiles.size());
          for (ShelvedChange shelvedChange : shelvedChanges) {
            changes.add(shelvedChange.getChange(myProject));
          }
          for (ShelvedBinaryFile binaryFile : shelvedBinaryFiles) {
            changes.add(binaryFile.createChange(myProject));
          }
          sink.put(VcsDataKeys.CHANGES, changes.toArray(new Change[changes.size()]));
        }
        else {
          final List<ShelvedChangeList> changeLists = TreeUtil.collectSelectedObjectsOfType(this, ShelvedChangeList.class);
          final List<Change> changes = new ArrayList<>();
          for (ShelvedChangeList changeList : changeLists) {
            shelvedChanges = changeList.getChanges(myProject);
            for (ShelvedChange shelvedChange : shelvedChanges) {
              changes.add(shelvedChange.getChange(myProject));
            }
            final List<ShelvedBinaryFile> binaryFiles = changeList.getBinaryFiles();
            for (ShelvedBinaryFile file : binaryFiles) {
              changes.add(file.createChange(myProject));
            }
          }
          sink.put(VcsDataKeys.CHANGES, changes.toArray(new Change[changes.size()]));
        }
      }
      else if (key == DeleteProvider.KEY) {
        sink.put(DeleteProvider.KEY, myDeleteProvider);
      }
      else if (Navigatable.KEY_OF_ARRAY.equals(key)) {
        List<ShelvedChange> shelvedChanges = new ArrayList<>(TreeUtil.collectSelectedObjectsOfType(this, ShelvedChange.class));
        final ArrayDeque<Navigatable> navigatables = new ArrayDeque<>();
        final List<ShelvedChangeList> changeLists = TreeUtil.collectSelectedObjectsOfType(this, ShelvedChangeList.class);
        for (ShelvedChangeList changeList : changeLists) {
          shelvedChanges.addAll(changeList.getChanges(myProject));
        }
        for (final ShelvedChange shelvedChange : shelvedChanges) {
          if (shelvedChange.getBeforePath() != null && !FileStatus.ADDED.equals(shelvedChange.getFileStatus())) {
            final Navigatable navigatable = requestFocus -> {
              final VirtualFile vf = shelvedChange.getBeforeVFUnderProject(myProject);
              if (vf != null) {
                OpenFileDescriptorFactory.getInstance(myProject).builder(vf).build().navigate(requestFocus);
              }
            };
            navigatables.add(navigatable);
          }
        }

        sink.put(Navigatable.KEY_OF_ARRAY, navigatables.toArray(new Navigatable[navigatables.size()]));
      }
    }

    private Set<ShelvedChangeList> getSelectedLists(final boolean recycled) {
      final TreePath[] selections = getSelectionPaths();
      final Set<ShelvedChangeList> changeLists = new HashSet<>();
      if (selections != null) {
        for (TreePath path : selections) {
          if (path.getPathCount() >= 2) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getPathComponent(1);
            if (node.getUserObject() instanceof ShelvedChangeList) {
              final ShelvedChangeList list = (ShelvedChangeList)node.getUserObject();
              if (((!recycled) && (!list.isRecycled())) || (recycled && list.isRecycled())) {
                changeLists.add(list);
              }
            }
          }
        }
      }
      return changeLists;
    }
  }

  private final static class ShelvedFilePatchComparator implements Comparator<Object> {
    private final static ShelvedFilePatchComparator ourInstance = new ShelvedFilePatchComparator();

    public static ShelvedFilePatchComparator getInstance() {
      return ourInstance;
    }

    @Override
    public int compare(final Object o1, final Object o2) {
      final String path1 = getPath(o1);
      final String path2 = getPath(o2);
      // case-insensitive; as in local changes
      if (path1 == null) return -1;
      if (path2 == null) return 1;
      return path1.compareToIgnoreCase(path2);
    }

    private static String getPath(final Object patch) {
      String path = null;
      if (patch instanceof ShelvedBinaryFile) {
        final ShelvedBinaryFile binaryFile = (ShelvedBinaryFile)patch;
        path = binaryFile.BEFORE_PATH;
        path = (path == null) ? binaryFile.AFTER_PATH : path;
      }
      else if (patch instanceof ShelvedChange) {
        final ShelvedChange shelvedChange = (ShelvedChange)patch;
        path = shelvedChange.getBeforePath().replace('/', File.separatorChar);
      }
      if (path == null) {
        return null;
      }
      final int pos = path.lastIndexOf(File.separatorChar);
      return (pos >= 0) ? path.substring(pos + 1) : path;
    }
  }

  private static class ShelfTreeCellRenderer extends ColoredTreeCellRenderer {
    private final IssueLinkRenderer myIssueLinkRenderer;
    private final Map<Couple<String>, String> myMoveRenameInfo;

    public ShelfTreeCellRenderer(Project project, final Map<Couple<String>, String> moveRenameInfo) {
      myMoveRenameInfo = moveRenameInfo;
      myIssueLinkRenderer = new IssueLinkRenderer(project, this);
    }

    @Override
    public void customizeCellRenderer(
      @Nonnull JTree tree,
      Object value,
      boolean selected,
      boolean expanded,
      boolean leaf,
      int row,
      boolean hasFocus
    ) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
      Object nodeValue = node.getUserObject();
      if (nodeValue instanceof ShelvedChangeList) {
        ShelvedChangeList changeListData = (ShelvedChangeList)nodeValue;
        if (changeListData.isRecycled()) {
          myIssueLinkRenderer.appendTextWithLinks(changeListData.DESCRIPTION, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
        }
        else {
          myIssueLinkRenderer.appendTextWithLinks(changeListData.DESCRIPTION);
        }
        final int count = node.getChildCount();
        final String numFilesText = " (" + count + ((count == 1) ? " file) " : " files) ");
        append(numFilesText, SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);

        final String date = DateFormatUtil.formatPrettyDateTime(changeListData.DATE);
        append(" (" + date + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
        setIcon(PatchFileType.INSTANCE.getIcon());
      }
      else if (nodeValue instanceof ShelvedChange) {
        ShelvedChange change = (ShelvedChange)nodeValue;
        final String movedMessage = myMoveRenameInfo.get(Couple.of(change.getBeforePath(), change.getAfterPath()));
        renderFileName(change.getBeforePath(), change.getFileStatus(), movedMessage);
      }
      else if (nodeValue instanceof ShelvedBinaryFile) {
        ShelvedBinaryFile binaryFile = (ShelvedBinaryFile)nodeValue;
        String path = binaryFile.BEFORE_PATH;
        if (path == null) {
          path = binaryFile.AFTER_PATH;
        }
        final String movedMessage = myMoveRenameInfo.get(Couple.of(binaryFile.BEFORE_PATH, binaryFile.AFTER_PATH));
        renderFileName(path, binaryFile.getFileStatus(), movedMessage);
      }
    }

    private void renderFileName(String path, final FileStatus fileStatus, final String movedMessage) {
      path = path.replace('/', File.separatorChar);
      int pos = path.lastIndexOf(File.separatorChar);
      String fileName;
      String directory;
      if (pos >= 0) {
        directory = path.substring(0, pos).replace(File.separatorChar, File.separatorChar);
        fileName = path.substring(pos + 1);
      }
      else {
        directory = "<project root>";
        fileName = path;
      }
      append(fileName, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, TargetAWT.to(fileStatus.getColor())));
      if (movedMessage != null) {
        append(movedMessage, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      append(" (" + directory + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      setIcon(FileTypeManager.getInstance().getFileTypeByFileName(fileName).getIcon());
    }
  }

  private class MyChangeListDeleteProvider implements DeleteProvider {
    @Override
    public void deleteElement(@Nonnull DataContext dataContext) {
      //noinspection unchecked
      final List<ShelvedChangeList> shelvedChangeLists = getLists(dataContext);
      if (shelvedChangeLists.isEmpty()) return;
      LocalizeValue message = (shelvedChangeLists.size() == 1)
        ? VcsLocalize.shelveChangesDeleteConfirm(shelvedChangeLists.get(0).DESCRIPTION)
        : VcsLocalize.shelveChangesDeleteMultipleConfirm(shelvedChangeLists.size());
      int rc = Messages.showOkCancelDialog(
        myProject,
        message.get(),
        VcsLocalize.shelvedchangesDeleteTitle().get(),
        CommonLocalize.buttonDelete().get(),
        CommonLocalize.buttonCancel().get(),
        UIUtil.getWarningIcon()
      );
      if (rc != 0) return;
      for (ShelvedChangeList changeList : shelvedChangeLists) {
        ShelveChangesManager.getInstance(myProject).deleteChangeList(changeList);
      }
    }

    @Override
    public boolean canDeleteElement(@Nonnull DataContext dataContext) {
      //noinspection unchecked
      return !getLists(dataContext).isEmpty();
    }

    private List<ShelvedChangeList> getLists(final DataContext dataContext) {
      final ShelvedChangeList[] shelved = dataContext.getData(SHELVED_CHANGELIST_KEY);
      final ShelvedChangeList[] recycled = dataContext.getData(SHELVED_RECYCLED_CHANGELIST_KEY);

      final List<ShelvedChangeList> shelvedChangeLists = (shelved == null && recycled == null) ? Collections.<ShelvedChangeList>emptyList() : new ArrayList<>();
      if (shelved != null) {
        ContainerUtil.addAll(shelvedChangeLists, shelved);
      }
      if (recycled != null) {
        ContainerUtil.addAll(shelvedChangeLists, recycled);
      }
      return shelvedChangeLists;
    }
  }

  private class MyChangesDeleteProvider implements DeleteProvider {
    @Override
    public void deleteElement(@Nonnull DataContext dataContext) {
      final Project project = dataContext.getData(Project.KEY);
      if (project == null) return;
      final ShelvedChangeList[] shelved = dataContext.getData(SHELVED_CHANGELIST_KEY);
      if (shelved == null || (shelved.length != 1)) return;
      final List<ShelvedChange> changes = dataContext.getData(SHELVED_CHANGE_KEY);
      final List<ShelvedBinaryFile> binaryFiles = dataContext.getData(SHELVED_BINARY_FILE_KEY);

      final ShelvedChangeList list = shelved[0];

      final LocalizeValue message = VcsLocalize.shelveChangesDeleteFilesFromList((changes == null ? 0 : changes.size()) + (binaryFiles == null ? 0 : binaryFiles.size()));
      int rc = Messages.showOkCancelDialog(
        myProject,
        message.get(),
        VcsLocalize.shelveChangesDeleteFilesFromListTitle().get(),
        UIUtil.getWarningIcon()
      );
      if (rc != 0) return;

      final ArrayList<ShelvedBinaryFile> oldBinaries = new ArrayList<>(list.getBinaryFiles());
      final ArrayList<ShelvedChange> oldChanges = new ArrayList<>(list.getChanges(project));

      oldBinaries.removeAll(binaryFiles);
      oldChanges.removeAll(changes);

      final CommitContext commitContext = new CommitContext();
      final List<FilePatch> patches = new ArrayList<>();
      final List<VcsException> exceptions = new ArrayList<>();
      for (ShelvedChange change : oldChanges) {
        try {
          patches.add(change.loadFilePatch(myProject, commitContext));
        }
        catch (IOException | PatchSyntaxException e) {
          //noinspection ThrowableInstanceNeverThrown
          exceptions.add(new VcsException(e));
        }
      }

      myShelveChangesManager.saveRemainingPatches(list, patches, oldBinaries, commitContext);

      if (!exceptions.isEmpty()) {
        String title = list.DESCRIPTION == null ? "" : list.DESCRIPTION;
        title = title.substring(0, Math.min(10, list.DESCRIPTION.length()));
        AbstractVcsHelper.getInstance(myProject).showErrors(exceptions, "Deleting files from '" + title + "'");
      }
    }

    @Override
    public boolean canDeleteElement(@Nonnull DataContext dataContext) {
      final ShelvedChangeList[] shelved = dataContext.getData(SHELVED_CHANGELIST_KEY);
      if (shelved == null || (shelved.length != 1)) return false;
      final List<ShelvedChange> changes = dataContext.getData(SHELVED_CHANGE_KEY);
      if (changes != null && (!changes.isEmpty())) return true;
      final List<ShelvedBinaryFile> binaryFiles = dataContext.getData(SHELVED_BINARY_FILE_KEY);
      return (binaryFiles != null && (!binaryFiles.isEmpty()));
    }
  }

  private class ShelvedChangeDeleteProvider implements DeleteProvider {
    private final List<DeleteProvider> myProviders;

    private ShelvedChangeDeleteProvider() {
      myProviders = Arrays.asList(new MyChangesDeleteProvider(), new MyChangeListDeleteProvider());
    }

    @Nullable
    private DeleteProvider selectDelegate(final DataContext dataContext) {
      for (DeleteProvider provider : myProviders) {
        if (provider.canDeleteElement(dataContext)) {
          return provider;
        }
      }
      return null;
    }

    @Override
    public void deleteElement(@Nonnull DataContext dataContext) {
      final DeleteProvider delegate = selectDelegate(dataContext);
      if (delegate != null) {
        delegate.deleteElement(dataContext);
      }
    }

    @Override
    public boolean canDeleteElement(@Nonnull DataContext dataContext) {
      return selectDelegate(dataContext) != null;
    }
  }
}
