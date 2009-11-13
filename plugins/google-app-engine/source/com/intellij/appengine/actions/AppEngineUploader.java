package com.intellij.appengine.actions;

import com.intellij.CommonBundle;
import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineBuilder;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

/**
 * @author nik
 */
public class AppEngineUploader {
  private Project myProject;

  public AppEngineUploader(Project project) {
    myProject = project;
  }

  public void startUploading(@NotNull final Artifact artifact) {
    final String explodedPath = artifact.getOutputPath();
    if (explodedPath == null) {
      Messages.showErrorDialog(myProject, "Output path isn't specified for '" + artifact.getName() + "' artifact", CommonBundle.getErrorTitle());
      return;
    }

    final AppEngineFacet appEngineFacet = AppEngineUtil.findAppEngineFacet(myProject, artifact);
    if (appEngineFacet == null) {
      Messages.showErrorDialog(myProject, "App Engine facet not found in '" + artifact.getName() + "' artifact", CommonBundle.getErrorTitle());
      return;
    }
    final AppEngineSdk sdk = appEngineFacet.getSdk();
    if (!sdk.getAppCfgFile().exists()) {
      Messages.showErrorDialog(myProject, "Path to App Engine SDK isn't specified correctly in App Engine Facet settings", CommonBundle.getErrorTitle());
      return;
    }

    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Uploading application", true, null) {
      public void run(@NotNull ProgressIndicator indicator) {
        compileAndUpload(artifact, appEngineFacet.getModule(), sdk, explodedPath);
      }
    });
  }

  private void compileAndUpload(final Artifact artifact, final Module module, final AppEngineSdk sdk, final String explodedPath) {
    final Runnable startUploading = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            startUploadingProcess(module, sdk, explodedPath);
          }
        });
      }
    };

    final CompilerManager compilerManager = CompilerManager.getInstance(myProject);
    final CompileScope compileScope = ArtifactCompileScope.createScopeWithArtifacts(compilerManager.createModuleCompileScope(module, true), Collections.singletonList(artifact));
    if (!compilerManager.isUpToDate(compileScope)) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          final int answer = Messages.showYesNoDialog(myProject, "Output directory may be out of date. Do you want to build it?", "Upload Application", null);
          if (answer == 0) {
            compilerManager.make(compileScope, new CompileStatusNotification() {
              public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
                if (!aborted && errors == 0) {
                  startUploading.run();
                }
              }
            });
          }
          else {
            startUploading.run();
          }
        }
      });
    }
    else {
      startUploading.run();
    }
  }

  private void startUploadingProcess(Module module, AppEngineSdk sdk, String explodedPath) {
    final Process process;
    final GeneralCommandLine commandLine;

    try {
      JavaParameters parameters = new JavaParameters();
      parameters.configureByModule(module, JavaParameters.JDK_ONLY);
      parameters.setMainClass("com.google.appengine.tools.admin.AppCfg");
      parameters.getClassPath().add(sdk.getToolsApiJarFile().getAbsolutePath());

      HttpConfigurable httpConfigurable = HttpConfigurable.getInstance();
      if (httpConfigurable.USE_HTTP_PROXY) {
        final ParametersList parametersList = parameters.getVMParametersList();
        parametersList.defineProperty("proxySet", "true");
        parametersList.defineProperty("http.proxyHost", httpConfigurable.PROXY_HOST);
        parametersList.defineProperty("http.proxyPort", Integer.toString(httpConfigurable.PROXY_PORT));
      }

      final ParametersList programParameters = parameters.getProgramParametersList();
      programParameters.add("update");
      programParameters.add(FileUtil.toSystemDependentName(explodedPath));

      commandLine = CommandLineBuilder.createFromJavaParameters(parameters);
      process = commandLine.createProcess();
    }
    catch (ExecutionException e) {
      Messages.showErrorDialog(myProject, "Cannot start uploading: " + e.getMessage(), CommonBundle.getErrorTitle());
      return;
    }

    final Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    final ConsoleView console = TextConsoleBuilderFactory.getInstance().createBuilder(myProject).getConsole();
    final RunnerLayoutUi ui = RunnerLayoutUi.Factory.getInstance(myProject).create("Upload", "Upload Application", "Upload Application", myProject);
    final DefaultActionGroup group = new DefaultActionGroup();
    ui.getOptions().setLeftToolbar(group, ActionPlaces.UNKNOWN);
    ui.addContent(ui.createContent("upload", console.getComponent(), "Upload Application", null, console.getPreferredFocusableComponent()));

    ProcessHandler processHandler = new OSProcessHandler(process, commandLine.getCommandLineString());
    console.attachToProcess(processHandler);
    final RunContentDescriptor contentDescriptor = new RunContentDescriptor(console, processHandler, ui.getComponent(), "Upload Application");
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_STOP_PROGRAM));
    group.add(new CloseAction(executor, contentDescriptor, myProject));

    ExecutionManager.getInstance(myProject).getContentManager().showRunContent(executor, contentDescriptor);
    processHandler.startNotify();
  }
}