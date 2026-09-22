package io.yak.ops.business.lineage.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;

/** Directed upstream-to-downstream edge with optional provenance evidence. */
public record LineageRelation(
    long id,
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
    JsonNode properties,
    Instant createTime,
    Instant updateTime) {

  public LineageRelation(
      long id,
      long sourceAssetId,
      long targetAssetId,
      LineageRelationType relationType,
      String sourceType,
      String sourceId,
      String expression,
      BigDecimal confidence,
      String version,
      Instant observedAt,
      JsonNode properties,
      Instant createTime,
      Instant updateTime) {
    this(
        id,
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
        properties,
        createTime,
        updateTime);
  }
}
