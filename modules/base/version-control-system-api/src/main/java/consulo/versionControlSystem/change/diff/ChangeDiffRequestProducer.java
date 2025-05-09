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
package consulo.versionControlSystem.change.diff;

import consulo.application.progress.ProgressIndicator;
import consulo.component.ProcessCanceledException;
import consulo.diff.DiffContentFactory;
import consulo.diff.DiffRequestFactory;
import consulo.diff.DiffUserDataKeys;
import consulo.diff.DiffViewerWrapper;
import consulo.diff.chain.DiffRequestProducer;
import consulo.diff.chain.DiffRequestProducerException;
import consulo.diff.content.DiffContent;
import consulo.diff.internal.DiffContentFactoryEx;
import consulo.diff.internal.DiffRequestFactoryEx;
import consulo.diff.internal.DiffUserDataKeysEx;
import consulo.diff.request.DiffRequest;
import consulo.diff.request.ErrorDiffRequest;
import consulo.diff.request.SimpleDiffRequest;
import consulo.diff.util.DiffUtil;
import consulo.diff.util.Side;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.dataholder.UserDataHolder;
import consulo.util.lang.ThreeState;
import consulo.util.lang.ref.SimpleReference;
import consulo.versionControlSystem.AbstractVcs;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsDataKeys;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.*;
import consulo.versionControlSystem.merge.MergeData;
import consulo.versionControlSystem.merge.MergeDiffUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ChangeDiffRequestProducer implements DiffRequestProducer {
    private static final Logger LOG = Logger.getInstance(ChangeDiffRequestProducer.class);

    public static Key<Change> CHANGE_KEY = Key.create("DiffRequestPresentable.Change");

    @Nullable
    private final Project myProject;
    @Nonnull
    private final Change myChange;
    @Nonnull
    private final Map<Key, Object> myChangeContext;

    private ChangeDiffRequestProducer(@Nullable Project project, @Nonnull Change change, @Nonnull Map<Key, Object> changeContext) {
        myChange = change;
        myProject = project;
        myChangeContext = changeContext;
    }

    @Nonnull
    public Change getChange() {
        return myChange;
    }

    @Nullable
    public Project getProject() {
        return myProject;
    }

    @Nonnull
    @Override
    public String getName() {
        return ChangesUtil.getFilePath(myChange).getPath();
    }

    public static boolean isEquals(@Nonnull Change change1, @Nonnull Change change2) {
        for (ChangeDiffViewerWrapperProvider provider : ChangeDiffViewerWrapperProvider.EP_NAME.getExtensionList()) {
            ThreeState equals = provider.isEquals(change1, change2);
            if (equals == ThreeState.NO) {
                return false;
            }
        }
        for (ChangeDiffRequestProvider provider : ChangeDiffRequestProvider.EP_NAME.getExtensionList()) {
            ThreeState equals = provider.isEquals(change1, change2);
            if (equals == ThreeState.YES) {
                return true;
            }
            if (equals == ThreeState.NO) {
                return false;
            }
        }

        return Objects.equals(change1.getClass(), change2.getClass())
            && isEquals(change1.getBeforeRevision(), change2.getBeforeRevision())
            && isEquals(change1.getAfterRevision(), change2.getAfterRevision());
    }

    private static boolean isEquals(@Nullable ContentRevision revision1, @Nullable ContentRevision revision2) {
        return Objects.equals(revision1, revision2)
            || revision1 instanceof CurrentContentRevision contentRevision1
            && revision2 instanceof CurrentContentRevision contentRevision2
            && Objects.equals(contentRevision1.getVirtualFile(), contentRevision2.getVirtualFile());
    }

    @Nullable
    public static ChangeDiffRequestProducer create(@Nullable Project project, @Nonnull Change change) {
        return create(project, change, Collections.<Key, Object>emptyMap());
    }

    @Nullable
    public static ChangeDiffRequestProducer create(
        @Nullable Project project,
        @Nonnull Change change,
        @Nonnull Map<Key, Object> changeContext
    ) {
        if (!canCreate(project, change)) {
            return null;
        }
        return new ChangeDiffRequestProducer(project, change, changeContext);
    }

    public static boolean canCreate(@Nullable Project project, @Nonnull Change change) {
        for (ChangeDiffViewerWrapperProvider provider : ChangeDiffViewerWrapperProvider.EP_NAME.getExtensionList()) {
            if (provider.canCreate(project, change)) {
                return true;
            }
        }
        for (ChangeDiffRequestProvider provider : ChangeDiffRequestProvider.EP_NAME.getExtensionList()) {
            if (provider.canCreate(project, change)) {
                return true;
            }
        }

        ContentRevision bRev = change.getBeforeRevision();
        ContentRevision aRev = change.getAfterRevision();

        if (bRev == null && aRev == null) {
            return false;
        }
        //noinspection SimplifiableIfStatement
        if (bRev != null && bRev.getFile().isDirectory()) {
            return false;
        }
        return aRev == null || !aRev.getFile().isDirectory();
    }

    @Nonnull
    @Override
    public DiffRequest process(
        @Nonnull UserDataHolder context,
        @Nonnull ProgressIndicator indicator
    ) throws DiffRequestProducerException, ProcessCanceledException {
        try {
            return loadCurrentContents(context, indicator);
        }
        catch (ProcessCanceledException | DiffRequestProducerException e) {
            throw e;
        }
        catch (Exception e) {
            LOG.warn(e);
            throw new DiffRequestProducerException(e.getMessage());
        }
    }

    @Nonnull
    protected DiffRequest loadCurrentContents(
        @Nonnull UserDataHolder context,
        @Nonnull ProgressIndicator indicator
    ) throws DiffRequestProducerException {
        DiffRequestProducerException wrapperException = null;
        DiffRequestProducerException requestException = null;

        DiffViewerWrapper wrapper = null;
        try {
            for (ChangeDiffViewerWrapperProvider provider : ChangeDiffViewerWrapperProvider.EP_NAME.getExtensionList()) {
                if (provider.canCreate(myProject, myChange)) {
                    wrapper = provider.process(this, context, indicator);
                    break;
                }
            }
        }
        catch (DiffRequestProducerException e) {
            wrapperException = e;
        }

        DiffRequest request = null;
        try {
            for (ChangeDiffRequestProvider provider : ChangeDiffRequestProvider.EP_NAME.getExtensionList()) {
                if (provider.canCreate(myProject, myChange)) {
                    request = provider.process(this, context, indicator);
                    break;
                }
            }
            if (request == null) {
                request = createRequest(myProject, myChange, context, indicator);
            }
        }
        catch (DiffRequestProducerException e) {
            requestException = e;
        }

        if (requestException != null && wrapperException != null) {
            String message = requestException.getMessage() + "\n\n" + wrapperException.getMessage();
            throw new DiffRequestProducerException(message);
        }
        if (requestException != null) {
            request = new ErrorDiffRequest(getRequestTitle(myChange), requestException);
            LOG.info("Request: " + requestException.getMessage());
        }
        if (wrapperException != null) {
            LOG.info("Wrapper: " + wrapperException.getMessage());
        }

        request.putUserData(CHANGE_KEY, myChange);
        request.putUserData(DiffViewerWrapper.KEY, wrapper);

        for (Map.Entry<Key, Object> entry : myChangeContext.entrySet()) {
            request.putUserData(entry.getKey(), entry.getValue());
        }

        DiffUtil.putDataKey(request, VcsDataKeys.CURRENT_CHANGE, myChange);

        return request;
    }

    @Nonnull
    private DiffRequest createRequest(
        @Nullable Project project,
        @Nonnull Change change,
        @Nonnull UserDataHolder context,
        @Nonnull ProgressIndicator indicator
    ) throws DiffRequestProducerException {
        if (ChangesUtil.isTextConflictingChange(change)) { // three side diff
            // FIXME: This part is ugly as a VCS merge subsystem itself.

            FilePath path = ChangesUtil.getFilePath(change);
            VirtualFile file = path.getVirtualFile();
            if (file == null) {
                file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.getPath());
            }
            if (file == null) {
                throw new DiffRequestProducerException("Can't show merge conflict - file not found");
            }

            if (project == null) {
                throw new DiffRequestProducerException("Can't show merge conflict - project is unknown");
            }
            AbstractVcs vcs = ChangesUtil.getVcsForChange(change, project);
            if (vcs == null || vcs.getMergeProvider() == null) {
                throw new DiffRequestProducerException("Can't show merge conflict - operation nos supported");
            }
            try {
                // FIXME: loadRevisions() can call runProcessWithProgressSynchronously() inside
                SimpleReference<Throwable> exceptionRef = new SimpleReference<>();
                SimpleReference<MergeData> mergeDataRef = new SimpleReference<>();
                VirtualFile finalFile = file;
                UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
                    try {
                        mergeDataRef.set(vcs.getMergeProvider().loadRevisions(finalFile));
                    }
                    catch (VcsException e) {
                        exceptionRef.set(e);
                    }
                });
                if (!exceptionRef.isNull()) {
                    Throwable e = exceptionRef.get();
                    if (e instanceof VcsException vcsException) {
                        throw vcsException;
                    }
                    if (e instanceof Error error) {
                        throw error;
                    }
                    if (e instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new RuntimeException(e);
                }
                MergeData mergeData = mergeDataRef.get();

                ContentRevision bRev = change.getBeforeRevision();
                ContentRevision aRev = change.getAfterRevision();
                String beforeRevisionTitle = getRevisionTitle(bRev, "Your version");
                String afterRevisionTitle = getRevisionTitle(aRev, "Server version");

                String title = DiffRequestFactory.getInstance().getTitle(file);
                List<String> titles = ContainerUtil.list(beforeRevisionTitle, "Base Version", afterRevisionTitle);

                DiffContentFactory contentFactory = DiffContentFactory.getInstance();
                List<DiffContent> contents = ContainerUtil.list(
                    contentFactory.createFromBytes(project, mergeData.CURRENT, file),
                    contentFactory.createFromBytes(project, mergeData.ORIGINAL, file),
                    contentFactory.createFromBytes(project, mergeData.LAST, file)
                );

                SimpleDiffRequest request = new SimpleDiffRequest(title, contents, titles);
                MergeDiffUtil.putRevisionInfos(request, mergeData);

                return request;
            }
            catch (VcsException | IOException e) {
                LOG.info(e);
                throw new DiffRequestProducerException(e);
            }
        }
        else {
            ContentRevision bRev = change.getBeforeRevision();
            ContentRevision aRev = change.getAfterRevision();

            if (bRev == null && aRev == null) {
                LOG.warn("Both revision contents are empty");
                throw new DiffRequestProducerException("Bad revisions contents");
            }
            if (bRev != null) {
                checkContentRevision(project, bRev, context, indicator);
            }
            if (aRev != null) {
                checkContentRevision(project, aRev, context, indicator);
            }

            String title = getRequestTitle(change);

            indicator.setIndeterminate(true);
            DiffContent content1 = createContent(project, bRev, context, indicator);
            DiffContent content2 = createContent(project, aRev, context, indicator);

            String userLeftRevisionTitle = (String)myChangeContext.get(DiffUserDataKeysEx.VCS_DIFF_LEFT_CONTENT_TITLE);
            String beforeRevisionTitle = userLeftRevisionTitle != null ? userLeftRevisionTitle : getRevisionTitle(bRev, "Base version");
            String userRightRevisionTitle = (String)myChangeContext.get(DiffUserDataKeysEx.VCS_DIFF_RIGHT_CONTENT_TITLE);
            String afterRevisionTitle = userRightRevisionTitle != null ? userRightRevisionTitle : getRevisionTitle(aRev, "Your version");

            SimpleDiffRequest request = new SimpleDiffRequest(title, content1, content2, beforeRevisionTitle, afterRevisionTitle);

            boolean bRevCurrent = bRev instanceof CurrentContentRevision;
            boolean aRevCurrent = aRev instanceof CurrentContentRevision;
            if (bRevCurrent && !aRevCurrent) {
                request.putUserData(DiffUserDataKeys.MASTER_SIDE, Side.LEFT);
            }
            if (!bRevCurrent && aRevCurrent) {
                request.putUserData(DiffUserDataKeys.MASTER_SIDE, Side.RIGHT);
            }

            return request;
        }
    }

    @Nonnull
    public static String getRequestTitle(@Nonnull Change change) {
        ContentRevision bRev = change.getBeforeRevision();
        ContentRevision aRev = change.getAfterRevision();

        assert bRev != null || aRev != null;
        if (bRev != null && aRev != null) {
            FilePath bPath = bRev.getFile();
            FilePath aPath = aRev.getFile();
            if (bPath.equals(aPath)) {
                return DiffRequestFactoryEx.getInstanceEx().getContentTitle(bPath);
            }
            else {
                return DiffRequestFactoryEx.getInstanceEx().getTitle(bPath, aPath, " -> ");
            }
        }
        else if (bRev != null) {
            return DiffRequestFactoryEx.getInstanceEx().getContentTitle(bRev.getFile());
        }
        else {
            return DiffRequestFactoryEx.getInstanceEx().getContentTitle(aRev.getFile());
        }
    }

    @Nonnull
    public static String getRevisionTitle(@Nullable ContentRevision revision, @Nonnull String defaultValue) {
        if (revision == null) {
            return defaultValue;
        }
        String title = revision.getRevisionNumber().asString();
        if (title == null || title.isEmpty()) {
            return defaultValue;
        }
        return title;
    }

    @Nonnull
    public static DiffContent createContent(
        @Nullable Project project,
        @Nullable ContentRevision revision,
        @Nonnull UserDataHolder context,
        @Nonnull ProgressIndicator indicator
    ) throws DiffRequestProducerException {
        try {
            indicator.checkCanceled();

            if (revision == null) {
                return DiffContentFactory.getInstance().createEmpty();
            }
            FilePath filePath = revision.getFile();
            DiffContentFactoryEx contentFactory = DiffContentFactoryEx.getInstanceEx();

            if (revision instanceof CurrentContentRevision currentContentRevision) {
                VirtualFile vFile = currentContentRevision.getVirtualFile();
                if (vFile == null) {
                    throw new DiffRequestProducerException("Can't get current revision content");
                }
                return contentFactory.create(project, vFile);
            }

            if (revision instanceof BinaryContentRevision binaryContentRevision) {
                byte[] content = binaryContentRevision.getBinaryContent();
                if (content == null) {
                    throw new DiffRequestProducerException("Can't get binary revision content");
                }
                return contentFactory.createFromBytes(project, content, filePath);
            }

            if (revision instanceof ByteBackedContentRevision byteBackedContentRevision) {
                byte[] revisionContent = byteBackedContentRevision.getContentAsBytes();
                if (revisionContent == null) {
                    throw new DiffRequestProducerException("Can't get revision content");
                }
                return contentFactory.createFromBytes(project, revisionContent, filePath);
            }
            else {
                String revisionContent = revision.getContent();
                if (revisionContent == null) {
                    throw new DiffRequestProducerException("Can't get revision content");
                }
                return contentFactory.create(project, revisionContent, filePath);
            }
        }
        catch (IOException | VcsException e) {
            LOG.info(e);
            throw new DiffRequestProducerException(e);
        }
    }

    public static void checkContentRevision(
        @Nullable Project project,
        @Nonnull ContentRevision rev,
        @Nonnull UserDataHolder context,
        @Nonnull ProgressIndicator indicator
    ) throws DiffRequestProducerException {
        if (rev.getFile().isDirectory()) {
            throw new DiffRequestProducerException("Can't show diff for directory");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ChangeDiffRequestProducer that = (ChangeDiffRequestProducer)o;

        return myChange.equals(that.myChange);
    }

    @Override
    public int hashCode() {
        return myChange.hashCode();
    }
}
