package com.intellij.remoteServer.impl.runtime;

import consulo.execution.configuration.RunProfile;
import consulo.execution.debug.DefaultDebugExecutor;
import consulo.execution.executor.DefaultRunExecutor;
import consulo.execution.runner.DefaultProgramRunner;
import com.intellij.remoteServer.impl.configuration.deployment.DeployToServerRunConfiguration;
import javax.annotation.Nonnull;

/**
 * @author nik
 */
public class DeployToServerRunner extends DefaultProgramRunner {
  @Nonnull
  @Override
  public String getRunnerId() {
    return "DeployToServer";
  }

  @Override
  public boolean canRun(@Nonnull String executorId, @Nonnull RunProfile profile) {
    if (!(profile instanceof DeployToServerRunConfiguration)) {
      return false;
    }
    if (executorId.equals(DefaultRunExecutor.EXECUTOR_ID)) {
      return true;
    }
    if (executorId.equals(DefaultDebugExecutor.EXECUTOR_ID)) {
      return ((DeployToServerRunConfiguration<?, ?>)profile).getServerType().createDebugConnector() != null;
    }
    return false;
  }
}
