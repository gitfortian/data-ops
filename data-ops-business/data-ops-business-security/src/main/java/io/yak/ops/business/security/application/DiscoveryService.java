package io.yak.ops.business.security.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.security.dao.mapper.DiscoveryRuleMapper;
import io.yak.ops.business.security.dao.mapper.SecurityLevelMapper;
import io.yak.ops.business.security.domain.DiscoverableField;
import io.yak.ops.business.security.exception.SecurityException;
import io.yak.ops.business.security.support.audit.SecurityAudit;
import io.yak.ops.business.security.dao.model.DsecDiscoveryRulePO;
import io.yak.ops.business.security.dao.model.DsecSecurityLevelPO;
import io.yak.ops.common.enums.security.SecurityErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 敏感数据发现规则服务:规则 CRUD + 匹配引擎,产出候选分级标签。 */
@Component
public class DiscoveryService {

  // CONTENT remains readable for existing rules, but it only scans column metadata; it never reads row values.
  private static final List<String> MATCH_TYPES = List.of("NAME", "COMMENT", "CONTENT", "REGEX");

  private final DiscoveryRuleMapper mapper;
  private final SecurityLevelMapper levelMapper;
  private final ClassificationService classificationService;
  private final CurrentProject currentProject;
  private final BusinessAuditService auditService;

  public DiscoveryService(
      DiscoveryRuleMapper mapper,
      SecurityLevelMapper levelMapper,
      ClassificationService classificationService,
      CurrentProject currentProject,
      BusinessAuditService auditService) {
    this.mapper = mapper;
    this.levelMapper = levelMapper;
    this.classificationService = classificationService;
    this.currentProject = currentProject;
    this.auditService = auditService;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DsecDiscoveryRulePO create(
      String code, String name, String matchType, String pattern, Long levelId, Long categoryId,
      Boolean enabled, String description, String operator) {
    if (!StringUtils.hasText(code)) {
      throw new SecurityException(SecurityErrorCode.DISCOVERY_RULE_DUPLICATE_CODE, "编码不能为空");
    }
    if (matchType == null || !MATCH_TYPES.contains(matchType)) {
      throw new SecurityException(SecurityErrorCode.DISCOVERY_INVALID_MATCH_TYPE, matchType);
    }
    if (!StringUtils.hasText(pattern)) {
      throw new SecurityException(SecurityErrorCode.DISCOVERY_INVALID_PATTERN);
    }
    validatePattern(matchType, pattern);
    Long projectId = currentProject.requireProjectId();
    if (existsByCode(code)) {
      throw new SecurityException(SecurityErrorCode.DISCOVERY_RULE_DUPLICATE_CODE, code);
    }
    if (levelId == null || levelMapper.selectOne(scopedLevel(projectId, levelId)) == null) {
      throw new SecurityException(SecurityErrorCode.CLASSIFICATION_INVALID_LEVEL, String.valueOf(levelId));
    }
    return SecurityAudit.tx(
        auditService,
        AuditEventType.RESOURCE_CREATED,
        SecurityAudit.request("DISCOVERY_RULE_CREATE", "Create discovery rule", "DISCOVERY_RULE", null, code),
        () -> {
          LocalDateTime now = LocalDateTime.now();
          DsecDiscoveryRulePO po = new DsecDiscoveryRulePO();
          po.setProjectId(projectId);
          po.setRuleCode(code);
          po.setRuleName(name);
          po.setMatchType(matchType);
          po.setPattern(pattern);
          po.setLevelId(levelId);
          po.setCategoryId(categoryId);
          po.setEnabled(Boolean.FALSE.equals(enabled) ? 0 : 1);
          po.setDescription(description);
          po.setCreatedBy(operator);
          po.setCreateTime(now);
          po.setUpdateTime(now);
          mapper.insert(po);
          return po;
        });
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DsecDiscoveryRulePO update(
      Long id, String name, String matchType, String pattern, Long levelId, Long categoryId,
      Boolean enabled, String description) {
    DsecDiscoveryRulePO po = get(id);
    if (matchType != null && !MATCH_TYPES.contains(matchType)) {
      throw new SecurityException(SecurityErrorCode.DISCOVERY_INVALID_MATCH_TYPE, matchType);
    }
    String mt = matchType == null ? po.getMatchType() : matchType;
    String pt = pattern == null ? po.getPattern() : pattern;
    validatePattern(mt, pt);
    SecurityAudit.tx(
        auditService,
        AuditEventType.RESOURCE_UPDATED,
        SecurityAudit.request(
            "DISCOVERY_RULE_UPDATE", "Update discovery rule", "DISCOVERY_RULE", String.valueOf(id), po.getRuleCode()),
        () -> {
          po.setRuleName(StringUtils.hasText(name) ? name : po.getRuleName());
          po.setMatchType(mt);
          po.setPattern(pt);
          if (levelId != null) {
            po.setLevelId(levelId);
          }
          po.setCategoryId(categoryId);
          if (enabled != null) {
            po.setEnabled(enabled ? 1 : 0);
          }
          po.setDescription(description);
          po.setUpdateTime(LocalDateTime.now());
          mapper.updateById(po);
          return Boolean.TRUE;
        });
    return po;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id) {
    DsecDiscoveryRulePO po = get(id);
    SecurityAudit.tx(
        auditService,
        AuditEventType.RESOURCE_DELETED,
        SecurityAudit.request(
            "DISCOVERY_RULE_DELETE", "Delete discovery rule", "DISCOVERY_RULE", String.valueOf(id), po.getRuleCode()),
        () -> {
          mapper.deleteById(id);
          return Boolean.TRUE;
        });
  }

  public DsecDiscoveryRulePO get(Long id) {
    DsecDiscoveryRulePO po = mapper.selectOne(
        new LambdaQueryWrapper<DsecDiscoveryRulePO>()
            .eq(DsecDiscoveryRulePO::getId, id)
            .eq(DsecDiscoveryRulePO::getProjectId, currentProject.requireProjectId()));
    if (po == null) {
      throw new SecurityException(SecurityErrorCode.DISCOVERY_RULE_NOT_FOUND, String.valueOf(id));
    }
    return po;
  }

  public PageData<DsecDiscoveryRulePO> page(int pageNo, int pageSize, String keyword) {
    Long projectId = currentProject.requireProjectId();
    LambdaQueryWrapper<DsecDiscoveryRulePO> wrapper =
        new LambdaQueryWrapper<DsecDiscoveryRulePO>().eq(DsecDiscoveryRulePO::getProjectId, projectId);
    if (StringUtils.hasText(keyword)) {
      wrapper.and(c -> c.like(DsecDiscoveryRulePO::getRuleCode, keyword.trim())
          .or().like(DsecDiscoveryRulePO::getRuleName, keyword.trim()));
    }
    wrapper.orderByDesc(DsecDiscoveryRulePO::getId);
    Page<DsecDiscoveryRulePO> page = Page.of(Math.max(1, pageNo), Math.max(1, pageSize));
    var result = mapper.selectPage(page, wrapper);
    return new PageData<>(
        result.getRecords(), result.getTotal(), result.getPages(), (long) pageNo, (long) pageSize);
  }

  /** 对给定字段列表跑启用规则,命中(且未定级)则落 CANDIDATE 标签;返回产出候选数。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public int scan(List<DiscoverableField> fields, String operator) {
    if (fields == null || fields.isEmpty()) {
      return 0;
    }
    Long projectId = currentProject.requireProjectId();
    List<DsecDiscoveryRulePO> rules = mapper.selectList(
        new LambdaQueryWrapper<DsecDiscoveryRulePO>()
            .eq(DsecDiscoveryRulePO::getProjectId, projectId)
            .eq(DsecDiscoveryRulePO::getEnabled, 1));
    if (rules.isEmpty()) {
      throw new SecurityException(SecurityErrorCode.COMPLIANCE_NO_ENABLED_RULE, "无启用的发现规则");
    }
    int produced = 0;
    for (DiscoverableField field : fields) {
      DsecDiscoveryRulePO best = bestMatch(rules, field, projectId);
      if (best == null) {
        continue;
      }
      classificationService.upsert(
          "COLUMN", field.datasourceId(), field.dbName(), field.tableName(), field.columnName(),
          best.getLevelId(), best.getCategoryId(), "DISCOVERED", confidence(best.getMatchType()),
          best.getId(), "CANDIDATE", field.tableName() + "." + field.columnName(), operator);
      produced++;
    }
    return produced;
  }

  /** 单条规则是否命中该字段(纯函数,可单测)。 */
  public static boolean matches(DsecDiscoveryRulePO rule, DiscoverableField field) {
    String pattern = rule.getPattern();
    if (!StringUtils.hasText(pattern)) {
      return false;
    }
    String col = field.columnName() == null ? "" : field.columnName();
    String cmt = field.comment() == null ? "" : field.comment();
    return switch (rule.getMatchType()) {
      case "NAME" -> contains(col, pattern);
      case "COMMENT" -> contains(cmt, pattern);
      case "CONTENT" -> contains(col, pattern) || contains(cmt, pattern);
      case "REGEX" -> regex(pattern, col) || regex(pattern, cmt);
      default -> false;
    };
  }

  private DsecDiscoveryRulePO bestMatch(
      List<DsecDiscoveryRulePO> rules, DiscoverableField field, Long projectId) {
    DsecDiscoveryRulePO best = null;
    int bestRank = -1;
    for (DsecDiscoveryRulePO rule : rules) {
      if (!matches(rule, field)) {
        continue;
      }
      int rank = rankOf(projectId, rule.getLevelId());
      if (rank > bestRank) {
        bestRank = rank;
        best = rule;
      }
    }
    return best;
  }

  private int rankOf(Long projectId, Long levelId) {
    DsecSecurityLevelPO level = levelMapper.selectOne(scopedLevel(projectId, levelId));
    return level == null || level.getRankNo() == null ? 0 : level.getRankNo();
  }

  private static int confidence(String matchType) {
    return "REGEX".equals(matchType) ? 80 : 90;
  }

  private static boolean contains(String text, String keyword) {
    return text.toLowerCase().contains(keyword.toLowerCase());
  }

  private static boolean regex(String pattern, String text) {
    try {
      return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text).find();
    } catch (PatternSyntaxException exception) {
      return false;
    }
  }

  private void validatePattern(String matchType, String pattern) {
    if ("REGEX".equals(matchType)) {
      try {
        Pattern.compile(pattern);
      } catch (PatternSyntaxException exception) {
        throw new SecurityException(SecurityErrorCode.DISCOVERY_INVALID_PATTERN, pattern, exception);
      }
    }
  }

  private boolean existsByCode(String code) {
    Long c = mapper.selectCount(
        new LambdaQueryWrapper<DsecDiscoveryRulePO>()
            .eq(DsecDiscoveryRulePO::getProjectId, currentProject.requireProjectId())
            .eq(DsecDiscoveryRulePO::getRuleCode, code));
    return c != null && c > 0;
  }

  private LambdaQueryWrapper<DsecSecurityLevelPO> scopedLevel(Long projectId, Long levelId) {
    return new LambdaQueryWrapper<DsecSecurityLevelPO>()
        .eq(DsecSecurityLevelPO::getId, levelId)
        .eq(DsecSecurityLevelPO::getProjectId, projectId);
  }
}
