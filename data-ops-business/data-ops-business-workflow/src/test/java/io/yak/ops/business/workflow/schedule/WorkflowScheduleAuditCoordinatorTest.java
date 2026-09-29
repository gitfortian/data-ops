package io.yak.ops.business.workflow.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.common.bean.dto.workflow.WorkflowScheduleCreateDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowScheduleUpdateDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowScheduleVO;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowScheduleAuditCoordinatorTest {

  @Test
  void createKeepsScheduleInputOutOfAuditPayload() {
    WorkflowScheduleCreateCommand creator = mock(WorkflowScheduleCreateCommand.class);
    WorkflowScheduleRevision revision = mock(WorkflowScheduleRevision.class);
    WorkflowScheduleLifecycle lifecycle = mock(WorkflowScheduleLifecycle.class);
    WorkflowScheduleQuery query = mock(WorkflowScheduleQuery.class);
    RecordingAuditService audit = new RecordingAuditService();
    WorkflowScheduleAuditCoordinator coordinator =
        new WorkflowScheduleAuditCoordinator(creator, revision, lifecycle, query, audit);
    WorkflowScheduleCreateDTO request = new WorkflowScheduleCreateDTO(
        "workflow-1",
        "Daily sync",
        "0 0 2 * * ?",
        "Asia/Shanghai",
        null,
        null,
        "SERIAL_WAIT",
        "FIRE_ONCE",
        Map.of("password", "schedule-secret"));
    WorkflowScheduleVO created = schedule("OFFLINE", Map.of("password", "schedule-secret"));
    when(creator.create(request)).thenReturn(created);

    coordinator.create(request);

    assertThat(audit.request.operationType()).isEqualTo("WORKFLOW_SCHEDULE_CREATE");
    assertThat(audit.request.resourceType()).isEqualTo("WORKFLOW_SCHEDULE");
    assertThat(audit.request.metadata().toString()).doesNotContain("schedule-secret");
    assertThat(audit.handle.events).hasSize(1);
    Map<String, ?> payload = audit.handle.events.get(0).payload();
    assertThat(payload.get("changeType")).isEqualTo("SCHEDULE_CREATED");
    assertThat(payload.get("inputConfigured")).isEqualTo(true);
    assertThat(payload.toString()).doesNotContain("schedule-secret");
  }

  @Test
  void unchangedUpdateDoesNotCreateAuditNoise() {
    WorkflowScheduleCreateCommand creator = mock(WorkflowScheduleCreateCommand.class);
    WorkflowScheduleRevision revision = mock(WorkflowScheduleRevision.class);
    WorkflowScheduleLifecycle lifecycle = mock(WorkflowScheduleLifecycle.class);
    WorkflowScheduleQuery query = mock(WorkflowScheduleQuery.class);
    RecordingAuditService audit = new RecordingAuditService();
    WorkflowScheduleAuditCoordinator coordinator =
        new WorkflowScheduleAuditCoordinator(creator, revision, lifecycle, query, audit);
    WorkflowScheduleVO before = schedule("OFFLINE", Map.of());
    WorkflowScheduleUpdateDTO request = new WorkflowScheduleUpdateDTO(
        before.name(),
        before.cronExpression(),
        before.timezone(),
        before.startTime(),
        before.endTime(),
        before.executionStrategy(),
        before.misfireStrategy(),
        before.input());
    when(query.get("schedule-1")).thenReturn(before);
    when(revision.save("schedule-1", request)).thenReturn(before);

    WorkflowScheduleVO result = coordinator.update("schedule-1", request);

    assertThat(result).isSameAs(before);
    assertThat(audit.starts).isZero();
    verify(revision).save("schedule-1", request);
  }

  @Test
  void schedulerDisableUsesSystemSourceAndStableCause() {
    WorkflowScheduleCreateCommand creator = mock(WorkflowScheduleCreateCommand.class);
    WorkflowScheduleRevision revision = mock(WorkflowScheduleRevision.class);
    WorkflowScheduleLifecycle lifecycle = mock(WorkflowScheduleLifecycle.class);
    WorkflowScheduleQuery query = mock(WorkflowScheduleQuery.class);
    RecordingAuditService audit = new RecordingAuditService();
    WorkflowScheduleAuditCoordinator coordinator =
        new WorkflowScheduleAuditCoordinator(creator, revision, lifecycle, query, audit);
    WorkflowScheduleVO before = schedule("ONLINE", Map.of());
    WorkflowScheduleVO after = schedule("OFFLINE", Map.of());
    when(query.get("schedule-1")).thenReturn(before);
    when(lifecycle.offline("schedule-1")).thenReturn(after);

    coordinator.offlineFromScheduler("schedule-1", "WORKFLOW_NOT_ONLINE");

    assertThat(audit.request.operationType()).isEqualTo("WORKFLOW_SCHEDULE_DISABLE");
    assertThat(audit.request.source()).isEqualTo("SCHEDULE");
    assertThat(audit.request.metadata().get("cause")).isEqualTo("WORKFLOW_NOT_ONLINE");
    assertThat(audit.handle.events.get(0).payload().get("changeType"))
        .isEqualTo("SCHEDULE_DISABLED");
    assertThat(audit.handle.events.get(0).payload().get("cause"))
        .isEqualTo("WORKFLOW_NOT_ONLINE");
  }

  @Test
  void expiryIsAStandaloneSystemAuditOperation() {
    WorkflowScheduleCreateCommand creator = mock(WorkflowScheduleCreateCommand.class);
    WorkflowScheduleRevision revision = mock(WorkflowScheduleRevision.class);
    WorkflowScheduleLifecycle lifecycle = mock(WorkflowScheduleLifecycle.class);
    WorkflowScheduleQuery query = mock(WorkflowScheduleQuery.class);
    RecordingAuditService audit = new RecordingAuditService();
    WorkflowScheduleAuditCoordinator coordinator =
        new WorkflowScheduleAuditCoordinator(creator, revision, lifecycle, query, audit);
    WorkflowScheduleVO before = schedule("ONLINE", Map.of());
    WorkflowScheduleVO after = schedule("OFFLINE", Map.of());
    Instant fireTime = Instant.parse("2026-09-02T02:00:00Z");
    when(query.get("schedule-1")).thenReturn(before);
    when(lifecycle.expire("schedule-1", fireTime)).thenReturn(after);

    coordinator.expire("schedule-1", fireTime);

    assertThat(audit.request.operationType()).isEqualTo("WORKFLOW_SCHEDULE_EXPIRE");
    assertThat(audit.request.source()).isEqualTo("SCHEDULE");
    assertThat(audit.handle.events.get(0).payload().get("changeType"))
        .isEqualTo("SCHEDULE_EXPIRED");
    assertThat(audit.handle.events.get(0).payload().get("fireTime")).isEqualTo(fireTime);
  }

  private static WorkflowScheduleVO schedule(String status, Map<String, Object> input) {
    Instant now = Instant.parse("2026-09-02T00:00:00Z");
    return new WorkflowScheduleVO(
        "schedule-1",
        "workflow-1",
        "Daily sync",
        "CRON",
        "0 0 2 * * ?",
        "Asia/Shanghai",
        null,
        null,
        status,
        "SERIAL_WAIT",
        "FIRE_ONCE",
        input,
        null,
        null,
        now,
        now);
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
      return "AUD-schedule";
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
