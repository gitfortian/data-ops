package io.yak.ops.core.notification;

/**
 * Application entry point for routing user-facing business notifications.
 *
 * <p>Implementations must treat notification delivery as a secondary side effect: policy or sink
 * failures must not fail the originating business execution.</p>
 */
public interface NotificationRouter {

  void publish(NotificationIntent intent);
}
