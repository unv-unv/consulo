// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.component.messagebus;

import consulo.annotation.component.TopicAPI;
import consulo.disposer.Disposable;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Core of IntelliJ IDEA messaging infrastructure. Basic functions:
 * <pre>
 * <ul>
 *   <li>allows to {@link #syncPublisher(Class) push messages};</li>
 *   <li>
 *     allows to {@link #connect() create connections} for further {@link MessageBusConnection#subscribe(Class, Object) subscriptions};
 *   </li>
 * </ul>
 * </pre>
 * <p/>
 * Use {@code 'consulo.ide.impl.idea.openapi.components.ComponentManager#getMessageBus()'} to obtain one.
 * <p/>
 * Please see <a href="docs">https://www.jetbrains.org/intellij/sdk/docs/reference_guide/messaging_infrastructure.html</a>.
 */
public interface MessageBus {

  /**
   * Messages buses can be organised into hierarchies. That allows facilities {@link TopicAPI#direction()} () broadcasting}.
   * <p/>
   * Current method exposes parent bus (if any is defined).
   *
   * @return parent bus (if defined)
   */
  @Nullable
  MessageBus getParent();

  /**
   * Allows to create new connection that is not bound to any {@link Disposable}.
   *
   * @return newly created connection
   */
  @Nonnull
  MessageBusConnection connect();

  /**
   * Allows to create new connection that is bound to the given {@link Disposable}. That means that returned connection
   * will be automatically {@link MessageBusConnection#dispose() released} if given {@link Disposable disposable parent} is collected.
   *
   * @param parentDisposable target parent disposable to which life cycle newly created connection shall be bound
   * @return newly created connection which life cycle is bound to the given disposable parent
   */
  @Nonnull
  MessageBusConnection connect(@Nonnull Disposable parentDisposable);

  /**
   * Allows to retrieve an interface for publishing messages to the target topic.
   * <p/>
   * Basically, the whole processing looks as follows:
   * <pre>
   * <ol>
   *   <li>
   *     Messaging clients create new {@link MessageBusConnection connections} within the target message bus and
   *     {@link MessageBusConnection#subscribe(Class, Object) subscribe} to the target {@link Class topics};
   *   </li>
   *   <li>
   *     Every time somebody wants to send a message for particular topic, he or she calls current method and receives object
   *     that conforms to the {@link Class business interface} of the target topic. Every method call on that
   *     object is dispatched by the messaging infrastructure to the subscribers.
   *     {@link TopicAPI#direction()} () broadcasting} is performed if necessary as well;
   *   </li>
   * </ol>
   * </pre>
   * <p/>
   * It's also very important to understand message processing strategy in case of <b>nested dispatches</b>.
   * Consider the following situation:
   * <pre>
   * <ol>
   *   <li>
   *     <code>Subscriber<sub>1</sub></code> and <code>subscriber<sub>2</sub></code> are registered for the same topic within
   *     the same message bus;
   *   </li>
   *   <li><code>Message<sub>1</sub></code> is sent to that topic within the same message bus;</li>
   *   <li>Queued messages delivery starts;</li>
   *   <li>Queued messages delivery ends as there are no messages queued but not dispatched;</li>
   *   <li><code>Message<sub>1</sub></code> is queued for delivery to both subscribers;</li>
   *   <li>Queued messages delivery starts;</li>
   *   <li><code>Message<sub>1</sub></code> is being delivered to the <code>subscriber<sub>1</sub></code>;</li>
   *   <li><code>Subscriber<sub>1</sub></code> sends <code>message<sub>2</sub></code> to the same topic within the same bus;</li>
   *   <li>Queued messages delivery starts;</li>
   *   <li>
   *     <b>Important:</b> <code>subscriber<sub>2</sub></code> is being notified about all queued but not delivered messages,
   *     i.e. its callback is invoked for the message<sub>1</sub>;
   *   </li>
   *   <li>Queued messages delivery ends because all subscribers have been notified on the <code>message<sub>1</sub></code>;</li>
   *   <li><code>Message<sub>2</sub></code> is queued for delivery to both subscribers;</li>
   *   <li>Queued messages delivery starts;</li>
   *   <li><code>Subscriber<sub>1</sub></code> is notified on <code>message<sub>2</sub></code></li>
   *   <li><code>Subscriber<sub>2</sub></code> is notified on <code>message<sub>2</sub></code></li>
   * </ol>
   * </pre>
   * <p/>
   * <b>Thread-safety.</b>
   * All subscribers are notified sequentially from the calling thread.
   * <p/>
   * <b>Memory management.</b>
   * Returned objects are very light-weight and stateless, so, they are cached by the message bus in {@code 'per-topic'} manner.
   * That means that caller of this method is not obliged to keep returned reference along with the reference to the message for
   * further publishing. It's enough to keep reference to the message bus only and publish
   * like {@code 'messageBus.syncPublisher(targetTopic).targetMethod()'}.
   *
   * @param topicClass target topic
   * @return publisher for target topic
   */
  @Nonnull
  <L> L syncPublisher(@Nonnull Class<L> topicClass);

  /**
   * Returns true if this bus is disposed.
   */
  boolean isDisposed();

  /**
   * @return true when events in the given topic are being dispatched in the current thread,
   * and not all listeners have received the events yet.
   */
  boolean hasUndeliveredEvents(@Nonnull Class<?> topic);
}