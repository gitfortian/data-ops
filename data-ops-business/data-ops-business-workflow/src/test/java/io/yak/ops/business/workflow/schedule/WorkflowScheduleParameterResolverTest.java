package io.yak.ops.business.workflow.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.workflow.domain.WorkflowTriggerContext;
import io.yak.ops.business.workflow.repository.support.WorkflowJsonCodec;
import io.yak.ops.common.bean.po.workflow.WorkflowBackfillPO;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowScheduleParameterResolverTest {
  private final WorkflowScheduleParameterResolver resolver =
      new WorkflowScheduleParameterResolver(new WorkflowJsonCodec(new ObjectMapper()));

  @Test
  void shouldInjectLogicalScheduleParametersAndOverrideReservedNames() {
    WorkflowSchedulePO schedule = new WorkflowSchedulePO();
    schedule.setId("schedule-1");
    schedule.setTimezone("Asia/Shanghai");
    schedule.setCronExpression("0 0 2 * * ?");
    schedule.setInputJson("{\"tenant\":\"schedule\",\"businessDate\":\"wrong\"}");
    WorkflowTriggerContext context = WorkflowTriggerContext.scheduled(
        "trigger-1",
        "schedule-1",
        Instant.parse("2026-08-14T18:00:00Z"),
        "Asia/Shanghai");

    Map<String, Object> input = resolver.forSchedule(schedule, context);

    assertThat(input.get("tenant")).isEqualTo("schedule");
    assertThat(input.get("businessDate")).isEqualTo("2026-08-15");
    assertThat(input.get("scheduleTime")).isEqualTo("2026-08-15T02:00:00+08:00");
    assertThat(input.get("scheduleTimezone")).isEqualTo("Asia/Shanghai");
    assertThat(input.get("triggerType")).isEqualTo("SCHEDULE");
    assertThat(input.get("scheduleId")).isEqualTo("schedule-1");
    assertThat(input).containsKey(WorkflowScheduleParameterResolver.NAMESPACE);
    @SuppressWarnings("unchecked")
    Map<String, Object> system =
        (Map<String, Object>) input.get(WorkflowScheduleParameterResolver.NAMESPACE);
    assertThat(system.get("businessDate")).isEqualTo("2026-08-15");
    assertThat(system.get("plannedFireTime")).isEqualTo("2026-08-14T18:00:00Z");
  }

  @Test
  void shouldOverlayBackfillInputAndExposeBatchVersionMetadata() {
    WorkflowBackfillPO backfill = new WorkflowBackfillPO();
    backfill.setId("backfill-1");
    backfill.setScheduleId("schedule-1");
    backfill.setTimezone("Asia/Shanghai");
    backfill.setCronExpression("0 0 2 * * ?");
    backfill.setWorkflowVersionId("workflow-version-5");
    backfill.setWorkflowVersionNo(5);
    backfill.setScheduleInputJson("{\"tenant\":\"schedule\",\"region\":\"cn\"}");
    backfill.setInputJson("{\"tenant\":\"backfill\",\"limit\":200}");
    WorkflowTriggerContext context = WorkflowTriggerContext.backfill(
        "backfill-trigger-1",
        "schedule-1",
        "backfill-1",
        Instant.parse("2026-08-09T18:00:00Z"),
        "Asia/Shanghai");

    Map<String, Object> input = resolver.forBackfill(backfill, context);

    assertThat(input.get("tenant")).isEqualTo("backfill");
    assertThat(input.get("region")).isEqualTo("cn");
    assertThat(input.get("limit")).isEqualTo(200);
    assertThat(input.get("businessDate")).isEqualTo("2026-08-10");
    assertThat(input.get("triggerType")).isEqualTo("BACKFILL");
    assertThat(input.get("backfillId")).isEqualTo("backfill-1");
    assertThat(input.get("workflowVersionId")).isEqualTo("workflow-version-5");
    assertThat(input.get("workflowVersionNo")).isEqualTo(5);
  }

  @Test
  void shouldExposeBusinessDateRerunOperationLineageAsReservedParameters() {
    WorkflowBackfillPO rerun = new WorkflowBackfillPO();
    rerun.setId("rerun-batch-1");
    rerun.setScheduleId("schedule-1");
    rerun.setTimezone("Asia/Shanghai");
    rerun.setCronExpression("0 0 2 * * ?");
    rerun.setWorkflowVersionId("workflow-version-5");
    rerun.setWorkflowVersionNo(5);
    rerun.setOperationType("BUSINESS_DATE_RERUN");
    rerun.setSourceExecutionId("execution-source");
    rerun.setScheduleInputJson("{\"sourceExecutionId\":\"wrong\"}");
    rerun.setInputJson("{\"operationType\":\"wrong\"}");
    WorkflowTriggerContext context = WorkflowTriggerContext.rerun(
        "rerun-trigger-1",
        "schedule-1",
        "rerun-batch-1",
        Instant.parse("2026-08-09T18:00:00Z"),
        "Asia/Shanghai");

    Map<String, Object> input = resolver.forBackfill(rerun, context);

    assertThat(input.get("triggerType")).isEqualTo("RERUN");
    assertThat(input.get("operationType")).isEqualTo("BUSINESS_DATE_RERUN");
    assertThat(input.get("sourceExecutionId")).isEqualTo("execution-source");
    @SuppressWarnings("unchecked")
    Map<String, Object> system = (Map<String, Object>) input.get(WorkflowScheduleParameterResolver.NAMESPACE);
    assertThat(system).containsEntry("operationType", "BUSINESS_DATE_RERUN");
    assertThat(system).containsEntry("sourceExecutionId", "execution-source");
  }
}
