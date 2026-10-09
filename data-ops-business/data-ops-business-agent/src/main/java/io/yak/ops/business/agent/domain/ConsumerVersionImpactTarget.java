package io.yak.ops.business.agent.domain;

/** Source selection only; IDs remain strings so browser numeric precision cannot change identity. */
public record ConsumerVersionImpactTarget(String productType, String productIdentity, String sourceVersionIdentity) {
  public ConsumerVersionImpactTarget {
    if (!("DATASET".equals(productType) || "DATA_SERVICE".equals(productType)) || productIdentity == null
        || !productIdentity.matches("[1-9][0-9]{0,18}") || Long.parseLong(productIdentity) <= 0
        || sourceVersionIdentity == null || !sourceVersionIdentity.matches("[1-9][0-9]{0,29}")) {
      throw new IllegalArgumentException("请选择规范产品身份与精确来源版本");
    }
  }
}
