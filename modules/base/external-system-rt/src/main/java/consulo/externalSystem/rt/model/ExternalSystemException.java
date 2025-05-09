package consulo.externalSystem.rt.model;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * We talk to external system API from the dedicated process, i.e. all external system classes are loaded at that process
 * in order to avoid memory problems at ide process. That means that if external system api throws an exception,
 * it can't be correctly read at the ide process (NoClassDefFoundError and ClassNotFoundException).
 * <p/>
 * This class allows to extract textual description of the target problem and deliver it for further processing without risking to 
 * get the problems mentioned above. I.e. it doesn't require anything specific can be safely delivered to ide process then.
 *
 * @author Denis Zhdanov
 * @since 10/21/11 11:42 AM
 */
public class ExternalSystemException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String myOriginalReason;

  private final String[] myQuickFixes;

  public ExternalSystemException() {
    this(null, null);
  }

  public ExternalSystemException(String message) {
    this(message, null);
  }

  public ExternalSystemException(Throwable cause) {
    this("", cause);
  }

  public ExternalSystemException(String message, Throwable cause, String... quickFixes) {
    super(extractMessage(message, cause));
    myQuickFixes = quickFixes;
    if (cause == null) {
      myOriginalReason = "";
      return;
    }

    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    try {
      cause.printStackTrace(printWriter);
    } finally {
      printWriter.close();
    }
    myOriginalReason = stringWriter.toString();
  }

  /**
   * @return    textual description of the wrapped exception (if any); empty string otherwise
   */
  public String getOriginalReason() {
    return myOriginalReason;
  }

  public String[] getQuickFixes() {
    return myQuickFixes;
  }

  @Override
  public void printStackTrace(PrintWriter s) {
    super.printStackTrace(s);
    s.println(myOriginalReason);
  }

  @Override
  public void printStackTrace(PrintStream s) {
    super.printStackTrace(s);
    s.println(myOriginalReason);
  }

  private static String extractMessage(String message, Throwable cause) {
    StringBuilder buffer = new StringBuilder();
    if (message != null) {
      buffer.append(message);
    }

    boolean first = true;
    for (Throwable t = cause; t != null; t = t.getCause()) {
      final String m = t.getLocalizedMessage();
      if (m == null) {
        continue;
      }
      if (first) {
        first = false;
      }
      else if (buffer.length() > 0) {
        buffer.append("\n");
      }
      buffer.append(m);
    }

    return buffer.toString();
  }
}
