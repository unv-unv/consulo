package consulo.remoteServer.impl.internal.agent;

import consulo.application.ApplicationManager;
import consulo.logging.Logger;
import consulo.util.lang.ref.Ref;
import consulo.application.util.Semaphore;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author michael.golubev
 */
public class SequentialTaskExecutor {

  private static final Logger LOG = Logger.getInstance(SequentialTaskExecutor.class);

  private BlockingQueue<Runnable> myTasks;

  public SequentialTaskExecutor() {
    myTasks = new LinkedBlockingQueue<Runnable>();

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {

      @Override
      public void run() {
        try {
          while (true) {
            Runnable task = myTasks.take();
            task.run();
            if (task instanceof FinalTask) {
              break;
            }
          }
        }
        catch (InterruptedException e) {
          LOG.debug(e);
        }
      }
    });
  }

  public void queueTask(Runnable task) {
    myTasks.offer(task);
  }

  public <V> V queueAndWaitTask(final Callable<V> task) throws Throwable {
    final Ref<V> resultRef = new Ref<V>();
    final Ref<Throwable> throwableRef = new Ref<Throwable>();

    final Semaphore taskSemaphore = new Semaphore();
    taskSemaphore.down();

    queueTask(new Runnable() {

      @Override
      public void run() {
        try {
          resultRef.set(task.call());
        }
        catch (Throwable e) {
          throwableRef.set(e);
          LOG.error(e);
        }
        finally {
          taskSemaphore.up();
        }
      }
    });

    taskSemaphore.waitFor();

    if (!throwableRef.isNull()) {
      throw throwableRef.get();
    }

    return resultRef.get();
  }
}
