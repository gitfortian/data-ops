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
import io.yak.ops.business.metric.catalog.MetricReferenceResolver;
import io.yak.ops.business.metric.repository.MetricPublicationRepository;
import io.yak.ops.business.metric.repository.MetricValidationEvidenceRepository;
import io.yak.ops.business.metric.repository.MetricVersionRepository;
import io.yak.ops.business.metric.support.MetricSnapshotDigest;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.domain.MetricValidationEvidence;
import io.yak.ops.business.metric.domain.MetricValidationEvidence.ProviderState;
import io.yak.ops.business.metric.domain.MetricValidationEvidence.Severity;
import io.yak.ops.business.metric.domain.MetricValidationEvidence.ValidationIssue;
import io.yak.ops.business.metric.domain.MetricValidationEvidence.ValidationResult;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.common.bean.po.metric.MetricVersionPO;
import io.yak.ops.common.enums.metric.MetricErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Definition Validation against an immutable {@code yak_metric_version} snapshot.
 *
 * <p>Definition facts come from the immutable version snapshot; references are resolved through
 * their owning APIs. The mutable Metric row is never used to backfill historical evidence.
 * Cross-provider dependency health and publication policy are separate gates.
 */
@Service
public class MetricDefinitionValidationService {

  public static final String PROVIDER = "metric-definition-validator/v1";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final MetricVersionRepository versionRepository;
  private final MetricValidationEvidenceRepository evidenceRepository;
  private final BusinessAuditService auditService;
  private final MetricReferenceResolver referenceResolver;
  private final MetricPublicationRepository metricLockRepository;

  public MetricDefinitionValidationService(
      MetricVersionRepository versionRepository,
      MetricValidationEvidenceRepository evidenceRepository,
      BusinessAuditService auditService,
      MetricReferenceResolver referenceResolver,
      MetricPublicationRepository metricLockRepository) {
    this.versionRepository = versionRepository;
    this.evidenceRepository = evidenceRepository;
    this.auditService = auditService;
    this.referenceResolver = referenceResolver;
    this.metricLockRepository = metricLockRepository;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public MetricValidationEvidence validate(Long metricId, int version, String operator) {
    AuditOperationHandle audit = auditService.start(new AuditOperationRequest(
        "METRIC_DEFINITION_VALIDATE",
        "Validate immutable MetricVersion definition",
        "METRIC",
        String.valueOf(metricId),
        metricId == null ? null : "Metric#" + metricId,
        "APPLICATION",
        Map.of("metricVersion", version, "provider", PROVIDER)));
    try {
      // Serialize evidence insertion with lifecycle deletion/status changes and publication. A
      // validation result must never be appended after its owning Metric was physically deleted.
      if (metricLockRepository.lockCurrentMetricVersion(metricId) == null) {
        throw new MetricException(MetricErrorCode.NOT_FOUND, String.valueOf(metricId));
      }
      MetricVersionPO subject = requireVersion(metricId, version);
      ValidationCheck check = validateSnapshot(subject.getSnapshot(), metricId, version);
      List<ValidationIssue> issues = check.issues();
      ValidationResult result = issues.stream().anyMatch(issue -> issue.severity() == Severity.BLOCKER)
          ? ValidationResult.FAILED
          : check.providerState() != ProviderState.READY
              ? ValidationResult.NOT_APPLICABLE
              : ValidationResult.PASSED;

      LocalDateTime checkedAt = LocalDateTime.now();
      MetricValidationEvidence evidence = evidenceRepository.append(new MetricValidationEvidence(
          null,
          metricId,
          subject.getId(),
          version,
          result,
          check.providerState(),
          issues,
          PROVIDER,
          MetricSnapshotDigest.sha256(subject.getSnapshot()),
          StringUtils.hasText(operator) ? operator : "system",
          checkedAt));

      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_UPDATED,
          "Metric definition validation evidence recorded",
          Map.of(
              "metricVersion", version,
              "metricVersionId", subject.getId(),
              "validationResult", result.name(),
              "providerState", check.providerState().name(),
              "provider", PROVIDER,
              "issueCount", issues.size()),
          "Metric definition validation " + result.name());
      return evidence;
    } catch (RuntimeException exception) {
      audit.failure("METRIC_DEFINITION_VALIDATION_FAILED", exception);
      throw exception;
    }
  }

  public List<MetricValidationEvidence> history(Long metricId, int version) {
    requireVersion(metricId, version);
    return evidenceRepository.listByVersion(metricId, version);
  }

  public MetricValidationEvidence latestReady(Long metricId, int version) {
    requireVersion(metricId, version);
    return evidenceRepository.findLatestReady(metricId, version);
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

  private ValidationCheck validateSnapshot(String snapshot, Long metricId, int version) {
    List<ValidationIssue> issues = new ArrayList<>();
    ProviderStateHolder providerState = new ProviderStateHolder();
    JsonNode root;
    try {
      root = OBJECT_MAPPER.readTree(snapshot);
    } catch (JsonProcessingException | RuntimeException exception) {
      issues.add(blocker("SNAPSHOT_INVALID", "snapshot", "版本快照不是合法 JSON，无法执行定义校验"));
      return new ValidationCheck(List.copyOf(issues), providerState.state);
    }
    if (root == null || !root.isObject()) {
      issues.add(blocker("SNAPSHOT_INVALID", "snapshot", "版本快照缺失，无法执行定义校验"));
      return new ValidationCheck(List.copyOf(issues), providerState.state);
    }

    requireText(root, "metricCode", "METRIC_CODE_REQUIRED", "指标编码不能为空", issues);
    requireText(root, "metricName", "METRIC_NAME_REQUIRED", "指标名称不能为空", issues);
    requireText(root, "metricType", "METRIC_TYPE_REQUIRED", "指标类型不能为空", issues);
    long domainId = number(root, "domainId");
    requirePositive(root, "domainId", "DOMAIN_REQUIRED", "必须绑定业务域", issues);
    requireText(root, "owner", "OWNER_REQUIRED", "必须指定指标负责人", issues);
    requireText(root, "businessDesc", "BUSINESS_DESC_REQUIRED", "必须填写业务口径说明", issues);
    long caliberId = number(root, "caliberId");
    long unitId = number(root, "unitId");
    requirePositive(root, "caliberId", "CALIBER_REQUIRED", "必须绑定口径标准", issues);
    requirePositive(root, "unitId", "UNIT_REQUIRED", "必须绑定单位标准", issues);
    if (domainId > 0) checkResolution(
        "DOMAIN", "domainId", referenceResolver.domainResolution(domainId), providerState, issues);
    if (caliberId > 0) checkResolution(
        "CALIBER", "caliberId",
        referenceResolver.standardReferenceResolution(caliberId, StandardKind.CALIBER), providerState, issues);
    if (unitId > 0) checkResolution(
        "UNIT", "unitId",
        referenceResolver.standardReferenceResolution(unitId, StandardKind.UNIT), providerState, issues);

    String metricType = text(root, "metricType");
    if ("ATOMIC".equals(metricType)) {
      long processId = number(root, "processId");
      long modelId = number(root, "modelId");
      requirePositive(root, "processId", "PROCESS_REQUIRED", "原子指标必须绑定业务过程", issues);
      requirePositive(root, "modelId", "MODEL_REQUIRED", "原子指标必须绑定来源模型", issues);
      if (processId > 0) checkResolution(
          "PROCESS", "processId", referenceResolver.processResolution(processId, domainId), providerState, issues);
      if (modelId > 0) checkResolution(
          "MODEL", "modelId", referenceResolver.modelReferenceResolution(modelId), providerState, issues);
      requireText(root, "measureExpr", "MEASURE_EXPR_REQUIRED", "原子指标必须定义度量表达式", issues);
    } else if ("DERIVED".equals(metricType)) {
      long refMetricId = number(root, "refMetricId");
      requirePositive(root, "refMetricId", "REF_METRIC_REQUIRED", "派生指标必须引用原子指标", issues);
      if (refMetricId > 0) {
        Metric upstream = referenceResolver.metricsById(List.of(refMetricId)).get(refMetricId);
        if (upstream == null) issues.add(blocker("REF_METRIC_REMOVED", "refMetricId", "引用的上游指标已不存在"));
        else if (upstream.metricType() != io.yak.ops.business.metric.domain.MetricType.ATOMIC) {
          issues.add(blocker("REF_METRIC_TYPE_INVALID", "refMetricId", "派生指标必须引用原子指标"));
        }
        int refVersion = (int) number(root, "refMetricVersion");
        if (refVersion <= 0 || versionRepository.findByMetricAndVersion(refMetricId, refVersion) == null) {
          issues.add(blocker("REF_METRIC_VERSION_MISSING", "refMetricVersion", "引用的原子指标版本快照不存在"));
        }
      }
      requireText(root, "measureExpr", "MEASURE_EXPR_REQUIRED", "派生指标必须具有可执行度量表达式", issues);
    } else if ("COMPOSITE".equals(metricType)) {
      validateComposition(root, metricId, version, issues);
    } else if (StringUtils.hasText(metricType)) {
      issues.add(blocker("METRIC_TYPE_INVALID", "metricType", "未知指标类型: " + metricType));
    }

    if (providerState.state != ProviderState.READY) {
      issues.add(new ValidationIssue(
          "REFERENCE_PROVIDER_" + providerState.state.name(),
          "references",
          providerState.state == ProviderState.FORBIDDEN
              ? "当前用户无权解析一个或多个引用对象"
              : "一个或多个 owning provider 暂不可用，无法完成引用校验",
          Severity.WARNING));
    }
    return new ValidationCheck(List.copyOf(issues), providerState.state);
  }

  private void validateComposition(JsonNode root, Long metricId, int version, List<ValidationIssue> issues) {
    JsonNode compositions = root.path("compositions");
    if (!compositions.isArray() || compositions.isEmpty()) {
      issues.add(blocker("COMPOSITION_REQUIRED", "compositions", "复合指标必须固化至少一个操作数"));
      return;
    }
    int depth = 0;
    int refs = 0;
    for (JsonNode token : compositions) {
      String operator = text(token, "operator").toUpperCase(java.util.Locale.ROOT);
      if ("LPAREN".equals(operator)) depth++;
      else if ("RPAREN".equals(operator)) {
        depth--;
        if (depth < 0) issues.add(blocker("COMPOSITION_TOKEN_INVALID", "compositions", "右括号没有匹配的左括号"));
      } else if ("REF".equals(operator)) {
        refs++;
        long subMetricId = number(token, "subMetricId");
        int subVersion = (int) number(token, "subMetricVersion");
        if (subMetricId <= 0 || subVersion <= 0) {
          issues.add(blocker("COMPOSITION_VERSION_REQUIRED", "compositions", "每个引用操作数必须绑定精确 MetricVersion"));
        } else if (subMetricId == metricId) {
          issues.add(blocker("COMPOSITION_CYCLE", "compositions", "复合指标不能引用自身"));
        } else if (versionRepository.findByMetricAndVersion(subMetricId, subVersion) == null) {
          issues.add(blocker("COMPOSITION_VERSION_MISSING", "compositions", "操作数 MetricVersion 不存在"));
        }
      } else if (!java.util.Set.of("ADD", "SUB", "MUL", "DIV").contains(operator)) {
        issues.add(blocker("COMPOSITION_TOKEN_INVALID", "compositions", "不支持的复合指标 token: " + operator));
      }
    }
    if (depth != 0) issues.add(blocker("COMPOSITION_TOKEN_INVALID", "compositions", "复合指标括号不匹配"));
    if (refs == 0) issues.add(blocker("COMPOSITION_OPERAND_REQUIRED", "compositions", "复合指标必须引用至少一个指标"));
    validateCompositionGraph(metricId, version, new java.util.HashSet<>(), new java.util.HashSet<>(), issues, 0);
  }

  private void validateCompositionGraph(
      Long metricId, int version, java.util.Set<String> visiting, java.util.Set<String> complete,
      List<ValidationIssue> issues, int depth) {
    String key = metricId + ":" + version;
    if (complete.contains(key)) return;
    if (!visiting.add(key)) {
      issues.add(blocker("COMPOSITION_CYCLE", "compositions", "版本化复合指标依赖存在循环"));
      return;
    }
    if (depth > 128) {
      issues.add(blocker("COMPOSITION_DEPTH_EXCEEDED", "compositions", "复合指标依赖超过校验深度"));
      return;
    }
    MetricVersionPO snapshot = versionRepository.findByMetricAndVersion(metricId, version);
    if (snapshot != null) {
      try {
        JsonNode definition = OBJECT_MAPPER.readTree(snapshot.getSnapshot());
        JsonNode children = definition.path("compositions");
        if ("COMPOSITE".equals(text(definition, "metricType")) && children.isArray()) {
          for (JsonNode token : children) {
            if (!"REF".equalsIgnoreCase(text(token, "operator"))) continue;
            long childId = number(token, "subMetricId");
            int childVersion = (int) number(token, "subMetricVersion");
            if (childId > 0 && childVersion > 0) {
              MetricVersionPO child = versionRepository.findByMetricAndVersion(childId, childVersion);
              if (child == null && depth > 0) {
                issues.add(blocker(
                    "COMPOSITION_VERSION_MISSING", "compositions", "操作数 MetricVersion 不存在"));
              } else if (child != null) {
                validateCompositionGraph(childId, childVersion, visiting, complete, issues, depth + 1);
              }
            }
          }
        }
      } catch (JsonProcessingException exception) {
        issues.add(blocker("COMPOSITION_SNAPSHOT_INVALID", "compositions", "上游复合指标快照不是合法 JSON"));
      }
    }
    visiting.remove(key);
    complete.add(key);
  }

  private static void checkResolution(
      String kind, String field, MetricReferenceResolver.ReferenceResolution resolution,
      ProviderStateHolder providerState, List<ValidationIssue> issues) {
    switch (resolution.status()) {
      case READY -> { }
      case REMOVED -> issues.add(blocker(kind + "_REMOVED", field, "引用的" + kind + "对象不存在或类型不匹配"));
      case UNAVAILABLE -> providerState.merge(ProviderState.UNAVAILABLE);
      case FORBIDDEN -> providerState.merge(ProviderState.FORBIDDEN);
    }
  }

  private record ValidationCheck(List<ValidationIssue> issues, ProviderState providerState) {}

  private static final class ProviderStateHolder {
    private ProviderState state = ProviderState.READY;
    private void merge(ProviderState next) {
      if (next == ProviderState.FORBIDDEN || state == ProviderState.READY) state = next;
    }
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

  private static long number(JsonNode root, String field) {
    JsonNode node = root == null ? null : root.get(field);
    return node != null && node.canConvertToLong() ? node.asLong() : 0L;
  }

  private static ValidationIssue blocker(String code, String field, String message) {
    return new ValidationIssue(code, field, message, Severity.BLOCKER);
  }

}
