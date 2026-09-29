package io.yak.ops.business.workflow.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.workflow.backfill.WorkflowBackfillPlanner.Occurrence;
import io.yak.ops.business.workflow.backfill.WorkflowBackfillPlanner.Plan;
import io.yak.ops.business.workflow.dao.WorkflowBackfillDao;
import io.yak.ops.business.workflow.dao.WorkflowBackfillDao.ProjectBackfillRef;
import io.yak.ops.business.workflow.schedule.trigger.WorkflowScheduleTriggerCoordinator;
import io.yak.ops.common.bean.po.workflow.WorkflowBackfillPO;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowBackfillReconcilerTest {
  @Mock private WorkflowBackfillDao dao;
  @Mock private WorkflowBackfillPlanner planner;
  @Mock private WorkflowScheduleTriggerCoordinator coordinator;

  private RecordingProjectContextScope projectScope;
  private WorkflowBackfillReconciler reconciler;

  @BeforeEach
  void setUp() {
    projectScope = new RecordingProjectContextScope();
    reconciler = new WorkflowBackfillReconciler(dao, planner, coordinator, projectScope);
  }

  @Test
  void shouldRestoreProjectAndReplayAllOccurrencesForRunningBatch() {
    WorkflowBackfillPO backfill = new WorkflowBackfillPO();
    backfill.setId("backfill-1");
    backfill.setProjectId(7L);
    backfill.setStatus("RUNNING");
    backfill.setCronExpression("0 0 2 * * ?");
    backfill.setTimezone("Asia/Shanghai");
    backfill.setStartBusinessDate(LocalDate.of(2026, 8, 1));
    backfill.setEndBusinessDate(LocalDate.of(2026, 8, 2));
    Occurrence first = new Occurrence(
        LocalDate.of(2026, 8, 1),
        Instant.parse("2026-07-31T18:00:00Z"),
        "2026-08-01T02:00:00+08:00");
    Occurrence second = new Occurrence(
        LocalDate.of(2026, 8, 2),
        Instant.parse("2026-08-01T18:00:00Z"),
        "2026-08-02T02:00:00+08:00");
    when(dao.selectRunningForReconciliation())
        .thenReturn(List.of(new ProjectBackfillRef(7L, "backfill-1")));
    when(dao.select("backfill-1")).thenReturn(backfill);
    when(planner.plan(
        backfill.getCronExpression(),
        backfill.getTimezone(),
        backfill.getStartBusinessDate(),
        backfill.getEndBusinessDate()))
        .thenReturn(new Plan("Asia/Shanghai", "0 0 2 * * ?", List.of(first, second)));

    reconciler.reconcile();

    assertThat(projectScope.projectIds()).containsExactly(7L);
    verify(coordinator).submitBackfill(
        backfill.getId(), first.businessDate(), first.scheduleInstant());
    verify(coordinator).submitBackfill(
        backfill.getId(), second.businessDate(), second.scheduleInstant());
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
