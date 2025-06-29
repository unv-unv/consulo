/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package consulo.desktop.awt.internal.diff.external;

import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.component.ProcessCanceledException;
import consulo.diff.DiffDialogHints;
import consulo.diff.DiffNotificationGroups;
import consulo.diff.chain.DiffRequestChain;
import consulo.diff.chain.DiffRequestProducer;
import consulo.diff.chain.DiffRequestProducerException;
import consulo.diff.chain.SimpleDiffRequestChain;
import consulo.diff.content.DiffContent;
import consulo.diff.impl.internal.external.ExternalDiffSettings;
import consulo.diff.impl.internal.external.ExternalDiffToolUtil;
import consulo.diff.internal.DiffManagerEx;
import consulo.diff.request.ContentDiffRequest;
import consulo.diff.request.DiffRequest;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationService;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.util.dataholder.UserDataHolderBase;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExternalDiffTool {
    public static final Logger LOG = Logger.getInstance(ExternalDiffTool.class);

    public static boolean isDefault() {
        return ExternalDiffSettings.getInstance().isDiffEnabled() && ExternalDiffSettings.getInstance().isDiffDefault();
    }

    public static boolean isEnabled() {
        return ExternalDiffSettings.getInstance().isDiffEnabled();
    }

    @RequiredUIAccess
    public static void show(
        @Nullable final Project project,
        @Nonnull final DiffRequestChain chain,
        @Nonnull DiffDialogHints hints
    ) {
        try {
            //noinspection unchecked
            final SimpleReference<List<DiffRequest>> requestsRef = new SimpleReference<>();
            final SimpleReference<Throwable> exceptionRef = new SimpleReference<>();
            ProgressManager.getInstance().run(new Task.Modal(project, LocalizeValue.localizeTODO("Loading Requests"), true) {
                @Override
                public void run(@Nonnull ProgressIndicator indicator) {
                    try {
                        requestsRef.set(collectRequests(project, chain, indicator));
                    }
                    catch (Throwable e) {
                        exceptionRef.set(e);
                    }
                }
            });

            if (!exceptionRef.isNull()) {
                throw exceptionRef.get();
            }

            List<DiffRequest> showInBuiltin = new ArrayList<>();
            for (DiffRequest request : requestsRef.get()) {
                if (canShow(request)) {
                    showRequest(project, request);
                }
                else {
                    showInBuiltin.add(request);
                }
            }

            if (!showInBuiltin.isEmpty()) {
                DiffManagerEx.getInstanceEx().showDiffBuiltin(project, new SimpleDiffRequestChain(showInBuiltin), hints);
            }
        }
        catch (ProcessCanceledException ignore) {
        }
        catch (Throwable e) {
            LOG.error(e);
            Messages.showErrorDialog(project, e.getMessage(), "Can't Show Diff In External Tool");
        }
    }

    @Nonnull
    private static List<DiffRequest> collectRequests(
        @Nullable Project project,
        @Nonnull DiffRequestChain chain,
        @Nonnull ProgressIndicator indicator
    ) {
        List<DiffRequest> requests = new ArrayList<>();

        UserDataHolderBase context = new UserDataHolderBase();
        List<String> errorRequests = new ArrayList<>();

        // TODO: show all changes on explicit selection
        List<? extends DiffRequestProducer> producers = Collections.singletonList(chain.getRequests().get(chain.getIndex()));

        for (DiffRequestProducer producer : producers) {
            try {
                requests.add(producer.process(context, indicator));
            }
            catch (DiffRequestProducerException e) {
                LOG.warn(e);
                errorRequests.add(producer.getName());
            }
        }

        if (!errorRequests.isEmpty()) {
            NotificationService.getInstance()
                .newError(DiffNotificationGroups.DIFF)
                .title(LocalizeValue.localizeTODO("Can't load some changes"))
                .content(LocalizeValue.localizeTODO(StringUtil.join(errorRequests, "<br>")))
                .notify(project);
        }

        return requests;
    }

    @RequiredUIAccess
    public static void showRequest(@Nullable Project project, @Nonnull DiffRequest request) throws ExecutionException, IOException {
        request.onAssigned(true);

        ExternalDiffSettings settings = ExternalDiffSettings.getInstance();

        ContentDiffRequest contentDiffRequest = (ContentDiffRequest)request;

        List<DiffContent> contents = contentDiffRequest.getContents();
        List<String> titles = contentDiffRequest.getContentTitles();

        ExternalDiffToolUtil.execute(settings, contents, titles, request.getTitle());

        request.onAssigned(false);
    }

    public static boolean canShow(@Nonnull DiffRequest request) {
        if (request instanceof ContentDiffRequest contentDiffRequest) {
            List<DiffContent> contents = contentDiffRequest.getContents();
            if (contents.size() != 2 && contents.size() != 3) {
                return false;
            }
            for (DiffContent content : contents) {
                if (!ExternalDiffToolUtil.canCreateFile(content)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
