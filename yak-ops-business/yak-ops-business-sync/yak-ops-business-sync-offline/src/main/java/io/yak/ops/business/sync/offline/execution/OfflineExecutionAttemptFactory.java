package io.yak.ops.business.sync.offline.execution;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.config.OfflineSyncProperties;
import io.yak.ops.business.sync.offline.domain.OfflineExecutionStatus;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Creates one persistence Attempt from an already frozen BatchExecution. */
@ConditionalOnOfflineSyncEnabled
@Component
@RequiredArgsConstructor
public class OfflineExecutionAttemptFactory {

  private final OfflineSyncProperties properties;

  public OfflineJobExecution create(
      BatchExecution batch,
      int attemptNo,
      String triggerType,
      Long retryFromExecutionId,
      String idempotencyKey) {
    LocalDateTime now = LocalDateTime.now();
    OfflineJobExecution execution = new OfflineJobExecution();
    execution.setJobDefinitionId(batch.taskId());
    execution.setBatchId(batch.id());
    execution.setDefinitionVersion(batch.snapshot().definitionRevision());
    execution.setEngineBaseUrl(properties.getEngine().getBaseUrl());
    execution.setExternalExecutionId("yak-offline-" + UUID.randomUUID());
    execution.setIdempotencyKey(requireText(idempotencyKey, "idempotencyKey 不能为空"));
    execution.setStatus(OfflineExecutionStatus.CREATED.name());
    execution.setStateVersion(1L);
    execution.setAttemptNo(Math.max(1, attemptNo));
    execution.setTriggerType(requireText(triggerType, "triggerType 不能为空"));
    execution.setRetryFromExecutionId(retryFromExecutionId);
    execution.setCancellationRequested(false);
    execution.setRetryCreated(false);
    execution.setConfigDigest(batch.snapshot().configDigest());
    execution.setDefinitionSnapshotJson(batch.snapshot().definitionSnapshot());
    execution.setSubmittedConfig(batch.snapshot().logicalJobSpec());
    execution.setSourceRecordCount(0L);
    execution.setSinkSuccessRecordCount(0L);
    execution.setSourceReadBytes(0L);
    execution.setSinkWrittenBytes(0L);
    execution.setQps(0D);
    execution.setDurationMillis(0L);
    execution.setCreateTime(now);
    execution.setUpdateTime(now);
    return execution;
  }

  private String requireText(String value, String message) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalStateException(message);
    }
    return value.trim();
  }
}
