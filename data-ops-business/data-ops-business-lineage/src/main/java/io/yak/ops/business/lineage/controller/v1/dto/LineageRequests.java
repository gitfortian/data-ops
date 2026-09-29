package io.yak.ops.business.lineage.controller.v1.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** HTTP request contracts for lineage registration APIs. */
public final class LineageRequests {

  private LineageRequests() {
  }

  public record RegisterAssetRequest(
      @NotBlank @Size(max = 512) String assetKey,
      @NotNull LineageAssetType assetType,
      @Size(max = 200) String name,
      @Size(max = 64) String sourceType,
      @Size(max = 200) String sourceId,
      Long parentAssetId,
      @Size(max = 64) String dataSourceId,
      @Size(max = 256) String databaseName,
      @Size(max = 256) String schemaName,
      @Size(max = 256) String tableName,
      @Size(max = 256) String columnName,
      JsonNode properties) {
  }

  public record RegisterRelationRequest(
      @NotNull Long sourceAssetId,
      @NotNull Long targetAssetId,
      @NotNull LineageRelationType relationType,
      @Size(max = 64) String sourceType,
      @Size(max = 200) String sourceId,
      @Size(max = 16000) String expression,
      @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
      @Size(max = 128) String version,
      Instant observedAt,
      JsonNode properties) {
  }
}
