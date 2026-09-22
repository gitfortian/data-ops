package io.yak.ops.business.workflow.execution;

import io.yak.ops.business.workflow.runtime.WorkflowRuntime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class WorkflowExecutionReactivatorTest {
  @Mock private WorkflowRuntime runtime;
  @Mock private ObjectProvider<WorkflowExecutionReactivationGuard> guardProvider;
  @Mock private WorkflowExecutionReactivationGuard guard;

  @Test
  void shouldRouteScheduledRetryThroughReactivationGuard() {
    WorkflowExecutionReactivator service =
        new WorkflowExecutionReactivator(runtime, guardProvider);
    WorkflowInstanceVO expected = instance("execution-1", "RUNNING");
    when(guardProvider.getIfAvailable()).thenReturn(guard);
    when(guard.reactivateExecution(
        eq("execution-1"), eq("RETRY_FAILED_NODES"), any()))
        .thenReturn(expected);

    WorkflowInstanceVO result = service.retryFailedNodes("execution-1");

    assertThat(result).isSameAs(expected);
    verify(guard).reactivateExecution(
        eq("execution-1"), eq("RETRY_FAILED_NODES"), any());
  }

  @Test
  void shouldKeepDatabaseDisabledFallbackOnRuntime() {
    WorkflowExecutionReactivator service =
        new WorkflowExecutionReactivator(runtime, guardProvider);
    WorkflowInstanceVO expected = instance("execution-1", "RUNNING");
    when(guardProvider.getIfAvailable()).thenReturn(null);
    when(runtime.retryFailedNode("execution-1", "node-1")).thenReturn(expected);

    WorkflowInstanceVO result = service.retryFailedNode("execution-1", "node-1");

    assertThat(result).isSameAs(expected);
    verify(runtime).retryFailedNode("execution-1", "node-1");
  }

  private WorkflowInstanceVO instance(String id, String status) {
    Instant now = Instant.parse("2026-08-14T04:00:00Z");
    return new WorkflowInstanceVO(
        id,
        "workflow-version-1",
        null,
        "订单同步",
        status,
        "CONTINUE_INDEPENDENT_BRANCHES",
        now,
        now,
        null,
        0L,
        Map.of(),
        1,
        0,
        List.of(),
        "workflow-version-1",
        1,
        false);
  }
}
