package io.yak.ops.business.workflow.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.common.bean.dto.workflow.WorkflowBackfillCreateDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowBusinessDateRerunDTO;
import io.yak.ops.common.bean.po.workflow.WorkflowBackfillPO;
import io.yak.ops.common.bean.vo.workflow.WorkflowBackfillVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowBackfillAuditCoordinatorTest {

  @Test
  void createStoresBoundedBatchFactsWithoutRawInput() {
    WorkflowBackfillManager manager = mock(WorkflowBackfillManager.class);
    WorkflowBackfillQuery query = mock(WorkflowBackfillQuery.class);
    RecordingAuditService audit = new RecordingAuditService();
    WorkflowBackfillAuditCoordinator coordinator =
        new WorkflowBackfillAuditCoordinator(manager, query, audit);
    WorkflowBackfillCreateDTO request = new WorkflowBackfillCreateDTO(
        "schedule-1",
        "August refill",
        LocalDate.parse("2026-08-01"),
        LocalDate.parse("2026-08-03"),
        "SERIAL_WAIT",
        Map.of("token", "backfill-secret"));
    WorkflowBackfillVO created = backfill("RUNNING", "BACKFILL", Map.of("token", "backfill-secret"));
    when(manager.create(request)).thenReturn(created);

    coordinator.create(request);

    assertThat(audit.request.operationType()).isEqualTo("WORKFLOW_BACKFILL_CREATE");
    assertThat(audit.request.resourceType()).isEqualTo("WORKFLOW_BACKFILL");
    assertThat(audit.request.metadata().toString()).doesNotContain("backfill-secret");
    Map<String, ?> payload = audit.handle.events.get(0).payload();
    assertThat(payload.get("changeType")).isEqualTo("BACKFILL_CREATED");
    assertThat(payload.get("totalCount")).isEqualTo(3);
    assertThat(payload.get("inputConfigured")).isEqualTo(true);
    assertThat(payload.toString()).doesNotContain("backfill-secret");
  }

  @Test
  void businessDateRerunIsTheGatewayAuditOwner() {
    WorkflowBackfillManager manager = mock(WorkflowBackfillManager.class);
    WorkflowBackfillQuery query = mock(WorkflowBackfillQuery.class);
    RecordingAuditService audit = new RecordingAuditService();
    WorkflowBackfillAuditCoordinator coordinator =
        new WorkflowBackfillAuditCoordinator(manager, query, audit);
    WorkflowBusinessDateRerunDTO request = new WorkflowBusinessDateRerunDTO(
        LocalDate.parse("2026-08-02"),
        "SERIAL_WAIT",
        Map.of("password", "rerun-secret"));
    WorkflowInstanceVO source = instance("execution-source");
    WorkflowBackfillVO created =
        backfill("RUNNING", "BUSINESS_DATE_RERUN", Map.of("password", "rerun-secret"));
    when(manager.createBusinessDateRerun("execution-source", source, request)).thenReturn(created);

    coordinator.createBusinessDateRerun("execution-source", source, request);

    assertThat(audit.request.operationType()).isEqualTo("WORKFLOW_BUSINESS_DATE_RERUN");
    assertThat(audit.request.metadata().get("sourceExecutionId")).isEqualTo("execution-source");
    assertThat(audit.request.metadata().toString()).doesNotContain("rerun-secret");
    assertThat(audit.handle.events.get(0).payload().get("changeType"))
        .isEqualTo("BUSINESS_DATE_RERUN_CREATED");
    assertThat(audit.handle.events.get(0).payload().toString()).doesNotContain("rerun-secret");
  }

  @Test
  void repeatedCancelDoesNotCreateAuditNoiseWhenDurableBatchIsAlreadyCanceled() {
    WorkflowBackfillManager manager = mock(WorkflowBackfillManager.class);
    WorkflowBackfillQuery query = mock(WorkflowBackfillQuery.class);
    RecordingAuditService audit = new RecordingAuditService();
    WorkflowBackfillAuditCoordinator coordinator =
        new WorkflowBackfillAuditCoordinator(manager, query, audit);
    WorkflowBackfillPO durable = new WorkflowBackfillPO();
    durable.setId("backfill-1");
    durable.setStatus("CANCELED");
    WorkflowBackfillVO canceled = backfill("CANCELED", "BACKFILL", Map.of());
    when(query.require("backfill-1")).thenReturn(durable);
    when(manager.cancel("backfill-1")).thenReturn(canceled);

    WorkflowBackfillVO result = coordinator.cancel("backfill-1");

    assertThat(result).isSameAs(canceled);
    assertThat(audit.starts).isZero();
    verify(manager).cancel("backfill-1");
  }

  private static WorkflowBackfillVO backfill(
      String status, String operationType, Map<String, Object> input) {
    Instant now = Instant.parse("2026-09-02T00:00:00Z");
    return new WorkflowBackfillVO(
        "backfill-1",
        "workflow-1",
        "workflow-version-2",
        2,
        "schedule-1",
        "Daily sync",
        "August refill",
        status,
        operationType,
        "BUSINESS_DATE_RERUN".equals(operationType) ? "execution-source" : null,
        LocalDate.parse("2026-08-01"),
        LocalDate.parse("2026-08-03"),
        "0 0 2 * * ?",
        "Asia/Shanghai",
        "SERIAL_WAIT",
        input,
        3,
        2,
        1,
        0,
        0,
        0,
        0,
        now,
        now);
  }

  private static WorkflowInstanceVO instance(String id) {
    Instant now = Instant.parse("2026-09-02T00:00:00Z");
    return new WorkflowInstanceVO(
        id,
        "workflow-version-2",
        null,
        "Daily sync",
        "FAILED",
        "FAIL_FAST",
        now,
        now,
        now,
        300L,
        Map.of(),
        0,
        0,
        List.of(),
        "workflow-version-2",
        2,
        false);
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

    @Override
    public String operationId() {
      return "AUD-backfill";
    }

    @Override
    public void resource(String resourceId, String resourceName) {}

    @Override
    public void event(AuditEventType type, String message, Map<String, ?> payload) {
      events.add(new Event(type, message, Map.copyOf(payload)));
    }

    @Override
    public void success(String summary) {}

    @Override
    public void failure(String reasonCode, Throwable cause) {}
  }

  private record Event(AuditEventType type, String message, Map<String, ?> payload) {}
}
