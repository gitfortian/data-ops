package io.yak.ops.business.analysis.controller.v1.dto;

import io.yak.ops.business.analysis.query.AnalysisAggregation;
import io.yak.ops.business.analysis.query.AnalysisFilterOperator;
import io.yak.ops.business.analysis.query.AnalysisSortDirection;
import io.yak.ops.business.analysis.visualization.AnalysisChartType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AnalysisRequests {
  private AnalysisRequests() {}

  public record SaveAnalysisRequest(
      @NotBlank @Size(max = 200) String name,
      @Size(max = 2000) String description,
      @Min(1) long datasetId,
      @NotNull AnalysisChartType chartType,
      @NotNull @Valid QuerySpecRequest querySpec,
      @Valid VisualConfigRequest visualConfig) {
  }

  public record QuerySpecRequest(
      List<@Size(max = 64) String> dimensions,
      List<@Valid MetricRequest> metrics,
      List<@Valid FilterRequest> filters,
      List<@Valid SortRequest> sorts,
      @Min(1) @Max(1000) Integer limit,
      @Min(1) @Max(120) Integer timeoutSeconds) {
  }

  public record MetricRequest(
      @NotBlank @Size(max = 64) String fieldId,
      @NotNull AnalysisAggregation aggregation) {
  }

  public record FilterRequest(
      @NotBlank @Size(max = 64) String fieldId,
      @NotNull AnalysisFilterOperator operator,
      Object value) {
  }

  public record SortRequest(
      @NotBlank @Size(max = 64) String fieldId,
      AnalysisAggregation aggregation,
      AnalysisSortDirection direction) {
  }

  public record VisualConfigRequest(
      boolean showLegend,
      boolean showDataLabels,
      boolean smooth,
      boolean showGrid) {
  }
}
