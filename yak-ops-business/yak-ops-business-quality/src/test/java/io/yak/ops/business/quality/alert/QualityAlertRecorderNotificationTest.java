package io.yak.ops.business.quality.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.quality.domain.execution.QualityExecutionPlan;
import io.yak.ops.business.quality.domain.execution.QualityExecutionPlan.MonitorSnapshot;
import io.yak.ops.business.quality.repository.QualityAlertRepository;
import io.yak.ops.common.enums.quality.QualityEnums.AlertLevel;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.NotifyChannel;
import io.yak.ops.common.enums.quality.QualityEnums.RuleFailureAction;
import io.yak.ops.core.notification.NotificationIntent;
import io.yak.ops.core.notification.NotificationRouter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class QualityAlertRecorderNotificationTest {

  @Test
  void lightweightConstructorKeepsNotificationRouterOptional() {
    QualityAlertRepository repository = mock(QualityAlertRepository.class);

    QualityAlertRecorder recorder = new QualityAlertRecorder(repository);
    recorder.recordIfNecessary(
        plan(false, NotifyChannel.EMAIL, AlertLevel.WARNING),
        CheckResult.ERROR,
        0,
        0,
        1);

    verify(repository, never()).insertAlertEvent(any());
  }

  @Test
  @SuppressWarnings("unchecked")
  void messageChannelPublishesQualityWarningIntent() {
    QualityAlertRepository repository = mock(QualityAlertRepository.class);
    NotificationRouter router = mock(NotificationRouter.class);
    ObjectProvider<NotificationRouter> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(router);

    QualityAlertRecorder recorder = new QualityAlertRecorder(repository, provider);
    recorder.recordIfNecessary(
        plan(true, NotifyChannel.MESSAGE, AlertLevel.WARNING),
        CheckResult.NOT_PASSED,
        8,
        2,
        0);

    verify(repository).insertAlertEvent(any());
    ArgumentCaptor<NotificationIntent> captor =
        ArgumentCaptor.forClass(NotificationIntent.class);
    verify(router).publish(captor.capture());
    NotificationIntent intent = captor.getValue();
    assertThat(intent.projectId()).isEqualTo(7L);
    assertThat(intent.type()).isEqualTo(NotificationIntent.Type.QUALITY);
    assertThat(intent.level()).isEqualTo(NotificationIntent.Level.WARNING);
    assertThat(intent.title()).isEqualTo("数据质量检查发现问题");
    assertThat(intent.sourceType()).isEqualTo("DATA_QUALITY_EXECUTION");
    assertThat(intent.sourceId()).isEqualTo("Q-20260831-001");
    assertThat(intent.actionPath())
        .isEqualTo("/data-quality/execution/Q-20260831-001");
  }

  @Test
  @SuppressWarnings("unchecked")
  void configuredNonMessageProblemKeepsAlertEvidenceWithoutNotificationRouting() {
    QualityAlertRepository repository = mock(QualityAlertRepository.class);
    NotificationRouter router = mock(NotificationRouter.class);
    ObjectProvider<NotificationRouter> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(router);

    QualityAlertRecorder recorder = new QualityAlertRecorder(repository, provider);
    recorder.recordIfNecessary(
        plan(true, NotifyChannel.EMAIL, AlertLevel.WARNING),
        CheckResult.NOT_PASSED,
        8,
        2,
        0);

    verify(repository).insertAlertEvent(any());
    verify(router, never()).publish(any());
  }

  @Test
  @SuppressWarnings("unchecked")
  void executionErrorAlwaysSurfacesEvenWhenConfiguredAlertsAreDisabled() {
    QualityAlertRepository repository = mock(QualityAlertRepository.class);
    NotificationRouter router = mock(NotificationRouter.class);
    ObjectProvider<NotificationRouter> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(router);

    QualityAlertRecorder recorder = new QualityAlertRecorder(repository, provider);
    recorder.recordIfNecessary(
        plan(false, NotifyChannel.EMAIL, AlertLevel.WARNING),
        CheckResult.ERROR,
        0,
        0,
        1);

    verify(repository, never()).insertAlertEvent(any());
    ArgumentCaptor<NotificationIntent> captor =
        ArgumentCaptor.forClass(NotificationIntent.class);
    verify(router).publish(captor.capture());
    assertThat(captor.getValue().level()).isEqualTo(NotificationIntent.Level.ERROR);
    assertThat(captor.getValue().title()).isEqualTo("数据质量执行异常");
  }

  @Test
  @SuppressWarnings("unchecked")
  void criticalQualityProblemUsesErrorSeverity() {
    QualityAlertRepository repository = mock(QualityAlertRepository.class);
    NotificationRouter router = mock(NotificationRouter.class);
    ObjectProvider<NotificationRouter> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(router);

    QualityAlertRecorder recorder = new QualityAlertRecorder(repository, provider);
    recorder.recordIfNecessary(
        plan(true, NotifyChannel.MESSAGE, AlertLevel.CRITICAL),
        CheckResult.NOT_PASSED,
        5,
        5,
        0);

    ArgumentCaptor<NotificationIntent> captor =
        ArgumentCaptor.forClass(NotificationIntent.class);
    verify(router).publish(captor.capture());
    assertThat(captor.getValue().level()).isEqualTo(NotificationIntent.Level.ERROR);
    assertThat(captor.getValue().title()).isEqualTo("数据质量检查发现严重问题");
  }

  private QualityExecutionPlan plan(
      boolean notifyEnabled,
      NotifyChannel channel,
      AlertLevel alertLevel) {
    return new QualityExecutionPlan(
        7L,
        101L,
        "Q-20260831-001",
        new MonitorSnapshot(
            21L,
            "订单完整性检查",
            31L,
            "warehouse",
            "dwd",
            "public",
            "dwd_order",
            null,
            "alice"),
        List.of(),
        RuleFailureAction.CONTINUE,
        notifyEnabled,
        channel,
        null,
        alertLevel);
  }
}
