package io.yak.ops.business.workflow.execution;

import io.yak.ops.business.workflow.runtime.WorkflowRuntime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.business.workflow.domain.WorkflowRunSpec;
import io.yak.ops.business.workflow.repository.WorkflowDefinitionRepository;
import io.yak.ops.business.workflow.repository.WorkflowDefinitionRepository.VersionRecord;
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
class WorkflowPublishedVersionRunnerTest {
  @Mock private WorkflowRuntime runtimeService;
  @Mock private WorkflowDefinitionRepository persistence;
  @Mock private ObjectProvider<WorkflowDefinitionRepository> provider;

  @Test
  void shouldRunRequestedImmutableVersionInsteadOfCurrentActiveVersion() {
    WorkflowRunSpec spec = new WorkflowRunSpec(
        "历史版本 V5", List.of(), List.of(), Map.of("base", "v5"), 0L,
        "CONTINUE_INDEPENDENT_BRANCHES");
    Map<String, TaskVersionSnapshot> tasks = Map.of();
    VersionRecord version = new VersionRecord(
        "workflow-version-5",
        "workflow-1",
        5,
        10L,
        spec,
        Map.of(),
        tasks,
        Instant.parse("2026-08-01T00:00:00Z"));
    WorkflowInstanceVO prepared = org.mockito.Mockito.mock(WorkflowInstanceVO.class);
    WorkflowInstanceVO activated = org.mockito.Mockito.mock(WorkflowInstanceVO.class);
    when(provider.getIfAvailable()).thenReturn(persistence);
    when(persistence.loadVersions("workflow-1")).thenReturn(List.of(version));
    when(runtimeService.run(spec, tasks, "workflow-version-5", 5, false)).thenReturn(prepared);
    when(prepared.id()).thenReturn("execution-v5");
    when(runtimeService.activate("execution-v5")).thenReturn(activated);

    WorkflowPublishedVersionRunner runner = new WorkflowPublishedVersionRunner(runtimeService, provider);
    WorkflowInstanceVO result = runner.run("workflow-1", "workflow-version-5");

    assertThat(result).isSameAs(activated);
    verify(persistence).loadVersions("workflow-1");
    verify(runtimeService).run(eq(spec), eq(tasks), eq("workflow-version-5"), eq(5), eq(false));
    verify(runtimeService).activate("execution-v5");
  }
}
