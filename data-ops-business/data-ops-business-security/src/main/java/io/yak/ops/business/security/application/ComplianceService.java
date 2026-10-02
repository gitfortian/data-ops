package io.yak.ops.business.security.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.framework.common.PageData;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.security.api.MaskingDirective;
import io.yak.ops.business.security.dao.mapper.ClassificationMapper;
import io.yak.ops.business.security.dao.mapper.ComplianceFindingMapper;
import io.yak.ops.business.security.dao.mapper.ComplianceRuleMapper;
import io.yak.ops.business.security.dao.mapper.SecurityLevelMapper;
import io.yak.ops.business.security.domain.ComplianceRunResult;
import io.yak.ops.business.security.exception.SecurityException;
import io.yak.ops.business.security.support.audit.SecurityAudit;
import io.yak.ops.common.bean.po.security.DsecClassificationPO;
import io.yak.ops.common.bean.po.security.DsecComplianceFindingPO;
import io.yak.ops.common.bean.po.security.DsecComplianceRulePO;
import io.yak.ops.common.bean.po.security.DsecSecurityLevelPO;
import io.yak.ops.common.enums.security.SecurityErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 合规服务:规则 CRUD + 对现有安全元数据做体检,量化缺口。 */
@Component
public class ComplianceService {

  static final String SENSITIVE_MUST_MASKED = "SENSITIVE_MUST_MASKED";
  static final String SENSITIVE_MUST_CONFIRM = "SENSITIVE_MUST_CONFIRM";
  static final String CLASSIFY_COVERAGE = "CLASSIFY_COVERAGE";

  private static final List<String> RULE_TYPES =
      List.of(SENSITIVE_MUST_MASKED, SENSITIVE_MUST_CONFIRM, CLASSIFY_COVERAGE);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ComplianceRuleMapper ruleMapper;
  private final ComplianceFindingMapper findingMapper;
  private final ClassificationMapper classificationMapper;
  private final SecurityLevelMapper levelMapper;
  private final MaskingService maskingService;
  private final CurrentProject currentProject;
  private final BusinessAuditService auditService;

  public ComplianceService(
      ComplianceRuleMapper ruleMapper,
      ComplianceFindingMapper findingMapper,
      ClassificationMapper classificationMapper,
      SecurityLevelMapper levelMapper,
      MaskingService maskingService,
      CurrentProject currentProject,
      BusinessAuditService auditService) {
    this.ruleMapper = ruleMapper;
    this.findingMapper = findingMapper;
    this.classificationMapper = classificationMapper;
    this.levelMapper = levelMapper;
    this.maskingService = maskingService;
    this.currentProject = currentProject;
    this.auditService = auditService;
  }

  // ==== 规则 CRUD ====

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DsecComplianceRulePO createRule(
      String code, String name, String ruleType, String params, String severity,
      Boolean enabled, String description, String operator) {
    if (ruleType == null || !RULE_TYPES.contains(ruleType)) {
      throw new SecurityException(SecurityErrorCode.COMPLIANCE_INVALID_RULE_TYPE, ruleType);
    }
    Long projectId = currentProject.requireProjectId();
    if (existsRuleCode(code)) {
      throw new SecurityException(SecurityErrorCode.COMPLIANCE_RULE_DUPLICATE_CODE, code);
    }
    return SecurityAudit.tx(
        auditService,
        AuditEventType.RESOURCE_CREATED,
        SecurityAudit.request("COMPLIANCE_RULE_CREATE", "Create compliance rule", "COMPLIANCE_RULE", null, code),
        () -> {
          LocalDateTime now = LocalDateTime.now();
          DsecComplianceRulePO po = new DsecComplianceRulePO();
          po.setProjectId(projectId);
          po.setRuleCode(code);
          po.setRuleName(name);
          po.setRuleType(ruleType);
          po.setParams(params);
          po.setSeverity(StringUtils.hasText(severity) ? severity : "MEDIUM");
          po.setEnabled(Boolean.FALSE.equals(enabled) ? 0 : 1);
          po.setDescription(description);
          po.setCreatedBy(operator);
          po.setCreateTime(now);
          po.setUpdateTime(now);
          ruleMapper.insert(po);
          return po;
        });
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void deleteRule(Long id) {
    getRule(id);
    SecurityAudit.tx(
        auditService,
        AuditEventType.RESOURCE_DELETED,
        SecurityAudit.request(
            "COMPLIANCE_RULE_DELETE", "Delete compliance rule", "COMPLIANCE_RULE", String.valueOf(id), null),
        () -> {
          ruleMapper.deleteById(id);
          return Boolean.TRUE;
        });
  }

  public DsecComplianceRulePO getRule(Long id) {
    DsecComplianceRulePO po = ruleMapper.selectOne(
        new LambdaQueryWrapper<DsecComplianceRulePO>()
            .eq(DsecComplianceRulePO::getId, id)
            .eq(DsecComplianceRulePO::getProjectId, currentProject.requireProjectId()));
    if (po == null) {
      throw new SecurityException(SecurityErrorCode.COMPLIANCE_RULE_NOT_FOUND, String.valueOf(id));
    }
    return po;
  }

  public PageData<DsecComplianceRulePO> pageRules(int pageNo, int pageSize, String keyword) {
    Long projectId = currentProject.requireProjectId();
    LambdaQueryWrapper<DsecComplianceRulePO> wrapper =
        new LambdaQueryWrapper<DsecComplianceRulePO>().eq(DsecComplianceRulePO::getProjectId, projectId);
    if (StringUtils.hasText(keyword)) {
      wrapper.and(c -> c.like(DsecComplianceRulePO::getRuleCode, keyword.trim())
          .or().like(DsecComplianceRulePO::getRuleName, keyword.trim()));
    }
    wrapper.orderByDesc(DsecComplianceRulePO::getId);
    Page<DsecComplianceRulePO> page = Page.of(Math.max(1, pageNo), Math.max(1, pageSize));
    var result = ruleMapper.selectPage(page, wrapper);
    return new PageData<>(
        result.getRecords(), result.getTotal(), result.getPages(), (long) pageNo, (long) pageSize);
  }

  // ==== 体检 ====

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public ComplianceRunResult run(Long ruleId, String operator) {
    Long projectId = currentProject.requireProjectId();
    List<DsecComplianceRulePO> rules = ruleId != null
        ? List.of(getRule(ruleId))
        : ruleMapper.selectList(
            new LambdaQueryWrapper<DsecComplianceRulePO>()
                .eq(DsecComplianceRulePO::getProjectId, projectId)
                .eq(DsecComplianceRulePO::getEnabled, 1));
    if (rules.isEmpty()) {
      throw new SecurityException(SecurityErrorCode.COMPLIANCE_NO_ENABLED_RULE);
    }
    String batchId = UUID.randomUUID().toString().replace("-", "");
    Map<Long, Integer> rankById = levelRanks(projectId);
    List<DsecClassificationPO> classifications = classificationMapper.selectList(
        new LambdaQueryWrapper<DsecClassificationPO>()
            .eq(DsecClassificationPO::getProjectId, projectId));
    int checked = 0;
    int passed = 0;
    int failed = 0;
    for (DsecComplianceRulePO rule : rules) {
      List<DsecComplianceFindingPO> findings =
          evaluate(rule, classifications, rankById, batchId, projectId);
      if (findings.isEmpty()) {
        // Persist one successful row per checked rule so a clean run remains distinguishable from no run.
        findings.add(finding(rule, batchId, projectId, true, null, "检查通过：未发现合规缺口"));
      }
      checked++;
      int ruleFailed = 0;
      for (DsecComplianceFindingPO finding : findings) {
        findingMapper.insert(finding);
        if (finding.getPassed() != null && finding.getPassed() == 0) {
          ruleFailed++;
        }
      }
      failed += ruleFailed;
      if (ruleFailed == 0) {
        passed++;
      }
    }
    return new ComplianceRunResult(batchId, checked, passed, failed);
  }

  List<DsecComplianceFindingPO> evaluate(
      DsecComplianceRulePO rule,
      List<DsecClassificationPO> classifications,
      Map<Long, Integer> rankById,
      String batchId,
      Long projectId) {
    int threshold = intParam(rule.getParams(), "rank", 3);
    List<DsecComplianceFindingPO> findings = new java.util.ArrayList<>();
    switch (rule.getRuleType()) {
      case SENSITIVE_MUST_MASKED -> {
        for (DsecClassificationPO c : classifications) {
          if (!"ACTIVE".equals(c.getStatus()) || rank(rankById, c.getLevelId()) < threshold) {
            continue;
          }
          MaskingDirective directive = maskingService.resolve(c.getObjectKey());
          if (!directive.mask()) {
            findings.add(finding(rule, batchId, projectId, false, c.getObjectKey(),
                "敏感数据未配置脱敏策略"));
          }
        }
      }
      case SENSITIVE_MUST_CONFIRM -> {
        for (DsecClassificationPO c : classifications) {
          if ("CANDIDATE".equals(c.getStatus()) && rank(rankById, c.getLevelId()) >= threshold) {
            findings.add(finding(rule, batchId, projectId, false, c.getObjectKey(),
                "敏感候选分级待确认"));
          }
        }
      }
      case CLASSIFY_COVERAGE -> {
        int total = (int) classifications.stream().filter(c -> !"REJECTED".equals(c.getStatus())).count();
        long active = classifications.stream().filter(c -> "ACTIVE".equals(c.getStatus())).count();
        int covThreshold = intParam(rule.getParams(), "threshold", 80);
        int coverage = total == 0 ? 100 : (int) (active * 100 / total);
        if (coverage < covThreshold) {
          findings.add(finding(rule, batchId, projectId, false, null,
              "定级确认覆盖率 " + coverage + "% 低于阈值 " + covThreshold + "%"));
        }
      }
      default -> {
        // 未知规则类型不产出缺口。
      }
    }
    return findings;
  }

  public PageData<DsecComplianceFindingPO> pageFindings(
      int pageNo, int pageSize, String batchId, Boolean passed) {
    Long projectId = currentProject.requireProjectId();
    LambdaQueryWrapper<DsecComplianceFindingPO> wrapper =
        new LambdaQueryWrapper<DsecComplianceFindingPO>()
            .eq(DsecComplianceFindingPO::getProjectId, projectId);
    if (StringUtils.hasText(batchId)) {
      wrapper.eq(DsecComplianceFindingPO::getBatchId, batchId);
    }
    if (passed != null) {
      wrapper.eq(DsecComplianceFindingPO::getPassed, passed ? 1 : 0);
    }
    wrapper.orderByDesc(DsecComplianceFindingPO::getCheckedTime).orderByDesc(DsecComplianceFindingPO::getId);
    Page<DsecComplianceFindingPO> page = Page.of(Math.max(1, pageNo), Math.max(1, pageSize));
    var result = findingMapper.selectPage(page, wrapper);
    return new PageData<>(
        result.getRecords(), result.getTotal(), result.getPages(), (long) pageNo, (long) pageSize);
  }

  public Map<String, Object> latestSummary() {
    Long projectId = currentProject.requireProjectId();
    DsecComplianceFindingPO last = findingMapper.selectOne(
        new LambdaQueryWrapper<DsecComplianceFindingPO>()
            .eq(DsecComplianceFindingPO::getProjectId, projectId)
            .orderByDesc(DsecComplianceFindingPO::getCheckedTime)
            .orderByDesc(DsecComplianceFindingPO::getId)
            .last("limit 1"));
    Map<String, Object> map = new HashMap<>();
    if (last == null) {
      map.put("status", "UNKNOWN");
      map.put("hasRun", null);
      map.put("batchId", null);
      map.put("openGaps", null);
      map.put("message", "当前没有可验证的体检批次；旧版无缺口批次无法从历史表中识别");
      return map;
    }
    Long open = findingMapper.selectCount(
        new LambdaQueryWrapper<DsecComplianceFindingPO>()
            .eq(DsecComplianceFindingPO::getProjectId, projectId)
            .eq(DsecComplianceFindingPO::getBatchId, last.getBatchId())
            .eq(DsecComplianceFindingPO::getPassed, 0));
    map.put("status", "COMPLETED");
    map.put("hasRun", Boolean.TRUE);
    map.put("batchId", last.getBatchId());
    // 批次号是无语义 UUID;给界面补一个可读的体检时间(S6-01)
    map.put("checkedTime", last.getCheckedTime());
    map.put("openGaps", open == null ? 0L : open);
    return map;
  }

  private DsecComplianceFindingPO finding(
      DsecComplianceRulePO rule, String batchId, Long projectId, boolean passed,
      String targetKey, String finding) {
    LocalDateTime now = LocalDateTime.now();
    DsecComplianceFindingPO po = new DsecComplianceFindingPO();
    po.setProjectId(projectId);
    po.setBatchId(batchId);
    po.setRuleId(rule.getId());
    po.setRuleType(rule.getRuleType());
    po.setTargetKey(targetKey);
    po.setPassed(passed ? 1 : 0);
    po.setFinding(finding);
    po.setSeverity(rule.getSeverity());
    po.setCheckedTime(now);
    po.setCreateTime(now);
    return po;
  }

  private Map<Long, Integer> levelRanks(Long projectId) {
    Map<Long, Integer> map = new HashMap<>();
    for (DsecSecurityLevelPO level :
        levelMapper.selectList(new LambdaQueryWrapper<DsecSecurityLevelPO>()
            .eq(DsecSecurityLevelPO::getProjectId, projectId))) {
      map.put(level.getId(), level.getRankNo() == null ? 0 : level.getRankNo());
    }
    return map;
  }

  private static int rank(Map<Long, Integer> rankById, Long levelId) {
    return levelId == null ? 0 : rankById.getOrDefault(levelId, 0);
  }

  private static int intParam(String paramsJson, String key, int defaultValue) {
    if (!StringUtils.hasText(paramsJson)) {
      return defaultValue;
    }
    try {
      JsonNode node = MAPPER.readTree(paramsJson).get(key);
      return node == null || !node.isNumber() ? defaultValue : node.asInt(defaultValue);
    } catch (Exception exception) {
      return defaultValue;
    }
  }

  private boolean existsRuleCode(String code) {
    if (!StringUtils.hasText(code)) {
      return false;
    }
    Long c = ruleMapper.selectCount(
        new LambdaQueryWrapper<DsecComplianceRulePO>()
            .eq(DsecComplianceRulePO::getProjectId, currentProject.requireProjectId())
            .eq(DsecComplianceRulePO::getRuleCode, code));
    return c != null && c > 0;
  }
}
