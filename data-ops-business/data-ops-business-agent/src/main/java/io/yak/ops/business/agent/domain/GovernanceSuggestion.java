package io.yak.ops.business.agent.domain;

import java.math.BigDecimal;
import java.util.List;

/** Validated output of this invocation; never a source-domain command or business truth. */
public record GovernanceSuggestion(String kind, Long targetId, String expectedDefinition,
    List<Rule> rules, String description, List<String> evidenceRefs) {
  public record Rule(long templateId, String name, String columnName, String operator,
      BigDecimal threshold, BigDecimal thresholdEnd, List<String> enumValues, boolean enabled) {}
}
