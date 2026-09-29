package io.yak.ops.business.workflow.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.audit.AuditCarrier;
import io.yak.ops.business.audit.AuditContext;
import io.yak.ops.business.audit.AuditEventRequest;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.workflow.domain.WorkflowExecutionTerminalEvent;
import io.yak.ops.business.workflow.domain.WorkflowTriggerContext;
import io.yak.ops.business.workflow.repository.WorkflowAuditCorrelationRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WorkflowExecutionAuditBridgeTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final RecordingAuditService auditService = new RecordingAuditService();
  private final InMemoryCorrelationRepository correlation = new InMemoryCorrelationRepository();
  private final WorkflowExecutionAuditBridge bridge =
      new WorkflowExecutionAuditBridge(auditService, correlation, objectMapper);

  @Test
  void launchFreezesCarrierAndCompletesFromDurableTerminalTruth() throws Exception {
    WorkflowExecutionAuditBridge.LaunchAudit launch =
        bridge.beginLaunch("PUBLISHED", "workflow-1", WorkflowTriggerContext.manual());

    String activeOperation = bridge.call(
        launch,
        () -> AuditContext.current().orElseThrow().operationId());
    assertThat(activeOperation).isEqualTo("AUD-1");

    bridge.attachLaunch(launch, "execution-1", "订单同步");
    AuditCarrier stored = objectMapper.readValue(
        correlation.findCarrierJson("execution-1").orElseThrow(), AuditCarrier.class);
    assertThat(stored.operationId()).isEqualTo("AUD-1");
    assertThat(stored.resourceId()).isEqualTo("execution-1");
    assertThat(stored.actorName()).isEqualTo("alice");

    bridge.observeTerminal(new WorkflowExecutionTerminalEvent(
        "execution-1", "SUCCESS", Instant.parse("2026-09-02T07:30:00Z")));

    RecordingHandle handle = auditService.handle("AUD-1");
    assertThat(handle.successSummary).isEqualTo("Workflow execution succeeded");
    assertThat(changeTypes(handle.events))
        .containsExactly("EXECUTION_STARTED", "EXECUTION_SUCCEEDED");
    assertThat(handle.events)
        .allSatisfy(event -> assertThat(event.payload()).doesNotContainKeys("input", "output", "errorMessage"));
  }

  @Test
  void reactivationReplacesOldActorCorrelationBeforeActionRuns() throws Exception {
    AuditCarrier previous = new AuditCarrier(
        "AUD-OLD",
        "user-old",
        "alice-old",
        "USER",
        7L,
        "Project A",
        "WORKFLOW_EXECUTION",
        "execution-2",
        "订单同步",
        "WEB");
    correlation.replaceCarrierJson("execution-2", objectMapper.writeValueAsString(previous));

    String activeOperation = bridge.reactivate(
        "execution-2",
        "RETRY_FAILED_NODE",
        "node-1",
        () -> AuditContext.current().orElseThrow().operationId());

    assertThat(activeOperation).isEqualTo("AUD-1");
    AuditCarrier stored = objectMapper.readValue(
        correlation.findCarrierJson("execution-2").orElseThrow(), AuditCarrier.class);
    assertThat(stored.operationId()).isEqualTo("AUD-1");
    assertThat(stored.actorName()).isEqualTo("alice");
    assertThat(auditService.requests.getFirst().metadata().get("launchMode"))
        .isEqualTo("RETRY_FAILED_NODE");
    assertThat(auditService.requests.getFirst().metadata().get("nodeId"))
        .isEqualTo("node-1");

    bridge.observeTerminal(new WorkflowExecutionTerminalEvent(
        "execution-2", "SUCCESS", Instant.parse("2026-09-02T07:31:00Z")));
    assertThat(auditService.handle("AUD-1").successSummary)
        .isEqualTo("Workflow execution succeeded");
  }

  @Test
  void failedReactivationRestoresPreviousCarrierAndKeepsBusinessException() throws Exception {
    AuditCarrier previous = new AuditCarrier(
        "AUD-OLD",
        "user-old",
        "alice-old",
        "USER",
        7L,
        "Project A",
        "WORKFLOW_EXECUTION",
        "execution-3",
        "订单同步",
        "WEB");
    String previousJson = objectMapper.writeValueAsString(previous);
    correlation.replaceCarrierJson("execution-3", previousJson);
    IllegalStateException failure = new IllegalStateException("runtime rejected reactivation");

    assertThatThrownBy(() -> bridge.reactivate(
            "execution-3",
            "CONTINUE_AFTER_FAILURE",
            "node-2",
            () -> {
              throw failure;
            }))
        .isSameAs(failure);

    assertThat(correlation.findCarrierJson("execution-3")).contains(previousJson);
    assertThat(auditService.handle("AUD-1").failureReason)
        .isEqualTo("WORKFLOW_EXECUTION_REACTIVATION_FAILED");
  }

  @Test
  void canceledTerminalUsesStableReasonWithoutRuntimeErrorText() {
    WorkflowExecutionAuditBridge.LaunchAudit launch =
        bridge.beginLaunch("DRAFT_TEST", "workflow-4", WorkflowTriggerContext.manual());
    bridge.attachLaunch(launch, "execution-4", "测试工作流");

    bridge.observeTerminal(new WorkflowExecutionTerminalEvent(
        "execution-4", "CANCELED", Instant.parse("2026-09-02T07:32:00Z")));

    RecordingHandle handle = auditService.handle("AUD-1");
    assertThat(handle.failureReason).isEqualTo("WORKFLOW_EXECUTION_CANCELED");
    AuditEventRequest terminal = handle.events.getLast();
    assertThat(terminal.reasonCode()).isEqualTo("WORKFLOW_EXECUTION_CANCELED");
    assertThat(terminal.payload()).containsOnlyKeys("changeType", "executionId", "status");
  }

  private List<String> changeTypes(List<AuditEventRequest> events) {
    return events.stream()
        .map(event -> String.valueOf(event.payload().get("changeType")))
        .toList();
  }

  private static final class InMemoryCorrelationRepository
      implements WorkflowAuditCorrelationRepository {
    private final Map<String, String> values = new LinkedHashMap<>();

    @Override
    public Optional<String> findCarrierJson(String executionId) {
      return Optional.ofNullable(values.get(executionId));
    }

    @Override
    public boolean replaceCarrierJson(String executionId, String carrierJson) {
      if (carrierJson == null) values.remove(executionId);
      else values.put(executionId, carrierJson);
      return true;
    }
  }

  private static final class RecordingAuditService implements BusinessAuditService {
    private int sequence;
    private final List<AuditOperationRequest> requests = new ArrayList<>();
    private final Map<String, RecordingHandle> handles = new LinkedHashMap<>();

    @Override
    public AuditOperationHandle start(AuditOperationRequest request) {
      requests.add(request);
      String operationId = "AUD-" + (++sequence);
      RecordingHandle handle = new RecordingHandle(new AuditCarrier(
          operationId,
          "user-1",
          "alice",
          "USER",
          7L,
          "Project A",
          request.resourceType(),
          request.resourceId(),
          request.resourceName(),
          request.source()));
      handles.put(operationId, handle);
      return handle;
    }

    @Override
    public AuditOperationHandle resume(AuditCarrier carrier) {
      RecordingHandle handle = handles.get(carrier.operationId());
      return handle == null ? AuditOperationHandle.noop(carrier) : handle;
    }

    RecordingHandle handle(String operationId) {
      return handles.get(operationId);
    }
  }

  private static final class RecordingHandle implements AuditOperationHandle {
    private AuditCarrier carrier;
    private final List<AuditEventRequest> events = new ArrayList<>();
    private String successSummary;
    private String failureReason;

    private RecordingHandle(AuditCarrier carrier) {
      this.carrier = carrier;
    }

    @Override
    public String operationId() {
      return carrier.operationId();
    }

    @Override
    public AuditCarrier carrier() {
      return carrier;
    }

    @Override
    public void resource(String resourceId, String resourceName) {
      carrier = carrier.withResource(resourceId, resourceName);
    }

    @Override
    public void event(AuditEventType type, String message, Map<String, ?> payload) {
      events.add(new AuditEventRequest(type, null, message, null, payload));
    }

    @Override
    public void event(AuditEventRequest request) {
      events.add(request);
    }

    @Override
    public void success(String summary) {
      successSummary = summary;
    }

    @Override
    public void failure(String reasonCode, Throwable cause) {
      failureReason = reasonCode;
    }
  }
}
