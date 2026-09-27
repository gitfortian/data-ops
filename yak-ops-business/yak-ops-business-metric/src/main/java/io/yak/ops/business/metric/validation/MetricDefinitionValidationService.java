package io.yak.ops.business.metric.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.metric.exception.MetricException;
import io.yak.ops.business.metric.repository.MetricValidationEvidenceRepository;
import io.yak.ops.business.metric.repository.MetricVersionRepository;
import io.yak.ops.common.bean.po.metric.MetricValidationEvidencePO;
import io.yak.ops.common.bean.po.metric.MetricVersionPO;
import io.yak.ops.common.enums.metric.MetricErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Definition Validation against an immutable {@code yak_metric_version} snapshot.
 *
 * <p>This first provider deliberately evaluates only facts contained in the version
 * snapshot. It never reads the mutable Metric row to "complete" historical evidence.
 * Cross-provider dependency health and publication policy are separate gates.
 */
@Service
public class MetricDefinitionValidationService {

  public static final String PROVIDER = "metric-definition-validator/v1";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final MetricVersionRepository versionRepository;
  private final MetricValidationEvidenceRepository evidenceRepository;
  private final BusinessAuditService auditService;

  public MetricDefinitionValidationService(
      MetricVersionRepository versionRepository,
      MetricValidationEvidenceRepository evidenceRepository,
      BusinessAuditService auditService) {
    this.versionRepository = versionRepository;
    this.evidenceRepository = evidenceRepository;
    this.auditService = auditService;
  }

  public enum ValidationResult {
    READY,
    BLOCKED
  }

  public enum Severity {
    BLOCKER,
    WARNING
  }

  public record ValidationIssue(
      String code,
      String field,
      String message,
      Severity severity) {
  }

  public record ValidationEvidence(
      Long evidenceId,
      Long metricId,
      Long metricVersionId,
      int metricVersion,
      ValidationResult result,
      List<ValidationIssue> issues,
      String provider,
      String snapshotDigest,
      String checkedBy,
      LocalDateTime checkedAt) {
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public ValidationEvidence validate(Long metricId, int version, String operator) {
    AuditOperationHandle audit = auditService.start(new AuditOperationRequest(
        "METRIC_DEFINITION_VALIDATE",
        "Validate immutable MetricVersion definition",
        "METRIC",
        String.valueOf(metricId),
        metricId == null ? null : "Metric#" + metricId,
        "APPLICATION",
        Map.of("metricVersion", version, "provider", PROVIDER)));
    try {
      MetricVersionPO subject = requireVersion(metricId, version);
      List<ValidationIssue> issues = validateSnapshot(subject.getSnapshot());
      ValidationResult result = issues.stream().anyMatch(issue -> issue.severity() == Severity.BLOCKER)
          ? ValidationResult.BLOCKED
          : ValidationResult.READY;

      LocalDateTime checkedAt = LocalDateTime.now();
      MetricValidationEvidencePO po = new MetricValidationEvidencePO();
      po.setMetricId(metricId);
      po.setMetricVersionId(subject.getId());
      po.setMetricVersion(version);
      po.setResult(result.name());
      po.setIssuesJson(writeIssues(issues));
      po.setProvider(PROVIDER);
      po.setSnapshotDigest(sha256(subject.getSnapshot()));
      po.setCheckedBy(StringUtils.hasText(operator) ? operator : "system");
      po.setCheckedAt(checkedAt);
      evidenceRepository.append(po);

      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_UPDATED,
          "Metric definition validation evidence recorded",
          Map.of(
              "metricVersion", version,
              "metricVersionId", subject.getId(),
              "validationResult", result.name(),
              "provider", PROVIDER,
              "issueCount", issues.size()),
          "Metric definition validation " + result.name());
      return toEvidence(po, issues);
    } catch (RuntimeException exception) {
      audit.failure("METRIC_DEFINITION_VALIDATION_FAILED", exception);
      throw exception;
    }
  }

  public List<ValidationEvidence> history(Long metricId, int version) {
    requireVersion(metricId, version);
    return evidenceRepository.listByVersion(metricId, version).stream()
        .map(this::toEvidence)
        .toList();
  }

  public ValidationEvidence latestReady(Long metricId, int version) {
    requireVersion(metricId, version);
    MetricValidationEvidencePO po = evidenceRepository.findLatestReady(metricId, version);
    return po == null ? null : toEvidence(po);
  }

  private MetricVersionPO requireVersion(Long metricId, int version) {
    if (metricId == null || metricId <= 0 || version <= 0) {
      throw new MetricException(MetricErrorCode.NOT_FOUND, "指标版本不存在");
    }
    MetricVersionPO subject = versionRepository.findByMetricAndVersion(metricId, version);
    if (subject == null) {
      throw new MetricException(MetricErrorCode.NOT_FOUND,
          "指标 " + metricId + " 的版本 v" + version + " 不存在");
    }
    return subject;
  }

  private List<ValidationIssue> validateSnapshot(String snapshot) {
    List<ValidationIssue> issues = new ArrayList<>();
    JsonNode root;
    try {
      root = OBJECT_MAPPER.readTree(snapshot);
    } catch (JsonProcessingException | RuntimeException exception) {
      issues.add(blocker("SNAPSHOT_INVALID", "snapshot", "版本快照不是合法 JSON，无法执行定义校验"));
      return List.copyOf(issues);
    }
    if (root == null || !root.isObject()) {
      issues.add(blocker("SNAPSHOT_INVALID", "snapshot", "版本快照缺失，无法执行定义校验"));
      return List.copyOf(issues);
    }

    requireText(root, "metricCode", "METRIC_CODE_REQUIRED", "指标编码不能为空", issues);
    requireText(root, "metricName", "METRIC_NAME_REQUIRED", "指标名称不能为空", issues);
    requireText(root, "metricType", "METRIC_TYPE_REQUIRED", "指标类型不能为空", issues);
    requirePositive(root, "domainId", "DOMAIN_REQUIRED", "必须绑定业务域", issues);
    requireText(root, "owner", "OWNER_REQUIRED", "必须指定指标负责人", issues);
    requireText(root, "businessDesc", "BUSINESS_DESC_REQUIRED", "必须填写业务口径说明", issues);
    requirePositive(root, "caliberId", "CALIBER_REQUIRED", "必须绑定口径标准", issues);
    requirePositive(root, "unitId", "UNIT_REQUIRED", "必须绑定单位标准", issues);

    String metricType = text(root, "metricType");
    if ("ATOMIC".equals(metricType)) {
      requirePositive(root, "processId", "PROCESS_REQUIRED", "原子指标必须绑定业务过程", issues);
      requirePositive(root, "modelId", "MODEL_REQUIRED", "原子指标必须绑定来源模型", issues);
      requireText(root, "measureExpr", "MEASURE_EXPR_REQUIRED", "原子指标必须定义度量表达式", issues);
    } else if ("DERIVED".equals(metricType)) {
      requirePositive(root, "refMetricId", "REF_METRIC_REQUIRED", "派生指标必须引用原子指标", issues);
      requireText(root, "measureExpr", "MEASURE_EXPR_REQUIRED", "派生指标必须具有可执行度量表达式", issues);
    } else if ("COMPOSITE".equals(metricType)) {
      issues.add(blocker(
          "COMPOSITION_VERSION_EVIDENCE_REQUIRED",
          "compositions",
          "当前版本快照未固化复合指标操作数，不能证明该版本的可执行定义"));
    } else if (StringUtils.hasText(metricType)) {
      issues.add(blocker("METRIC_TYPE_INVALID", "metricType", "未知指标类型: " + metricType));
    }

    return List.copyOf(issues);
  }

  private static void requireText(
      JsonNode root, String field, String code, String message, List<ValidationIssue> issues) {
    if (!StringUtils.hasText(text(root, field))) {
      issues.add(blocker(code, field, message));
    }
  }

  private static void requirePositive(
      JsonNode root, String field, String code, String message, List<ValidationIssue> issues) {
    JsonNode node = root.get(field);
    if (node == null || !node.canConvertToLong() || node.asLong() <= 0) {
      issues.add(blocker(code, field, message));
    }
  }

  private static String text(JsonNode root, String field) {
    JsonNode node = root.get(field);
    return node == null || node.isNull() ? "" : node.asText("").trim();
  }

  private static ValidationIssue blocker(String code, String field, String message) {
    return new ValidationIssue(code, field, message, Severity.BLOCKER);
  }

  private static String writeIssues(List<ValidationIssue> issues) {
    try {
      return OBJECT_MAPPER.writeValueAsString(issues);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize metric validation issues", exception);
    }
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private ValidationEvidence toEvidence(MetricValidationEvidencePO po) {
    return toEvidence(po, readIssues(po.getIssuesJson()));
  }

  private static ValidationEvidence toEvidence(
      MetricValidationEvidencePO po, List<ValidationIssue> issues) {
    return new ValidationEvidence(
        po.getId(),
        po.getMetricId(),
        po.getMetricVersionId(),
        po.getMetricVersion(),
        ValidationResult.valueOf(po.getResult()),
        List.copyOf(issues),
        po.getProvider(),
        po.getSnapshotDigest(),
        po.getCheckedBy(),
        po.getCheckedAt());
  }

  private static List<ValidationIssue> readIssues(String issuesJson) {
    if (!StringUtils.hasText(issuesJson)) {
      return List.of();
    }
    try {
      return OBJECT_MAPPER.readerForListOf(ValidationIssue.class).readValue(issuesJson);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Stored metric validation issues are invalid", exception);
    }
  }
}
