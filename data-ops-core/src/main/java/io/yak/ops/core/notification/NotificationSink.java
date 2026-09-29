package io.yak.ops.core.notification;

/** Outbound delivery port for one notification destination. */
public interface NotificationSink {

  NotificationPolicy.Destination destination();

  /** Deliver one intent according to the already-resolved policy. */
  void deliver(NotificationIntent intent, NotificationPolicy policy);
}
