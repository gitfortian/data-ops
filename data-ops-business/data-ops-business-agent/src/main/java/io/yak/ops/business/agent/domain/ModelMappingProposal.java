package io.yak.ops.business.agent.domain;

import java.util.List;

/** SDK structured response; business labels and types are supplied by the source domain. */
public record ModelMappingProposal(List<Choice> candidates, List<String> questions) {
  public record Choice(String sourceColumn, String reason) {}
}
