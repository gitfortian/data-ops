package io.yak.ops.business.security.api;

/** 数据级访问裁决结果。decision ∈ ALLOW / DENY / NEED_APPROVAL。 */
public record AccessDecision(
    boolean allowed,
    String decision,
    Long matchedPolicyId,
    boolean maskingRequired,
    String algoCode) {

  /** Compatibility alias: true means a directive exists; it does not mean masking was executed. */
  @Deprecated
  public boolean masked() {
    return maskingRequired;
  }

  public static final String ALLOW = "ALLOW";
  public static final String DENY = "DENY";
  public static final String NEED_APPROVAL = "NEED_APPROVAL";
}
