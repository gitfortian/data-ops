package io.yak.ops.business.sync.offline.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Read-only Offline-owned execution evidence. Never infer success from
 * Catalog observation or a mutable task draft. The caller must provide the
 * concrete execution ID; discovery/indexing is a separate capability.
 */
@Service
@ConditionalOnOfflineSyncEnabled
@RequiredArgsConstructor
public class OfflineOdsExecutionEvidenceQuery {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final OfflineJobExecutionRepository executions;
  private final OfflineBatchExecutionRepository batches;

  public record Evidence(
      Long executionId, Long jobDefinitionId, Long batchId, int jobRevisionNo,
      int attemptNo, String status, String externalExecutionId,
      String engineJobId, LocalDateTime startTime, LocalDateTime endTime,
      Long sourceRows, Long sinkCommittedRows, Long failedRows, String errorMessage) {}

  public Evidence require(long executionId, long modelId, int versionNo, long observationId) {
    if (executionId <= 0 || modelId <= 0 || versionNo <= 0 || observationId <= 0) {
      throw new IllegalArgumentException("ODS 执行证据身份必须为正整数");
    }
    // Repository findById is project scoped. Never inspect a foreign attempt.
    OfflineJobExecution execution = executions.findById(executionId)
        .orElseThrow(() -> new IllegalArgumentException("当前项目没有该执行实例"));
    if (execution.getBatchId() == null || execution.getBatchId() <= 0) {
      throw new IllegalArgumentException("历史执行缺少冻结 Batch 证据");
    }
    var batch = batches.findById(execution.getBatchId())
        .orElseThrow(() -> new IllegalArgumentException("执行实例的 Batch 不存在"));
    if (!Objects.equals(execution.getJobDefinitionId(), batch.taskId())
        || batch.snapshot() == null) {
      throw new IllegalArgumentException("Attempt 与冻结 Batch 身份不一致");
    }
    JsonNode root;
    try {
      root = JSON.readTree(batch.snapshot().definitionSnapshot());
    } catch (Exception e) {
      throw new IllegalArgumentException("Batch 冻结任务定义不可解析", e);
    }
    if (root == null || !root.isObject()) {
      throw new IllegalArgumentException("Batch 冻结任务定义不是 JSON 对象");
    }
    JsonNode handoff = root.path("odsHandoff");
    if (!handoff.isObject()
        || handoff.size() != 3
        || !exactId(handoff.path("modelId"), modelId)
        || !exactId(handoff.path("versionNo"), versionNo)
        || !exactId(handoff.path("targetObservationId"), observationId)) {
      throw new IllegalArgumentException("该执行快照不属于指定的 ODS 模型版本及目标观察记录");
    }
    return new Evidence(execution.getId(), execution.getJobDefinitionId(),
        execution.getBatchId(), batch.snapshot().definitionRevision(),
        execution.getAttemptNo() == null ? 0 : execution.getAttemptNo(),
        execution.getStatus(), execution.getExternalExecutionId(),
        execution.getEngineJobId(), execution.getStartTime(), execution.getEndTime(),
        execution.getSourceRecordCount(), execution.getSinkCommittedRecordCount(),
        execution.getFailedRecordCount(), execution.getErrorMessage());
  }

  private static boolean exactId(JsonNode value, long expected) {
    return value.isIntegralNumber() && value.canConvertToLong()
        && value.longValue() == expected;
  }
}
