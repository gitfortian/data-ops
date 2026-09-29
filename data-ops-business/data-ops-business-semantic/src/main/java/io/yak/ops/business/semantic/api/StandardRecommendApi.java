package io.yak.ops.business.semantic.api;

import java.util.List;

/**
 * Rule-based standard recommendation SPI (ticket 39; shared by modeling 39/41
 * and the agent later). Recommendation never blocks a flow — callers treat
 * results as hints. Implementations must not leak internal types.
 */
public interface StandardRecommendApi {

  RecommendationReport recommend(RecommendRequest request);

  /** 推荐输入:字段名/类型/角色(UNKNOWN 时不做维度/度量倾向推荐)。 */
  record RecommendRequest(String fieldName, String dataType, String role) {}

  /** 一条候选标准(引用方一键套用时直接落 std_*_id = standardId)。 */
  record StandardCandidate(
      Long standardId,
      String kind,
      String code,
      String name,
      String codeSetCode,
      String ruleExpr,
      String reason) {}

  /** 命名校验结果:matched=字段名符合该命名标准;不匹配时 standard 为建议修正项。 */
  record NamingCheck(
      boolean evaluated, boolean matched, Long standardId, String code, String name,
      String ruleExpr) {}

  /** 推荐报告(各分组可为空;候选上限 3)。 */
  record RecommendationReport(
      NamingCheck naming,
      List<StandardCandidate> typeCandidates,
      List<StandardCandidate> codeCandidates,
      List<StandardCandidate> unitCandidates,
      List<StandardCandidate> caliberCandidates,
      List<StandardCandidate> securityCandidates) {}
}
