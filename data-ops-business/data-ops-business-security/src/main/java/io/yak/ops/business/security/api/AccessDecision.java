package io.yak.ops.business.security.api;

/** 数据级访问裁决结果。decision ∈ ALLOW / DENY / NEED_APPROVAL。 */
public record AccessDecision(
    boolean allowed,
    String decision,
    Long matchedPolicyId,
    boolean masked,
    String algoCode) {

  public static final String ALLOW = "ALLOW";
  public static final String DENY = "DENY";
  public static final String NEED_APPROVAL = "NEED_APPROVAL";
}
