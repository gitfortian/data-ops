package io.yak.ops.business.security.domain;

import java.util.List;
import java.util.Map;

/**
 * 数据安全总览:把分级分类、权限、脱敏、审计、合规五条能力的关键指标聚合为一次可读快照,
 * 供前端总览页(overview)一次拉取,避免前端拼多个接口。
 */
public record SecurityOverview(
    long levelCount,
    long categoryCount,
    long classifiedTotal,
    long activeClassification,
    long candidateClassification,
    List<LevelCount> levelDistribution,
    long enabledPolicyCount,
    long maskingPolicyCount,
    long accessDenyRecent,
    long accessMaskedRecent,
    List<Map<String, Object>> topActors,
    Map<String, Object> complianceSummary) {

  public static SecurityOverview empty() {
    return new SecurityOverview(
        0, 0, 0, 0, 0, List.of(), 0, 0, 0, 0, List.of(), Map.of());
  }
}
