package io.yak.ops.business.sync.offline.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.audit.AuditCarrier;
import io.yak.ops.business.audit.AuditEventRequest;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchKey;
import io.yak.ops.business.sync.offline.domain.core.BatchScope;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.domain.core.BatchTrigger;
import io.yak.ops.business.sync.offline.domain.core.ExecutionSnapshot;
import io.yak.ops.business.sync.offline.domain.core.RetryPolicySnapshot;
import io.yak.ops.business.sync.offline.repository.OfflineAuditCorrelationRepository;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OfflineAuditBridgeTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void newFirstAttemptFreezesCarrierWithoutRewritingBatchRuntimeTruth() throws Exception {
    BusinessAuditService auditService = mock(BusinessAuditService.class);
    OfflineBatchExecutionRepository batchRepository = mock(OfflineBatchExecutionRepository.class);
    OfflineAuditCorrelationRepository correlationRepository =
        mock(OfflineAuditCorrelationRepository.class);
    AuditOperationHandle handle = mock(AuditOperationHandle.class);
    AuditCarrier carrier = carrier();
    OfflineJobExecution execution = execution("CREATED", 1);

    when(correlationRepository.findCarrierJson(77L)).thenReturn(Optional.empty());
    when(auditService.start(any(AuditOperationRequest.class))).thenReturn(handle);
    when(handle.carrier()).thenReturn(carrier);
    when(correlationRepository.updateCarrierJson(eq(77L), any(String.class))).thenReturn(true);

    AuditOperationHandle result =
        new OfflineAuditBridge(
                auditService, batchRepository, correlationRepository, objectMapper)
            .ensureOperation(execution);

    assertThat(result).isSameAs(handle);
    ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
    verify(correlationRepository).updateCarrierJson(eq(77L), stored.capture());
    assertThat(objectMapper.readValue(stored.getValue(), AuditCarrier.class)).isEqualTo(carrier);
    verify(batchRepository, never()).update(any(BatchExecution.class));
  }

  @Test
  void runningAttemptResumesFrozenBatchCarrierAndUsesDeterministicEventKey() throws Exception {
    BusinessAuditService auditService = mock(BusinessAuditService.class);
    OfflineBatchExecutionRepository batchRepository = mock(OfflineBatchExecutionRepository.class);
    OfflineAuditCorrelationRepository correlationRepository =
        mock(OfflineAuditCorrelationRepository.class);
    AuditOperationHandle handle = mock(AuditOperationHandle.class);
    AuditCarrier carrier = carrier();
    BatchExecution batch = batch(BatchStatus.RUNNING);
    OfflineJobExecution execution = execution("RUNNING", 2);
    String carrierJson = objectMapper.writeValueAsString(carrier);

    when(batchRepository.findById(77L)).thenReturn(Optional.of(batch));
    when(correlationRepository.findCarrierJson(77L)).thenReturn(Optional.of(carrierJson));
    when(auditService.resume(carrier)).thenReturn(handle);
    when(handle.carrier()).thenReturn(carrier);

    new OfflineAuditBridge(auditService, batchRepository, correlationRepository, objectMapper)
        .observeState(execution);

    ArgumentCaptor<AuditEventRequest> event = ArgumentCaptor.forClass(AuditEventRequest.class);
    verify(handle).event(event.capture());
    assertThat(event.getValue().type()).isEqualTo(AuditEventType.WORKER_STARTED);
    assertThat(event.getValue().eventKey()).isEqualTo("offline:attempt:99:worker-started");
    Map<String, ?> payload = event.getValue().payload();
    assertThat(payload.get("batchId")).isEqualTo(77L);
    assertThat(payload.get("attemptId")).isEqualTo(99L);
    assertThat(payload.get("attemptNo")).isEqualTo(2);
    assertThat(payload.get("status")).isEqualTo("RUNNING");
    verify(auditService).resume(carrier);
  }

  @Test
  void failedAttemptKeepsOperationOpenWhileBatchIsWaitingRetry() throws Exception {
    BusinessAuditService auditService = mock(BusinessAuditService.class);
    OfflineBatchExecutionRepository batchRepository = mock(OfflineBatchExecutionRepository.class);
    OfflineAuditCorrelationRepository correlationRepository =
        mock(OfflineAuditCorrelationRepository.class);
    AuditOperationHandle handle = mock(AuditOperationHandle.class);
    AuditCarrier carrier = carrier();
    BatchExecution batch = batch(BatchStatus.WAITING_RETRY);
    OfflineJobExecution execution = execution("FAILED", 1);
    String carrierJson = objectMapper.writeValueAsString(carrier);

    when(batchRepository.findById(77L)).thenReturn(Optional.of(batch));
    when(correlationRepository.findCarrierJson(77L)).thenReturn(Optional.of(carrierJson));
    when(auditService.resume(carrier)).thenReturn(handle);
    when(handle.carrier()).thenReturn(carrier);

    new OfflineAuditBridge(auditService, batchRepository, correlationRepository, objectMapper)
        .observeState(execution);

    ArgumentCaptor<AuditEventRequest> event = ArgumentCaptor.forClass(AuditEventRequest.class);
    verify(handle).event(event.capture());
    assertThat(event.getValue().type()).isEqualTo(AuditEventType.TASK_FAILED);
    assertThat(event.getValue().eventKey()).isEqualTo("offline:attempt:99:failed");
    verify(handle, never()).failure(any(), any());
  }

  private AuditCarrier carrier() {
    return new AuditCarrier(
        "AUD-123",
        "7",
        "tester",
        "USER",
        10L,
        "Project A",
        "OFFLINE_SYNC",
        "10",
        "orders",
        "WEB");
  }

  private BatchExecution batch(BatchStatus status) {
    return new BatchExecution(
        77L,
        10L,
        new BatchKey("manual:test"),
        BatchTrigger.MANUAL,
        BatchScope.fullSelection(),
        new ExecutionSnapshot(
            "{}",
            1,
            new RetryPolicySnapshot(3, 30),
            "digest",
            "logical"),
        status,
        List.of());
  }

  private OfflineJobExecution execution(String status, int attemptNo) {
    OfflineJobExecution execution = new OfflineJobExecution();
    execution.setId(99L);
    execution.setBatchId(77L);
    execution.setJobDefinitionId(10L);
    execution.setDefinitionVersion(1);
    execution.setAttemptNo(attemptNo);
    execution.setTriggerType(attemptNo > 1 ? "RETRY" : "MANUAL");
    execution.setStatus(status);
    return execution;
  }
}
