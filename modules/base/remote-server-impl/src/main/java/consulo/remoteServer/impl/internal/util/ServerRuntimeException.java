package consulo.remoteServer.impl.internal.util;

/**
 * @author michael.golubev
 */
public class ServerRuntimeException extends Exception {

    public ServerRuntimeException(Throwable cause) {
        super(cause);
    }

    public ServerRuntimeException(String message) {
        super(message);
    }
}
