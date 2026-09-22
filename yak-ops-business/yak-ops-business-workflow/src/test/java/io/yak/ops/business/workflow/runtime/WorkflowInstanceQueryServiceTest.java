package io.yak.ops.business.workflow.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditQueryService;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import io.yak.ops.core.project.CurrentProject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowInstanceQueryServiceTest {

  @Test
  void enrichesInstancesWithTheFirstExecuteActorName() {
    WorkflowRuntime runtime = mock(WorkflowRuntime.class);
    AuditQueryService auditQueryService = mock(AuditQueryService.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    WorkflowInstanceVO instance = instance("execution-1");

    when(runtime.listInstances()).thenReturn(List.of(instance));
    when(currentProject.requireProjectId()).thenReturn(42L);
    when(auditQueryService.firstActorNames(
            "WORKFLOW_EXECUTE",
            "WORKFLOW_EXECUTION",
            List.of("execution-1"),
            42L))
        .thenReturn(Map.of("execution-1", "alice"));

    WorkflowInstanceQueryService service =
        new WorkflowInstanceQueryService(runtime, auditQueryService, currentProject);

    List<WorkflowInstanceVO> result = service.listInstances();

    assertEquals(1, result.size());
    assertEquals("alice", result.get(0).creatorName());
    verify(auditQueryService)
        .firstActorNames(
            "WORKFLOW_EXECUTE",
            "WORKFLOW_EXECUTION",
            List.of("execution-1"),
            42L);
  }

  @Test
  void keepsCreatorEmptyWhenNoLaunchAuditExists() {
    WorkflowRuntime runtime = mock(WorkflowRuntime.class);
    AuditQueryService auditQueryService = mock(AuditQueryService.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    WorkflowInstanceVO instance = instance("execution-legacy");

    when(runtime.listInstances()).thenReturn(List.of(instance));
    when(currentProject.requireProjectId()).thenReturn(7L);
    when(auditQueryService.firstActorNames(
            "WORKFLOW_EXECUTE",
            "WORKFLOW_EXECUTION",
            List.of("execution-legacy"),
            7L))
        .thenReturn(Map.of());

    WorkflowInstanceQueryService service =
        new WorkflowInstanceQueryService(runtime, auditQueryService, currentProject);

    WorkflowInstanceVO result = service.listInstances().get(0);

    assertNull(result.creatorName());
  }

  private WorkflowInstanceVO instance(String id) {
    return new WorkflowInstanceVO(
        id,
        "definition-1",
        null,
        "Example workflow",
        "SUCCESS",
        "FAIL_FAST",
        Instant.parse("2026-09-03T00:00:00Z"),
        Instant.parse("2026-09-03T00:00:00Z"),
        Instant.parse("2026-09-03T00:00:01Z"),
        3600L,
        Map.of(),
        1,
        0,
        List.of(),
        "version-1",
        1,
        false);
  }
}
