package io.yak.ops.business.consumption.relationship;

/** Explicit normalization result so evidence gaps are never reported as zero usage. */
public record UsageNormalizationResult(
    UsageNormalizationState state,
    UsageEvidence evidence,
    String providerEvidenceRef,
    String message) {

  public static UsageNormalizationResult normalized(UsageEvidence evidence) {
    return new UsageNormalizationResult(
        UsageNormalizationState.NORMALIZED,
        evidence,
        evidence.providerEvidenceRef(),
        null);
  }

  public static UsageNormalizationResult ignored(String ref, String message) {
    return new UsageNormalizationResult(UsageNormalizationState.IGNORED, null, ref, message);
  }

  public static UsageNormalizationResult gap(String ref, String message) {
    return new UsageNormalizationResult(UsageNormalizationState.GAP, null, ref, message);
  }

  public static UsageNormalizationResult unavailable(String ref, String message) {
    return new UsageNormalizationResult(UsageNormalizationState.UNAVAILABLE, null, ref, message);
  }
}
