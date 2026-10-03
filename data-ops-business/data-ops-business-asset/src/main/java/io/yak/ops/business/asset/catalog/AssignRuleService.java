package io.yak.ops.business.asset.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.asset.controller.v1.dto.AssetRequests;
import io.yak.ops.business.asset.dao.mapper.AssetAssignRuleMapper;
import io.yak.ops.business.asset.dao.mapper.AssetItemMapper;
import io.yak.ops.business.asset.dao.mapper.AssetTagRelMapper;
import io.yak.ops.business.asset.exception.AssetException;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.asset.dao.model.AssetAssignRulePO;
import io.yak.ops.business.asset.dao.model.AssetItemPO;
import io.yak.ops.business.asset.dao.model.AssetTagRelPO;
import io.yak.ops.common.enums.asset.AssetEnums.RuleType;
import io.yak.ops.common.enums.asset.AssetErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 编目规则(自动归目录/打标签,ticket 95)。
 * 匹配为 static 纯函数;D10:未试跑(last_apply_hit=null)不可启用(48010),
 * 修改条件即重置试跑标记,防止"改完直接开"。
 */
@Service
@RequiredArgsConstructor
public class AssignRuleService {

  /** 条件 JSON 形态:{assetTypes,layerCodes,domainCodes,sourceTypes,nameRegex,keyword};空=通配,多项 AND。 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record RuleConditions(
      List<String> assetTypes, List<String> layerCodes, List<String> domainCodes,
      List<String> sourceTypes, String nameRegex, String keyword) {

    static final RuleConditions WILDCARD =
        new RuleConditions(null, null, null, null, null, null);

    static RuleConditions parse(String json) {
      if (!StringUtils.hasText(json)) {
        return WILDCARD;
      }
      try {
        return MAPPER.readValue(json, RuleConditions.class);
      } catch (JsonProcessingException e) {
        return WILDCARD;
      }
    }

    static String toJson(RuleConditions conditions) {
      try {
        return MAPPER.writeValueAsString(conditions == null ? WILDCARD : conditions);
      } catch (JsonProcessingException e) {
        throw new AssetException(AssetErrorCode.INVALID_ARGUMENT, "条件 JSON 序列化失败");
      }
    }
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int DRY_RUN_SAMPLE_LIMIT = 50;

  private final CurrentProject currentProject;
  private final AssetAssignRuleMapper ruleMapper;
  private final AssetItemMapper itemMapper;
  private final AssetTagRelMapper tagRelMapper;
  private final DirectoryService directoryService;
  private final TagService tagService;
  private final BusinessAuditService auditService;

  public record RuleView(
      Long id, String ruleName, String ruleType, RuleConditions conditions,
      Long targetDirectoryId, Long targetTagId, Integer priority, boolean enabled,
      Integer lastApplyHit, String createdBy, LocalDateTime updateTime) {}

  public record DryRunResult(int hitCount, List<DryRunSample> samples) {}

  public record DryRunSample(Long assetId, String name, Long targetId) {}

  public List<RuleView> list() {
    Long projectId = currentProject.requireProjectId();
    return ruleMapper.selectList(new LambdaQueryWrapper<AssetAssignRulePO>()
            .eq(AssetAssignRulePO::getProjectId, projectId)
            .eq(AssetAssignRulePO::getDeleted, false)
            .orderByAsc(AssetAssignRulePO::getPriority)
            .orderByAsc(AssetAssignRulePO::getId))
        .stream().map(AssignRuleService::toView).toList();
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public RuleView create(AssetRequests.RuleUpsertDTO dto, String operator) {
    Long projectId = currentProject.requireProjectId();
    AssetAssignRulePO draft = toPO(dto);
    validate(projectId, draft);
    draft.setId(null);
    draft.setProjectId(projectId);
    draft.setEnabled(false);
    // lastApplyHit=null 即"未试跑",启用被 48010 拦截(D10)。
    draft.setLastApplyHit(null);
    draft.setCreatedBy(operator);
    draft.setUpdatedBy(operator);
    draft.setDeleted(false);
    ruleMapper.insert(draft);
    audit("ASSET_RULE_CREATE", "Create assign rule", draft.getId(), draft.getRuleName(), operator,
        AuditEventType.RESOURCE_CREATED, "新建编目规则 " + draft.getRuleName());
    return toView(draft);
  }

  /** 修改(含条件变更)一律回到"未启用+未试跑",强制重新试跑(D10)。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public RuleView update(Long id, AssetRequests.RuleUpsertDTO dto, String operator) {
    Long projectId = currentProject.requireProjectId();
    AssetAssignRulePO po = requireRule(projectId, id);
    AssetAssignRulePO patch = toPO(dto);
    po.setRuleName(patch.getRuleName());
    po.setRuleType(patch.getRuleType());
    po.setConditions(patch.getConditions());
    po.setTargetDirectoryId(patch.getTargetDirectoryId());
    po.setTargetTagId(patch.getTargetTagId());
    po.setPriority(patch.getPriority());
    validate(projectId, po);
    po.setEnabled(false);
    po.setLastApplyHit(null);
    po.setUpdatedBy(operator);
    po.setUpdateTime(LocalDateTime.now());
    ruleMapper.updateById(po);
    return toView(po);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id, String operator) {
    Long projectId = currentProject.requireProjectId();
    AssetAssignRulePO po = requireRule(projectId, id);
    po.setDeleted(true);
    po.setUpdatedBy(operator);
    po.setUpdateTime(LocalDateTime.now());
    ruleMapper.updateById(po);
    audit("ASSET_RULE_DELETE", "Delete assign rule", id, po.getRuleName(), operator,
        AuditEventType.RESOURCE_DELETED, "删除编目规则 " + po.getRuleName());
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public RuleView setEnabled(Long id, boolean enabled, String operator) {
    Long projectId = currentProject.requireProjectId();
    AssetAssignRulePO po = requireRule(projectId, id);
    if (enabled && po.getLastApplyHit() == null) {
      throw new AssetException(AssetErrorCode.RULE_DRY_RUN_REQUIRED,
          "规则「" + po.getRuleName() + "」尚未试跑");
    }
    po.setEnabled(enabled);
    po.setUpdatedBy(operator);
    po.setUpdateTime(LocalDateTime.now());
    ruleMapper.updateById(po);
    return toView(po);
  }

  /** 试跑:返回命中数+前 50 样例;结果写回 last_apply_hit。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DryRunResult dryRun(Long id, String operator) {
    Long projectId = currentProject.requireProjectId();
    AssetAssignRulePO po = requireRule(projectId, id);
    List<AssetItemPO> hits = matchingItems(projectId, po);
    po.setLastApplyHit(hits.size());
    po.setUpdatedBy(operator);
    po.setUpdateTime(LocalDateTime.now());
    ruleMapper.updateById(po);
    List<DryRunSample> samples = hits.stream().limit(DRY_RUN_SAMPLE_LIMIT)
        .map(item -> new DryRunSample(item.getId(), item.getName(), targetOf(po)))
        .toList();
    return new DryRunResult(hits.size(), samples);
  }

  /** 重应用:DIRECTORY 覆盖主目录,TAG 幂等补挂;返回实际写入数。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public int applyAgain(Long id, String operator) {
    Long projectId = currentProject.requireProjectId();
    AssetAssignRulePO po = requireRule(projectId, id);
    List<AssetItemPO> hits = matchingItems(projectId, po);
    int applied = 0;
    if (RuleType.DIRECTORY.name().equals(po.getRuleType())) {
      for (AssetItemPO item : hits) {
        if (!po.getTargetDirectoryId().equals(item.getDirectoryId())) {
          item.setDirectoryId(po.getTargetDirectoryId());
          item.setUpdatedBy(operator);
          item.setUpdateTime(LocalDateTime.now());
          itemMapper.updateById(item);
          applied++;
        }
      }
    } else {
      for (AssetItemPO item : hits) {
        if (attachTagIdempotent(projectId, item.getId(), po.getTargetTagId(), operator)) {
          applied++;
        }
      }
    }
    po.setLastApplyHit(hits.size());
    po.setUpdatedBy(operator);
    po.setUpdateTime(LocalDateTime.now());
    ruleMapper.updateById(po);
    audit("ASSET_RULE_APPLY", "Apply assign rule", id, po.getRuleName(), operator,
        AuditEventType.RESOURCE_UPDATED, "重应用规则命中 " + hits.size() + " 写入 " + applied);
    return applied;
  }

  // ---------- 对账入口(无请求上下文,projectId 显式传入) ----------

  /** 新资产入台账时的建议目录:DIRECTORY 规则按优先级首条命中。 */
  public Long suggestedDirectoryId(Long projectId, AssetItemPO candidate) {
    for (AssetAssignRulePO rule : enabledRules(projectId, RuleType.DIRECTORY)) {
      if (matches(RuleConditions.parse(rule.getConditions()), candidate)) {
        return rule.getTargetDirectoryId();
      }
    }
    return null;
  }

  /** 新资产入台账时的建议标签:全部启用的 TAG 规则各首条(标签可叠加,与"首条即停"仅约束同类型目标)。 */
  public List<Long> suggestedTagIds(Long projectId, AssetItemPO candidate) {
    List<Long> tagIds = new ArrayList<>();
    for (AssetAssignRulePO rule : enabledRules(projectId, RuleType.TAG)) {
      if (matches(RuleConditions.parse(rule.getConditions()), candidate)
          && rule.getTargetTagId() != null && !tagIds.contains(rule.getTargetTagId())) {
        tagIds.add(rule.getTargetTagId());
      }
    }
    return tagIds;
  }

  /** 纯函数匹配:各条件 AND;列表内 OR(忽略大小写);nameRegex find;keyword 含名称或描述。 */
  public static boolean matches(RuleConditions c, AssetItemPO item) {
    if (!wildcardContains(c.assetTypes(), item.getAssetType())
        || !wildcardContains(c.layerCodes(), item.getLayerCode())
        || !wildcardContains(c.domainCodes(), item.getDomainCode())
        || !wildcardContains(c.sourceTypes(), item.getSourceType())) {
      return false;
    }
    if (StringUtils.hasText(c.nameRegex())) {
      if (item.getName() == null
          || !Pattern.compile(c.nameRegex(), Pattern.CASE_INSENSITIVE)
              .matcher(item.getName()).find()) {
        return false;
      }
    }
    if (StringUtils.hasText(c.keyword())) {
      String kw = c.keyword().toLowerCase(Locale.ROOT);
      String name = item.getName() == null ? "" : item.getName().toLowerCase(Locale.ROOT);
      String desc = item.getDescription() == null ? "" : item.getDescription()
          .toLowerCase(Locale.ROOT);
      if (!name.contains(kw) && !desc.contains(kw)) {
        return false;
      }
    }
    return true;
  }

  private static boolean wildcardContains(List<String> expected, String actual) {
    if (expected == null || expected.isEmpty()) {
      return true;
    }
    if (actual == null) {
      return false;
    }
    return expected.stream().filter(java.util.Objects::nonNull)
        .anyMatch(e -> e.equalsIgnoreCase(actual));
  }

  // ---------- internal ----------

  private static AssetAssignRulePO toPO(AssetRequests.RuleUpsertDTO dto) {
    AssetAssignRulePO po = new AssetAssignRulePO();
    po.setRuleName(dto.getRuleName().trim());
    po.setRuleType(dto.getRuleType().trim().toUpperCase());
    po.setConditions(RuleConditions.toJson(toConditions(dto.getConditions())));
    po.setTargetDirectoryId(dto.getTargetDirectoryId());
    po.setTargetTagId(dto.getTargetTagId());
    po.setPriority(dto.getPriority());
    return po;
  }

  private static RuleConditions toConditions(AssetRequests.RuleConditionsDTO c) {
    if (c == null) {
      return WILDCARD_CONDITIONS;
    }
    return new RuleConditions(c.getAssetTypes(), c.getLayerCodes(), c.getDomainCodes(),
        c.getSourceTypes(), c.getNameRegex(), c.getKeyword());
  }

  private static final RuleConditions WILDCARD_CONDITIONS = RuleConditions.WILDCARD;

  private List<AssetAssignRulePO> enabledRules(Long projectId, RuleType type) {
    return ruleMapper.selectList(new LambdaQueryWrapper<AssetAssignRulePO>()
        .eq(AssetAssignRulePO::getProjectId, projectId)
        .eq(AssetAssignRulePO::getRuleType, type.name())
        .eq(AssetAssignRulePO::getEnabled, true)
        .eq(AssetAssignRulePO::getDeleted, false)
        .orderByAsc(AssetAssignRulePO::getPriority)
        .orderByAsc(AssetAssignRulePO::getId));
  }

  private List<AssetItemPO> matchingItems(Long projectId, AssetAssignRulePO rule) {
    RuleConditions conditions = RuleConditions.parse(rule.getConditions());
    if (StringUtils.hasText(conditions.nameRegex())) {
      try {
        Pattern.compile(conditions.nameRegex(), Pattern.CASE_INSENSITIVE);
      } catch (PatternSyntaxException e) {
        throw new AssetException(AssetErrorCode.INVALID_ARGUMENT,
            "名称正则不合法: " + e.getMessage());
      }
    }
    List<AssetItemPO> items = itemMapper.selectList(new LambdaQueryWrapper<AssetItemPO>()
        .eq(AssetItemPO::getProjectId, projectId)
        .eq(AssetItemPO::getDeleted, false)
        .orderByAsc(AssetItemPO::getId));
    return items.stream().filter(item -> matches(conditions, item)).toList();
  }

  private void validate(Long projectId, AssetAssignRulePO draft) {
    if (!StringUtils.hasText(draft.getRuleName())) {
      throw new AssetException(AssetErrorCode.INVALID_ARGUMENT, "规则名称必填");
    }
    try {
      RuleType.valueOf(draft.getRuleType());
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new AssetException(AssetErrorCode.INVALID_ARGUMENT, "规则类型仅支持 DIRECTORY/TAG");
    }
    if (draft.getPriority() == null) {
      draft.setPriority(100);
    }
    if (RuleType.DIRECTORY.name().equals(draft.getRuleType())) {
      draft.setTargetTagId(null);
      if (draft.getTargetDirectoryId() == null) {
        throw new AssetException(AssetErrorCode.INVALID_ARGUMENT, "归目录规则必须选择目标目录");
      }
      directoryService.requireDirectory(projectId, draft.getTargetDirectoryId());
    } else {
      draft.setTargetDirectoryId(null);
      if (draft.getTargetTagId() == null) {
        throw new AssetException(AssetErrorCode.INVALID_ARGUMENT, "打标签规则必须选择目标标签");
      }
      tagService.requireTag(projectId, draft.getTargetTagId());
    }
    RuleConditions conditions = RuleConditions.parse(draft.getConditions());
    if (StringUtils.hasText(conditions.nameRegex())) {
      try {
        Pattern.compile(conditions.nameRegex());
      } catch (PatternSyntaxException e) {
        throw new AssetException(AssetErrorCode.INVALID_ARGUMENT,
            "名称正则不合法: " + e.getMessage());
      }
    }
  }

  private AssetAssignRulePO requireRule(Long projectId, Long id) {
    AssetAssignRulePO po = ruleMapper.selectOne(new LambdaQueryWrapper<AssetAssignRulePO>()
        .eq(AssetAssignRulePO::getProjectId, projectId)
        .eq(AssetAssignRulePO::getId, id)
        .eq(AssetAssignRulePO::getDeleted, false));
    if (po == null) {
      throw new AssetException(AssetErrorCode.INVALID_ARGUMENT, "编目规则不存在 id=" + id);
    }
    return po;
  }

  private static Long targetOf(AssetAssignRulePO po) {
    return RuleType.DIRECTORY.name().equals(po.getRuleType())
        ? po.getTargetDirectoryId() : po.getTargetTagId();
  }

  /** 幂等补挂标签:已存在即 false。 */
  private boolean attachTagIdempotent(Long projectId, Long assetId, Long tagId,
      String operator) {
    boolean exists = tagRelMapper.selectCount(new LambdaQueryWrapper<AssetTagRelPO>()
        .eq(AssetTagRelPO::getProjectId, projectId)
        .eq(AssetTagRelPO::getAssetId, assetId)
        .eq(AssetTagRelPO::getTagId, tagId)) > 0;
    if (exists) {
      return false;
    }
    AssetTagRelPO rel = new AssetTagRelPO();
    rel.setProjectId(projectId);
    rel.setAssetId(assetId);
    rel.setTagId(tagId);
    rel.setCreatedBy(operator);
    rel.setCreateTime(LocalDateTime.now());
    try {
      tagRelMapper.insert(rel);
      return true;
    } catch (org.springframework.dao.DuplicateKeyException e) {
      return false;
    }
  }

  private void audit(String code, String action, Long id, String name, String operator,
      AuditEventType type, String message) {
    AuditOperationHandle handle = auditService.start(new AuditOperationRequest(
        code, action, "ASSET_RULE", id == null ? null : String.valueOf(id), name,
        "APPLICATION", Map.of()));
    AuditTransactions.completeOnCommit(handle, type, message,
        Map.of("target", String.valueOf(id)), null);
  }

  static RuleView toView(AssetAssignRulePO po) {
    return new RuleView(po.getId(), po.getRuleName(), po.getRuleType(),
        RuleConditions.parse(po.getConditions()), po.getTargetDirectoryId(),
        po.getTargetTagId(), po.getPriority(), Boolean.TRUE.equals(po.getEnabled()),
        po.getLastApplyHit(), po.getCreatedBy(), po.getUpdateTime());
  }
}
