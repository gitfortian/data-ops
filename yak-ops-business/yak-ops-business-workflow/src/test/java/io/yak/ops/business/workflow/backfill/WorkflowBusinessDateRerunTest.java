package io.yak.ops.business.workflow.backfill;

import io.yak.ops.business.workflow.definition.WorkflowDefinitionManager;
import io.yak.ops.business.workflow.schedule.WorkflowScheduleQuery;
import io.yak.ops.business.workflow.schedule.trigger.WorkflowScheduleTriggerAdmission;
import io.yak.ops.business.workflow.schedule.trigger.WorkflowScheduleTriggerCoordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.workflow.dao.WorkflowBackfillDao;
import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.business.workflow.repository.support.WorkflowJsonCodec;
import io.yak.ops.common.bean.dto.workflow.WorkflowBusinessDateRerunDTO;
import io.yak.ops.common.bean.po.workflow.WorkflowBackfillPO;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowBusinessDateRerunTest {
  @Mock private WorkflowScheduleQuery schedules;
  @Mock private WorkflowDefinitionManager definitions;
  @Mock private WorkflowBackfillDao dao;
  @Mock private WorkflowBackfillQuery query;
  @Mock private WorkflowScheduleTriggerDao triggers;
  @Mock private WorkflowScheduleTriggerAdmission admission;
  @Mock private WorkflowScheduleTriggerCoordinator coordinator;

  private WorkflowJsonCodec json;
  private WorkflowBackfillManager service;

  @BeforeEach
  void setUp() {
    json = new WorkflowJsonCodec(new ObjectMapper());
    service = new WorkflowBackfillManager(
        schedules,
        definitions,
        new WorkflowBackfillPlanner(),
        dao,
        query,
        triggers,
        admission,
        coordinator,
        json);
  }

  @Test
  void shouldPinSourceVersionAndReplaceOldSchedulingParameters() {
    WorkflowSchedulePO schedule = new WorkflowSchedulePO();
    schedule.setId("schedule-1");
    schedule.setName("每日订单同步");
    when(schedules.require("schedule-1")).thenReturn(schedule);
    when(triggers.selectWorkflowIdByExecution("execution-source")).thenReturn("workflow-1");
    when(dao.insert(any(WorkflowBackfillPO.class))).thenReturn(1);

    WorkflowInstanceVO source = new WorkflowInstanceVO(
        "execution-source",
        "workflow-version-5",
        null,
        "订单同步",
        "FAILED",
        "CONTINUE_INDEPENDENT_BRANCHES",
        Instant.parse("2026-08-09T18:00:00Z"),
        Instant.parse("2026-08-09T18:00:01Z"),
        Instant.parse("2026-08-09T18:10:00Z"),
        0L,
        Map.ofEntries(
            Map.entry("tenant", "hospital-a"),
            Map.entry("businessDate", "2026-08-09"),
            Map.entry("scheduleTime", "2026-08-09T02:00:00+08:00"),
            Map.entry("scheduleTimezone", "Asia/Shanghai"),
            Map.entry("plannedFireTime", "2026-08-08T18:00:00Z"),
            Map.entry("triggerType", "SCHEDULE"),
            Map.entry("triggerId", "old-trigger"),
            Map.entry("scheduleId", "schedule-1"),
            Map.entry("cronExpression", "0 0 2 * * ?"),
            Map.entry("__schedule", Map.of("businessDate", "2026-08-09"))),
        1,
        0,
        List.of(),
        "workflow-version-5",
        5,
        false);

    service.createBusinessDateRerun(
        "execution-source",
        source,
        new WorkflowBusinessDateRerunDTO(
            LocalDate.of(2026, 8, 10),
            "SERIAL_WAIT",
            Map.of("force", true)));

    ArgumentCaptor<WorkflowBackfillPO> captor = ArgumentCaptor.forClass(WorkflowBackfillPO.class);
    verify(dao).insert(captor.capture());
    WorkflowBackfillPO batch = captor.getValue();
    assertThat(batch.getOperationType()).isEqualTo("BUSINESS_DATE_RERUN");
    assertThat(batch.getSourceExecutionId()).isEqualTo("execution-source");
    assertThat(batch.getWorkflowId()).isEqualTo("workflow-1");
    assertThat(batch.getWorkflowVersionId()).isEqualTo("workflow-version-5");
    assertThat(batch.getWorkflowVersionNo()).isEqualTo(5);
    assertThat(batch.getStartBusinessDate()).isEqualTo(LocalDate.of(2026, 8, 10));
    assertThat(batch.getEndBusinessDate()).isEqualTo(LocalDate.of(2026, 8, 10));
    assertThat(batch.getTotalCount()).isEqualTo(1);
    assertThat(json.readMap(batch.getScheduleInputJson()))
        .containsEntry("tenant", "hospital-a")
        .doesNotContainKeys("businessDate", "scheduleTime", "triggerId", "__schedule");
    assertThat(json.readMap(batch.getInputJson())).containsEntry("force", true);
    verify(coordinator).submitBackfill(
        eq(batch.getId()), eq(LocalDate.of(2026, 8, 10)), any(Instant.class));
  }
}
