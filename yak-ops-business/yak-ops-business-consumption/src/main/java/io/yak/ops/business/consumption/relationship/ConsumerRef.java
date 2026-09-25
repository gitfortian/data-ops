package io.yak.ops.business.consumption.relationship;

import java.util.Locale;
import java.util.Objects;

/** Stable consumer reference; displayHint is presentation-only and never part of identity. */
public record ConsumerRef(
    ConsumerType consumerType,
    String sourceDomain,
    String sourceIdentity,
    String displayHint) {

  public ConsumerRef {
    Objects.requireNonNull(consumerType, "consumerType");
    sourceDomain = requireText(sourceDomain, "sourceDomain").toUpperCase(Locale.ROOT);
    sourceIdentity = requireText(sourceIdentity, "sourceIdentity");
    displayHint = normalize(displayHint);
  }

  public String identityKey() {
    return consumerType.name() + ":" + sourceDomain + ":" + sourceIdentity;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
