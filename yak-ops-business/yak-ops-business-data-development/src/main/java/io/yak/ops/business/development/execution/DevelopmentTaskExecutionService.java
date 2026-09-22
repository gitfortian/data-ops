package io.yak.ops.business.development.execution;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.execution.model.DevelopmentTaskExecutionDetail;
import io.yak.ops.business.development.execution.model.DevelopmentTaskExecutionPage;
import io.yak.ops.business.development.execution.model.DevelopmentTaskExecutionSummary;
import io.yak.ops.business.development.repository.DevelopmentTaskExecutionRepository;
import io.yak.ops.business.development.repository.DevelopmentTaskExecutionRepository.ExecutionRecord;
import io.yak.ops.business.development.repository.DevelopmentTaskExecutionRepository.PendingExecution;
import io.yak.ops.business.development.repository.DevelopmentTaskExecutionRepository.Query;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Application boundary for durable Data Development manual execution history. */
@Service
public class DevelopmentTaskExecutionService {

  private static final TypeReference<Map<String, Object>> OUTPUT_TYPE = new TypeReference<>() {};
  private static final int MAX_PAGE_SIZE = 100;
  private static final int MAX_OUTPUT_JSON_LENGTH = 2_000_000;

  private final DevelopmentTaskExecutionRepository repository;
  private final ObjectMapper objectMapper;

  public DevelopmentTaskExecutionService(
      DevelopmentTaskExecutionRepository repository,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  /** Source-compatible entry for callers that predate persisted schema/retry metadata. */
  public long createPending(
      DevelopmentNode node,
      String taskType,
      String content,
      String configJson,
      String operatorName) {
    return createPending(node, taskType, 1, content, configJson, operatorName, null);
  }

  public long createPending(
      DevelopmentNode node,
      String taskType,
      int schemaVersion,
      String content,
      String configJson,
      String operatorName,
      Long retryOfExecutionId) {
    if (node == null) throw new IllegalArgumentException("运行节点不能为空");
    return repository.createPending(
        new PendingExecution(
            node.projectId(),
            node.id(),
            node.name(),
            normalizeUpper(taskType),
            Math.max(1, schemaVersion),
            normalizeOperator(operatorName),
            retryOfExecutionId,
            content == null ? "" : content,
            configJson == null || configJson.isBlank() ? "{}" : configJson));
  }

  public void attachRuntime(long id, String runtimeExecutionId, String status) {
    String normalized = normalizeUpper(status);
    if (!"PENDING".equals(normalized) && !"RUNNING".equals(normalized)) normalized = "RUNNING";
    repository.attachRuntime(id, runtimeExecutionId, normalized);
  }

  public void markRunning(long id, String runtimeExecutionId) {
    attachRuntime(id, runtimeExecutionId, "RUNNING");
  }

  public void updateActiveStatus(long id, String status) {
    String normalized = normalizeUpper(status);
    if (!"PENDING".equals(normalized) && !"RUNNING".equals(normalized)) return;
    repository.updateActiveStatus(id, normalized);
  }

  public void complete(
      long id,
      String status,
      long durationMs,
      String errorMessage,
      Map<String, Object> output) {
    repository.complete(
        id,
        normalizeUpper(status),
        Math.max(0L, durationMs),
        trim(errorMessage, 1000),
        serializeOutput(output));
  }

  public DevelopmentTaskExecutionPage page(
      int pageNo,
      int pageSize,
      String keyword,
      String status,
      String taskType,
      String triggerType,
      LocalDateTime startTime,
      LocalDateTime endTime) {
    int normalizedPageNo = Math.max(1, pageNo);
    int normalizedPageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, pageSize));
    DevelopmentTaskExecutionRepository.Page page = repository.page(
        new Query(
            normalizedPageNo,
            normalizedPageSize,
            trim(keyword, 200),
            optionalUpper(status),
            optionalUpper(taskType),
            optionalUpper(triggerType),
            startTime,
            endTime));
    return new DevelopmentTaskExecutionPage(
        page.records().stream().map(this::toSummary).toList(),
        page.total(),
        page.pageNo(),
        page.pageSize());
  }

  public DevelopmentTaskExecutionDetail get(long id) {
    return repository.findById(id)
        .map(this::toDetail)
        .orElseThrow(() -> new IllegalArgumentException("运行记录不存在：" + id));
  }

  Optional<DevelopmentTaskExecutionDetail> findLatestActiveByNode(long nodeId) {
    return repository.findLatestActiveByNode(nodeId).map(this::toDetail);
  }

  List<ReconciliationCandidate> listActiveForReconciliation(int limit) {
    return repository.listActiveForReconciliation(limit).stream()
        .map(record -> new ReconciliationCandidate(record.projectId(), toDetail(record)))
        .toList();
  }

  private DevelopmentTaskExecutionSummary toSummary(ExecutionRecord record) {
    return new DevelopmentTaskExecutionSummary(
        record.id(),
        record.nodeId(),
        record.taskName(),
        record.taskType(),
        record.schemaVersion(),
        record.triggerType(),
        record.runtimeExecutionId(),
        record.retryOfExecutionId(),
        record.status(),
        record.operatorName(),
        record.durationMs(),
        record.errorMessage(),
        record.startTime(),
        record.endTime());
  }

  private DevelopmentTaskExecutionDetail toDetail(ExecutionRecord record) {
    return new DevelopmentTaskExecutionDetail(
        record.id(),
        record.nodeId(),
        record.taskName(),
        record.taskType(),
        record.schemaVersion(),
        record.triggerType(),
        record.runtimeExecutionId(),
        record.retryOfExecutionId(),
        record.status(),
        record.operatorName(),
        record.durationMs(),
        record.errorMessage(),
        record.content(),
        record.configJson(),
        parseOutput(record.outputJson()),
        record.startTime(),
        record.endTime());
  }

  private String serializeOutput(Map<String, Object> output) {
    try {
      String json = objectMapper.writeValueAsString(output == null ? Map.of() : output);
      if (json.length() <= MAX_OUTPUT_JSON_LENGTH) return json;
      return objectMapper.writeValueAsString(
          Map.of("truncated", true, "message", "运行结果过大，历史记录仅保留概要"));
    } catch (Exception exception) {
      return "{}";
    }
  }

  private Map<String, Object> parseOutput(String json) {
    if (json == null || json.isBlank()) return Map.of();
    try {
      return objectMapper.readValue(json, OUTPUT_TYPE);
    } catch (Exception exception) {
      Map<String, Object> fallback = new LinkedHashMap<>();
      fallback.put("message", "历史运行结果无法解析");
      return fallback;
    }
  }

  private String optionalUpper(String value) {
    return value == null || value.isBlank() ? null : normalizeUpper(value);
  }

  private String normalizeUpper(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private String normalizeOperator(String value) {
    String normalized = value == null ? "" : value.trim();
    return normalized.isBlank() ? "unknown" : trim(normalized, 128);
  }

  private String trim(String value, int max) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.length() > max ? normalized.substring(0, max) : normalized;
  }

  record ReconciliationCandidate(Long projectId, DevelopmentTaskExecutionDetail execution) {
    ReconciliationCandidate {
      if (projectId == null || projectId <= 0L) {
        throw new IllegalArgumentException("execution reconciliation projectId must be positive");
      }
      if (execution == null) {
        throw new IllegalArgumentException("execution reconciliation detail must not be null");
      }
    }
  }
}
