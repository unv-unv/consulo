package consulo.remoteServer.impl.internal.agent;

import consulo.remoteServer.agent.RemoteAgentProxyFactory;
import jakarta.annotation.Nonnull;

import java.io.File;
import java.util.List;

/**
 * @author michael.golubev
 */
public class RemoteAgentThreadProxyFactory implements RemoteAgentProxyFactory {

  private final RemoteAgentThreadProxyCreator myCreator;
  private final RemoteAgentProxyFactory myDelegate;

  public RemoteAgentThreadProxyFactory(CallerClassLoaderProvider callerClassLoaderProvider, @Nonnull RemoteAgentProxyFactory delegate,
                                       @jakarta.annotation.Nullable ChildWrapperCreator preWrapperCreator) {
    myCreator = new RemoteAgentThreadProxyCreator(callerClassLoaderProvider, preWrapperCreator);
    myDelegate = delegate;
  }

  @Override
  public <T> T createProxy(List<File> libraries, Class<T> agentInterface, String agentClassName) throws Exception {
    T agentDelegate = myDelegate.createProxy(libraries, agentInterface, agentClassName);
    return myCreator.createProxy(agentInterface, agentDelegate);
  }
}
