package io.yak.ops.business.workflow.backfill;

import io.yak.ops.business.workflow.execution.WorkflowLauncher;
import io.yak.ops.business.workflow.schedule.WorkflowScheduleParameterResolver;
import io.yak.ops.business.workflow.schedule.WorkflowScheduleQuery;
import io.yak.ops.business.workflow.schedule.trigger.WorkflowScheduleTriggerAdmission;
import io.yak.ops.business.workflow.schedule.trigger.WorkflowScheduleTriggerCoordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
class WorkflowBusinessDateRerunCoordinatorTest {
  @Mock private WorkflowScheduleTriggerDao ledger;
  @Mock private WorkflowScheduleQuery schedules;
  @Mock private WorkflowLauncher launchService;
  @Mock private WorkflowScheduleTriggerAdmission admission;
  @Mock private WorkflowBackfillQuery backfills;
  @Mock private WorkflowScheduleParameterResolver parameters;
  @Mock private WorkflowInstanceVO launched;

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
  void shouldUseOperationalPinnedLaunchAndRerunTriggerSource() {
    WorkflowBackfillPO batch = new WorkflowBackfillPO();
    batch.setId("batch-rerun-1");
    batch.setWorkflowId("workflow-1");
    batch.setWorkflowVersionId("workflow-version-5");
    batch.setWorkflowVersionNo(5);
    batch.setScheduleId("schedule-1");
    batch.setTimezone("Asia/Shanghai");
    batch.setCronExpression("0 0 2 * * ?");
    batch.setExecutionStrategy("SERIAL_WAIT");
    batch.setOperationType("BUSINESS_DATE_RERUN");
    batch.setSourceExecutionId("execution-source");

    Occurrence occurrence = new Occurrence(
        LocalDate.of(2026, 8, 10),
        Instant.parse("2026-08-09T18:00:00Z"),
        "2026-08-10T02:00:00+08:00");

    when(admission.admitNew(any(WorkflowScheduleTriggerPO.class)))
        .thenAnswer(invocation -> new AdmissionResult(invocation.getArgument(0), true, false));
    when(backfills.require("batch-rerun-1")).thenReturn(batch);
    when(parameters.forBackfill(eq(batch), any(WorkflowTriggerContext.class)))
        .thenReturn(Map.of("businessDate", "2026-08-10"));
    when(launchService.runOperationalPublished(
        eq("workflow-1"),
        eq("workflow-version-5"),
        any(WorkflowTriggerContext.class),
        any()))
        .thenReturn(launched);
    when(launched.id()).thenReturn("execution-rerun");
    when(admission.bindLaunch(any(WorkflowScheduleTriggerPO.class), eq("execution-rerun")))
        .thenAnswer(invocation -> new AdmissionResult(invocation.getArgument(0), false, false));

    var result = coordinator.submitBackfill(
        batch.getId(), occurrence.businessDate(), occurrence.scheduleInstant());

    ArgumentCaptor<WorkflowScheduleTriggerPO> trigger =
        ArgumentCaptor.forClass(WorkflowScheduleTriggerPO.class);
    verify(admission).admitNew(trigger.capture());
    assertThat(trigger.getValue().getTriggerSource()).isEqualTo("BUSINESS_DATE_RERUN");
    assertThat(trigger.getValue().getBusinessDate()).isEqualTo(LocalDate.of(2026, 8, 10));
    assertThat(result.businessExecutionId()).isEqualTo("execution-rerun");
    verify(launchService).runOperationalPublished(
        eq("workflow-1"), eq("workflow-version-5"), any(WorkflowTriggerContext.class), any());
    verify(launchService, never()).runBackfillPublished(any(), any(), any(), any());
  }
}
