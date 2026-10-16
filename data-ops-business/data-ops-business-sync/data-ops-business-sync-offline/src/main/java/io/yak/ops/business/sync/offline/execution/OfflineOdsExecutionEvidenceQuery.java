package io.yak.ops.business.sync.offline.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineJobExecution;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import io.yak.ops.business.sync.offline.domain.core.BatchExecution;
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

  /**
   * One bounded page of project-local candidate Attempts, filtered against
   * immutable Batch snapshots. Page positions refer to the candidate scan,
   * not total matching ODS executions; no fabricated total is returned.
   */
  public record DiscoveryPage(int scanPage, int scanSize, boolean moreCandidates,
                              List<Evidence> matches) {}

  public DiscoveryPage discover(long modelId, int versionNo, long observationId,
                                int scanPage, int scanSize) {
    if (modelId <= 0 || versionNo <= 0 || observationId <= 0
        || scanPage < 1 || scanPage > 100000 || scanSize < 1 || scanSize > 100) {
      throw new IllegalArgumentException("ODS 执行历史查询参数非法");
    }
    List<OfflineJobExecution> candidates = executions.scanRecent(scanPage, scanSize);
    List<Evidence> matches = new ArrayList<>();
    // Attempts from the same Batch share one immutable snapshot. Bound lookups
    // to one Batch read per candidate page, including missing batches.
    Map<Long, java.util.Optional<BatchExecution>> batchCache = new HashMap<>();
    for (OfflineJobExecution candidate : candidates) {
      if (candidate.getId() == null || candidate.getBatchId() == null
          || candidate.getBatchId() <= 0) continue;
      var batch = batchCache.computeIfAbsent(candidate.getBatchId(), batches::findById)
          .orElse(null);
      if (batch == null || batch.snapshot() == null
          || !Objects.equals(candidate.getJobDefinitionId(), batch.taskId())) continue;
      JsonNode root;
      try {
        root = JSON.readTree(batch.snapshot().definitionSnapshot());
      } catch (Exception ignored) {
        continue;
      }
      if (root == null || !root.isObject()) continue;
      JsonNode handoff = root.path("odsHandoff");
      if (!handoff.isObject() || handoff.size() != 3
          || !exactId(handoff.path("modelId"), modelId)
          || !exactId(handoff.path("versionNo"), versionNo)
          || !exactId(handoff.path("targetObservationId"), observationId)) continue;
      matches.add(toEvidence(candidate, batch.snapshot().definitionRevision()));
    }
    return new DiscoveryPage(scanPage, scanSize, candidates.size() == scanSize,
        List.copyOf(matches));
  }

  private static Evidence toEvidence(OfflineJobExecution execution, int revision) {
    return new Evidence(execution.getId(), execution.getJobDefinitionId(),
        execution.getBatchId(), revision,
        execution.getAttemptNo() == null ? 0 : execution.getAttemptNo(),
        execution.getStatus(), execution.getExternalExecutionId(),
        execution.getEngineJobId(), execution.getStartTime(), execution.getEndTime(),
        execution.getSourceRecordCount(), execution.getSinkCommittedRecordCount(),
        execution.getFailedRecordCount(), execution.getErrorMessage());
  }

  /** Stable ID-seek slice. Cursor is exclusive; newly inserted IDs cannot shift previous pages. */
  public record CursorPage(long nextBeforeId, boolean moreCandidates, List<Evidence> matches) {}

  public CursorPage discoverBefore(long modelId, int versionNo, long observationId,
                                   long beforeId, int scanSize) {
    if (modelId <= 0 || versionNo <= 0 || observationId <= 0
        || beforeId <= 0 || scanSize < 1 || scanSize > 100) {
      throw new IllegalArgumentException("ODS 执行历史游标查询参数非法");
    }
    List<OfflineJobExecution> candidates = executions.scanBeforeId(beforeId, scanSize);
    List<Evidence> matches = new ArrayList<>();
    Map<Long, java.util.Optional<BatchExecution>> cache = new HashMap<>();
    long next = beforeId;
    for (OfflineJobExecution candidate : candidates) {
      if (candidate.getId() == null || candidate.getId() <= 0 || candidate.getId() >= next) {
        throw new IllegalStateException("执行游标扫描结果未按 ID 严格降序排列");
      }
      next = candidate.getId();
      if (candidate.getBatchId() == null || candidate.getBatchId() <= 0) continue;
      var batch = cache.computeIfAbsent(candidate.getBatchId(), batches::findById).orElse(null);
      if (batch == null || batch.snapshot() == null
          || !Objects.equals(batch.taskId(), candidate.getJobDefinitionId())) continue;
      JsonNode root;
      try {
        root = JSON.readTree(batch.snapshot().definitionSnapshot());
      } catch (Exception ignored) {
        continue;
      }
      if (root == null || !root.isObject()) continue;
      JsonNode ref = root.path("odsHandoff");
      if (ref.isObject() && ref.size() == 3
          && exactId(ref.path("modelId"), modelId)
          && exactId(ref.path("versionNo"), versionNo)
          && exactId(ref.path("targetObservationId"), observationId)) {
        matches.add(toEvidence(candidate, batch.snapshot().definitionRevision()));
      }
    }
    return new CursorPage(next, candidates.size() == scanSize, List.copyOf(matches));
  }

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
    return toEvidence(execution, batch.snapshot().definitionRevision());
  }

  private static boolean exactId(JsonNode value, long expected) {
    return value.isIntegralNumber() && value.canConvertToLong()
        && value.longValue() == expected;
  }
}
