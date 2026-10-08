package io.yak.ops.business.agent.domain;

import java.util.List;

public record ModelMappingSuggestion(String kind, ModelMappingTarget target, String expectedDefinition,
    String sourceDefinition, String targetType, int skillVersion, String skillHash,
    boolean truncated, List<Candidate> candidates, List<String> questions) {
  public record Candidate(String sourceColumn, String type, boolean nullable, String reason) {}
}
