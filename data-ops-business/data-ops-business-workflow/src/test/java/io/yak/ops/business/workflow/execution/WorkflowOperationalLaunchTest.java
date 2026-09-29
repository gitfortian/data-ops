package io.yak.ops.business.workflow.execution;

import io.yak.ops.business.workflow.definition.WorkflowDefinitionManager;
import io.yak.ops.business.workflow.runtime.WorkflowRuntime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.workflow.domain.WorkflowTriggerContext;
import io.yak.ops.business.workflow.repository.WorkflowExecutionTriggerRecorder;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowOperationalLaunchTest {
  @Mock private WorkflowDefinitionManager definitions;
  @Mock private WorkflowRuntime runtime;
  @Mock private WorkflowExecutionTriggerRecorder recorder;
  @Mock private WorkflowPublishedVersionRunner publishedVersionRunner;
  @Mock private WorkflowInstanceVO launched;

  @Test
  void shouldRunPinnedHistoricalVersionWithoutFollowingCurrentDefinitionState() {
    WorkflowLauncher service = new WorkflowLauncher(
        definitions, runtime, recorder, publishedVersionRunner);
    WorkflowTriggerContext context = WorkflowTriggerContext.rerun(
        "rerun-trigger-1",
        "schedule-1",
        "rerun-batch-1",
        Instant.parse("2026-08-09T18:00:00Z"),
        "Asia/Shanghai");
    when(publishedVersionRunner.run("workflow-1", "workflow-version-5")).thenReturn(launched);
    when(launched.id()).thenReturn("execution-rerun");

    WorkflowInstanceVO result = service.runOperationalPublished(
        "workflow-1",
        "workflow-version-5",
        context,
        Map.of("businessDate", "2026-08-10"));

    assertThat(result).isSameAs(launched);
    verifyNoInteractions(definitions);
    verify(publishedVersionRunner).run("workflow-1", "workflow-version-5");
    verify(recorder).record("execution-rerun", context);
  }
}
