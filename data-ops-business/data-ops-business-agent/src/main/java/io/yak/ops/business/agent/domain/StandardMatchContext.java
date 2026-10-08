package io.yak.ops.business.agent.domain;

import java.util.List;

public record StandardMatchContext(String modelName, String definition, List<TypeCandidate> candidates, boolean truncated) {
  public record TypeCandidate(long id, int version, String code, String name, String stdType, String description) {}
}
