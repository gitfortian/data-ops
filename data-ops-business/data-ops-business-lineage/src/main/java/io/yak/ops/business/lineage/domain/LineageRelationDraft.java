package io.yak.ops.business.lineage.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;

/** Validated domain write model for registering a lineage relation. */
public record LineageRelationDraft(
    Long projectId,
    long sourceAssetId,
    long targetAssetId,
    LineageRelationType relationType,
    String sourceType,
    String sourceId,
    String expression,
    BigDecimal confidence,
    String version,
    Instant observedAt,
    JsonNode properties) {

  public LineageRelationDraft(
      long sourceAssetId,
      long targetAssetId,
      LineageRelationType relationType,
      String sourceType,
      String sourceId,
      String expression,
      BigDecimal confidence,
      String version,
      Instant observedAt,
      JsonNode properties) {
    this(
        null,
        sourceAssetId,
        targetAssetId,
        relationType,
        sourceType,
        sourceId,
        expression,
        confidence,
        version,
        observedAt,
        properties);
  }
}
