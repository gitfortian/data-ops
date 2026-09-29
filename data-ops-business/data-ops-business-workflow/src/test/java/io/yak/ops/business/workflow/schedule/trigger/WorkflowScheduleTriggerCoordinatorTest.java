package io.yak.ops.business.workflow.schedule.trigger;

import io.yak.ops.business.workflow.execution.WorkflowLauncher;
import io.yak.ops.business.workflow.schedule.WorkflowScheduleQuery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.business.workflow.domain.WorkflowTriggerContext;
import io.yak.ops.business.workflow.schedule.trigger.WorkflowScheduleTriggerAdmission.AdmissionResult;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.po.workflow.WorkflowScheduleTriggerPO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowScheduleTriggerCoordinatorTest {
  @Mock private WorkflowScheduleTriggerDao ledger;
  @Mock private WorkflowScheduleQuery schedules;
  @Mock private WorkflowLauncher launchService;
  @Mock private WorkflowScheduleTriggerAdmission admission;

  private WorkflowScheduleTriggerCoordinator coordinator;

  @BeforeEach
  void setUp() {
    coordinator = new WorkflowScheduleTriggerCoordinator(ledger, schedules, launchService, admission);
  }

  @Test
  void shouldNotLaunchDuplicatePlannedFire() {
    WorkflowSchedulePO schedule = schedule();
    WorkflowScheduleTriggerPO existing = trigger("RUNNING");
    existing.setWorkflowExecutionId("execution-existing");
    when(admission.admitNew(eq(schedule), any()))
        .thenReturn(new AdmissionResult(existing, false, true));

    var result = coordinator.submit(
        schedule,
        "quartz-trigger-2",
        Instant.parse("2026-08-14T02:00:00Z"),
        Instant.parse("2026-08-14T02:00:01Z"),
        "CRON");

    assertThat(result.businessExecutionId()).isEqualTo("execution-existing");
    assertThat(result.message()).contains("幂等拦截");
    verify(launchService, never()).runScheduledPublished(any(), any(), any());
  }

  @Test
  void shouldLaunchOnlyAfterAdmissionReservation() {
    WorkflowSchedulePO schedule = schedule();
    WorkflowScheduleTriggerPO reserved = trigger("LAUNCHING");
    WorkflowDefinitionVO launched = org.mockito.Mockito.mock(WorkflowDefinitionVO.class);
    when(admission.admitNew(eq(schedule), any()))
        .thenReturn(new AdmissionResult(reserved, true, false));
    when(schedules.require("schedule-1")).thenReturn(schedule);
    when(launchService.runScheduledPublished(
        eq("workflow-1"), any(WorkflowTriggerContext.class), eq(Map.of())))
        .thenReturn(launched);
    when(launched.latestExecutionId()).thenReturn("execution-1");
    when(admission.bindLaunch(reserved, "execution-1"))
        .thenReturn(new AdmissionResult(reserved, false, false));

    var result = coordinator.submit(
        schedule,
        "quartz-trigger-1",
        Instant.parse("2026-08-14T02:00:00Z"),
        Instant.parse("2026-08-14T02:00:01Z"),
        "CRON");

    assertThat(result.businessExecutionId()).isEqualTo("execution-1");
    verify(launchService).runScheduledPublished(
        eq("workflow-1"), any(WorkflowTriggerContext.class), eq(Map.of()));
    verify(admission).bindLaunch(reserved, "execution-1");
  }

  @Test
  void shouldReserveLedgerBeforeInPlaceReactivationAndSettleAfterRuntimeReturns() {
    WorkflowInstanceVO expected = org.mockito.Mockito.mock(WorkflowInstanceVO.class);
    when(admission.reserveReactivation("execution-1", "RETRY_FAILED_NODES")).thenReturn(true);
    when(admission.finishReactivation("execution-1"))
        .thenReturn(new AdmissionResult(null, false, false));

    WorkflowInstanceVO result = coordinator.reactivateExecution(
        "execution-1",
        "RETRY_FAILED_NODES",
        () -> expected);

    assertThat(result).isSameAs(expected);
    verify(admission).reserveReactivation("execution-1", "RETRY_FAILED_NODES");
    verify(admission).finishReactivation("execution-1");
  }

  @Test
  void shouldReleaseReactivationReservationWhenRuntimeRejectsRetry() {
    RuntimeException failure = new IllegalStateException("no retryable nodes");
    when(admission.reserveReactivation("execution-1", "RETRY_FAILED_NODES")).thenReturn(true);
    when(admission.failReactivation("execution-1", failure))
        .thenReturn(new AdmissionResult(null, false, false));

    assertThatThrownBy(() -> coordinator.reactivateExecution(
        "execution-1",
        "RETRY_FAILED_NODES",
        () -> { throw failure; }))
        .isSameAs(failure);

    verify(admission).failReactivation("execution-1", failure);
    verify(admission, never()).finishReactivation("execution-1");
  }

  private WorkflowSchedulePO schedule() {
    WorkflowSchedulePO value = new WorkflowSchedulePO();
    value.setId("schedule-1");
    value.setWorkflowId("workflow-1");
    value.setStatus("ONLINE");
    value.setTimezone("Asia/Shanghai");
    value.setExecutionStrategy("SERIAL_WAIT");
    value.setMisfireStrategy("FIRE_ONCE");
    return value;
  }

  private WorkflowScheduleTriggerPO trigger(String status) {
    WorkflowScheduleTriggerPO value = new WorkflowScheduleTriggerPO();
    value.setId("ledger-1");
    value.setScheduleId("schedule-1");
    value.setWorkflowId("workflow-1");
    value.setTriggerId("quartz-trigger-1");
    value.setDedupeKey("schedule-1|SCHEDULE|1786672800000");
    value.setPlannedFireTime(Instant.parse("2026-08-14T02:00:00Z"));
    value.setActualFireTime(Instant.parse("2026-08-14T02:00:01Z"));
    value.setExecutionStrategy("SERIAL_WAIT");
    value.setMisfireStrategy("FIRE_ONCE");
    value.setStatus(status);
    return value;
  }
}
