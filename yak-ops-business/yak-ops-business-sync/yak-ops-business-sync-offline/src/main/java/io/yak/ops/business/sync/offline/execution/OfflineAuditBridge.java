package io.yak.ops.business.sync.offline.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.audit.AuditCarrier;
import io.yak.ops.business.audit.AuditContext;
import io.yak.ops.business.audit.AuditEventRequest;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionStatus;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchStatus;
import io.yak.ops.business.sync.offline.repository.OfflineAuditCorrelationRepository;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Bridges durable Offline Batch identity to one cross-thread business AuditOperation. */
@ConditionalOnOfflineSyncEnabled
@Component
public class OfflineAuditBridge {

  private static final Logger log = LoggerFactory.getLogger(OfflineAuditBridge.class);

  private final BusinessAuditService auditService;
  private final OfflineBatchExecutionRepository batchRepository;
  private final OfflineAuditCorrelationRepository correlationRepository;
  private final ObjectMapper objectMapper;

  public OfflineAuditBridge(
      BusinessAuditService auditService,
      OfflineBatchExecutionRepository batchRepository,
      OfflineAuditCorrelationRepository correlationRepository,
      @Qualifier("offlineSyncJsonMapper") ObjectMapper objectMapper) {
    this.auditService = auditService;
    this.batchRepository = batchRepository;
    this.correlationRepository = correlationRepository;
    this.objectMapper = objectMapper;
  }

  /** Returns an existing operation or creates correlation only for a new first Attempt. */
  public AuditOperationHandle ensureOperation(OfflineJobExecution execution) {
    if (!hasDurableBatch(execution)) {
      return AuditOperationHandle.noop(null);
    }
    try {
      AuditCarrier existing = storedCarrier(execution.getBatchId());
      if (existing != null) {
        return auditService.resume(existing);
      }
      if (!eligibleToStartOperation(execution)) {
        return AuditOperationHandle.noop(null);
      }

      AuditOperationHandle handle =
          auditService.start(
              new AuditOperationRequest(
                  "OFFLINE_SYNC_EXECUTE",
                  "Execute offline sync",
                  "OFFLINE_SYNC",
                  String.valueOf(execution.getJobDefinitionId()),
                  null,
                  source(execution.getTriggerType()),
                  startMetadata(execution)));
      AuditCarrier carrier = handle.carrier();
      if (carrier != null) {
        String carrierJson = objectMapper.writeValueAsString(carrier);
        if (!correlationRepository.updateCarrierJson(execution.getBatchId(), carrierJson)) {
          log.warn("Unable to persist offline audit carrier, batchId={}", execution.getBatchId());
        }
      }
      return handle;
    } catch (RuntimeException | JsonProcessingException exception) {
      log.warn(
          "Offline audit correlation failed; execution will continue, executionId={}",
          execution.getId(),
          exception);
      return AuditOperationHandle.noop(null);
    }
  }

  public void submitted(OfflineJobExecution execution) {
    safe(
        execution,
        () -> {
          AuditOperationHandle handle = resume(execution);
          if (handle.carrier() == null) return;
          handle.event(
              AuditEventRequest.of(
                  AuditEventType.TASK_SUBMITTED,
                  eventKey(execution, "submitted"),
                  "Offline sync attempt submitted",
                  payload(execution, "SUBMITTED")));
        });
  }

  /** Projects only business-meaningful Attempt/Batch transitions, never the full runtime log. */
  public void observeState(OfflineJobExecution execution) {
    safe(
        execution,
        () -> {
          BatchExecution batch = requireBatch(execution);
          AuditOperationHandle handle = resume(execution);
          if (handle.carrier() == null) return;

          if (batch.status() == BatchStatus.CANCELED) {
            handle.event(
                new AuditEventRequest(
                    AuditEventType.TASK_CANCELED,
                    eventKey(execution, "canceled"),
                    "Offline sync batch canceled",
                    "OFFLINE_SYNC_CANCELED",
                    payload(execution, batch.status().name())));
            handle.failure("OFFLINE_SYNC_CANCELED", null);
            return;
          }

          OfflineExecutionStatus status = OfflineExecutionStatus.parse(execution.getStatus());
          switch (status) {
            case QUEUED ->
                handle.event(
                    AuditEventRequest.of(
                        AuditEventType.TASK_QUEUED,
                        eventKey(execution, "queued"),
                        "Offline sync attempt queued",
                        payload(execution, status.name())));
            case RUNNING ->
                handle.event(
                    AuditEventRequest.of(
                        AuditEventType.WORKER_STARTED,
                        eventKey(execution, "worker-started"),
                        "Offline sync worker started",
                        payload(execution, status.name())));
            case SUCCEEDED -> {
              handle.event(
                  AuditEventRequest.of(
                      AuditEventType.TASK_SUCCEEDED,
                      eventKey(execution, "succeeded"),
                      "Offline sync attempt succeeded",
                      payload(execution, status.name())));
              if (batch.status() == BatchStatus.SUCCEEDED) {
                handle.success("Offline sync succeeded");
              }
            }
            case FAILED -> {
              handle.event(
                  new AuditEventRequest(
                      AuditEventType.TASK_FAILED,
                      eventKey(execution, "failed"),
                      "Offline sync attempt failed",
                      "OFFLINE_SYNC_ATTEMPT_FAILED",
                      payload(execution, batch.status().name())));
              if (batch.status() == BatchStatus.FAILED) {
                handle.failure("OFFLINE_SYNC_FAILED", null);
              }
            }
            case CANCELED -> {
              handle.event(
                  new AuditEventRequest(
                      AuditEventType.TASK_CANCELED,
                      eventKey(execution, "canceled"),
                      "Offline sync attempt canceled",
                      "OFFLINE_SYNC_CANCELED",
                      payload(execution, status.name())));
              if (batch.status() == BatchStatus.CANCELED) {
                handle.failure("OFFLINE_SYNC_CANCELED", null);
              }
            }
            case CREATED, SUBMITTED, UNKNOWN -> {
              // Audit is intentionally coarser than the runtime state/event log.
            }
          }
        });
  }

  private AuditOperationHandle resume(OfflineJobExecution execution) {
    AuditCarrier carrier = storedCarrier(execution.getBatchId());
    if (carrier == null) {
      carrier = AuditContext.current().orElse(null);
    }
    return carrier == null ? AuditOperationHandle.noop(null) : auditService.resume(carrier);
  }

  private AuditCarrier storedCarrier(Long batchId) {
    if (batchId == null || batchId <= 0L) return null;
    return correlationRepository.findCarrierJson(batchId).map(this::readCarrier).orElse(null);
  }

  private boolean eligibleToStartOperation(OfflineJobExecution execution) {
    return OfflineExecutionStatus.isCreated(execution.getStatus())
        && (execution.getAttemptNo() == null || execution.getAttemptNo() <= 1);
  }

  private boolean hasDurableBatch(OfflineJobExecution execution) {
    return execution != null && execution.getBatchId() != null && execution.getBatchId() > 0L;
  }

  private BatchExecution requireBatch(OfflineJobExecution execution) {
    Long batchId = execution.getBatchId();
    if (batchId == null || batchId <= 0L) {
      throw new IllegalStateException("Offline audit requires a durable BatchExecution");
    }
    return batchRepository
        .findById(batchId)
        .orElseThrow(() -> new IllegalStateException("Offline audit BatchExecution does not exist"));
  }

  private AuditCarrier readCarrier(String value) {
    if (!StringUtils.hasText(value)) return null;
    try {
      return objectMapper.readValue(value, AuditCarrier.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Offline audit carrier is invalid", exception);
    }
  }

  private Map<String, Object> startMetadata(OfflineJobExecution execution) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("batchId", execution.getBatchId());
    if (StringUtils.hasText(execution.getTriggerType())) {
      metadata.put("triggerType", execution.getTriggerType());
    }
    if (execution.getDefinitionVersion() != null) {
      metadata.put("definitionVersion", execution.getDefinitionVersion());
    }
    return Map.copyOf(metadata);
  }

  private Map<String, Object> payload(OfflineJobExecution execution, String status) {
    Map<String, Object> payload = new LinkedHashMap<>();
    if (execution.getBatchId() != null) payload.put("batchId", execution.getBatchId());
    if (execution.getId() != null) payload.put("attemptId", execution.getId());
    if (execution.getAttemptNo() != null) payload.put("attemptNo", execution.getAttemptNo());
    if (StringUtils.hasText(status)) payload.put("status", status);
    return Map.copyOf(payload);
  }

  private String eventKey(OfflineJobExecution execution, String suffix) {
    return "offline:attempt:" + execution.getId() + ":" + suffix;
  }

  private String source(String triggerType) {
    if (!StringUtils.hasText(triggerType)) return "APPLICATION";
    String normalized = triggerType.trim().toUpperCase(Locale.ROOT);
    if (normalized.startsWith("SCHEDULE")) return "SCHEDULE";
    if (normalized.startsWith("WORKFLOW")) return "WORKFLOW";
    if (normalized.startsWith("BACKFILL")) return "BACKFILL";
    if (normalized.startsWith("RETRY")) return "WORKER";
    if (normalized.startsWith("MANUAL")) return "WEB";
    return "APPLICATION";
  }

  private void safe(OfflineJobExecution execution, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException exception) {
      log.warn(
          "Offline audit projection failed; execution will continue, executionId={}",
          execution == null ? null : execution.getId(),
          exception);
    }
  }
}
