package io.yak.ops.business.workflow.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.common.bean.dto.workflow.WorkflowDefinitionUpdateDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO.NodeVO;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowDefinitionAuditCoordinatorTest {

  @Test
  void unchangedDraftDoesNotCreateAuditNoise() {
    WorkflowDefinitionManager definitions = mock(WorkflowDefinitionManager.class);
    RecordingAuditService audit = new RecordingAuditService();
    WorkflowDefinitionAuditCoordinator coordinator =
        new WorkflowDefinitionAuditCoordinator(definitions, audit);
    WorkflowDefinitionVO before = workflow("DRAFT", null, null, false, Map.of(), Map.of(), List.of());
    WorkflowDefinitionUpdateDTO request =
        new WorkflowDefinitionUpdateDTO(
            before.name(),
            before.description(),
            List.of(),
            List.of(),
            before.input(),
            before.editorMeta(),
            before.workflowTimeoutSeconds(),
            before.failureStrategy());
    when(definitions.get("wf-1")).thenReturn(before);
    when(definitions.update("wf-1", request)).thenReturn(before);

    WorkflowDefinitionVO result = coordinator.update("wf-1", request);

    assertThat(result).isSameAs(before);
    assertThat(audit.starts).isZero();
    verify(definitions).update("wf-1", request);
  }

  @Test
  void draftUpdateStoresOnlySafeChangeFlags() {
    WorkflowDefinitionManager definitions = mock(WorkflowDefinitionManager.class);
    RecordingAuditService audit = new RecordingAuditService();
    WorkflowDefinitionAuditCoordinator coordinator =
        new WorkflowDefinitionAuditCoordinator(definitions, audit);
    WorkflowDefinitionVO before =
        workflow(
            "DRAFT",
            null,
            null,
            false,
            Map.of("token", "secret-before"),
            Map.of("layout", "private-before"),
            List.of());
    WorkflowDefinitionVO after =
        workflow(
            "DRAFT",
            null,
            null,
            false,
            Map.of("token", "secret-after"),
            Map.of("layout", "private-after"),
            List.of());
    WorkflowDefinitionUpdateDTO request =
        new WorkflowDefinitionUpdateDTO(
            before.name(),
            "updated description",
            List.of(),
            List.of(),
            after.input(),
            after.editorMeta(),
            before.workflowTimeoutSeconds(),
            before.failureStrategy());
    when(definitions.get("wf-1")).thenReturn(before);
    when(definitions.update("wf-1", request)).thenReturn(after);

    coordinator.update("wf-1", request);

    assertThat(audit.starts).isEqualTo(1);
    assertThat(audit.request.operationType()).isEqualTo("WORKFLOW_UPDATE");
    assertThat(audit.handle.events).hasSize(1);
    Map<String, ?> payload = audit.handle.events.get(0).payload();
    assertThat(payload.get("descriptionChanged")).isEqualTo(true);
    assertThat(payload.get("inputChanged")).isEqualTo(true);
    assertThat(payload.get("editorMetaChanged")).isEqualTo(true);
    assertThat(payload.toString())
        .doesNotContain("secret-before", "secret-after", "private-before", "private-after");
    assertThat(audit.handle.successSummary).isEqualTo("Workflow updated");
  }

  @Test
  void publishAndEnableShareOneOperationWithBusinessChangeTypes() {
    WorkflowDefinitionManager definitions = mock(WorkflowDefinitionManager.class);
    RecordingAuditService audit = new RecordingAuditService();
    WorkflowDefinitionAuditCoordinator coordinator =
        new WorkflowDefinitionAuditCoordinator(definitions, audit);
    WorkflowDefinitionVO before = workflow("DRAFT", null, null, true, Map.of(), Map.of(), List.of());
    WorkflowDefinitionVO after = workflow("ONLINE", "wv-1", 1, false, Map.of(), Map.of(), List.of());
    when(definitions.get("wf-1")).thenReturn(before);
    when(definitions.online("wf-1")).thenReturn(after);

    coordinator.online("wf-1");

    assertThat(audit.request.operationType()).isEqualTo("WORKFLOW_PUBLISH");
    assertThat(audit.handle.events).hasSize(2);
    assertThat(audit.handle.events)
        .extracting(event -> String.valueOf(event.payload().get("changeType")))
        .containsExactly("VERSION_PUBLISHED", "RESOURCE_ENABLED");
    assertThat(audit.handle.successSummary).isEqualTo("Workflow published");
  }

  @Test
  void taskRevisionNoopDoesNotCreateAuditOperation() {
    WorkflowDefinitionManager definitions = mock(WorkflowDefinitionManager.class);
    RecordingAuditService audit = new RecordingAuditService();
    WorkflowDefinitionAuditCoordinator coordinator =
        new WorkflowDefinitionAuditCoordinator(definitions, audit);
    WorkflowDefinitionVO before =
        workflow("DRAFT", null, null, true, Map.of(), Map.of(), List.of(node(false, 2)));
    when(definitions.get("wf-1")).thenReturn(before);
    when(definitions.upgradeTaskRevision("wf-1", "node-1")).thenReturn(before);

    coordinator.upgradeTaskRevision("wf-1", "node-1");

    assertThat(audit.starts).isZero();
    verify(definitions).upgradeTaskRevision("wf-1", "node-1");
  }

  @Test
  void editorPauseAuditsLatestExecutionInsteadOfOnlyWorkflowDefinition() {
    WorkflowDefinitionManager definitions = mock(WorkflowDefinitionManager.class);
    RecordingAuditService audit = new RecordingAuditService();
    WorkflowDefinitionAuditCoordinator coordinator =
        new WorkflowDefinitionAuditCoordinator(definitions, audit);
    WorkflowDefinitionVO before = workflowWithExecution("RUNNING");
    WorkflowDefinitionVO after = workflowWithExecution("PAUSED");
    when(definitions.get("wf-1")).thenReturn(before);
    when(definitions.pause("wf-1")).thenReturn(after);

    WorkflowDefinitionVO result = coordinator.pauseExecution("wf-1");

    assertThat(result).isSameAs(after);
    assertThat(audit.request.operationType()).isEqualTo("WORKFLOW_EXECUTION_PAUSE");
    assertThat(audit.request.resourceType()).isEqualTo("WORKFLOW_EXECUTION");
    assertThat(audit.request.resourceId()).isEqualTo("execution-1");
    assertThat(audit.handle.events).singleElement().satisfies(event -> {
      assertThat(event.payload().get("changeType")).isEqualTo("EXECUTION_PAUSED");
      assertThat(event.payload().get("executionId")).isEqualTo("execution-1");
      assertThat(event.payload().get("status")).isEqualTo("PAUSED");
    });
  }

  @Test
  void deleteFailureClosesAuditAsFailedWithoutChangingBusinessException() {
    WorkflowDefinitionManager definitions = mock(WorkflowDefinitionManager.class);
    RecordingAuditService audit = new RecordingAuditService();
    WorkflowDefinitionAuditCoordinator coordinator =
        new WorkflowDefinitionAuditCoordinator(definitions, audit);
    WorkflowDefinitionVO before = workflow("OFFLINE", "wv-2", 2, false, Map.of(), Map.of(), List.of());
    when(definitions.get("wf-1")).thenReturn(before);
    IllegalStateException failure = new IllegalStateException("still referenced");
    org.mockito.Mockito.doThrow(failure).when(definitions).delete("wf-1");

    assertThatThrownBy(() -> coordinator.delete("wf-1")).isSameAs(failure);

    assertThat(audit.request.operationType()).isEqualTo("WORKFLOW_DELETE");
    assertThat(audit.handle.failureReason).isEqualTo("WORKFLOW_DELETE_FAILED");
    assertThat(audit.handle.failureCause).isSameAs(failure);
  }

  private static WorkflowDefinitionVO workflow(
      String status,
      String activeVersionId,
      Integer activeVersionNo,
      boolean draftChanged,
      Map<String, Object> input,
      Map<String, Object> editorMeta,
      List<NodeVO> nodes) {
    return workflow(
        status,
        activeVersionId,
        activeVersionNo,
        draftChanged,
        input,
        editorMeta,
        nodes,
        null,
        null);
  }

  private static WorkflowDefinitionVO workflowWithExecution(String executionStatus) {
    return workflow(
        "ONLINE",
        "wv-1",
        1,
        false,
        Map.of(),
        Map.of(),
        List.of(),
        "execution-1",
        executionStatus);
  }

  private static WorkflowDefinitionVO workflow(
      String status,
      String activeVersionId,
      Integer activeVersionNo,
      boolean draftChanged,
      Map<String, Object> input,
      Map<String, Object> editorMeta,
      List<NodeVO> nodes,
      String latestExecutionId,
      String latestExecutionStatus) {
    Instant now = Instant.parse("2026-09-02T00:00:00Z");
    return new WorkflowDefinitionVO(
        "wf-1",
        "Orders workflow",
        "description",
        status,
        nodes.size(),
        0,
        nodes,
        List.of(),
        input,
        editorMeta,
        300L,
        "FAIL_FAST",
        activeVersionId,
        activeVersionNo,
        activeVersionNo == null ? 0 : activeVersionNo,
        draftChanged,
        latestExecutionId,
        latestExecutionStatus,
        now,
        now);
  }

  private static NodeVO node(boolean updateAvailable, int revisionNo) {
    return new NodeVO(
        "node-1",
        "task-asset:10",
        10L,
        100L + revisionNo,
        revisionNo,
        "Sync task",
        "OFFLINE_SYNC",
        "ONLINE",
        updateAvailable ? 200L : 100L + revisionNo,
        updateAvailable ? revisionNo + 1 : revisionNo,
        updateAvailable,
        0D,
        0D,
        1,
        0L,
        0L,
        0L,
        Map.of(),
        "ALL_SUCCESS",
        "FAIL_WORKFLOW");
  }

  private static final class RecordingAuditService implements BusinessAuditService {
    int starts;
    AuditOperationRequest request;
    RecordingHandle handle;

    @Override
    public AuditOperationHandle start(AuditOperationRequest request) {
      starts++;
      this.request = request;
      this.handle = new RecordingHandle();
      return handle;
    }
  }

  private static final class RecordingHandle implements AuditOperationHandle {
    final List<Event> events = new ArrayList<>();
    String resourceId;
    String resourceName;
    String successSummary;
    String failureReason;
    Throwable failureCause;

    @Override
    public String operationId() {
      return "AUD-test";
    }

    @Override
    public void resource(String resourceId, String resourceName) {
      this.resourceId = resourceId;
      this.resourceName = resourceName;
    }

    @Override
    public void event(AuditEventType type, String message, Map<String, ?> payload) {
      events.add(new Event(type, message, Map.copyOf(payload)));
    }

    @Override
    public void success(String summary) {
      successSummary = summary;
    }

    @Override
    public void failure(String reasonCode, Throwable cause) {
      failureReason = reasonCode;
      failureCause = cause;
    }
  }

  private record Event(AuditEventType type, String message, Map<String, ?> payload) {}
}
