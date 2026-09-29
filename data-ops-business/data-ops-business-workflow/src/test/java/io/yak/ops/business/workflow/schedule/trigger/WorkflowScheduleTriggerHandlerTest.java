package io.yak.ops.business.workflow.schedule.trigger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.framework.schedule.api.ScheduleExecutionContext;
import io.yak.framework.schedule.api.ScheduleExecutionResult;
import io.yak.framework.schedule.api.ScheduleKey;
import io.yak.ops.business.workflow.definition.WorkflowDefinitionManager;
import io.yak.ops.business.workflow.schedule.WorkflowScheduleLifecycle;
import io.yak.ops.business.workflow.schedule.WorkflowScheduleQuery;
import io.yak.ops.business.workflow.schedule.WorkflowScheduleRuntimeState;
import io.yak.ops.business.workflow.schedule.engine.WorkflowScheduleEngineBridge;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO;
import io.yak.ops.common.schedule.YakScheduleNamespaces;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowScheduleTriggerHandlerTest {
  @Mock private WorkflowScheduleQuery schedules;
  @Mock private WorkflowDefinitionManager definitions;
  @Mock private WorkflowScheduleTriggerCoordinator coordinator;
  @Mock private WorkflowScheduleLifecycle lifecycle;
  @Mock private WorkflowScheduleEngineBridge engine;
  @Mock private WorkflowScheduleRuntimeState runtimeState;

  private RecordingProjectContextScope projectScope;
  private WorkflowScheduleTriggerHandler handler;

  @BeforeEach
  void setUp() {
    projectScope = new RecordingProjectContextScope();
    handler = new WorkflowScheduleTriggerHandler(
        schedules, definitions, coordinator, lifecycle, engine, runtimeState, projectScope);
  }

  @Test
  void shouldRestoreProjectAndRouteQuartzFireIntoDurableTriggerCoordinator() {
    Instant planned = Instant.parse("2026-08-14T02:00:00Z");
    Instant actual = planned.plusSeconds(1);
    WorkflowSchedulePO schedule = schedule();
    WorkflowDefinitionVO workflow = org.mockito.Mockito.mock(WorkflowDefinitionVO.class);
    when(workflow.status()).thenReturn("ONLINE");
    when(workflow.activeVersionId()).thenReturn("workflow-version-1");
    when(schedules.require("schedule-1")).thenReturn(schedule);
    when(definitions.get("workflow-1")).thenReturn(workflow);
    when(coordinator.submit(schedule, "trigger-1", planned, actual, "CRON"))
        .thenReturn(ScheduleExecutionResult.accepted("execution-1"));
    when(engine.snapshot("schedule-1")).thenReturn(Optional.empty());

    ScheduleExecutionResult result = handler.execute(context(planned, 7L));

    assertThat(result.accepted()).isTrue();
    assertThat(result.businessExecutionId()).isEqualTo("execution-1");
    assertThat(projectScope.projectIds()).containsExactly(7L);
    verify(coordinator).submit(schedule, "trigger-1", planned, actual, "CRON");
    verify(runtimeState).recordFire("schedule-1", actual, null);
  }

  @Test
  void shouldIgnoreFireBeforeScheduleStartTime() {
    Instant planned = Instant.parse("2026-08-14T02:00:00Z");
    Instant actual = planned.plusSeconds(1);
    WorkflowSchedulePO schedule = schedule();
    schedule.setStartTime(Instant.parse("2026-08-15T00:00:00Z"));
    when(schedules.require("schedule-1")).thenReturn(schedule);
    when(engine.snapshot("schedule-1")).thenReturn(Optional.empty());

    ScheduleExecutionResult result = handler.execute(context(planned, 7L));

    assertThat(result.accepted()).isTrue();
    assertThat(result.businessExecutionId()).isNull();
    assertThat(projectScope.projectIds()).containsExactly(7L);
    verify(coordinator, never()).submit(any(), any(), any(), any(), any());
    verify(runtimeState).recordFire("schedule-1", actual, null);
  }

  @Test
  void shouldRejectScheduleWhoseDurableProjectDoesNotMatchPayload() {
    Instant planned = Instant.parse("2026-08-14T02:00:00Z");
    WorkflowSchedulePO schedule = schedule();
    schedule.setProjectId(9L);
    when(schedules.require("schedule-1")).thenReturn(schedule);

    assertThatThrownBy(() -> handler.execute(context(planned, 7L)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Project identity");
  }

  private WorkflowSchedulePO schedule() {
    WorkflowSchedulePO value = new WorkflowSchedulePO();
    value.setId("schedule-1");
    value.setProjectId(7L);
    value.setWorkflowId("workflow-1");
    value.setStatus("ONLINE");
    return value;
  }

  private ScheduleExecutionContext context(Instant planned, long projectId) {
    return new ScheduleExecutionContext(
        "trigger-1",
        new ScheduleKey(YakScheduleNamespaces.WORKFLOW, "schedule-1"),
        "quartz",
        WorkflowScheduleEngineBridge.HANDLER,
        Map.of(
            "scheduleId", "schedule-1",
            "workflowId", "workflow-1",
            "projectId", projectId),
        planned,
        planned.plusSeconds(1),
        false,
        1);
  }

  private static final class RecordingProjectContextScope implements ProjectContextScope {
    private final List<Long> projectIds = new ArrayList<>();

    @Override
    public <T> T call(ProjectContext context, Supplier<T> action) {
      projectIds.add(context.projectId());
      return action.get();
    }

    List<Long> projectIds() {
      return List.copyOf(projectIds);
    }
  }
}
