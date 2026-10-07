package io.yak.ops.business.agent.domain;

import java.util.List;

/** Server-validated delivery; source labels and identities are never accepted from model prose. */
public record StandardMatchSuggestion(String kind, StandardMatchTarget target, String expectedDefinition,
    int skillVersion, String skillHash, boolean truncated, List<Candidate> candidates, List<String> questions) {
  public record Candidate(long standardId, int version, String code, String name, String stdType, String reason) {}
}
