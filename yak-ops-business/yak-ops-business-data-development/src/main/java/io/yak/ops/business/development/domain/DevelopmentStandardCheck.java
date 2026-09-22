package io.yak.ops.business.development.domain;

import java.util.List;

/**
 * Editor-time standard-compliance report for the output fields of one SQL task
 * (semantic gap ticket 02). Hints only — never a gate: {@code evaluated=false}
 * means the semantic recommendation side abstained or failed.
 */
public record DevelopmentStandardCheck(
    List<FieldCheck> items,
    int fieldCount,
    boolean truncated,
    String parseError) {

  /** One output field against the NAMING standards; unmatched fields carry the suggested standard. */
  public record FieldCheck(
      String field,
      boolean evaluated,
      boolean matched,
      Long standardId,
      String standardCode,
      String standardName,
      String ruleExpr) {}
}
