package io.yak.ops.business.workflow.execution;

import io.yak.ops.business.workflow.runtime.WorkflowRuntime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.yak.framework.workflow.engine.definition.EdgeDefinition;
import io.yak.framework.workflow.engine.definition.WorkflowDefinition;
import io.yak.framework.workflow.engine.definition.WorkflowFailureStrategy;
import io.yak.framework.workflow.engine.spi.WorkflowDefinitionRepository;
import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.business.workflow.repository.WorkflowRuntimeRepository;
import io.yak.ops.common.bean.dto.workflow.WorkflowBatchRetryDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class WorkflowExecutionManagerTest {
  @Mock private WorkflowRuntime runtime;
  @Mock private WorkflowExecutionReactivator reactivation;
  @Mock private ObjectProvider<WorkflowRuntimeRepository> runtimePersistence;
  @Mock private ObjectProvider<WorkflowDefinitionRepository> definitionProvider;
  @Mock private ObjectProvider<WorkflowScheduleTriggerDao> triggerProvider;
  @Mock private ObjectProvider<WorkflowBusinessDateRerunGateway> rerunProvider;
  @Mock private WorkflowDefinitionRepository definitionRepository;
  @Mock private WorkflowScheduleTriggerDao triggerDao;
  @Mock private WorkflowBusinessDateRerunGateway rerunGateway;

  private WorkflowExecutionManager service;

  @BeforeEach
  void setUp() {
    service = new WorkflowExecutionManager(
        runtime,
        reactivation,
        runtimePersistence,
        definitionProvider,
        triggerProvider,
        rerunProvider);
  }

  @Test
  void shouldDescribeImmutableRuntimeDagAndSchedulingLineage() {
    WorkflowInstanceVO instance = instance("execution-1", "FAILED");
    when(runtime.getInstance("execution-1")).thenReturn(instance);
    when(runtimePersistence.getIfAvailable()).thenReturn(null);
    when(triggerProvider.getIfAvailable()).thenReturn(triggerDao);
    when(triggerDao.selectWorkflowIdByExecution("execution-1")).thenReturn("workflow-1");
    when(definitionProvider.getIfAvailable()).thenReturn(definitionRepository);
    when(rerunProvider.getIfAvailable()).thenReturn(rerunGateway);
    WorkflowDefinition definition = new WorkflowDefinition(
        "workflow-version-5",
        "订单同步",
        WorkflowFailureStrategy.CONTINUE_INDEPENDENT_BRANCHES,
        List.of(),
        List.of(new EdgeDefinition("extract", "load")));
    when(definitionRepository.findById("workflow-version-5")).thenReturn(Optional.of(definition));

    var result = service.describe("execution-1");

    assertThat(result.workflowId()).isEqualTo("workflow-1");
    assertThat(result.triggerType()).isEqualTo("SCHEDULE");
    assertThat(result.scheduleId()).isEqualTo("schedule-1");
    assertThat(result.businessDate()).hasToString("2026-08-10");
    assertThat(result.businessDateRerunSupported()).isTrue();
    assertThat(result.edges()).containsExactly(
        new io.yak.ops.common.bean.vo.workflow.WorkflowInstanceOperationsVO.EdgeVO("extract", "load"));
  }

  @Test
  void shouldIsolateFailuresWhenBatchRetryingInstances() {
    WorkflowInstanceVO accepted = instance("execution-a", "RUNNING");
    when(reactivation.retryFailedNodes("execution-a")).thenReturn(accepted);
    when(reactivation.retryFailedNodes("execution-b"))
        .thenThrow(new IllegalStateException("serial slot already occupied"));

    var result = service.batchRetryFailed(new WorkflowBatchRetryDTO(
        List.of("execution-a", "execution-b", "execution-a")));

    assertThat(result.requestedCount()).isEqualTo(2);
    assertThat(result.acceptedCount()).isEqualTo(1);
    assertThat(result.failedCount()).isEqualTo(1);
    assertThat(result.items()).hasSize(2);
    assertThat(result.items().get(0).accepted()).isTrue();
    assertThat(result.items().get(1).message()).contains("serial slot already occupied");
  }

  private WorkflowInstanceVO instance(String id, String status) {
    return new WorkflowInstanceVO(
        id,
        "workflow-version-5",
        null,
        "订单同步",
        status,
        "CONTINUE_INDEPENDENT_BRANCHES",
        Instant.parse("2026-08-10T01:00:00Z"),
        Instant.parse("2026-08-10T01:00:01Z"),
        status.equals("RUNNING") ? null : Instant.parse("2026-08-10T01:10:00Z"),
        0L,
        Map.of(
            "businessDate", "2026-08-10",
            "scheduleTime", "2026-08-10T02:00:00+08:00",
            "scheduleTimezone", "Asia/Shanghai",
            "plannedFireTime", "2026-08-09T18:00:00Z",
            "triggerType", "SCHEDULE",
            "triggerId", "trigger-1",
            "scheduleId", "schedule-1",
            "cronExpression", "0 0 2 * * ?"),
        2,
        1,
        List.of(),
        "workflow-version-5",
        5,
        false);
  }
}
