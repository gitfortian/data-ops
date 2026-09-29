package io.yak.ops.business.workflow.backfill;

import io.yak.ops.business.workflow.execution.WorkflowLauncher;
import io.yak.ops.business.workflow.schedule.WorkflowScheduleParameterResolver;
import io.yak.ops.business.workflow.schedule.WorkflowScheduleQuery;
import io.yak.ops.business.workflow.schedule.trigger.WorkflowScheduleTriggerAdmission;
import io.yak.ops.business.workflow.schedule.trigger.WorkflowScheduleTriggerCoordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.business.workflow.domain.WorkflowTriggerContext;
import io.yak.ops.business.workflow.backfill.WorkflowBackfillPlanner.Occurrence;
import io.yak.ops.business.workflow.schedule.trigger.WorkflowScheduleTriggerAdmission.AdmissionResult;
import io.yak.ops.common.bean.po.workflow.WorkflowBackfillPO;
import io.yak.ops.common.bean.po.workflow.WorkflowScheduleTriggerPO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowBackfillTriggerCoordinatorTest {
  @Mock private WorkflowScheduleTriggerDao ledger;
  @Mock private WorkflowScheduleQuery schedules;
  @Mock private WorkflowLauncher launchService;
  @Mock private WorkflowScheduleTriggerAdmission admission;
  @Mock private WorkflowBackfillQuery backfills;
  @Mock private WorkflowScheduleParameterResolver parameters;

  private WorkflowScheduleTriggerCoordinator coordinator;
  private WorkflowBackfillTriggerAdapter backfillTriggerAdapter;

  @BeforeEach
  void setUp() {
    backfillTriggerAdapter =
        new WorkflowBackfillTriggerAdapter(backfills, parameters, launchService);
    coordinator = new WorkflowScheduleTriggerCoordinator(
        ledger, schedules, launchService, admission, backfillTriggerAdapter, parameters);
  }

  @Test
  void shouldCreateBatchScopedLedgerAndLaunchPinnedVersion() {
    WorkflowBackfillPO backfill = backfill();
    Occurrence occurrence = new Occurrence(
        LocalDate.of(2026, 8, 10),
        Instant.parse("2026-08-09T18:00:00Z"),
        "2026-08-10T02:00:00+08:00");
    WorkflowInstanceVO launched = org.mockito.Mockito.mock(WorkflowInstanceVO.class);
    when(admission.admitNew(any(WorkflowScheduleTriggerPO.class)))
        .thenAnswer(invocation -> {
          WorkflowScheduleTriggerPO trigger = invocation.getArgument(0);
          trigger.setStatus("LAUNCHING");
          return new AdmissionResult(trigger, true, false);
        });
    when(backfills.require("backfill-1")).thenReturn(backfill);
    when(parameters.forBackfill(eq(backfill), any(WorkflowTriggerContext.class)))
        .thenReturn(Map.of("businessDate", "2026-08-10"));
    when(launchService.runBackfillPublished(
        eq("workflow-1"),
        eq("workflow-version-5"),
        any(WorkflowTriggerContext.class),
        eq(Map.of("businessDate", "2026-08-10"))))
        .thenReturn(launched);
    when(launched.id()).thenReturn("execution-v5-20260810");
    when(admission.bindLaunch(any(WorkflowScheduleTriggerPO.class), eq("execution-v5-20260810")))
        .thenReturn(new AdmissionResult(null, false, false));

    var result = coordinator.submitBackfill(
        backfill.getId(), occurrence.businessDate(), occurrence.scheduleInstant());

    assertThat(result.businessExecutionId()).isEqualTo("execution-v5-20260810");
    ArgumentCaptor<WorkflowScheduleTriggerPO> triggerCaptor =
        ArgumentCaptor.forClass(WorkflowScheduleTriggerPO.class);
    verify(admission).admitNew(triggerCaptor.capture());
    WorkflowScheduleTriggerPO trigger = triggerCaptor.getValue();
    assertThat(trigger.getBackfillId()).isEqualTo("backfill-1");
    assertThat(trigger.getTriggerSource()).isEqualTo("BACKFILL");
    assertThat(trigger.getBusinessDate()).isEqualTo(LocalDate.of(2026, 8, 10));
    assertThat(trigger.getDedupeKey())
        .startsWith("schedule-1|BACKFILL|backfill-1|");
    verify(launchService).runBackfillPublished(
        eq("workflow-1"),
        eq("workflow-version-5"),
        any(WorkflowTriggerContext.class),
        eq(Map.of("businessDate", "2026-08-10")));
  }

  private WorkflowBackfillPO backfill() {
    WorkflowBackfillPO value = new WorkflowBackfillPO();
    value.setId("backfill-1");
    value.setWorkflowId("workflow-1");
    value.setWorkflowVersionId("workflow-version-5");
    value.setWorkflowVersionNo(5);
    value.setScheduleId("schedule-1");
    value.setScheduleName("每日订单同步");
    value.setStatus("RUNNING");
    value.setTimezone("Asia/Shanghai");
    value.setCronExpression("0 0 2 * * ?");
    value.setExecutionStrategy("SERIAL_WAIT");
    return value;
  }
}
