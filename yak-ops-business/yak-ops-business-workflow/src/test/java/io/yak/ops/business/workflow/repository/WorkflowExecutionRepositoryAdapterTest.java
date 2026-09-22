package io.yak.ops.business.workflow.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.framework.workflow.engine.execution.WorkflowExecution;
import io.yak.framework.workflow.engine.execution.WorkflowExecutionSnapshot;
import io.yak.framework.workflow.engine.state.WorkflowExecutionStatus;
import io.yak.ops.business.workflow.dao.WorkflowExecutionDao;
import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.business.workflow.domain.WorkflowScheduleLaunchBindingScope;
import io.yak.ops.business.workflow.repository.support.WorkflowJsonCodec;
import io.yak.ops.common.bean.po.workflow.WorkflowExecutionPO;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class WorkflowExecutionRepositoryAdapterTest {

  @Mock private WorkflowExecutionDao executionDao;
  @Mock private WorkflowScheduleTriggerDao scheduleTriggerDao;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private WorkflowExecution execution;

  @Test
  void shouldBindRunningExecutionOnFirstDurableSave() {
    WorkflowExecutionRepositoryAdapter repository = repository();
    WorkflowExecutionSnapshot snapshot = runningSnapshot("execution-1");
    when(execution.snapshot()).thenReturn(snapshot);
    when(executionDao.selectExecution("execution-1")).thenReturn(null);
    when(scheduleTriggerDao.bindPreparedExecution(
        "trigger-1", "execution-1", "RUNNING")).thenReturn(1);

    try (var ignored = WorkflowScheduleLaunchBindingScope.open("trigger-1")) {
      repository.save(execution);
    }

    InOrder order = inOrder(executionDao, scheduleTriggerDao);
    order.verify(executionDao).upsertExecution(any(WorkflowExecutionPO.class));
    order.verify(scheduleTriggerDao).bindPreparedExecution(
        "trigger-1", "execution-1", "RUNNING");
  }

  @Test
  void shouldNotRebindTriggerAfterExecutionAlreadyExists() {
    WorkflowExecutionRepositoryAdapter repository = repository();
    when(execution.snapshot()).thenReturn(runningSnapshot("execution-2"));
    when(executionDao.selectExecution("execution-2")).thenReturn(new WorkflowExecutionPO());

    try (var ignored = WorkflowScheduleLaunchBindingScope.open("trigger-2")) {
      repository.save(execution);
    }

    verify(scheduleTriggerDao, never()).bindPreparedExecution(any(), any(), any());
  }

  private WorkflowExecutionRepositoryAdapter repository() {
    return new WorkflowExecutionRepositoryAdapter(
        executionDao,
        scheduleTriggerDao,
        new WorkflowJsonCodec(new ObjectMapper()),
        eventPublisher);
  }

  private WorkflowExecutionSnapshot runningSnapshot(String executionId) {
    Instant now = Instant.parse("2026-08-14T02:00:00Z");
    return new WorkflowExecutionSnapshot(
        executionId,
        "workflow-version-1",
        null,
        Map.of(),
        List.of(),
        now,
        WorkflowExecutionStatus.RUNNING,
        false,
        now,
        null,
        Duration.ZERO,
        now,
        null);
  }
}
