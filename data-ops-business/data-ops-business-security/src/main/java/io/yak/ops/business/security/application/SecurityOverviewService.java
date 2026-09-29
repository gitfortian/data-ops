package io.yak.ops.business.security.application;

import io.yak.ops.business.security.api.AccessDecision;
import io.yak.ops.business.security.domain.SecurityOverview;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 数据安全总览聚合服务(票据 79)。只做读侧拼装,不持有独立事务:
 * 依赖各能力服务已有的计数方法,避免重复查询逻辑。
 */
@Component
public class SecurityOverviewService {

  private final SecurityLevelService levelService;
  private final DataCategoryService categoryService;
  private final ClassificationService classificationService;
  private final AccessPolicyService accessPolicyService;
  private final MaskingService maskingService;
  private final AccessLogService accessLogService;
  private final ComplianceService complianceService;

  public SecurityOverviewService(
      SecurityLevelService levelService,
      DataCategoryService categoryService,
      ClassificationService classificationService,
      AccessPolicyService accessPolicyService,
      MaskingService maskingService,
      AccessLogService accessLogService,
      ComplianceService complianceService) {
    this.levelService = levelService;
    this.categoryService = categoryService;
    this.classificationService = classificationService;
    this.accessPolicyService = accessPolicyService;
    this.maskingService = maskingService;
    this.accessLogService = accessLogService;
    this.complianceService = complianceService;
  }

  public SecurityOverview overview() {
    long classifiedTotal = classificationService.countTotal();
    return new SecurityOverview(
        levelService.countAll(),
        categoryService.listAll().size(),
        classifiedTotal,
        classificationService.countByStatus("ACTIVE"),
        classificationService.countByStatus("CANDIDATE"),
        classificationService.levelDistribution(),
        accessPolicyService.countEnabled(),
        maskingService.countPolicies(),
        accessLogService.countByDecision(AccessDecision.DENY),
        accessLogService.countMasked(),
        safeActors(),
        safeComplianceSummary());
  }

  private List<Map<String, Object>> safeActors() {
    try {
      return accessLogService.topActors();
    } catch (RuntimeException ex) {
      return List.of();
    }
  }

  private Map<String, Object> safeComplianceSummary() {
    try {
      Map<String, Object> summary = complianceService.latestSummary();
      return summary == null ? Map.of() : summary;
    } catch (RuntimeException ex) {
      return Map.of();
    }
  }
}
