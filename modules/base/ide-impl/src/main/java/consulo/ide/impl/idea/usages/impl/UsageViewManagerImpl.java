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
package consulo.ide.impl.idea.usages.impl;

import consulo.annotation.component.ServiceImpl;
import consulo.ide.impl.idea.find.SearchInBackgroundOption;
import consulo.dataContext.DataSink;
import consulo.dataContext.TypeSafeDataProvider;
import consulo.application.internal.TooManyUsagesStatus;
import consulo.util.lang.StringUtil;
import consulo.ide.impl.idea.usages.UsageLimitUtil;
import consulo.usage.rule.PsiElementUsage;
import consulo.usage.rule.UsageInFile;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.content.scope.SearchScope;
import consulo.language.impl.internal.content.scope.ProjectAndLibrariesScope;
import consulo.language.impl.internal.content.scope.ProjectScopeImpl;
import consulo.language.file.inject.VirtualFileWindow;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.scope.EverythingGlobalScope;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowId;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.usage.*;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * @author max
 */
@Singleton
@ServiceImpl
public class UsageViewManagerImpl extends UsageViewManager {
  private static final Logger LOG = Logger.getInstance(UsageViewManagerImpl.class);
  private final Project myProject;
  private static final Key<UsageView> USAGE_VIEW_KEY = Key.create("USAGE_VIEW");

  @Inject
  public UsageViewManagerImpl(@Nonnull Project project) {
    myProject = project;
  }

  @Override
  @Nonnull
  public UsageView createUsageView(@Nonnull UsageTarget[] targets, @Nonnull Usage[] usages, @Nonnull UsageViewPresentation presentation, Supplier<UsageSearcher> usageSearcherFactory) {
    UsageViewImpl usageView = new UsageViewImpl(myProject, presentation, targets, usageSearcherFactory);
    appendUsages(usages, usageView);
    usageView.setSearchInProgress(false);
    return usageView;
  }

  @Override
  @Nonnull
  public UsageView showUsages(@Nonnull UsageTarget[] searchedFor, @Nonnull Usage[] foundUsages, @Nonnull UsageViewPresentation presentation, Supplier<UsageSearcher> factory) {
    UsageView usageView = createUsageView(searchedFor, foundUsages, presentation, factory);
    addContent((UsageViewImpl)usageView, presentation);
    showToolWindow(true);
    UIUtil.invokeLaterIfNeeded(() -> {
      if (!((UsageViewImpl)usageView).isDisposed()) {
        ((UsageViewImpl)usageView).expandRoot();
      }
    });
    return usageView;
  }

  @Override
  @Nonnull
  public UsageView showUsages(@Nonnull UsageTarget[] searchedFor, @Nonnull Usage[] foundUsages, @Nonnull UsageViewPresentation presentation) {
    return showUsages(searchedFor, foundUsages, presentation, null);
  }

  void addContent(@Nonnull UsageViewImpl usageView, @Nonnull UsageViewPresentation presentation) {
    Content content = UsageViewContentManager.getInstance(myProject)
            .addContent(presentation.getTabText(), presentation.getTabName(), presentation.getToolwindowTitle(), true, usageView.getComponent(), presentation.isOpenInNewTab(), true);
    usageView.setContent(content);
    content.putUserData(USAGE_VIEW_KEY, usageView);
  }

  @Override
  public UsageView searchAndShowUsages(@Nonnull final UsageTarget[] searchFor,
                                       @Nonnull final Supplier<UsageSearcher> searcherFactory,
                                       final boolean showPanelIfOnlyOneUsage,
                                       final boolean showNotFoundMessage,
                                       @Nonnull final UsageViewPresentation presentation,
                                       @Nullable final UsageViewStateListener listener) {
    final FindUsagesProcessPresentation processPresentation = new FindUsagesProcessPresentation(presentation);
    processPresentation.setShowNotFoundMessage(showNotFoundMessage);
    processPresentation.setShowPanelIfOnlyOneUsage(showPanelIfOnlyOneUsage);

    return doSearchAndShow(searchFor, searcherFactory, presentation, processPresentation, listener);
  }

  private UsageView doSearchAndShow(@Nonnull final UsageTarget[] searchFor,
                                    @Nonnull final Supplier<UsageSearcher> searcherFactory,
                                    @Nonnull final UsageViewPresentation presentation,
                                    @Nonnull final FindUsagesProcessPresentation processPresentation,
                                    @Nullable final UsageViewStateListener listener) {
    final SearchScope searchScopeToWarnOfFallingOutOf = getMaxSearchScopeToWarnOfFallingOutOf(searchFor);
    final AtomicReference<UsageViewImpl> usageViewRef = new AtomicReference<>();
    long start = System.currentTimeMillis();
    Task.Backgroundable task = new Task.Backgroundable(myProject, getProgressTitle(presentation), true, new SearchInBackgroundOption()) {
      @Override
      public void run(@Nonnull final ProgressIndicator indicator) {
        new SearchForUsagesRunnable(UsageViewManagerImpl.this, UsageViewManagerImpl.this.myProject, usageViewRef, presentation, searchFor, searcherFactory, processPresentation,
                                    searchScopeToWarnOfFallingOutOf, listener).run();
      }

      @Nonnull
      @Override
      public NotificationInfo getNotificationInfo() {
        UsageViewImpl usageView = usageViewRef.get();
        int count = usageView == null ? 0 : usageView.getUsagesCount();
        String notification = StringUtil.capitalizeWords(UsageViewBundle.message("usages.n", count), true);
        LOG.debug(notification + " in " + (System.currentTimeMillis() - start) + "ms.");
        return new NotificationInfo("Find Usages", "Find Usages Finished", notification);
      }
    };
    ProgressManager.getInstance().run(task);
    return usageViewRef.get();
  }

  @Nonnull
  SearchScope getMaxSearchScopeToWarnOfFallingOutOf(@Nonnull UsageTarget[] searchFor) {
    UsageTarget target = searchFor.length > 0 ? searchFor[0] : null;
    if (target instanceof TypeSafeDataProvider) {
      final SearchScope[] scope = new SearchScope[1];
      ((TypeSafeDataProvider)target).calcData(UsageView.USAGE_SCOPE, new DataSink() {
        @Override
        public <T> void put(Key<T> key, T data) {
          scope[0] = (SearchScope)data;
        }
      });
      return scope[0];
    }
    return GlobalSearchScope.allScope(myProject); // by default do not warn of falling out of scope
  }

  @Override
  public void searchAndShowUsages(@Nonnull UsageTarget[] searchFor,
                                  @Nonnull Supplier<UsageSearcher> searcherFactory,
                                  @Nonnull FindUsagesProcessPresentation processPresentation,
                                  @Nonnull UsageViewPresentation presentation,
                                  @Nullable UsageViewStateListener listener) {
    doSearchAndShow(searchFor, searcherFactory, presentation, processPresentation, listener);
  }

  @Override
  public UsageView getSelectedUsageView() {
    final Content content = UsageViewContentManager.getInstance(myProject).getSelectedContent();
    if (content != null) {
      return content.getUserData(USAGE_VIEW_KEY);
    }

    return null;
  }

  @Nonnull
  public static String getProgressTitle(@Nonnull UsageViewPresentation presentation) {
    final String scopeText = presentation.getScopeText();
    String usagesString = StringUtil.capitalize(presentation.getUsagesString());
    return UsageViewBundle.message("progress.searching.for.in", usagesString, scopeText, presentation.getContextText());
  }

  void showToolWindow(boolean activateWindow) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.FIND);
    toolWindow.show(null);
    if (activateWindow && !toolWindow.isActive()) {
      toolWindow.activate(null);
    }
  }

  protected static void appendUsages(@Nonnull final Usage[] foundUsages, @Nonnull final UsageViewImpl usageView) {
    ApplicationManager.getApplication().runReadAction(() -> {
      for (Usage foundUsage : foundUsages) {
        usageView.appendUsage(foundUsage);
      }
    });
  }


  public static void showTooManyUsagesWarningLater(@Nonnull final Project project,
                                                   @Nonnull final TooManyUsagesStatus tooManyUsagesStatus,
                                                   @Nonnull final ProgressIndicator indicator,
                                                   @Nonnull final UsageViewPresentation presentation,
                                                   final int usageCount,
                                                   @Nullable final UsageViewImpl usageView) {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (usageView != null && usageView.searchHasBeenCancelled() || indicator.isCanceled()) return;
      int shownUsageCount = usageView == null ? usageCount : usageView.getRoot().getRecursiveUsageCount();
      String message = UsageViewBundle.message("find.excessive.usage.count.prompt", shownUsageCount, StringUtil.pluralize(presentation.getUsagesWord()));
      UsageLimitUtil.Result ret = UsageLimitUtil.showTooManyUsagesWarning(project, message, presentation);
      if (ret == UsageLimitUtil.Result.ABORT) {
        if (usageView != null) {
          usageView.cancelCurrentSearch();
        }
        indicator.cancel();
      }
      tooManyUsagesStatus.userResponded();
    });
  }

  public static long getFileLength(@Nonnull final VirtualFile virtualFile) {
    final long[] length = {-1L};
    ApplicationManager.getApplication().runReadAction(() -> {
      if (!virtualFile.isValid()) return;
      length[0] = virtualFile.getLength();
    });
    return length[0];
  }

  @Nonnull
  public static String presentableSize(long bytes) {
    long megabytes = bytes / (1024 * 1024);
    return UsageViewBundle.message("find.file.size.megabytes", Long.toString(megabytes));
  }

  public static boolean isInScope(@Nonnull Usage usage, @Nonnull SearchScope searchScope) {
    PsiElement element = null;
    VirtualFile file =
            usage instanceof UsageInFile ? ((UsageInFile)usage).getFile() : usage instanceof PsiElementUsage ? PsiUtilCore.getVirtualFile(element = ((PsiElementUsage)usage).getElement()) : null;
    if (file != null) {
      return isFileInScope(file, searchScope);
    }
    return element != null && (searchScope instanceof EverythingGlobalScope || searchScope instanceof ProjectScopeImpl || searchScope instanceof ProjectAndLibrariesScope);
  }

  private static boolean isFileInScope(@Nonnull VirtualFile file, @Nonnull SearchScope searchScope) {
    if (file instanceof VirtualFileWindow) {
      file = ((VirtualFileWindow)file).getDelegate();
    }
    return searchScope.contains(file);
  }

  @Nonnull
  public static String outOfScopeMessage(int nUsages, @Nonnull SearchScope searchScope) {
    return (nUsages == 1 ? "One usage is" : nUsages + " usages are") + " out of scope '" + searchScope.getDisplayName() + "'";
  }

}
