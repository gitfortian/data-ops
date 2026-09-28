package io.yak.ops.business.consumption.product.model;

/** Keeps an access decision separate from the health/state of the access provider itself. */
public record AccessProjection(
    AccessDecision decision,
    ProviderEvidenceState providerState,
    String reason,
    String subject,
    String action,
    String plane,
    String nextStep) {

  public AccessProjection(
      AccessDecision decision, ProviderEvidenceState providerState, String reason) {
    this(decision, providerState, reason, null, null, null, null);
  }

  public AccessProjection {
    if (providerState == null) {
      throw new NullPointerException("providerState");
    }
    if (providerState == ProviderEvidenceState.READY && decision == null) {
      throw new IllegalArgumentException("READY access projection requires a decision");
    }
    if (providerState != ProviderEvidenceState.READY && decision != null) {
      throw new IllegalArgumentException("non-READY access projection must not fabricate a decision");
    }
  }

  public static AccessProjection ready(AccessDecision decision) {
    return new AccessProjection(decision, ProviderEvidenceState.READY, null);
  }

  public static AccessProjection unavailable(String reason) {
    return new AccessProjection(null, ProviderEvidenceState.UNAVAILABLE, reason);
  }

  public static AccessProjection unavailable(
      String reason, String subject, String action, String plane, String nextStep) {
    return new AccessProjection(
        null, ProviderEvidenceState.UNAVAILABLE, reason, subject, action, plane, nextStep);
  }
}
