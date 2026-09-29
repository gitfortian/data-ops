package io.yak.ops.core.notification;

/**
 * Resolves the effective delivery policy for notification intents it owns.
 *
 * <p>Resolvers are ordered from lowest {@link #order()} value to highest. The router uses the first
 * resolver whose {@link #supports(NotificationIntent)} method returns {@code true}. A product module
 * can therefore override the fallback policy for its own source type without changing the router.</p>
 */
public interface NotificationPolicyResolver {

  boolean supports(NotificationIntent intent);

  NotificationPolicy resolve(NotificationIntent intent);

  default int order() {
    return 0;
  }
}
