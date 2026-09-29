package io.yak.ops.boot.notification;

import io.yak.ops.core.notification.NotificationIntent;
import io.yak.ops.core.notification.NotificationPolicy;
import io.yak.ops.core.notification.NotificationPolicyResolver;
import io.yak.ops.core.notification.NotificationRouter;
import io.yak.ops.core.notification.NotificationSink;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Default notification router coordinating policy resolution and destination delivery. */
@Component
public class DefaultNotificationRouter implements NotificationRouter {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultNotificationRouter.class);

  private final List<NotificationPolicyResolver> policyResolvers;
  private final List<NotificationSink> sinks;

  public DefaultNotificationRouter(
      List<NotificationPolicyResolver> policyResolvers,
      List<NotificationSink> sinks) {
    this.policyResolvers = policyResolvers.stream()
        .sorted(Comparator.comparingInt(NotificationPolicyResolver::order))
        .toList();
    this.sinks = List.copyOf(sinks);
  }

  @Override
  public void publish(NotificationIntent intent) {
    Objects.requireNonNull(intent, "intent");
    try {
      Runnable delivery = () -> routeNow(intent);
      if (TransactionSynchronizationManager.isSynchronizationActive()
          && TransactionSynchronizationManager.isActualTransactionActive()) {
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
              @Override
              public void afterCommit() {
                delivery.run();
              }
            });
        return;
      }
      delivery.run();
    } catch (RuntimeException exception) {
      logFailure("notification routing setup failed", intent, exception);
    }
  }

  private void routeNow(NotificationIntent intent) {
    NotificationPolicy policy;
    try {
      policy = resolvePolicy(intent);
    } catch (RuntimeException exception) {
      // Policy failures are fail-closed. Falling back could violate an explicit business opt-out.
      logFailure("notification policy resolution failed", intent, exception);
      return;
    }

    if (policy == null || !policy.enabled()) return;

    for (NotificationSink sink : sinks) {
      if (!policy.routesTo(sink.destination())) continue;
      try {
        sink.deliver(intent, policy);
      } catch (RuntimeException exception) {
        // One destination must never block the originating business flow or another destination.
        logFailure("notification sink delivery failed: " + sink.destination(), intent, exception);
      }
    }
  }

  private NotificationPolicy resolvePolicy(NotificationIntent intent) {
    for (NotificationPolicyResolver resolver : policyResolvers) {
      if (resolver.supports(intent)) {
        return Objects.requireNonNull(
            resolver.resolve(intent),
            "notification policy resolver returned null: " + resolver.getClass().getName());
      }
    }
    LOGGER.debug(
        "Notification skipped because no policy resolver matched: sourceType={}, sourceId={}",
        intent.sourceType(),
        intent.sourceId());
    return null;
  }

  private void logFailure(
      String message,
      NotificationIntent intent,
      RuntimeException exception) {
    LOGGER.error(
        "{}: projectId={}, sourceType={}, sourceId={}",
        message,
        intent.projectId(),
        intent.sourceType(),
        intent.sourceId(),
        exception);
  }
}
