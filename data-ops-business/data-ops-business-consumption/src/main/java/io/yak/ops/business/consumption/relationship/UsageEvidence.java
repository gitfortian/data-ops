package io.yak.ops.business.consumption.relationship;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.identity.SourceVersionRef;
import java.time.LocalDateTime;
import java.util.Objects;

/** Consumption-owned normalized proof of one successful product consumption. */
public record UsageEvidence(
    Long id,
    Long projectId,
    ProductKey productKey,
    SourceVersionRef sourceVersion,
    ConsumerRef consumerRef,
    LocalDateTime observedAt,
    ConsumptionMode consumptionMode,
    UsageOutcome outcome,
    String provider,
    String providerEvidenceRef,
    String deduplicationId,
    LocalDateTime normalizedAt) {

  public UsageEvidence {
    Objects.requireNonNull(projectId, "projectId");
    Objects.requireNonNull(productKey, "productKey");
    Objects.requireNonNull(sourceVersion, "sourceVersion");
    Objects.requireNonNull(consumerRef, "consumerRef");
    Objects.requireNonNull(observedAt, "observedAt");
    Objects.requireNonNull(consumptionMode, "consumptionMode");
    Objects.requireNonNull(outcome, "outcome");
    if (outcome != UsageOutcome.SUCCESS) {
      throw new IllegalArgumentException("UsageEvidence only accepts successful consumption");
    }
    provider = requireText(provider, "provider");
    providerEvidenceRef = requireText(providerEvidenceRef, "providerEvidenceRef");
    deduplicationId = requireText(deduplicationId, "deduplicationId");
    Objects.requireNonNull(normalizedAt, "normalizedAt");
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
