package io.yak.ops.business.mdm.application;

import io.yak.framework.common.PageData;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.mdm.dao.MdmDedupKeyRow;
import io.yak.ops.business.mdm.domain.attribute.MdmAttribute;
import io.yak.ops.business.mdm.domain.attribute.MdmAttributeType;
import io.yak.ops.business.mdm.domain.clean.CleanJson;
import io.yak.ops.business.mdm.domain.clean.CompleteExpr;
import io.yak.ops.business.mdm.domain.clean.MdmCleanRule;
import io.yak.ops.business.mdm.domain.clean.MdmCleanRuleExpr;
import io.yak.ops.business.mdm.domain.clean.MdmCleanRuleType;
import io.yak.ops.business.mdm.domain.clean.MdmDedupIgnore;
import io.yak.ops.business.mdm.domain.clean.MdmMatchField;
import io.yak.ops.business.mdm.domain.clean.MdmMatchType;
import io.yak.ops.business.mdm.domain.clean.MdmMergeLog;
import io.yak.ops.business.mdm.domain.clean.StandardizeExpr;
import io.yak.ops.business.mdm.domain.record.MdmRecord;
import io.yak.ops.business.mdm.domain.record.MdmRecordStatus;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmAttributeRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmCleanRuleRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmDedupIgnoreRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmMergeLogRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmRecordRepository;
import io.yak.ops.business.mdm.notification.MdmNotifier;
import io.yak.ops.business.mdm.processing.DedupSql;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.common.util.AuditDiffs;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Owns master data cleansing (ticket 56/57): dedup rule CRUD, server-side dedup
 * discovery (DB-level GROUP BY aggregation, home-overview-contract: no unbounded
 * list() then in-memory stats), and explicit merge with preview. Standardize and
 * complete rule CRUD + preview/apply (ticket 57, minimal design: simple value
 * mapping and default fill, no separate execution engine). Quality checks
 * (completeness/format) belong to data-quality and are reached by navigation.
 */
@Component
public class MdmCleanService {

  private static final int GROUP_MEMBER_LIMIT = 50;
  /** 预览返回的变更明细上限（`affectedCount` 仍是全量口径）。 */
  public static final int MAX_PREVIEW_ITEMS = 50;
  private static final int MAX_RULE_NAME_LENGTH = 128;
  private static final int MAX_MATCH_KEY_LENGTH = 512;
  private static final int MAX_IGNORE_REASON_LENGTH = 255;

  private final MdmCleanRuleRepository ruleRepository;
  private final MdmMergeLogRepository mergeLogRepository;
  private final MdmDedupIgnoreRepository dedupIgnoreRepository;
  private final MdmRecordRepository recordRepository;
  private final MdmEntityService entityService;
  private final MdmAttributeRepository attributeRepository;
  private final BusinessAuditService auditService;
  private final MdmNotifier notifier;

  public MdmCleanService(
      MdmCleanRuleRepository ruleRepository,
      MdmMergeLogRepository mergeLogRepository,
      MdmDedupIgnoreRepository dedupIgnoreRepository,
      MdmRecordRepository recordRepository,
      MdmEntityService entityService,
      MdmAttributeRepository attributeRepository,
      BusinessAuditService auditService,
      MdmNotifier notifier) {
    this.ruleRepository = ruleRepository;
    this.mergeLogRepository = mergeLogRepository;
    this.dedupIgnoreRepository = dedupIgnoreRepository;
    this.recordRepository = recordRepository;
    this.entityService = entityService;
    this.attributeRepository = attributeRepository;
    this.auditService = auditService;
    this.notifier = notifier;
  }

  // ==== 去重规则 CRUD ====

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public MdmCleanRule create(
      Long entityId, String ruleName, MdmCleanRuleExpr expr, Integer sortOrder, String operator) {
    entityService.get(entityId);
    if (!StringUtils.hasText(ruleName) || ruleName.trim().length() > MAX_RULE_NAME_LENGTH) {
      throw new MdmException(
          MdmErrorCode.INVALID_CLEAN_RULE, "规则名称必填且不超过 128 字符");
    }
    if (expr == null || expr.fields() == null || expr.fields().isEmpty()) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "至少配置一个匹配字段");
    }
    validateAttrCodes(entityId, expr.fields());
    return doCreate(entityId, MdmCleanRuleType.DEDUP, ruleName,
        CleanJson.writeExpr(expr), sortOrder, operator);
  }

  /** 通用规则创建:支持 DEDUP/STANDARDIZE/COMPLETE 三种类型(ticket 57)。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public MdmCleanRule createGeneric(
      Long entityId,
      MdmCleanRuleType ruleType,
      String ruleName,
      String ruleExprJson,
      Integer sortOrder,
      String operator) {
    entityService.get(entityId);
    if (ruleType == null) {
      ruleType = MdmCleanRuleType.DEDUP;
    }
    validateGenericRule(entityId, ruleType, ruleName, ruleExprJson);
    return doCreate(entityId, ruleType, ruleName, ruleExprJson, sortOrder, operator);
  }

  private MdmCleanRule doCreate(
      Long entityId, MdmCleanRuleType ruleType, String ruleName,
      String ruleExprJson, Integer sortOrder, String operator) {
    if (ruleRepository.existsByName(entityId, ruleName, null)) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "规则名称已存在: " + ruleName);
    }
    String eventType = "MDM_CLEAN_RULE_CREATE_" + ruleType.name();
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                eventType,
                "Create " + ruleType.name().toLowerCase(Locale.ROOT) + " cleansing rule",
                "MDM_CLEAN_RULE",
                null,
                ruleName,
                "APPLICATION",
                Map.of(
                    "entityId", String.valueOf(entityId),
                    "ruleType", ruleType.name())));
    try {
      MdmCleanRule inserted =
          ruleRepository.insert(
              new MdmCleanRule(
                  null, entityId, ruleType, ruleName, ruleExprJson, true,
                  sortOrder == null ? 0 : sortOrder, operator, null, null),
              operator);
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_CREATED, "Cleansing rule created",
          Map.of(), "Cleansing rule created");
      return inserted;
    } catch (RuntimeException exception) {
      audit.failure("MDM_CLEAN_RULE_CREATE_FAILED", exception);
      throw exception;
    }
  }

  private void validateAttrCodes(Long entityId, List<MdmMatchField> fields) {
    Set<String> validCodes =
        attributeRepository.listByEntity(entityId).stream()
            .map(MdmAttribute::code)
            .collect(Collectors.toSet());
    String condition = null;
    Set<String> seen = new HashSet<>();
    for (MdmMatchField field : fields) {
      if (field == null || field.attrCode() == null || field.attrCode().isBlank()) {
        throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "匹配字段编码不能为空");
      }
      if (!DedupSql.isValidAttrCode(field.attrCode())) {
        throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "非法属性编码: " + field.attrCode());
      }
      if (!validCodes.contains(field.attrCode())) {
        throw new MdmException(
            MdmErrorCode.INVALID_CLEAN_RULE, "匹配字段必须是实体已定义属性: " + field.attrCode());
      }
      if (!seen.add(field.attrCode())) {
        throw new MdmException(
            MdmErrorCode.INVALID_CLEAN_RULE, "同一字段仅允许出现一次: " + field.attrCode());
      }
      if (field.matchType() == null) {
        throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "匹配方式不能为空");
      }
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void update(Long id, String ruleName, MdmCleanRuleExpr expr, Integer sortOrder) {
    MdmCleanRule existing = get(id);
    if (!StringUtils.hasText(ruleName) || ruleName.trim().length() > MAX_RULE_NAME_LENGTH) {
      throw new MdmException(
          MdmErrorCode.INVALID_CLEAN_RULE, "规则名称必填且不超过 128 字符");
    }
    if (expr == null || expr.fields() == null || expr.fields().isEmpty()) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "至少配置一个匹配字段");
    }
    validateAttrCodes(existing.entityId(), expr.fields());
    doUpdate(id, existing, ruleName, CleanJson.writeExpr(expr), sortOrder);
  }

  /** 通用规则更新:名称 + 表达式 + 排序(编码/实体/类型不可改)。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void updateGeneric(Long id, String ruleName, String ruleExprJson, Integer sortOrder) {
    MdmCleanRule existing = get(id);
    validateGenericRule(existing.entityId(), existing.ruleType(), ruleName, ruleExprJson);
    doUpdate(id, existing, ruleName, ruleExprJson, sortOrder);
  }

  private void doUpdate(
      Long id, MdmCleanRule existing, String ruleName,
      String ruleExprJson, Integer sortOrder) {
    if (ruleRepository.existsByName(existing.entityId(), ruleName, id)) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "规则名称已存在: " + ruleName);
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_CLEAN_RULE_UPDATE",
                "Update cleansing rule",
                "MDM_CLEAN_RULE",
                String.valueOf(id),
                ruleName,
                "APPLICATION",
                Map.of("entityId", String.valueOf(existing.entityId()))));
    try {
      MdmCleanRule updated = existing.withEditable(
          ruleName, ruleExprJson,
          sortOrder == null ? existing.sortOrder() : sortOrder);
      if (!ruleRepository.update(updated)) {
        throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "规则更新失败");
      }
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_UPDATED,
          "Cleansing rule updated",
          AuditDiffs.diff(ruleSnapshot(existing), ruleSnapshot(updated)),
          "Cleansing rule updated");
    } catch (RuntimeException exception) {
      audit.failure("MDM_CLEAN_RULE_UPDATE_FAILED", exception);
      throw exception;
    }
  }

  private static Map<String, Object> ruleSnapshot(MdmCleanRule value) {
    Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
    snapshot.put("ruleName", value.ruleName());
    snapshot.put("ruleType", value.ruleType() == null ? null : value.ruleType().name());
    snapshot.put("ruleExpr", value.ruleExpr());
    snapshot.put("sortOrder", value.sortOrder());
    snapshot.put("enabled", value.enabled());
    return snapshot;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void setEnabled(Long id, boolean enabled) {
    MdmCleanRule existing = get(id);
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_CLEAN_RULE_ENABLED",
                "Toggle cleansing rule",
                "MDM_CLEAN_RULE",
                String.valueOf(id),
                existing.ruleName(),
                "APPLICATION",
                Map.of("enabled", String.valueOf(enabled))));
    try {
      if (!ruleRepository.update(existing.withEnabled(enabled))) {
        throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "规则状态更新失败");
      }
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_UPDATED,
          "Cleansing rule toggled",
          Map.of(),
          "Cleansing rule toggled");
    } catch (RuntimeException exception) {
      audit.failure("MDM_CLEAN_RULE_ENABLED_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id) {
    MdmCleanRule existing = get(id);
    // Merge logs are append-only evidence and keep the rule ID as their provenance.
    if (isReferenced(existing.id())) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "规则已被合并日志引用,无法删除");
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_CLEAN_RULE_DELETE",
                "Delete cleansing rule",
                "MDM_CLEAN_RULE",
                String.valueOf(id),
                existing.ruleName(),
                "APPLICATION",
                Map.of("entityId", String.valueOf(existing.entityId()))));
    try {
      if (!ruleRepository.deleteById(id)) {
        throw new MdmException(MdmErrorCode.DELETE_FAILED, String.valueOf(id));
      }
      // 忽略键归属到规则,规则没了就再也查不到/撤不掉,随规则一起清账。
      dedupIgnoreRepository.deleteByRule(id);
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_DELETED,
          "Cleansing rule deleted",
          Map.of(),
          "Cleansing rule deleted");
    } catch (RuntimeException exception) {
      audit.failure("MDM_CLEAN_RULE_DELETE_FAILED", exception);
      throw exception;
    }
  }

  /** ruleType 为空取全部类型；非法值按业务错误回，不让前端拼错参数静默看到全集。 */
  public List<MdmCleanRule> listRules(Long entityId, String ruleType) {
    entityService.get(entityId);
    return ruleRepository.listByEntity(entityId, parseRuleType(ruleType));
  }

  private static MdmCleanRuleType parseRuleType(String ruleType) {
    if (ruleType == null || ruleType.isBlank()) {
      return null;
    }
    try {
      return MdmCleanRuleType.valueOf(ruleType.trim().toUpperCase());
    } catch (IllegalArgumentException exception) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, ruleType);
    }
  }

  public MdmCleanRule get(Long id) {
    return ruleRepository
        .findById(id)
        .orElseThrow(
            () -> new MdmException(MdmErrorCode.CLEAN_RULE_NOT_FOUND, String.valueOf(id)));
  }

  /** 停用即不参与任何执行:规则表的「启用」开关必须真的能关掉发现与清洗,否则开关只是装饰。 */
  private MdmCleanRule requireEnabled(MdmCleanRule rule) {
    if (!rule.enabled()) {
      throw new MdmException(
          MdmErrorCode.INVALID_CLEAN_RULE, "规则「" + rule.ruleName() + "」已停用，请先启用后再执行");
    }
    return rule;
  }

  // ==== 去重发现(服务端聚合) ====

  /** 按规则执行去重发现,DB 侧 GROUP BY 聚合重复键,分页返回重复组。 */
  public PageData<DedupGroup> findDuplicates(
      Long entityId, Long ruleId, int pageNo, int pageSize) {
    MdmCleanRule rule = requireEnabled(get(ruleId));
    if (!entityId.equals(rule.entityId())) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "规则不属于该实体");
    }
    MdmCleanRuleExpr expr;
    try {
      expr = CleanJson.parseExpr(rule.ruleExpr());
    } catch (IllegalArgumentException exception) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, exception.getMessage());
    }
    Map<String, String> attrNames =
        attributeRepository.listByEntity(entityId).stream()
            .collect(Collectors.toMap(MdmAttribute::code, MdmAttribute::name));
    int safePageNo = Math.max(1, pageNo);
    int safePageSize = Math.max(1, pageSize);
    return expr.isAnd()
        ? findDuplicatesAnd(entityId, rule.id(), expr, attrNames, safePageNo, safePageSize)
        : findDuplicatesOr(entityId, rule.id(), expr, attrNames, safePageNo, safePageSize);
  }

  private PageData<DedupGroup> findDuplicatesAnd(
      Long entityId,
      Long ruleId,
      MdmCleanRuleExpr expr,
      Map<String, String> attrNames,
      int pageNo,
      int pageSize) {
    String keyExpr = DedupSql.combinedKeyExpr(expr.fields());
    String valueCondition = DedupSql.combinedValueCondition(expr.fields());
    List<MdmDedupKeyRow> hits =
        new ArrayList<>(
            recordRepository.countDedupKeys(entityId, ruleId, keyExpr, valueCondition));
    hits.sort(
        Comparator.comparingLong(MdmDedupKeyRow::matchCount)
            .reversed()
            .thenComparing(MdmDedupKeyRow::matchKey));
    List<DedupGroup> groups = new ArrayList<>();
    for (MdmDedupKeyRow hit : slice(hits, pageNo, pageSize)) {
      List<MdmRecord> members =
          recordRepository.listByDedupKey(entityId, keyExpr, hit.matchKey(), GROUP_MEMBER_LIMIT);
      groups.add(buildGroup(expr, attrNames, members, hit.matchCount(), hit.matchKey()));
    }
    return toPageData(groups, hits.size(), pageNo, pageSize);
  }

  private PageData<DedupGroup> findDuplicatesOr(
      Long entityId,
      Long ruleId,
      MdmCleanRuleExpr expr,
      Map<String, String> attrNames,
      int pageNo,
      int pageSize) {
    // OR 语义:任一字段命中即构成重复组,按字段分别聚合。
    List<FieldHit> hits = new ArrayList<>();
    for (MdmMatchField field : expr.fields()) {
      String keyExpr = DedupSql.fieldKeyExpr(field);
      String valueCondition = DedupSql.fieldValueCondition(field);
      for (MdmDedupKeyRow row :
          recordRepository.countDedupKeys(entityId, ruleId, keyExpr, valueCondition)) {
        hits.add(new FieldHit(field, keyExpr, row));
      }
    }
    hits.sort(
        Comparator.comparingLong((FieldHit hit) -> hit.row().matchCount())
            .reversed()
            .thenComparing(hit -> hit.row().matchKey()));
    List<DedupGroup> groups = new ArrayList<>();
    for (FieldHit hit : slice(hits, pageNo, pageSize)) {
      List<MdmRecord> members =
          recordRepository.listByDedupKey(
              entityId, hit.keyExpr(), hit.row().matchKey(), GROUP_MEMBER_LIMIT);
      MdmCleanRuleExpr single =
          new MdmCleanRuleExpr(List.of(hit.field()), MdmCleanRuleExpr.CONDITION_AND);
      groups.add(
          buildGroup(single, attrNames, members, hit.row().matchCount(), hit.row().matchKey()));
    }
    return toPageData(groups, hits.size(), pageNo, pageSize);
  }

  private DedupGroup buildGroup(
      MdmCleanRuleExpr expr,
      Map<String, String> attrNames,
      List<MdmRecord> members,
      long total,
      String matchKey) {
    String matchBasis = "";
    double confidence = 1.0;
    if (!members.isEmpty()) {
      try {
        Map<String, Object> attributes = CleanJson.readObject(members.get(0).attributes());
        List<String> parts = new ArrayList<>();
        for (MdmMatchField field : expr.fields()) {
          Object raw = attributes.get(field.attrCode());
          if (raw == null) {
            continue;
          }
          String value =
              field.matchType() == MdmMatchType.FUZZY
                  ? DedupSql.normalize(String.valueOf(raw))
                  : String.valueOf(raw);
          parts.add(
              attrNames.getOrDefault(field.attrCode(), field.attrCode())
                  + "="
                  + value
                  + "("
                  + field.matchType().name()
                  + ")");
        }
        matchBasis = String.join(", ", parts);
      } catch (IllegalArgumentException ignored) {
        // 脏数据容错:属性 JSON 解析失败时仅展示组大小,不阻断去重发现。
      }
      for (MdmMatchField field : expr.fields()) {
        confidence *= field.matchType() == MdmMatchType.EXACT ? 1.0 : 0.9;
      }
    }
    return new DedupGroup(members, matchBasis, confidence, total, matchKey);
  }

  // ==== 忽略组(R7):判定「已知非重复」后不再反复出现在待处理列表 ====

  /**
   * 忽略一个重复组键:登记在 (规则, 组键) 上,去重发现从此排除。
   * 幂等——同一组键重复忽略不记第二账。matchKey 必须原样存:发现 SQL 以二进制排序规则
   * 比对组键,任何 trim/大小写归一都会让它永不命中,变成一条静音失效的忽略。
   */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public MdmDedupIgnore ignoreDedupGroup(
      Long entityId,
      Long ruleId,
      String matchKey,
      String matchBasis,
      String reason,
      String operator) {
    MdmCleanRule rule = requireDedupRule(entityId, ruleId);
    if (matchKey == null || matchKey.isEmpty()) {
      throw new MdmException(MdmErrorCode.MERGE_INVALID, "缺少重复组键");
    }
    if (matchKey.length() > MAX_MATCH_KEY_LENGTH) {
      throw new MdmException(
          MdmErrorCode.MERGE_INVALID, "重复组键超过 " + MAX_MATCH_KEY_LENGTH + " 字符,请缩小匹配字段");
    }
    if (reason != null && reason.length() > MAX_IGNORE_REASON_LENGTH) {
      throw new MdmException(
          MdmErrorCode.MERGE_INVALID, "忽略原因超过 " + MAX_IGNORE_REASON_LENGTH + " 字符");
    }
    MdmDedupIgnore existing =
        dedupIgnoreRepository.findByKey(entityId, rule.id(), matchKey).orElse(null);
    if (existing != null) {
      return existing;
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_DEDUP_GROUP_IGNORE",
                "Ignore dedup group",
                "MDM_CLEAN_RULE",
                String.valueOf(rule.id()),
                rule.ruleName(),
                "APPLICATION",
                Map.of("entityId", String.valueOf(entityId))));
    try {
      MdmDedupIgnore created =
          dedupIgnoreRepository.insert(
              new MdmDedupIgnore(
                  null, entityId, rule.id(), matchKey, matchBasis, reason, null, null),
              operator);
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_CREATED, "Dedup group ignored", Map.of(),
          "Dedup group ignored");
      return created;
    } catch (RuntimeException exception) {
      audit.failure("MDM_DEDUP_GROUP_IGNORE_FAILED", exception);
      throw exception;
    }
  }

  /** 撤销忽略:该组键重新回到去重发现结果。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void unignoreDedupGroup(Long id) {
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_DEDUP_GROUP_UNIGNORE",
                "Revoke ignored dedup group",
                "MDM_DEDUP_IGNORE",
                String.valueOf(id),
                null,
                "APPLICATION",
                Map.of()));
    try {
      if (!dedupIgnoreRepository.delete(id)) {
        throw new MdmException(MdmErrorCode.DEDUP_IGNORE_NOT_FOUND, String.valueOf(id));
      }
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_DELETED, "Ignored dedup group revoked", Map.of(),
          "Ignored dedup group revoked");
    } catch (RuntimeException exception) {
      audit.failure("MDM_DEDUP_GROUP_UNIGNORE_FAILED", exception);
      throw exception;
    }
  }

  public List<MdmDedupIgnore> listIgnoredGroups(Long entityId, Long ruleId) {
    return dedupIgnoreRepository.listByRule(entityId, requireDedupRule(entityId, ruleId).id());
  }

  private MdmCleanRule requireDedupRule(Long entityId, Long ruleId) {
    MdmCleanRule rule = get(ruleId);
    if (!entityId.equals(rule.entityId())) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "规则不属于该实体");
    }
    // 存量规则可能没有类型(早期默认去重),不能因此挡住忽略入口。
    if (rule.ruleType() != null && rule.ruleType() != MdmCleanRuleType.DEDUP) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "仅去重规则支持忽略重复组");
    }
    return rule;
  }

  // ==== 合并预览与执行 ====

  public MergePreview previewMerge(Long entityId, Long masterRecordId, List<Long> mergedRecordIds) {
    entityService.get(entityId);
    MergeTarget target = resolveMergeTargets(entityId, masterRecordId, mergedRecordIds);
    List<RecordView> views = new ArrayList<>();
    views.add(toRecordView(target.master()));
    target.merged().forEach(record -> views.add(toRecordView(record)));
    Map<String, Object> mergedAttributes =
        computeMergedAttributes(target.master(), target.merged());
    validatePkPreserved(target.master(), mergedAttributes);
    return new MergePreview(
        views,
        mergedAttributes,
        computeMergedSourceIds(target.master(), target.merged()));
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void executeMerge(
      Long entityId,
      Long ruleId,
      Long masterRecordId,
      List<Long> mergedRecordIds,
      String operator) {
    entityService.get(entityId);
    MdmCleanRule rule = ruleId == null ? null : get(ruleId);
    if (rule != null && !entityId.equals(rule.entityId())) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "规则不属于该实体");
    }
    MergeTarget target = resolveMergeTargets(entityId, masterRecordId, mergedRecordIds);
    Map<String, Object> mergedAttributes =
        computeMergedAttributes(target.master(), target.merged());
    validatePkPreserved(target.master(), mergedAttributes);
    Map<String, Object> mergedSourceIds =
        computeMergedSourceIds(target.master(), target.merged());
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_MERGE_EXECUTE",
                "Execute master data merge",
                "MDM_MERGE",
                String.valueOf(masterRecordId),
                target.master().masterId(),
                "APPLICATION",
                Map.of(
                    "entityId",
                    String.valueOf(entityId),
                    "ruleId",
                    String.valueOf(ruleId),
                    "mergedCount",
                    String.valueOf(target.merged().size()))));
    try {
      MdmRecord master =
          target
              .master()
              .mergedAsMaster(
                  CleanJson.write(mergedAttributes), CleanJson.write(mergedSourceIds),
                  CleanJson.write(mergeOverrides(target.master(), mergedAttributes)));
      if (!recordRepository.update(master)) {
        throw new MdmException(MdmErrorCode.MERGE_INVALID, "主记录更新失败");
      }
      for (MdmRecord record : target.merged()) {
        if (!recordRepository.update(record.mergedAway())) {
          throw new MdmException(MdmErrorCode.MERGE_INVALID, "被合并记录更新失败: " + record.id());
        }
      }
      mergeLogRepository.insert(
          new MdmMergeLog(
              null,
              entityId,
              rule == null ? null : rule.id(),
              masterRecordId,
              CleanJson.writeLongs(
                  target.merged().stream().map(MdmRecord::id).collect(Collectors.toList())),
              "主记录 "
                  + target.master().masterId()
                  + " 合并 "
                  + target.merged().size()
                  + " 条记录",
              operator,
              null),
          operator);
      AuditTransactions.completeOnCommit(
          audit,
          AuditEventType.RESOURCE_UPDATED,
          "Master data records merged",
          Map.of(),
          "Master data records merged");
      notifier.mergeCompleted(entityId, target.master().masterId(), target.merged().size());
    } catch (RuntimeException exception) {
      audit.failure("MDM_MERGE_EXECUTE_FAILED", exception);
      throw exception;
    }
  }

  public List<MdmMergeLog> mergeLogs(Long entityId) {
    entityService.get(entityId);
    return mergeLogRepository.listByEntity(entityId);
  }

  // ==== 内部方法 ====

  private MergeTarget resolveMergeTargets(
      Long entityId, Long masterRecordId, List<Long> mergedRecordIds) {
    if (masterRecordId == null) {
      throw new MdmException(MdmErrorCode.MERGE_INVALID, "请选择保留的主记录");
    }
    if (mergedRecordIds == null || mergedRecordIds.isEmpty()) {
      throw new MdmException(MdmErrorCode.MERGE_INVALID, "请选择至少一条被合并记录");
    }
    List<Long> ids = new ArrayList<>(mergedRecordIds);
    if (ids.contains(masterRecordId)) {
      throw new MdmException(MdmErrorCode.MERGE_INVALID, "主记录不能同时作为被合并记录");
    }
    if (ids.stream().distinct().count() != ids.size()) {
      throw new MdmException(MdmErrorCode.MERGE_INVALID, "被合并记录不能重复");
    }
    List<Long> allIds = new ArrayList<>(ids);
    allIds.add(masterRecordId);
    Map<Long, MdmRecord> byId =
        recordRepository.listActiveByIds(entityId, allIds).stream()
            .collect(Collectors.toMap(MdmRecord::id, record -> record));
    MdmRecord master = byId.get(masterRecordId);
    if (master == null) {
      throw new MdmException(MdmErrorCode.RECORD_NOT_FOUND, "主记录不存在或已合并: " + masterRecordId);
    }
    List<MdmRecord> merged =
        ids.stream().map(byId::get).filter(Objects::nonNull).collect(Collectors.toList());
    if (merged.size() != ids.size()) {
      throw new MdmException(MdmErrorCode.MERGE_INVALID, "存在已合并或不存在的被合并记录");
    }
    return new MergeTarget(master, merged);
  }

  /** 属性合并:主记录优先,其余记录非空补全缺失/空值属性。 */
  private Map<String, Object> computeMergedAttributes(
      MdmRecord master, List<MdmRecord> merged) {
    Map<String, Object> attributes = safeObject(master.attributes());
    for (MdmRecord record : merged) {
      Map<String, Object> other = safeObject(record.attributes());
      for (Map.Entry<String, Object> entry : other.entrySet()) {
        if (isBlank(attributes.get(entry.getKey())) && !isBlank(entry.getValue())) {
          attributes.put(entry.getKey(), entry.getValue());
        }
      }
    }
    return attributes;
  }

  /** source_ids 合并:按数据源去重并集(同一数据源保留主记录原始 ID)。 */
  private Map<String, Object> computeMergedSourceIds(MdmRecord master, List<MdmRecord> merged) {
    Map<String, Object> sourceIds = safeObject(master.sourceIds());
    for (MdmRecord record : merged) {
      Map<String, Object> other = safeObject(record.sourceIds());
      for (Map.Entry<String, Object> entry : other.entrySet()) {
        sourceIds.putIfAbsent(entry.getKey(), entry.getValue());
      }
    }
    return sourceIds;
  }

  private Map<String, Object> mergeOverrides(
      MdmRecord master, Map<String, Object> mergedAttributes) {
    Map<String, Object> overrides = safeObject(master.attributeOverrides());
    Map<String, Object> original = safeObject(master.attributes());
    for (Map.Entry<String, Object> entry : mergedAttributes.entrySet()) {
      if (!Objects.equals(original.get(entry.getKey()), entry.getValue())) {
        overrides.put(entry.getKey(), entry.getValue());
      }
    }
    return overrides;
  }

  private void validatePkPreserved(MdmRecord master, Map<String, Object> mergedAttributes) {
    Map<String, Object> original = safeObject(master.attributes());
    Set<String> pkCodes = attributeRepository.listByEntity(master.entityId()).stream()
        .filter(attribute -> attribute.type() == MdmAttributeType.PK)
        .map(MdmAttribute::code)
        .collect(Collectors.toSet());
    for (String pkCode : pkCodes) {
      if (!Objects.equals(original.get(pkCode), mergedAttributes.get(pkCode))) {
        throw new MdmException(
            MdmErrorCode.MERGE_INVALID,
            "合并不能修改 PK 属性 " + pkCode + "，该值参与 master_id 身份计算");
      }
    }
  }

  private Map<String, Object> safeObject(String json) {
    try {
      return new LinkedHashMap<>(CleanJson.readObject(json));
    } catch (IllegalArgumentException exception) {
      return new LinkedHashMap<>();
    }
  }

  private static boolean isBlank(Object value) {
    return value == null || String.valueOf(value).trim().isEmpty();
  }

  private static RecordView toRecordView(MdmRecord record) {
    return new RecordView(
        record.masterId(),
        record.status() == null ? null : record.status().name(),
        record.version(),
        CleanJson.readObject(record.attributes()),
        CleanJson.readObject(record.sourceIds()));
  }

  private static <T> List<T> slice(List<T> source, int pageNo, int pageSize) {
    int from = (pageNo - 1) * pageSize;
    if (from >= source.size()) {
      return List.of();
    }
    return source.subList(from, Math.min(source.size(), from + pageSize));
  }

  private static PageData<DedupGroup> toPageData(
      List<DedupGroup> groups, long total, int pageNo, int pageSize) {
    long pages = (total + pageSize - 1) / pageSize;
    return new PageData<>(groups, total, pages, (long) pageNo, (long) pageSize);
  }

  /** A rule used by a completed merge is retained for audit and cannot be deleted. */
  private boolean isReferenced(Long ruleId) {
    return mergeLogRepository.existsByRuleId(ruleId);
  }

  // ==== 标准化/补全 预览与执行(ticket 57) ====

  /** 预览标准化/补全影响:受影响总数为全量口径，明细只取前 {@link #MAX_PREVIEW_ITEMS} 条样本。 */
  public TransformPreview previewTransform(Long entityId, Long ruleId) {
    MdmCleanRule rule = requireEnabled(get(ruleId));
    if (!entityId.equals(rule.entityId())) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "规则不属于该实体");
    }
    if (!isTransformRule(rule)) {
      throw new MdmException(
          MdmErrorCode.INVALID_CLEAN_RULE, "规则类型 " + rule.ruleType() + " 不支持标准化/补全预览");
    }
    validateGenericRule(rule.entityId(), rule.ruleType(), rule.ruleName(), rule.ruleExpr());
    List<MdmRecord> records = recordRepository.listActiveByEntity(entityId);
    List<ChangeItem> changes = new ArrayList<>();
    int affected = 0;
    for (MdmRecord record : records) {
      Map<String, Object> attrs = safeObject(record.attributes());
      Map<String, Object> transformed = applyTransformExpr(rule, attrs);
      if (transformed == null) {
        continue;
      }
      affected++;
      // 明细是给人判读规则的样本，不该跟着实体规模线性膨胀响应体（总数仍按全量计）。
      if (changes.size() < MAX_PREVIEW_ITEMS) {
        changes.add(new ChangeItem(record.id(), record.masterId(), attrs, transformed));
      }
    }
    return new TransformPreview(affected, changes.size() < affected, changes);
  }

  private static boolean isTransformRule(MdmCleanRule rule) {
    return rule.ruleType() == MdmCleanRuleType.STANDARDIZE
        || rule.ruleType() == MdmCleanRuleType.COMPLETE;
  }

  /** 执行标准化/补全:批量更新受影响记录,返回变更条数。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public int applyTransform(Long entityId, Long ruleId, String operator) {
    entityService.get(entityId);
    MdmCleanRule rule = requireEnabled(get(ruleId));
    if (!entityId.equals(rule.entityId())) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "规则不属于该实体");
    }
    if (rule.ruleType() != MdmCleanRuleType.STANDARDIZE
        && rule.ruleType() != MdmCleanRuleType.COMPLETE) {
      throw new MdmException(
          MdmErrorCode.INVALID_CLEAN_RULE,
          "规则类型 " + rule.ruleType() + " 不支持标准化/补全执行");
    }
    validateGenericRule(rule.entityId(), rule.ruleType(), rule.ruleName(), rule.ruleExpr());
    List<MdmRecord> records = recordRepository.listActiveByEntity(entityId);
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_CLEAN_APPLY_" + rule.ruleType().name(),
                "Apply " + rule.ruleType().name().toLowerCase(Locale.ROOT) + " transform",
                "MDM_CLEAN_RECORD",
                null,
                rule.ruleName(),
                "APPLICATION",
                Map.of(
                    "entityId", String.valueOf(entityId),
                    "ruleId", String.valueOf(ruleId))));
    try {
      int count = 0;
      for (MdmRecord record : records) {
        Map<String, Object> attrs = safeObject(record.attributes());
        Map<String, Object> transformed = applyTransformExpr(rule, attrs);
        if (transformed != null) {
          Map<String, Object> overrides = safeObject(record.attributeOverrides());
          for (Map.Entry<String, Object> entry : transformed.entrySet()) {
            if (!Objects.equals(attrs.get(entry.getKey()), entry.getValue())) {
              overrides.put(entry.getKey(), entry.getValue());
            }
          }
          if (!recordRepository.update(record.cleaned(
              CleanJson.write(transformed), CleanJson.write(overrides)))) {
            throw new MdmException(
                MdmErrorCode.RECORD_NOT_FOUND, "记录更新失败: " + record.masterId());
          }
          count++;
        }
      }
      int finalCount = count;
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_UPDATED,
          "Clean transform applied: " + finalCount + " records",
          Map.of(), "Clean transform applied");
      return count;
    } catch (RuntimeException exception) {
      audit.failure("MDM_CLEAN_APPLY_FAILED", exception);
      throw exception;
    }
  }

  /** 按规则类型分发表达式解析与执行;未知类型抛异常。 */
  private Map<String, Object> applyTransformExpr(MdmCleanRule rule, Map<String, Object> attrs) {
    return switch (rule.ruleType()) {
      case STANDARDIZE -> {
        StandardizeExpr expr;
        try {
          expr = CleanJson.parseStandardizeExpr(rule.ruleExpr());
        } catch (IllegalArgumentException exception) {
          throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, exception.getMessage());
        }
        yield expr.apply(attrs);
      }
      case COMPLETE -> {
        CompleteExpr expr;
        try {
          expr = CleanJson.parseCompleteExpr(rule.ruleExpr());
        } catch (IllegalArgumentException exception) {
          throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, exception.getMessage());
        }
        yield expr.apply(attrs);
      }
      default -> throw new MdmException(
          MdmErrorCode.INVALID_CLEAN_RULE,
          "规则类型 " + rule.ruleType() + " 不支持标准化/补全执行");
    };
  }

  // ==== 通用规则校验(ticket 57) ====

  private void validateGenericRule(
      Long entityId, MdmCleanRuleType ruleType, String ruleName, String ruleExprJson) {
    if (!StringUtils.hasText(ruleName) || ruleName.trim().length() > MAX_RULE_NAME_LENGTH) {
      throw new MdmException(
          MdmErrorCode.INVALID_CLEAN_RULE, "规则名称必填且不超过 128 字符");
    }
    if (!StringUtils.hasText(ruleExprJson)) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "规则表达式不能为空");
    }
    List<MdmAttribute> attributes = attributeRepository.listByEntity(entityId);
    Set<String> validCodes = attributes.stream()
        .map(MdmAttribute::code)
        .collect(Collectors.toSet());
    Set<String> pkCodes = attributes.stream()
        .filter(attribute -> attribute.type() == MdmAttributeType.PK)
        .map(MdmAttribute::code)
        .collect(Collectors.toSet());
    switch (ruleType) {
      case DEDUP -> validateDedupExpr(ruleExprJson, validCodes);
      case STANDARDIZE -> validateStandardizeExpr(ruleExprJson, validCodes, pkCodes);
      case COMPLETE -> validateCompleteExpr(ruleExprJson, validCodes, pkCodes);
    }
  }

  private void validateDedupExpr(String json, Set<String> validCodes) {
    MdmCleanRuleExpr expr;
    try {
      expr = CleanJson.parseExpr(json);
    } catch (IllegalArgumentException exception) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, exception.getMessage());
    }
    if (expr.fields() == null || expr.fields().isEmpty()) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "至少配置一个匹配字段");
    }
    Set<String> seen = new HashSet<>();
    for (MdmMatchField field : expr.fields()) {
      if (field == null || field.attrCode() == null || field.attrCode().isBlank()) {
        throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "匹配字段编码不能为空");
      }
      if (!DedupSql.isValidAttrCode(field.attrCode())) {
        throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "非法属性编码: " + field.attrCode());
      }
      if (!validCodes.contains(field.attrCode())) {
        throw new MdmException(
            MdmErrorCode.INVALID_CLEAN_RULE, "匹配字段必须是实体已定义属性: " + field.attrCode());
      }
      if (!seen.add(field.attrCode())) {
        throw new MdmException(
            MdmErrorCode.INVALID_CLEAN_RULE, "同一字段仅允许出现一次: " + field.attrCode());
      }
      if (field.matchType() == null) {
        throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "匹配方式不能为空");
      }
    }
  }

  private void validateStandardizeExpr(
      String json, Set<String> validCodes, Set<String> pkCodes) {
    StandardizeExpr expr;
    try {
      expr = CleanJson.parseStandardizeExpr(json);
    } catch (IllegalArgumentException exception) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, exception.getMessage());
    }
    if (expr.fields() == null || expr.fields().isEmpty()) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "至少配置一个标准化字段");
    }
    for (Map.Entry<String, Map<String, String>> entry : expr.fields().entrySet()) {
      if (!validCodes.contains(entry.getKey())) {
        throw new MdmException(
            MdmErrorCode.INVALID_CLEAN_RULE, "字段必须是实体已定义属性: " + entry.getKey());
      }
      rejectPkTransform(entry.getKey(), pkCodes);
      if (!DedupSql.isValidAttrCode(entry.getKey())) {
        throw new MdmException(
            MdmErrorCode.INVALID_CLEAN_RULE, "非法属性编码: " + entry.getKey());
      }
      if (entry.getValue() == null || entry.getValue().isEmpty()) {
        throw new MdmException(
            MdmErrorCode.INVALID_CLEAN_RULE, "字段 " + entry.getKey() + " 的值映射不能为空");
      }
    }
  }

  private void validateCompleteExpr(
      String json, Set<String> validCodes, Set<String> pkCodes) {
    CompleteExpr expr;
    try {
      expr = CleanJson.parseCompleteExpr(json);
    } catch (IllegalArgumentException exception) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, exception.getMessage());
    }
    if (expr.defaults() == null || expr.defaults().isEmpty()) {
      throw new MdmException(MdmErrorCode.INVALID_CLEAN_RULE, "至少配置一个补全默认值");
    }
    for (Map.Entry<String, String> entry : expr.defaults().entrySet()) {
      if (!validCodes.contains(entry.getKey())) {
        throw new MdmException(
            MdmErrorCode.INVALID_CLEAN_RULE, "字段必须是实体已定义属性: " + entry.getKey());
      }
      rejectPkTransform(entry.getKey(), pkCodes);
      if (!DedupSql.isValidAttrCode(entry.getKey())) {
        throw new MdmException(
            MdmErrorCode.INVALID_CLEAN_RULE, "非法属性编码: " + entry.getKey());
      }
      if (entry.getValue() == null) {
        throw new MdmException(
            MdmErrorCode.INVALID_CLEAN_RULE, "字段 " + entry.getKey() + " 的默认值不能为空");
      }
    }
  }

  private static void rejectPkTransform(String attributeCode, Set<String> pkCodes) {
    if (pkCodes.contains(attributeCode)) {
      throw new MdmException(
          MdmErrorCode.INVALID_CLEAN_RULE,
          "PK 属性参与 master_id 身份计算，不能通过清洗规则修改: " + attributeCode);
    }
  }

  /**
   * 去重发现的重复组:组内记录(截断)、匹配依据、置信度、组大小、匹配键。
   * matchKey 是 SQL 聚合出来的组键原文,忽略/撤销忽略都以它为准(matchBasis 只供展示)。
   */
  public record DedupGroup(
      List<MdmRecord> records, String matchBasis, double confidence, long total, String matchKey) {}

  /** 合并预览:各记录属性/source_ids 对比 + 合并结果。 */
  public record MergePreview(
      List<RecordView> records,
      Map<String, Object> mergedAttributes,
      Map<String, Object> mergedSourceIds) {}

  /** 合并预览中的单条记录视图(attributes/source_ids 已解析)。 */
  public record RecordView(
      String masterId,
      String status,
      int version,
      Map<String, Object> attributes,
      Map<String, Object> sourceIds) {}

  /** 标准化/补全预览:受影响总数 + 有界变更样本 + 是否被截断。 */
  public record TransformPreview(
      int affectedCount, boolean truncated, List<ChangeItem> changes) {}

  /** 标准化/补全的单条变更明细:记录 ID、master_id、变更前/后属性。 */
  public record ChangeItem(
      Long recordId,
      String masterId,
      Map<String, Object> before,
      Map<String, Object> after) {}

  private record MergeTarget(MdmRecord master, List<MdmRecord> merged) {}

  private record FieldHit(MdmMatchField field, String keyExpr, MdmDedupKeyRow row) {}
}
