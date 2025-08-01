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
package consulo.compiler.execution;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.AccessRule;
import consulo.application.dumb.IndexNotReadyException;
import consulo.compiler.CompileStatusNotification;
import consulo.compiler.CompilerManager;
import consulo.compiler.CompilerRunner;
import consulo.compiler.scope.CompileScope;
import consulo.component.extension.ExtensionPoint;
import consulo.dataContext.DataContext;
import consulo.execution.BeforeRunTask;
import consulo.execution.BeforeRunTaskProvider;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.configuration.RunProfileWithCompileBeforeLaunchOption;
import consulo.execution.localize.ExecutionLocalize;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import consulo.util.concurrent.AsyncResult;
import consulo.util.dataholder.Key;
import consulo.util.lang.Comparing;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;

/**
 * @author spleaner
 */
@ExtensionImpl(id = "compileBeforeRun")
public class CompileStepBeforeRun extends BeforeRunTaskProvider<CompileStepBeforeRun.MakeBeforeRunTask> {
    /**
     * Marked for disable adding CompileStepBeforeRun
     */
    public static interface Suppressor {
    }

    private static final Logger LOG = Logger.getInstance(CompileStepBeforeRun.class);
    public static final Key<MakeBeforeRunTask> ID = Key.create("Make");

    protected static final String MAKE_PROJECT_ON_RUN_KEY = "makeProjectOnRun";

    private final Project myProject;

    @Inject
    public CompileStepBeforeRun(@Nonnull Project project) {
        myProject = project;
    }

    @Nonnull
    @Override
    public Key<MakeBeforeRunTask> getId() {
        return ID;
    }

    @Nonnull
    @Override
    public String getName() {
        return ExecutionLocalize.beforeLaunchCompileStep().get();
    }

    @Nonnull
    @Override
    public String getDescription(MakeBeforeRunTask task) {
        return ExecutionLocalize.beforeLaunchCompileStep().get();
    }

    @Override
    public Image getIcon() {
        ExtensionPoint<CompilerRunner> point = myProject.getExtensionPoint(CompilerRunner.class);
        CompilerRunner runner = point.findFirstSafe(CompilerRunner::isAvailable);
        if (runner != null) {
            return runner.getBuildIcon();
        }
        return PlatformIconGroup.actionsCompile();
    }

    @Override
    public Image getTaskIcon(MakeBeforeRunTask task) {
        return getIcon();
    }

    @Override
    public MakeBeforeRunTask createTask(RunConfiguration configuration) {
        MakeBeforeRunTask task = null;

        if (!(configuration instanceof Suppressor) && configuration instanceof RunProfileWithCompileBeforeLaunchOption) {
            task = new MakeBeforeRunTask();
            if (configuration instanceof RunConfigurationBase runConfiguration) {
                task.setEnabled(runConfiguration.isCompileBeforeLaunchAddedByDefault());
            }
        }
        return task;
    }

    @Nonnull
    @RequiredUIAccess
    @Override
    public AsyncResult<Void> configureTask(RunConfiguration runConfiguration, MakeBeforeRunTask task) {
        return AsyncResult.rejected();
    }

    @Nonnull
    @Override
    public AsyncResult<Void> executeTaskAsync(
        UIAccess uiAccess,
        DataContext context,
        RunConfiguration configuration,
        ExecutionEnvironment env,
        MakeBeforeRunTask task
    ) {
        return doMake(uiAccess, myProject, configuration, false);
    }

    static AsyncResult<Void> doMake(
        UIAccess uiAccess,
        Project myProject,
        RunConfiguration configuration,
        boolean ignoreErrors
    ) {
        if (!(configuration instanceof RunProfileWithCompileBeforeLaunchOption runConfiguration)) {
            return AsyncResult.rejected();
        }

        if (configuration instanceof RunConfigurationBase rcb && rcb.excludeCompileBeforeLaunchOption()) {
            return AsyncResult.resolved();
        }

        AsyncResult<Void> result = AsyncResult.undefined();
        try {
            CompileStatusNotification callback = (aborted, errors, warnings, compileContext) -> {
                if ((errors == 0 || ignoreErrors) && !aborted) {
                    result.setDone();
                }
                else {
                    result.setRejected();
                }
            };

            boolean[] isTestCompile = new boolean[]{true};
            try {
                isTestCompile[0] = DumbService.getInstance(myProject)
                    .runWithAlternativeResolveEnabled(runConfiguration::includeTestScope);
            }
            catch (IndexNotReadyException ignored) {
            }

            CompileScope scope;
            CompilerManager compilerManager = CompilerManager.getInstance(myProject);
            if (Comparing.equal(Boolean.TRUE.toString(), System.getProperty(MAKE_PROJECT_ON_RUN_KEY))) {
                // user explicitly requested whole-project make
                scope = AccessRule.read(() -> compilerManager.createProjectCompileScope(isTestCompile[0]));
            }
            else {
                Module[] modules = runConfiguration.getModules();
                if (modules.length > 0) {
                    for (Module module : modules) {
                        if (module == null) {
                            LOG.error(
                                "RunConfiguration should not return null modules. Configuration=" + runConfiguration.getName() +
                                    "; class=" + runConfiguration.getClass().getName()
                            );
                        }
                    }
                    scope = AccessRule.read(() -> compilerManager.createModulesCompileScope(modules, true, isTestCompile[0]));
                }
                else if (runConfiguration.isBuildProjectOnEmptyModuleList()) {
                    scope = AccessRule.read(() -> compilerManager.createProjectCompileScope(isTestCompile[0]));
                }
                else {
                    result.setDone();
                    return result;
                }
            }

            scope.putUserData(RunConfiguration.KEY, configuration);

            uiAccess.give(() -> compilerManager.make(scope, callback));
        }
        catch (Exception e) {
            result.rejectWithThrowable(e);
        }

        return result;
    }

    public static class MakeBeforeRunTask extends BeforeRunTask<MakeBeforeRunTask> {
        private MakeBeforeRunTask() {
            super(ID);
            setEnabled(true);
        }
    }
}
