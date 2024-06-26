package consulo.remoteServer.impl.internal.ui.tree;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.AllIcons;
import consulo.application.Application;
import consulo.configurable.internal.ShowConfigurableService;
import consulo.execution.RunConfigurationEditor;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.executor.DefaultRunExecutor;
import consulo.execution.executor.Executor;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.project.ui.view.tree.AbstractTreeStructureBase;
import consulo.project.ui.view.tree.TreeStructureProvider;
import consulo.remoteServer.configuration.RemoteServer;
import consulo.remoteServer.configuration.RemoteServersManager;
import consulo.remoteServer.impl.internal.configuration.RemoteServerListConfigurable;
import consulo.remoteServer.impl.internal.runtime.deployment.DeploymentTaskImpl;
import consulo.remoteServer.impl.internal.runtime.log.DeploymentLogManagerImpl;
import consulo.remoteServer.impl.internal.runtime.log.LoggingHandlerImpl;
import consulo.remoteServer.impl.internal.ui.RemoteServersViewContributor;
import consulo.remoteServer.runtime.ConnectionStatus;
import consulo.remoteServer.runtime.Deployment;
import consulo.remoteServer.runtime.ServerConnection;
import consulo.remoteServer.runtime.ServerConnectionManager;
import consulo.remoteServer.runtime.deployment.DeploymentRuntime;
import consulo.remoteServer.runtime.deployment.DeploymentStatus;
import consulo.remoteServer.runtime.deployment.DeploymentTask;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.tree.PresentationData;
import consulo.ui.image.Image;
import consulo.ui.image.ImageEffects;
import consulo.util.lang.EmptyRunnable;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: michael.golubev
 */
public class ServersTreeStructure extends AbstractTreeStructureBase {
  // 1st level: servers (RunnerAndConfigurationSettings (has CommonStrategy (extends RunConfiguration)) or RemoteServer)
  // 2nd level: deployments (DeploymentModel or Deployment)

  private final ServersTreeRootNode myRootElement;
  private final Project myProject;

  public ServersTreeStructure(@Nonnull Project project) {
    super(project);
    myProject = project;
    myRootElement = new ServersTreeRootNode();
  }

  public static Image getServerNodeIcon(@Nonnull Image itemIcon, @Nullable Image statusIcon) {
    if (statusIcon == null) {
      return itemIcon;
    }

    return ImageEffects.layered(itemIcon, statusIcon);
  }

  @Override
  public List<TreeStructureProvider> getProviders() {
    return Collections.emptyList();
  }

  @Nonnull
  Project doGetProject() {
    return myProject;
  }

  @Override
  public Object getRootElement() {
    return myRootElement;
  }

  @Override
  public void commit() {
  }

  @Override
  public boolean hasSomethingToCommit() {
    return false;
  }

  public interface LogProvidingNode {
    @Nullable
    LoggingHandlerImpl getLoggingHandler();

    @Nonnull
    String getLogId();
  }

  public class ServersTreeRootNode extends AbstractTreeNode<Object> {
    public ServersTreeRootNode() {
      super(doGetProject(), new Object());
    }

    @RequiredReadAction
    @Nonnull
    @Override
    public Collection<? extends AbstractTreeNode> getChildren() {
      List<AbstractTreeNode<?>> result = new ArrayList<AbstractTreeNode<?>>();
      for (RemoteServersViewContributor contributor : RemoteServersViewContributor.EP_NAME.getExtensionList()) {
        result.addAll(contributor.createServerNodes(doGetProject()));
      }
      for (RemoteServer<?> server : RemoteServersManager.getInstance().getServers()) {
        result.add(new RemoteServerNode(server));
      }
      return result;
    }

    @Override
    protected void update(PresentationData presentation) {
    }
  }

  public class RemoteServerNode extends AbstractTreeNode<RemoteServer<?>> implements ServerNode {
    public RemoteServerNode(RemoteServer<?> server) {
      super(doGetProject(), server);
    }

    @RequiredReadAction
    @Nonnull
    @Override
    public Collection<? extends AbstractTreeNode> getChildren() {
      ServerConnection<?> connection = getConnection();
      if (connection == null) {
        return Collections.emptyList();
      }
      List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
      for (Deployment deployment : connection.getDeployments()) {
        children.add(new DeploymentNodeImpl(connection, this, deployment));
      }
      return children;
    }

    @Override
    protected void update(PresentationData presentation) {
      RemoteServer<?> server = getValue();
      ServerConnection connection = getConnection();
      presentation.setPresentableText(server.getName());
      presentation.setIcon(getServerNodeIcon(server.getType().getIcon(), connection != null ? getStatusIcon(connection.getStatus()) : null));
      presentation.setTooltip(connection != null ? connection.getStatusText() : null);
    }

    @Nullable
    private ServerConnection<?> getConnection() {
      return ServerConnectionManager.getInstance().getConnection(getValue());
    }

    @Override
    public boolean isConnected() {
      ServerConnection<?> connection = getConnection();
      return connection != null && connection.getStatus() == ConnectionStatus.CONNECTED;
    }

    @Override
    public boolean isStopActionEnabled() {
      return isConnected();
    }

    @Override
    public void stopServer() {
      ServerConnection<?> connection = getConnection();
      if (connection != null) {
        connection.disconnect();
      }
    }

    @Override
    @RequiredUIAccess
    public void editConfiguration() {
      ShowConfigurableService configurableService = Application.get().getInstance(ShowConfigurableService.class);

      configurableService.showAndSelect(doGetProject(), RemoteServerListConfigurable.class, remoteServerConfigurable -> {
        remoteServerConfigurable.selectNodeInTree(getValue());
      });
    }

    @Override
    public boolean isStartActionEnabled(@Nonnull Executor executor) {
      ServerConnection connection = getConnection();
      return executor.equals(DefaultRunExecutor.getRunExecutorInstance()) &&
             (connection == null || connection.getStatus() == ConnectionStatus.DISCONNECTED);
    }

    @Override
    public void startServer(@Nonnull Executor executor) {
      ServerConnection<?> connection = getConnection();
      if (connection != null) {
        connection.computeDeployments(EmptyRunnable.INSTANCE);
      }
    }

    @Override
    public boolean isDeployAllEnabled() {
      return false;
    }

    @Override
    public void deployAll() {
    }

    @Nullable
    private Image getStatusIcon(final ConnectionStatus status) {
      switch (status) {
        case CONNECTED: return PlatformIconGroup.remoteserversResumescaled();
        case DISCONNECTED: return PlatformIconGroup.remoteserversSuspendscaled();
        default: return null;
      }
    }
  }

  public class DeploymentNodeImpl extends AbstractTreeNode<Deployment> implements LogProvidingNode, DeploymentNode {
    private final ServerConnection<?> myConnection;
    private final RemoteServerNode myParentNode;

    private DeploymentNodeImpl(@Nonnull ServerConnection<?> connection, @Nonnull RemoteServerNode parentNode, Deployment value) {
      super(doGetProject(), value);
      myConnection = connection;
      myParentNode = parentNode;
    }

    @Override
    public boolean equals(Object object) {
      return object instanceof DeploymentNodeImpl && getValue().getName().equals(((DeploymentNodeImpl)object).getValue().getName());
    }

    @Override
    public int hashCode() {
      return getValue().getName().hashCode();
    }

    @Nonnull
    @Override
    public ServerNode getServerNode() {
      return myParentNode;
    }

    @Override
    public boolean isUndeployActionEnabled() {
      DeploymentRuntime runtime = getValue().getRuntime();
      return runtime != null && runtime.isUndeploySupported();
    }

    @Override
    public void undeploy() {
      DeploymentRuntime runtime = getValue().getRuntime();
      if (runtime != null) {
        getConnection().undeploy(getValue(), runtime);
      }
    }

    @Override
    public boolean isEditConfigurationActionEnabled() {
      return getValue().getDeploymentTask() != null;
    }

    @Override
    public void editConfiguration() {
      DeploymentTask<?> task = getValue().getDeploymentTask();
      if (task != null) {
        RunnerAndConfigurationSettings settings = ((DeploymentTaskImpl)task).getExecutionEnvironment().getRunnerAndConfigurationSettings();
        if (settings != null) {
          RunConfigurationEditor.getInstance(doGetProject()).editConfiguration(doGetProject(), settings, "Edit Deployment Configuration");
        }
      }
    }

    public ServerConnection<?> getConnection() {
      return myConnection;
    }

    @jakarta.annotation.Nullable
    @Override
    public LoggingHandlerImpl getLoggingHandler() {
      DeploymentLogManagerImpl logManager = getLogManager();
      return logManager != null ? logManager.getMainLoggingHandler() : null;
    }

    @Nullable
    private DeploymentLogManagerImpl getLogManager() {
      return (DeploymentLogManagerImpl)myConnection.getLogManager(getValue());
    }

    @Nonnull
    @Override
    public String getLogId() {
      return "deployment:" + getValue().getName();
    }

    @RequiredReadAction
    @Nonnull
    @Override
    public Collection<? extends AbstractTreeNode> getChildren() {
      DeploymentLogManagerImpl logManager = (DeploymentLogManagerImpl)getConnection().getLogManager(getValue());
      if (logManager != null) {
        Map<String,LoggingHandlerImpl> handlers = logManager.getAdditionalLoggingHandlers();
        List<AbstractTreeNode> nodes = new ArrayList<AbstractTreeNode>();
        for (Map.Entry<String, LoggingHandlerImpl> entry : handlers.entrySet()) {
          nodes.add(new DeploymentLogNode(Pair.create(entry.getValue(), entry.getKey()), this));
        }
        return nodes;
      }
      return Collections.emptyList();
    }

    @Override
    protected void update(PresentationData presentation) {
      Deployment deployment = getValue();
      presentation.setIcon(getStatusIcon(deployment.getStatus()));
      presentation.setPresentableText(deployment.getName());
      presentation.setTooltip(deployment.getStatusText());
    }

    @Nullable
    private Image getStatusIcon(DeploymentStatus status) {
      switch (status) {
        case DEPLOYED: return AllIcons.RunConfigurations.TestPassed;
        case NOT_DEPLOYED: return AllIcons.RunConfigurations.TestIgnored;
        case DEPLOYING: return AllIcons.Process.Step_4;
        case UNDEPLOYING: return AllIcons.Process.Step_4;
      }
      return null;
    }
  }

  public class DeploymentLogNode extends AbstractTreeNode<Pair<LoggingHandlerImpl, String>> implements LogProvidingNode {
    @Nonnull
    private final DeploymentNodeImpl myDeploymentNode;

    public DeploymentLogNode(@Nonnull Pair<LoggingHandlerImpl, String> value, @Nonnull DeploymentNodeImpl deploymentNode) {
      super(doGetProject(), value);
      myDeploymentNode = deploymentNode;
    }

    @RequiredReadAction
    @Nonnull
    @Override
    public Collection<? extends AbstractTreeNode> getChildren() {
      return Collections.emptyList();
    }

    @Override
    protected void update(PresentationData presentation) {
      presentation.setIcon(AllIcons.Debugger.Console);
      presentation.setPresentableText(getLogName());
    }

    private String getLogName() {
      return getValue().getSecond();
    }

    @Nullable
    @Override
    public LoggingHandlerImpl getLoggingHandler() {
      return getValue().getFirst();
    }

    @Nonnull
    @Override
    public String getLogId() {
      return "deployment:" + myDeploymentNode.getValue().getName() + ";log:" + getLogName();
    }
  }
}
