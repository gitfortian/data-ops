package io.yak.ops.business.agent.domain;

/** Frozen server-prepared comparison identity; never an authorization grant. */
public record ModelStructureReviewTarget(String modelId, int baselineVersionNo, String definition) {
  public ModelStructureReviewTarget {
    if (modelId == null || !modelId.matches("[1-9][0-9]{0,18}")
        || (modelId.length() == 19 && modelId.compareTo("9223372036854775807") > 0)
        || baselineVersionNo <= 0 || definition == null || !definition.matches("[a-f0-9]{64}")) {
      throw new IllegalArgumentException("请从原模型版本页重新准备结构比较");
    }
  }
}
