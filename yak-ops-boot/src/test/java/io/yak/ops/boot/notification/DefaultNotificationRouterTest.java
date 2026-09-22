package io.yak.ops.boot.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.core.notification.NotificationIntent;
import io.yak.ops.core.notification.NotificationPolicy;
import io.yak.ops.core.notification.NotificationPolicyResolver;
import io.yak.ops.core.notification.NotificationSink;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class DefaultNotificationRouterTest {

  @AfterEach
  void clearTransactionSynchronization() {
    TransactionSynchronizationManager.setActualTransactionActive(false);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void firstSupportingResolverByOrderOverridesFallbackPolicy() {
    NotificationPolicyResolver fallback = resolver(true, Integer.MAX_VALUE,
        NotificationPolicy.projectOwnersInApp());
    NotificationPolicy alertPolicy = new NotificationPolicy(
        true,
        NotificationPolicy.RecipientStrategy.PROJECT_OWNER,
        List.of(),
        Set.of(NotificationPolicy.Destination.ALERT),
        List.of(9L));
    NotificationPolicyResolver specific = resolver(true, 10, alertPolicy);
    NotificationSink inApp = sink(NotificationPolicy.Destination.IN_APP);
    NotificationSink alert = sink(NotificationPolicy.Destination.ALERT);

    DefaultNotificationRouter router =
        new DefaultNotificationRouter(List.of(fallback, specific), List.of(inApp, alert));
    router.publish(intent());

    verify(alert).deliver(intent(), alertPolicy);
    verify(inApp, never()).deliver(any(), any());
  }

  @Test
  void defersRoutingUntilBusinessTransactionCommits() {
    NotificationPolicy policy = NotificationPolicy.projectOwnersInApp();
    NotificationPolicyResolver resolver = resolver(true, 0, policy);
    NotificationSink inApp = sink(NotificationPolicy.Destination.IN_APP);

    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);

    DefaultNotificationRouter router =
        new DefaultNotificationRouter(List.of(resolver), List.of(inApp));
    router.publish(intent());

    verify(inApp, never()).deliver(any(), any());
    List<TransactionSynchronization> synchronizations =
        TransactionSynchronizationManager.getSynchronizations();
    assertThat(synchronizations).hasSize(1);

    synchronizations.get(0).afterCommit();

    verify(inApp).deliver(intent(), policy);
  }

  @Test
  void sinkFailureDoesNotEscapeOrBlockAnotherSink() {
    NotificationPolicy policy = NotificationPolicy.projectOwnersInApp();
    NotificationPolicyResolver resolver = resolver(true, 0, policy);
    NotificationSink failing = sink(NotificationPolicy.Destination.IN_APP);
    NotificationSink healthy = sink(NotificationPolicy.Destination.IN_APP);
    doThrow(new IllegalStateException("store unavailable"))
        .when(failing).deliver(any(), any());

    DefaultNotificationRouter router =
        new DefaultNotificationRouter(List.of(resolver), List.of(failing, healthy));

    assertThatCode(() -> router.publish(intent())).doesNotThrowAnyException();
    verify(healthy).deliver(intent(), policy);
  }

  @Test
  void policyResolverFailureIsFailClosed() {
    NotificationPolicyResolver failing = mock(NotificationPolicyResolver.class);
    when(failing.order()).thenReturn(0);
    when(failing.supports(any())).thenReturn(true);
    when(failing.resolve(any())).thenThrow(new IllegalStateException("policy unavailable"));
    NotificationPolicyResolver fallback = resolver(true, Integer.MAX_VALUE,
        NotificationPolicy.projectOwnersInApp());
    NotificationSink inApp = sink(NotificationPolicy.Destination.IN_APP);

    DefaultNotificationRouter router =
        new DefaultNotificationRouter(List.of(fallback, failing), List.of(inApp));

    assertThatCode(() -> router.publish(intent())).doesNotThrowAnyException();
    verify(inApp, never()).deliver(any(), any());
  }

  private NotificationPolicyResolver resolver(
      boolean supports,
      int order,
      NotificationPolicy policy) {
    NotificationPolicyResolver resolver = mock(NotificationPolicyResolver.class);
    when(resolver.order()).thenReturn(order);
    when(resolver.supports(any())).thenReturn(supports);
    when(resolver.resolve(any())).thenReturn(policy);
    return resolver;
  }

  private NotificationSink sink(NotificationPolicy.Destination destination) {
    NotificationSink sink = mock(NotificationSink.class);
    when(sink.destination()).thenReturn(destination);
    return sink;
  }

  private NotificationIntent intent() {
    return new NotificationIntent(
        7L,
        NotificationIntent.Type.TASK,
        NotificationIntent.Level.ERROR,
        "离线同步任务执行失败",
        "订单同步",
        "engine down",
        "OFFLINE_SYNC_EXECUTION",
        "99",
        "/sync/batch-link-up/10/detail");
  }
}
