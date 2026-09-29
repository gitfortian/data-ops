package io.yak.ops.business.sync.offline.execution;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.framework.common.PagingData;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.domain.core.BatchTriggerToken;
import io.yak.ops.business.sync.offline.engine.LinkUpClient;
import io.yak.ops.business.sync.offline.engine.LinkUpClient.LinkUpJobResponse;
import io.yak.ops.business.sync.offline.engine.connector.OfflineSyncConnectorRuntime;
import io.yak.ops.business.sync.offline.execution.query.OfflineExecutionLogQuery;
import io.yak.ops.business.sync.offline.execution.query.OfflineExecutionQuery;
import io.yak.ops.business.sync.offline.mapping.OfflineSyncViewMapper;
import io.yak.ops.business.sync.offline.schedule.OfflineScheduleExecutionGateway;
import io.yak.ops.common.bean.dto.sync.offline.OfflineBatchOperationDTO;
import io.yak.ops.common.bean.dto.sync.offline.OfflineJobExecutionQueryDTO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineBatchOperationErrorVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineBatchOperationVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineConnectorRuntimeSnapshotVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineEngineHealthVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineExecutionEventVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineExecutionLogPageVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobExecutionDetailVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobExecutionVO;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Stable execution facade for controllers, Backfill dispatch, Schedule and Reconcile. */
@ConditionalOnOfflineSyncEnabled
@Service
@RequiredArgsConstructor
public class OfflineJobExecutionService implements OfflineScheduleExecutionGateway {

  private final OfflineExecutionCoordinator coordinator;
  private final OfflineBatchRuntime batchRuntime;
  private final OfflineExecutionQuery executionQuery;
  private final OfflineExecutionLogQuery executionLogQuery;
  private final LinkUpClient linkUpClient;
  private final OfflineSyncViewMapper viewMapper;
  private final OfflineSyncConnectorRuntime connectorRuntime;

  public OfflineEngineHealthVO health() {
    return viewMapper.engineHealth(linkUpClient.node());
  }

  /** Global runtime inventory for the currently configured Link-Up Worker. */
  public OfflineConnectorRuntimeSnapshotVO connectorRuntime() {
    return connectorRuntime.snapshot();
  }

  /** Full current Worker schema. Unlike the inventory summary, this never falls back to stale data. */
  public JsonNode connectorSchema(String connectorId, String role) {
    return connectorRuntime.schema(connectorId, role);
  }

  @Override
  public boolean hasOccupyingBatch(Long definitionId) {
    return batchRuntime.hasOccupyingBatch(definitionId);
  }

  public OfflineJobExecutionVO execute(Long id) {
    return executionQuery.toVO(coordinator.execute(id, BatchTriggerToken.MANUAL, null, 1));
  }

  /** Workflow executes the immutable task snapshot captured at publish time. */
  public OfflineJobExecutionVO executeSnapshot(
      Long id,
      long version,
      String configDigest,
      String definitionSnapshotJson,
      String logicalJobSpecJson) {
    return executeSnapshot(
        id,
        version,
        configDigest,
        definitionSnapshotJson,
        logicalJobSpecJson,
        null);
  }

  /** Workflow executes the frozen snapshot with an Attempt idempotency key. */
  public OfflineJobExecutionVO executeSnapshot(
      Long id,
      long version,
      String configDigest,
      String definitionSnapshotJson,
      String logicalJobSpecJson,
      String idempotencyKey) {
    return executionQuery.toVO(
        coordinator.executeSnapshot(
            id,
            version,
            configDigest,
            definitionSnapshotJson,
            logicalJobSpecJson,
            idempotencyKey));
  }

  public OfflineJobExecutionVO executeScheduled(Long id) {
    return executeScheduled(id, BatchTriggerToken.SCHEDULE);
  }

  /** Preserves the complete schedule trigger token that identifies one planned fire. */
  public OfflineJobExecutionVO executeScheduled(Long id, String triggerToken) {
    return executionQuery.toVO(coordinator.execute(id, triggerToken, null, 1));
  }

  /** Schedule depends only on the narrow execution gateway contract. */
  @Override
  public Long submitScheduled(Long definitionId, String triggerToken) {
    OfflineJobExecutionVO execution = executeScheduled(definitionId, triggerToken);
    return execution == null ? null : execution.getId();
  }

  /** Backfill Dispatcher enters execution through this stable facade. */
  public OfflineJobExecutionVO executePendingBackfill(Long batchId) {
    return executionQuery.toVO(coordinator.executePendingBackfill(batchId));
  }

  public OfflineJobExecutionVO retry(Long id) {
    return executionQuery.toVO(coordinator.retryFrom(executionQuery.require(id)));
  }

  public OfflineJobExecutionVO retryFrom(OfflineJobExecution previous) {
    return executionQuery.toVO(coordinator.retryFrom(previous));
  }

  public OfflineJobExecutionVO cancel(Long id) {
    return executionQuery.toVO(coordinator.cancel(id));
  }

  /** Task-level cancel resolves the target from BatchExecution/latest Attempt, never Task.last-*. */
  public OfflineJobExecutionVO cancelLatest(Long definitionId) {
    return executionQuery.toVO(coordinator.cancelLatestBatch(definitionId));
  }

  public OfflineBatchOperationVO batchExecute(OfflineBatchOperationDTO request) {
    return batch(request, BatchCommand.EXECUTE);
  }

  public OfflineBatchOperationVO batchCancel(OfflineBatchOperationDTO request) {
    return batch(request, BatchCommand.CANCEL);
  }

  public PagingData<OfflineJobExecutionVO> page(OfflineJobExecutionQueryDTO query) {
    return executionQuery.page(query);
  }

  public OfflineJobExecutionDetailVO detail(Long id) {
    return executionQuery.detail(id);
  }

  public JsonNode tableMetrics(Long id) {
    return executionQuery.tableMetrics(id);
  }

  public List<OfflineExecutionEventVO> events(Long id) {
    return executionQuery.events(id);
  }

  /** Compatibility text endpoint renders the unified execution timeline. */
  public String logs(Long id) {
    return executionLogQuery.text(executionQuery.require(id));
  }

  public OfflineExecutionLogPageVO logs(Long id, String cursor, int limit) {
    return executionLogQuery.logs(executionQuery.require(id), cursor, limit);
  }

  /** Reconciler lets the execution layer own FAN_OUT child discovery/aggregation. */
  public boolean reconcileFanOutIfNeeded(OfflineJobExecution execution) {
    return coordinator.reconcileFanOutIfNeeded(execution);
  }

  /** Reconciler enters state application through the stable facade. */
  public void applySnapshot(OfflineJobExecution execution, LinkUpJobResponse response, String type) {
    coordinator.applySnapshot(execution, response, type);
  }

  /** Reconciler enters UNKNOWN convergence through the stable facade. */
  public void markUnknown(OfflineJobExecution execution, String message) {
    coordinator.markUnknown(execution, message);
  }

  private OfflineBatchOperationVO batch(
      OfflineBatchOperationDTO request, BatchCommand command) {
    requireBatchRequest(request);

    int success = 0;
    List<OfflineBatchOperationErrorVO> errors = new ArrayList<>();
    for (Long id : request.getJobDefinitionIds()) {
      try {
        requireDefinitionId(id);
        applyBatchCommand(id, command);
        success++;
      } catch (RuntimeException exception) {
        errors.add(
            OfflineBatchOperationErrorVO.builder()
                .jobDefinitionId(id)
                .message(exception.getMessage())
                .build());
      }
    }

    return OfflineBatchOperationVO.builder()
        .successCount(success)
        .failedCount(errors.size())
        .errors(errors)
        .build();
  }

  private void requireBatchRequest(OfflineBatchOperationDTO request) {
    if (request == null
        || request.getJobDefinitionIds() == null
        || request.getJobDefinitionIds().isEmpty()) {
      throw new IllegalArgumentException("jobDefinitionIds 不能为空");
    }
  }

  private void requireDefinitionId(Long id) {
    if (id == null || id <= 0L) {
      throw new IllegalArgumentException("任务定义 ID 不合法");
    }
  }

  private void applyBatchCommand(Long id, BatchCommand command) {
    switch (command) {
      case EXECUTE -> execute(id);
      case CANCEL -> cancelLatest(id);
    }
  }

  private enum BatchCommand {
    EXECUTE,
    CANCEL
  }
}
