package io.yak.ops.business.metric.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.framework.common.PageData;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.metric.api.MetricApi;
import io.yak.ops.business.metric.dao.mapper.MetricTagRelMapper;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.domain.MetricQualifier;
import io.yak.ops.business.metric.domain.MetricStatus;
import io.yak.ops.business.metric.domain.MetricType;
import io.yak.ops.business.metric.domain.StatPeriod;
import io.yak.ops.business.metric.exception.MetricException;
import io.yak.ops.business.metric.lineage.MetricLineageRegistrationService;
import io.yak.ops.business.metric.repository.MetricCompositionRepository;
import io.yak.ops.business.metric.repository.MetricDependencyRepository;
import io.yak.ops.business.metric.repository.MetricRepository;
import io.yak.ops.business.metric.repository.MetricVersionRepository;
import io.yak.ops.business.metric.repository.MetricUsageRepository;
import io.yak.ops.business.metric.support.CodeGenerator;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.business.metric.dao.model.MetricCompositionPO;
import io.yak.ops.business.metric.dao.model.MetricTagRelPO;
import io.yak.ops.common.enums.metric.MetricErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * Owns the metric catalog lifecycle (create/get/page/update/status/delete).
 * Single home of catalog rules; persistence stays project-scoped in the
 * repository. Reference checks before status/delete are the service's duty.
 */
@Component
@Slf4j
public class MetricCatalogService {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /** 复合指标允许的运算 token(符号项)与引用 token。 */
  private static final Set<String> SYMBOL_OPERATORS =
      Set.of("ADD", "SUB", "MUL", "DIV", "LPAREN", "RPAREN");
  private static final String REF_OPERATOR = "REF";

  private final MetricRepository repository;
  private final MetricCompositionRepository compositionRepository;
  private final MetricDependencyRepository dependencyRepository;
  private final MetricVersionRepository versionRepository;
  private final MetricTagRelMapper tagRelMapper;
  private final MetricUsageRepository usageRepository;
  private final BusinessAuditService auditService;
  private final MetricLineageRegistrationService lineageRegistrationService;
  private final MetricReferenceResolver referenceResolver;
  private final DerivedMetricAssembler derivedMetricAssembler;

  public MetricCatalogService(
      MetricRepository repository,
      MetricCompositionRepository compositionRepository,
      MetricDependencyRepository dependencyRepository,
      MetricVersionRepository versionRepository,
      MetricTagRelMapper tagRelMapper,
      MetricUsageRepository usageRepository,
      BusinessAuditService auditService,
      MetricLineageRegistrationService lineageRegistrationService,
      MetricReferenceResolver referenceResolver,
      DerivedMetricAssembler derivedMetricAssembler) {
    this.repository = repository;
    this.compositionRepository = compositionRepository;
    this.dependencyRepository = dependencyRepository;
    this.versionRepository = versionRepository;
    this.tagRelMapper = tagRelMapper;
    this.usageRepository = usageRepository;
    this.auditService = auditService;
    this.lineageRegistrationService = lineageRegistrationService;
    this.referenceResolver = referenceResolver;
    this.derivedMetricAssembler = derivedMetricAssembler;
  }

  // ── Create ──

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Metric create(MetricApi.CreateRequest request, String operator) {
    MetricType type = parseType(request.metricType());
    StatPeriod period = parseStatPeriod(request.statPeriod());
    String code = resolveCode(request.metricCode(), request.metricName());

    if (repository.existsByCode(code)) {
      throw new MetricException(MetricErrorCode.DUPLICATE_CODE, code);
    }
    validateByType(type, request.measureExpr(), request.processId(), request.modelId(),
        request.refMetricId(), request.compositions(), null);

    Metric metric = assembleDerivedIfQualified(new Metric(
        null, code, request.metricName(), request.domainId(), request.processId(), type,
        request.caliberId(), request.calRule(), request.measureExpr(),
        request.filterExpr(), request.dimModelIds(), request.refMetricId(),
        request.dimConstraint(), request.qualifiersJson(), request.modelId(),
        request.statDimensions(), period, request.unitId(),
        request.businessDesc(), request.owner(),
        MetricStatus.ENABLED, 1,
        null, null, null, null), type);

    AuditOperationHandle audit = auditService.start(
        new AuditOperationRequest(
            "METRIC_CREATE", "Create metric", "METRIC",
            null, code, "APPLICATION", Map.of("metricType", type.name())));
    try {
      Metric inserted = repository.insert(metric, operator);

      if (type == MetricType.COMPOSITE && request.compositions() != null) {
        compositionRepository.replaceCompositions(
            inserted.id(), request.compositions());
      }

      dependencyRepository.syncDependencies(inserted,
          buildDependencySpecs(inserted, request.compositions()));

      versionRepository.saveSnapshot(inserted, "创建指标", operator);

      // Bridge to global lineage graph — after commit, so the registration
      // transaction reads the dependencies this transaction is about to persist.
      scheduleAfterCommit(() -> registerLineageSafe(inserted));

      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_CREATED,
          "Metric created", Map.of("metricType", type.name()),
          "Metric created");
      return inserted;
    } catch (RuntimeException exception) {
      audit.failure("METRIC_CREATE_FAILED", exception);
      throw exception;
    }
  }

  // ── Read ──

  public Metric get(Long id) {
    return repository.findById(id)
        .orElseThrow(() -> new MetricException(MetricErrorCode.NOT_FOUND, String.valueOf(id)));
  }

  public PageData<Metric> page(int pageNo, int pageSize, Long domainId,
      String metricType, String status, String keyword, String owner, List<Long> tagIds) {
    return repository.page(pageNo, pageSize, domainId, metricType, status, keyword, owner, tagIds);
  }

  // ── Update ──

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Metric update(Long id, MetricApi.UpdateRequest request, String operator) {
    Metric existing = get(id);
    MetricType type = parseType(request.metricType());
    StatPeriod period = parseStatPeriod(request.statPeriod());

    if (request.expectedVersion() != existing.version()) {
      throw new MetricException(MetricErrorCode.VERSION_CONFLICT,
          "当前版本 " + existing.version() + "，请求版本 " + request.expectedVersion());
    }
    validateByType(type, request.measureExpr(), request.processId(), request.modelId(),
        request.refMetricId(), request.compositions(), id);

    LocalDateTime now = LocalDateTime.now();
    Metric updated = assembleDerivedIfQualified(existing.withEditable(
        request.metricName(), request.domainId(), request.processId(), type,
        request.caliberId(), request.calRule(), request.measureExpr(),
        request.filterExpr(), request.dimModelIds(), request.refMetricId(),
        request.dimConstraint(), request.qualifiersJson(), request.modelId(),
        request.statDimensions(), period, request.unitId(),
        request.businessDesc(), request.owner(), operator, now), type);

    AuditOperationHandle audit = auditService.start(
        new AuditOperationRequest(
            "METRIC_UPDATE", "Update metric", "METRIC",
            String.valueOf(id), existing.metricCode(),
            "APPLICATION", Map.of("metricType", type.name())));
    try {
      if (!repository.update(updated)) {
        throw new MetricException(MetricErrorCode.UPDATE_FAILED, String.valueOf(id));
      }

      if (type == MetricType.COMPOSITE && request.compositions() != null) {
        compositionRepository.replaceCompositions(id, request.compositions());
      } else {
        compositionRepository.replaceCompositions(id, null);
      }

      dependencyRepository.syncDependencies(updated,
          buildDependencySpecs(updated, request.compositions()));

      versionRepository.saveSnapshot(updated, "更新指标", operator);

      // Bridge to global lineage graph — after commit (see create()).
      scheduleAfterCommit(() -> registerLineageSafe(updated));

      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_UPDATED,
          "Metric updated", Map.of("version", String.valueOf(updated.version())),
          "Metric updated");
      return updated;
    } catch (RuntimeException exception) {
      audit.failure("METRIC_UPDATE_FAILED", exception);
      throw exception;
    }
  }

  // ── Status ──

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public Metric changeStatus(Long id, String status, String operator) {
    Metric existing = get(id);
    MetricStatus target = parseStatus(status);
    if (target == MetricStatus.DISABLED) {
      requireNotReferenced(existing, "停用");
    }

    AuditOperationHandle audit = auditService.start(
        new AuditOperationRequest(
            "METRIC_STATUS", "Change metric status", "METRIC",
            String.valueOf(id), existing.metricCode(),
            "APPLICATION", Map.of("status", target.name())));
    try {
      LocalDateTime now = LocalDateTime.now();
      Metric updated = existing.withStatus(target, operator, now);
      if (!repository.update(updated)) {
        throw new MetricException(MetricErrorCode.UPDATE_FAILED, String.valueOf(id));
      }
      versionRepository.saveSnapshot(updated,
          "状态变更:" + existing.status().name() + "→" + target.name(), operator);
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_UPDATED,
          "Metric status changed", Map.of("status", target.name()),
          "Metric status changed");
      return updated;
    } catch (RuntimeException exception) {
      audit.failure("METRIC_STATUS_FAILED", exception);
      throw exception;
    }
  }

  // ── Delete ──

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id) {
    Metric existing = get(id);

    AuditOperationHandle audit = auditService.start(
        new AuditOperationRequest(
            "METRIC_DELETE", "Delete metric", "METRIC",
            String.valueOf(id), existing.metricCode(),
            "APPLICATION", Map.of()));
    try {
      if (compositionRepository.countBySubMetric(id) > 0) {
        throw new MetricException(MetricErrorCode.METRIC_REFERENCED,
            "指标已被复合指标引用，无法删除");
      }
      List<Metric> referring = repository.listReferring(id);
      if (!referring.isEmpty()) {
        throw new MetricException(MetricErrorCode.METRIC_REFERENCED,
            "指标被派生指标引用（" + referring.stream().map(Metric::metricCode).limit(3).toList()
                + "），无法删除");
      }
      long usageCount = usageRepository.countByMetric(id);
      if (usageCount > 0) {
        throw new MetricException(MetricErrorCode.METRIC_REFERENCED,
            "指标已被 " + usageCount + " 处使用（报表/看板/API），无法删除");
      }
      if (!repository.deleteById(id)) {
        throw new MetricException(MetricErrorCode.DELETE_FAILED, String.valueOf(id));
      }
      compositionRepository.deleteByMetric(id);
      dependencyRepository.deleteByMetric(id);
      tagRelMapper.delete(new LambdaQueryWrapper<MetricTagRelPO>()
          .eq(MetricTagRelPO::getMetricId, id));
      usageRepository.deleteByMetric(id);
      versionRepository.deleteByMetric(id);

      // Remove from global lineage graph — after commit (see create()).
      scheduleAfterCommit(() -> lineageRegistrationService.removeMetric(id));

      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_DELETED,
          "Metric deleted", Map.of(), "Metric deleted");
    } catch (RuntimeException exception) {
      audit.failure("METRIC_DELETE_FAILED", exception);
      throw exception;
    }
  }

  // ── Counts (server-side aggregation) ──

  /**
   * Register metric lineage with fail-open semantics: lineage registration
   * failure must not block metric CRUD operations.
   */
  private void registerLineageSafe(Metric metric) {
    try {
      var deps = dependencyRepository.listByMetric(metric.id());
      lineageRegistrationService.registerMetric(metric, deps);
    } catch (RuntimeException e) {
      log.warn("Failed to register lineage for metric {} (fail-open): {}",
          metric.id(), e.getMessage());
    }
  }

  /** Run {@code action} after the surrounding transaction commits (or now, if none). */
  private static void scheduleAfterCommit(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        action.run();
      }
    });
  }

  public long count() {
    return repository.count();
  }

  public long countByDomain(Long domainId) {
    return repository.countByDomain(domainId);
  }

  public long countByType(String metricType) {
    return repository.countByType(metricType);
  }

  // ── Validation helpers ──

  private String resolveCode(String metricCode, String metricName) {
    if (StringUtils.hasText(metricCode)) {
      return metricCode.trim();
    }
    String base = CodeGenerator.fromName(metricName, "metric");
    if (base.isEmpty()) {
      throw new MetricException(MetricErrorCode.CREATE_FAILED, "无法从名称生成指标编码");
    }
    String candidate = base;
    for (int seq = 2; repository.existsByCode(candidate); seq++) {
      candidate = base + "_" + seq;
    }
    return candidate;
  }

  private MetricType parseType(String metricType) {
    if (!StringUtils.hasText(metricType)) {
      throw new MetricException(MetricErrorCode.INVALID_TYPE, "指标类型不能为空");
    }
    try {
      return MetricType.valueOf(metricType.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new MetricException(MetricErrorCode.INVALID_TYPE, metricType);
    }
  }

  private MetricStatus parseStatus(String status) {
    if (!StringUtils.hasText(status)) {
      throw new MetricException(MetricErrorCode.INVALID_STATUS, "状态不能为空");
    }
    try {
      return MetricStatus.valueOf(status.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new MetricException(MetricErrorCode.INVALID_STATUS, status);
    }
  }

  private StatPeriod parseStatPeriod(String statPeriod) {
    if (!StringUtils.hasText(statPeriod)) {
      return StatPeriod.DAY;
    }
    try {
      return StatPeriod.valueOf(statPeriod.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new MetricException(MetricErrorCode.INVALID_TYPE, "统计周期不合法: " + statPeriod);
    }
  }

  /**
   * 复合指标公式 = token 流(REQUIREMENTS 决策 1):
   * 符号项仅存 operator/expression,REF 项必须引用真实存在的指标;
   * 禁止自引用,并沿组成图做环检测。
   *
   * @param metricId 被编辑的指标 id(创建时为 null)
   */
  private void validateCompositions(Long metricId, List<MetricApi.CompositionItem> compositions) {
    if (compositions == null || compositions.isEmpty()) {
      throw new MetricException(MetricErrorCode.INVALID_COMPOSITION,
          "复合指标至少需要一个子指标");
    }
    long refCount = 0;
    List<Long> referenced = new ArrayList<>();
    for (MetricApi.CompositionItem item : compositions) {
      String operator = item.operator() == null ? "" : item.operator().trim().toUpperCase();
      if (SYMBOL_OPERATORS.contains(operator)) {
        continue;
      }
      if (!REF_OPERATOR.equals(operator)) {
        throw new MetricException(MetricErrorCode.INVALID_COMPOSITION,
            "不支持的运算方式: " + item.operator());
      }
      Long subId = item.subMetricId();
      if (subId == null || subId <= 0) {
        throw new MetricException(MetricErrorCode.INVALID_COMPOSITION,
            "REF 项必须携带子指标 ID");
      }
      if (subId.equals(metricId)) {
        throw new MetricException(MetricErrorCode.INVALID_COMPOSITION,
            "复合指标不能引用自身");
      }
      if (repository.findById(subId).isEmpty()) {
        throw new MetricException(MetricErrorCode.INVALID_COMPOSITION,
            "子指标不存在: " + subId);
      }
      refCount++;
      referenced.add(subId);
    }
    if (refCount == 0) {
      throw new MetricException(MetricErrorCode.INVALID_COMPOSITION,
          "复合指标至少需要一个子指标");
    }
    if (metricId != null) {
      detectCompositionCycle(metricId, referenced);
    }
  }

  /** 从各子指标出发沿组成图 DFS，若能回到本指标即为环。 */
  private void detectCompositionCycle(Long metricId, List<Long> referenced) {
    Set<Long> visited = new HashSet<>();
    List<Long> stack = new ArrayList<>(referenced);
    while (!stack.isEmpty()) {
      Long current = stack.remove(stack.size() - 1);
      if (current.equals(metricId)) {
        throw new MetricException(MetricErrorCode.INVALID_COMPOSITION,
            "复合指标组成存在循环引用");
      }
      if (!visited.add(current)) {
        continue;
      }
      for (MetricCompositionPO comp : compositionRepository.listByMetric(current)) {
        Long sub = comp.getSubMetricId();
        if (sub != null && sub > 0) {
          stack.add(sub);
        }
      }
    }
  }

  /** 停用/删除前的引用阻断(DOMAIN 不变量 4/5)。 */
  private void requireNotReferenced(Metric metric, String action) {
    boolean referencedByComposite = compositionRepository.countBySubMetric(metric.id()) > 0;
    List<Metric> derived = repository.listReferring(metric.id());
    if (referencedByComposite || !derived.isEmpty()) {
      throw new MetricException(MetricErrorCode.METRIC_REFERENCED,
          "指标被" + (derived.isEmpty() ? "复合指标" : "派生指标") + "引用，无法" + action
              + "，请先处理下游指标");
    }
  }

  /**
   * 按指标类型做差异化校验(REQUIREMENTS 决策 4)。
   *
   * @param metricId 被编辑指标 id(创建时 null),用于禁止派生自引用
   */
  private void validateByType(MetricType type, String measureExpr, Long processId, Long modelId,
      Long refMetricId, List<MetricApi.CompositionItem> compositions, Long metricId) {
    switch (type) {
      case ATOMIC -> {
        if (!StringUtils.hasText(measureExpr)) {
          throw new MetricException(MetricErrorCode.INVALID_COMPOSITION,
              "原子指标的度量表达式不能为空");
        }
        if (modelId == null || modelId <= 0) {
          throw new MetricException(MetricErrorCode.INVALID_COMPOSITION,
              "原子指标的数据来源(DWD)不能为空");
        }
        if (processId == null || processId <= 0) {
          throw new MetricException(MetricErrorCode.INVALID_COMPOSITION,
              "原子指标必须绑定业务过程");
        }
      }
      case DERIVED -> {
        if (refMetricId == null || refMetricId <= 0) {
          throw new MetricException(MetricErrorCode.INVALID_COMPOSITION,
              "派生指标必须引用一个原子指标");
        }
        if (refMetricId.equals(metricId)) {
          throw new MetricException(MetricErrorCode.INVALID_COMPOSITION,
              "派生指标不能引用自身");
        }
        Metric ref = repository.findById(refMetricId).orElse(null);
        if (ref == null) {
          throw new MetricException(MetricErrorCode.INVALID_COMPOSITION,
              "引用的原子指标不存在: " + refMetricId);
        }
        if (ref.metricType() != MetricType.ATOMIC) {
          throw new MetricException(MetricErrorCode.INVALID_COMPOSITION,
              "派生指标只能引用原子指标，当前引用的是 " + ref.metricType().name());
        }
      }
      case COMPOSITE -> validateCompositions(metricId, compositions);
    }
  }

  /**
   * 派生指标自动组装(02):仅当 qualifiersJson 携带有效限定时触发——
   * measureExpr/modelId 继承原子,filterExpr=原子 filter AND 限定条件,
   * 口径/单位/域/过程/计算规则在表单留空时一并继承。
   * 存量自由文本 dimConstraint(qualifiersJson 空)原样落库,不组装(兼容分支,见单测)。
   */
  private Metric assembleDerivedIfQualified(Metric draft, MetricType type) {
    if (type != MetricType.DERIVED || draft.refMetricId() == null) {
      return draft;
    }
    List<MetricQualifier> qualifiers = derivedMetricAssembler.parse(draft.qualifiersJson());
    if (qualifiers.stream().allMatch(DerivedMetricAssembler::isBlank)) {
      return draft;
    }
    Metric atomic = repository.findById(draft.refMetricId()).orElse(null);
    if (atomic == null) {
      return draft; // validateByType 已拦截,防御性留空
    }
    return derivedMetricAssembler.assemble(draft, atomic, qualifiers);
  }

  /**
   * 构建血缘登记清单:MODEL/CALIBER/UNIT/REF_METRIC/COMPOSITION，
   * 每项携带引用时刻的 code/version 快照(供展示与影响分析比对)。
   * 上游解析全部 best-effort(SPI 不可用时快照留空，不影响登记)。
   */
  private List<MetricDependencyRepository.DependencySpec> buildDependencySpecs(
      Metric metric, List<MetricApi.CompositionItem> compositions) {
    List<MetricDependencyRepository.DependencySpec> specs = new ArrayList<>();
    if (metric.modelId() != null && metric.modelId() > 0) {
      var ref = referenceResolver.modelReference(metric.modelId());
      specs.add(new MetricDependencyRepository.DependencySpec(
          "MODEL", metric.modelId(), ref.code(), ref.version()));
    }
    if (metric.caliberId() != null && metric.caliberId() > 0) {
      var ref = referenceResolver.standardReference(metric.caliberId());
      specs.add(new MetricDependencyRepository.DependencySpec(
          "CALIBER", metric.caliberId(), ref.code(), ref.version()));
    }
    if (metric.unitId() != null && metric.unitId() > 0) {
      var ref = referenceResolver.standardReference(metric.unitId());
      specs.add(new MetricDependencyRepository.DependencySpec(
          "UNIT", metric.unitId(), ref.code(), ref.version()));
    }
    if (metric.refMetricId() != null && metric.refMetricId() > 0) {
      repository.findById(metric.refMetricId()).ifPresent(ref -> specs.add(
          new MetricDependencyRepository.DependencySpec(
              "REF_METRIC", ref.id(), ref.metricCode(), ref.version())));
    }
    if (metric.metricType() == MetricType.COMPOSITE && compositions != null) {
      Set<Long> seen = new HashSet<>();
      List<Long> subIds = compositions.stream()
          .map(MetricApi.CompositionItem::subMetricId)
          .filter(id -> id != null && id > 0 && seen.add(id))
          .toList();
      referenceResolver.metricsById(subIds).forEach((subId, sub) -> specs.add(
          new MetricDependencyRepository.DependencySpec(
              "COMPOSITION", sub.id(), sub.metricCode(), sub.version())));
    }
    return specs;
  }

  /** 序列化指标快照为 JSON（版本记录用）。 */
  public static String toJsonSnapshot(Metric metric) {
    return toJsonSnapshot(metric, List.of(), Map.of());
  }

  /** 序列化指标与其版本化组成依赖；下游 MetricVersion 不读取可变组成表。 */
  public static String toJsonSnapshot(
      Metric metric,
      List<MetricCompositionPO> compositions,
      Map<Long, Integer> compositionVersions) {
    try {
      Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
      snapshot.put("metricCode", nullSafe(metric.metricCode()));
      snapshot.put("metricName", nullSafe(metric.metricName()));
      snapshot.put("domainId", metric.domainId() != null ? metric.domainId() : 0);
      snapshot.put("processId", metric.processId() != null ? metric.processId() : 0);
      snapshot.put("metricType", metric.metricType() != null ? metric.metricType().name() : "");
      snapshot.put("caliberId", metric.caliberId() != null ? metric.caliberId() : 0);
      snapshot.put("calRule", nullSafe(metric.calRule()));
      snapshot.put("measureExpr", nullSafe(metric.measureExpr()));
      snapshot.put("filterExpr", nullSafe(metric.filterExpr()));
      snapshot.put("dimModelIds", nullSafe(metric.dimModelIds()));
      snapshot.put("refMetricId", metric.refMetricId() != null ? metric.refMetricId() : 0);
      snapshot.put("refMetricVersion", metric.refMetricId() == null
          ? 0 : compositionVersions.getOrDefault(metric.refMetricId(), 0));
      snapshot.put("dimConstraint", nullSafe(metric.dimConstraint()));
      snapshot.put("qualifiersJson", nullSafe(metric.qualifiersJson()));
      snapshot.put("modelId", metric.modelId() != null ? metric.modelId() : 0);
      snapshot.put("statDimensions", nullSafe(metric.statDimensions()));
      snapshot.put("statPeriod", metric.statPeriod() != null ? metric.statPeriod().name() : "DAY");
      snapshot.put("unitId", metric.unitId() != null ? metric.unitId() : 0);
      snapshot.put("businessDesc", nullSafe(metric.businessDesc()));
      snapshot.put("owner", nullSafe(metric.owner()));
      snapshot.put("status", metric.status() != null ? metric.status().name() : "ENABLED");
      snapshot.put("compositions", (compositions == null ? List.<MetricCompositionPO>of() : compositions)
          .stream()
          .sorted(java.util.Comparator.comparing(
              row -> row.getSortOrder() == null ? Integer.MAX_VALUE : row.getSortOrder()))
          .map(row -> Map.of(
              "subMetricId", row.getSubMetricId() == null ? 0 : row.getSubMetricId(),
              "subMetricVersion", row.getSubMetricId() == null
                  ? 0 : compositionVersions.getOrDefault(row.getSubMetricId(), 0),
              "operator", nullSafe(row.getOperator()),
              "expression", nullSafe(row.getExpression()),
              "sortOrder", row.getSortOrder() == null ? 0 : row.getSortOrder()))
          .toList());
      return OBJECT_MAPPER.writeValueAsString(snapshot);
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize metric snapshot for metric {}: {}",
          metric.id(), e.getMessage());
      return "{}";
    }
  }

  private static String nullSafe(String value) {
    return value != null ? value : "";
  }
}
