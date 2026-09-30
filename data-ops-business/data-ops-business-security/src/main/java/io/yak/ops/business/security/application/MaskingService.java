package io.yak.ops.business.security.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.security.api.ClassificationView;
import io.yak.ops.business.security.api.MaskingDirective;
import io.yak.ops.business.security.api.SecurityMaskingApi;
import io.yak.ops.business.security.dao.mapper.MaskingAlgorithmMapper;
import io.yak.ops.business.security.dao.mapper.MaskingPolicyMapper;
import io.yak.ops.business.security.exception.SecurityException;
import io.yak.ops.business.security.support.audit.SecurityAudit;
import io.yak.ops.common.bean.po.security.DsecMaskingAlgorithmPO;
import io.yak.ops.common.bean.po.security.DsecMaskingPolicyPO;
import io.yak.ops.common.enums.security.SecurityErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 脱敏服务:算法字典 + 脱敏策略 CRUD,并作为脱敏 SPI 裁决 + 执行。 */
@Component
public class MaskingService implements SecurityMaskingApi {

  private final MaskingAlgorithmMapper algorithmMapper;
  private final MaskingPolicyMapper policyMapper;
  private final ClassificationService classificationService;
  private final CurrentProject currentProject;
  private final BusinessAuditService auditService;

  public MaskingService(
      MaskingAlgorithmMapper algorithmMapper,
      MaskingPolicyMapper policyMapper,
      ClassificationService classificationService,
      CurrentProject currentProject,
      BusinessAuditService auditService) {
    this.algorithmMapper = algorithmMapper;
    this.policyMapper = policyMapper;
    this.classificationService = classificationService;
    this.currentProject = currentProject;
    this.auditService = auditService;
  }

  // ==== 脱敏 SPI ====

  @Override
  public MaskingDirective resolve(String objectKey) {
    ClassificationView view = classificationService.find(objectKey);
    if (view == null || !"ACTIVE".equals(view.status())) {
      return MaskingDirective.none();
    }
    String columnName = lastSegment(objectKey);
    Long projectId = currentProject.requireProjectId();
    List<DsecMaskingPolicyPO> policies = policyMapper.selectList(
        new LambdaQueryWrapper<DsecMaskingPolicyPO>()
            .eq(DsecMaskingPolicyPO::getProjectId, projectId)
            .eq(DsecMaskingPolicyPO::getEnabled, 1));
    DsecMaskingPolicyPO hit = policies.stream()
        .filter(p -> matches(p, view, columnName))
        .max(Comparator.comparingInt(p -> p.getPriority() == null ? 0 : p.getPriority()))
        .orElse(null);
    if (hit == null) {
      return MaskingDirective.none();
    }
    DsecMaskingAlgorithmPO algo = algorithmMapper.selectOne(new LambdaQueryWrapper<DsecMaskingAlgorithmPO>()
        .eq(DsecMaskingAlgorithmPO::getId, hit.getAlgoId())
        .eq(DsecMaskingAlgorithmPO::getProjectId, projectId)
        .eq(DsecMaskingAlgorithmPO::getStatus, "ACTIVE"));
    if (algo == null || !MaskingEngine.supported().contains(algo.getAlgoCode())) {
      throw new SecurityException(SecurityErrorCode.MASKING_UNSUPPORTED_ALGO,
          algo == null ? String.valueOf(hit.getAlgoId()) : algo.getAlgoCode());
    }
    validateAlgorithm(algo.getAlgoCode(), algo.getParams());
    return new MaskingDirective(true, algo.getAlgoCode(), algo.getParams());
  }

  /** Returns whether each existing classification has a matching enabled masking policy. */
  public Map<String, Boolean> maskingCoverage(List<ClassificationView> classifications) {
    if (classifications == null || classifications.isEmpty()) {
      return Map.of();
    }
    Long projectId = currentProject.requireProjectId();
    List<DsecMaskingPolicyPO> policies = policyMapper.selectList(
        new LambdaQueryWrapper<DsecMaskingPolicyPO>()
            .eq(DsecMaskingPolicyPO::getProjectId, projectId)
            .eq(DsecMaskingPolicyPO::getEnabled, 1));
    Set<Long> algorithmIds = new HashSet<>(
        algorithmMapper.selectList(
                new LambdaQueryWrapper<DsecMaskingAlgorithmPO>()
                    .eq(DsecMaskingAlgorithmPO::getProjectId, projectId)
                    .eq(DsecMaskingAlgorithmPO::getStatus, "ACTIVE"))
            .stream()
            .filter(algo -> MaskingEngine.supported().contains(algo.getAlgoCode()))
            .map(DsecMaskingAlgorithmPO::getId)
            .toList());

    Map<String, Boolean> result = new HashMap<>();
    for (ClassificationView classification : classifications) {
      String columnName = lastSegment(classification.objectKey());
      DsecMaskingPolicyPO matched = policies.stream()
          .filter(policy -> matches(policy, classification, columnName))
          .max(Comparator.comparingInt(policy -> policy.getPriority() == null ? 0 : policy.getPriority()))
          .orElse(null);
      result.put(
          classification.objectKey(),
          "ACTIVE".equals(classification.status())
              && matched != null
              && matched.getAlgoId() != null
              && algorithmIds.contains(matched.getAlgoId()));
    }
    return Map.copyOf(result);
  }

  @Override
  public String mask(String value, String algoCode, String algoParams) {
    return MaskingEngine.mask(value, algoCode, algoParams);
  }

  private static boolean matches(
      DsecMaskingPolicyPO policy, ClassificationView view, String columnName) {
    if (policy.getLevelId() != null && policy.getLevelId().equals(view.levelId())) {
      return true;
    }
    if (policy.getCategoryId() != null && policy.getCategoryId().equals(view.categoryId())) {
      return true;
    }
    return StringUtils.hasText(policy.getColumnPattern())
        && columnMatch(policy.getColumnPattern(), columnName);
  }

  static boolean columnMatch(String pattern, String columnName) {
    if (!StringUtils.hasText(columnName)) {
      return false;
    }
    if (pattern.indexOf('*') < 0) {
      return pattern.equalsIgnoreCase(columnName);
    }
    String regex = pattern.replace(".", "\\.").replace("*", ".*");
    return columnName.matches("(?i)" + regex);
  }

  private static String lastSegment(String objectKey) {
    if (objectKey == null) {
      return null;
    }
    int idx = objectKey.lastIndexOf(':');
    String col = idx < 0 ? objectKey : objectKey.substring(idx + 1);
    return "-".equals(col) ? null : col;
  }

  // ==== 算法字典 CRUD ====

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DsecMaskingAlgorithmPO createAlgorithm(
      String code, String name, String params, String description, String operator) {
    String normalizedCode = StringUtils.hasText(code) ? code.trim().toUpperCase(java.util.Locale.ROOT) : null;
    validateAlgorithm(normalizedCode, params);
    Long projectId = currentProject.requireProjectId();
    if (existsAlgorithm(normalizedCode)) {
      throw new SecurityException(SecurityErrorCode.MASKING_ALGO_DUPLICATE, normalizedCode);
    }
    return SecurityAudit.tx(
        auditService,
        AuditEventType.RESOURCE_CREATED,
        SecurityAudit.request("MASKING_ALGO_CREATE", "Create masking algorithm", "MASKING_ALGO", null, normalizedCode),
        () -> {
          LocalDateTime now = LocalDateTime.now();
          DsecMaskingAlgorithmPO po = new DsecMaskingAlgorithmPO();
          po.setProjectId(projectId);
          po.setAlgoCode(normalizedCode);
          po.setAlgoName(name);
          po.setParams(params);
          po.setBuiltin(0);
          po.setDescription(description);
          po.setStatus("ACTIVE");
          po.setCreatedBy(operator);
          po.setCreateTime(now);
          po.setUpdateTime(now);
          algorithmMapper.insert(po);
          return po;
        });
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void deleteAlgorithm(Long id) {
    DsecMaskingAlgorithmPO po = getAlgorithm(id);
    if (po.getBuiltin() != null && po.getBuiltin() == 1) {
      throw new SecurityException(SecurityErrorCode.MASKING_ALGO_BUILTIN_READONLY, po.getAlgoCode());
    }
    SecurityAudit.tx(
        auditService,
        AuditEventType.RESOURCE_DELETED,
        SecurityAudit.request(
            "MASKING_ALGO_DELETE", "Delete masking algorithm", "MASKING_ALGO", String.valueOf(id), po.getAlgoCode()),
        () -> {
          algorithmMapper.deleteById(id);
          return Boolean.TRUE;
        });
  }

  public DsecMaskingAlgorithmPO getAlgorithm(Long id) {
    DsecMaskingAlgorithmPO po = algorithmMapper.selectOne(
        new LambdaQueryWrapper<DsecMaskingAlgorithmPO>()
            .eq(DsecMaskingAlgorithmPO::getId, id)
            .eq(DsecMaskingAlgorithmPO::getProjectId, currentProject.requireProjectId()));
    if (po == null) {
      throw new SecurityException(SecurityErrorCode.MASKING_ALGO_NOT_FOUND, String.valueOf(id));
    }
    return po;
  }

  public List<DsecMaskingAlgorithmPO> listAlgorithms() {
    return algorithmMapper.selectList(
        new LambdaQueryWrapper<DsecMaskingAlgorithmPO>()
            .eq(DsecMaskingAlgorithmPO::getProjectId, currentProject.requireProjectId())
            .orderByAsc(DsecMaskingAlgorithmPO::getBuiltin)
            .orderByAsc(DsecMaskingAlgorithmPO::getId));
  }

  // ==== 策略 CRUD ====

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DsecMaskingPolicyPO createPolicy(
      String name, Long levelId, Long categoryId, String columnPattern, Long algoId,
      Integer priority, Boolean enabled, String description, String operator) {
    if (!StringUtils.hasText(name) || algoId == null) {
      throw new SecurityException(SecurityErrorCode.MASKING_INVALID_ALGO, "策略名与算法必填");
    }
    Long projectId = currentProject.requireProjectId();
    DsecMaskingAlgorithmPO algorithm = algorithmMapper.selectOne(new LambdaQueryWrapper<DsecMaskingAlgorithmPO>()
        .eq(DsecMaskingAlgorithmPO::getId, algoId)
        .eq(DsecMaskingAlgorithmPO::getProjectId, projectId));
    if (algorithm == null || !"ACTIVE".equals(algorithm.getStatus())
        || !MaskingEngine.supported().contains(algorithm.getAlgoCode())) {
      throw new SecurityException(SecurityErrorCode.MASKING_INVALID_ALGO, String.valueOf(algoId));
    }
    validateAlgorithm(algorithm.getAlgoCode(), algorithm.getParams());
    return SecurityAudit.tx(
        auditService,
        AuditEventType.RESOURCE_CREATED,
        SecurityAudit.request("MASKING_POLICY_CREATE", "Create masking policy", "MASKING_POLICY", null, name),
        () -> {
          LocalDateTime now = LocalDateTime.now();
          DsecMaskingPolicyPO po = new DsecMaskingPolicyPO();
          po.setProjectId(projectId);
          po.setPolicyName(name);
          po.setLevelId(levelId);
          po.setCategoryId(categoryId);
          po.setColumnPattern(columnPattern);
          po.setAlgoId(algoId);
          po.setPriority(priority == null ? 0 : priority);
          po.setEnabled(Boolean.FALSE.equals(enabled) ? 0 : 1);
          po.setDescription(description);
          po.setCreatedBy(operator);
          po.setCreateTime(now);
          po.setUpdateTime(now);
          policyMapper.insert(po);
          return po;
        });
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void deletePolicy(Long id) {
    getPolicy(id);
    SecurityAudit.tx(
        auditService,
        AuditEventType.RESOURCE_DELETED,
        SecurityAudit.request(
            "MASKING_POLICY_DELETE", "Delete masking policy", "MASKING_POLICY", String.valueOf(id), null),
        () -> {
          policyMapper.deleteById(id);
          return Boolean.TRUE;
        });
  }

  public DsecMaskingPolicyPO getPolicy(Long id) {
    DsecMaskingPolicyPO po = policyMapper.selectOne(
        new LambdaQueryWrapper<DsecMaskingPolicyPO>()
            .eq(DsecMaskingPolicyPO::getId, id)
            .eq(DsecMaskingPolicyPO::getProjectId, currentProject.requireProjectId()));
    if (po == null) {
      throw new SecurityException(SecurityErrorCode.MASKING_POLICY_NOT_FOUND, String.valueOf(id));
    }
    return po;
  }

  public PageData<DsecMaskingPolicyPO> pagePolicies(int pageNo, int pageSize, String keyword) {
    Long projectId = currentProject.requireProjectId();
    LambdaQueryWrapper<DsecMaskingPolicyPO> wrapper =
        new LambdaQueryWrapper<DsecMaskingPolicyPO>().eq(DsecMaskingPolicyPO::getProjectId, projectId);
    if (StringUtils.hasText(keyword)) {
      wrapper.like(DsecMaskingPolicyPO::getPolicyName, keyword.trim());
    }
    wrapper.orderByDesc(DsecMaskingPolicyPO::getPriority).orderByDesc(DsecMaskingPolicyPO::getId);
    Page<DsecMaskingPolicyPO> page = Page.of(Math.max(1, pageNo), Math.max(1, pageSize));
    var result = policyMapper.selectPage(page, wrapper);
    return new PageData<>(
        result.getRecords(), result.getTotal(), result.getPages(), (long) pageNo, (long) pageSize);
  }

  public long countPolicies() {
    Long c = policyMapper.selectCount(
        new LambdaQueryWrapper<DsecMaskingPolicyPO>()
            .eq(DsecMaskingPolicyPO::getProjectId, currentProject.requireProjectId()));
    return c == null ? 0L : c;
  }

  private boolean existsAlgorithm(String code) {
    Long c = algorithmMapper.selectCount(
        new LambdaQueryWrapper<DsecMaskingAlgorithmPO>()
            .eq(DsecMaskingAlgorithmPO::getProjectId, currentProject.requireProjectId())
            .eq(DsecMaskingAlgorithmPO::getAlgoCode, code));
    return c != null && c > 0;
  }

  private void validateAlgorithm(String code, String params) {
    try {
      MaskingEngine.validate(code, params);
    } catch (IllegalArgumentException exception) {
      throw new SecurityException(SecurityErrorCode.MASKING_UNSUPPORTED_ALGO, code, exception);
    }
  }
}
