package io.yak.ops.business.asset.api;

import java.time.Instant;
import java.util.List;

/**
 * Product projection of quality facts. Quality domain remains the source of truth.
 */
public record QualitySectionSummary(
    String truthOwner,
    String status,
    String latestStatus,
    String summary,
    Instant latestObservedAt,
    List<QualityEvidence> evidence,
    QualityProvenance provenance) {

  public QualitySectionSummary {
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
  }

  public record QualityEvidence(
      String sourceType,
      String sourceId,
      String description,
      Instant collectedAt) {}

  public record QualityProvenance(
      String domain,
      String querySource,
      String queryTraceId) {}

  public static QualitySectionSummary unavailable(String reason) {
    return new QualitySectionSummary(
        "QUALITY",
        "UNAVAILABLE",
        "UNKNOWN",
        reason,
        null,
        List.of(),
        new QualityProvenance("QUALITY", "quality-reader", null));
  }

  public static QualitySectionSummary empty(String reason) {
    return new QualitySectionSummary(
        "QUALITY",
        "EMPTY",
        "NO_DATA",
        reason,
        null,
        List.of(),
        new QualityProvenance("QUALITY", "quality-reader", null));
  }
}
