package io.yak.ops.business.dataset.controller.v1.dto;

import io.yak.ops.business.dataset.DatasetAggregation;
import io.yak.ops.business.dataset.DatasetFieldDataType;
import io.yak.ops.business.dataset.DatasetFieldRole;
import io.yak.ops.business.dataset.DatasetFilterOperator;
import io.yak.ops.business.dataset.DatasetSortDirection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** HTTP input contracts for Dataset v1 APIs. */
public final class DatasetRequests {

  private DatasetRequests() {
  }

  public record PublishDatasetRequest(
      @NotNull Long sourceTaskAssetId,
      @Size(max = 200) String name,
      @Size(max = 2000) String description,
      List<@Valid DatasetFieldRequest> fields) {
  }

  public record CreateDatasetVersionRequest(List<@Valid DatasetFieldRequest> fields) {
  }

  public record DatasetFieldRequest(
      @Size(max = 64) String fieldId,
      @NotNull @Size(max = 128) String physicalName,
      @Size(max = 200) String displayName,
      DatasetFieldDataType dataType,
      boolean nullable,
      @Size(max = 1000) String description,
      DatasetFieldRole defaultRole) {
  }

  public record QueryDatasetRequest(
      @Min(1) Integer versionNo,
      List<@Size(max = 64) String> dimensions,
      List<@Valid QueryMetricRequest> metrics,
      List<@Valid QueryFilterRequest> filters,
      List<@Valid QuerySortRequest> sorts,
      @Min(1) @Max(1000) Integer limit,
      @Min(1) @Max(120) Integer timeoutSeconds) {
  }

  public record QueryMetricRequest(
      @NotNull @Size(max = 64) String fieldId,
      @NotNull DatasetAggregation aggregation) {
  }

  public record QueryFilterRequest(
      @NotNull @Size(max = 64) String fieldId,
      @NotNull DatasetFilterOperator operator,
      Object value,
      @Size(max = 100) List<Object> values) {
  }

  public record QuerySortRequest(
      @NotNull @Size(max = 64) String fieldId,
      DatasetAggregation aggregation,
      DatasetSortDirection direction) {
  }
}
