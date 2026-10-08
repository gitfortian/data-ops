package io.yak.ops.business.agent.domain;

import java.util.List;

/** SDK structured-output contract. Everything in this record remains untrusted until validation. */
public record StandardMatchProposal(List<Choice> candidates, List<String> questions, String fieldDescription) {
  public StandardMatchProposal(List<Choice> candidates, List<String> questions) {
    this(candidates, questions, null);
  }
  public record Choice(long standardId, int version, String reason) {}
}
