package io.yak.ops.business.analysis.controller.v1.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.yak.ops.business.analysis.query.AnalysisAggregation;
import io.yak.ops.business.analysis.query.AnalysisFilterOperator;
import io.yak.ops.business.analysis.query.AnalysisSortDirection;
import io.yak.ops.business.analysis.visualization.AnalysisChartType;
import java.time.Instant;
import java.util.List;

public final class AnalysisViews {
  private AnalysisViews() {}

  public record Analysis(
      @JsonSerialize(using = ToStringSerializer.class) long id,
      String name,
      String description,
      @JsonSerialize(using = ToStringSerializer.class) long datasetId,
      AnalysisChartType chartType,
      QuerySpec querySpec,
      VisualConfig visualConfig,
      Instant createTime,
      Instant updateTime) {
  }

  public record QuerySpec(
      List<String> dimensions,
      List<Metric> metrics,
      List<Filter> filters,
      List<Sort> sorts,
      int limit,
      int timeoutSeconds) {
  }

  public record Metric(String fieldId, AnalysisAggregation aggregation) {
  }

  public record Filter(String fieldId, AnalysisFilterOperator operator, Object value) {
  }

  public record Sort(
      String fieldId,
      AnalysisAggregation aggregation,
      AnalysisSortDirection direction) {
  }

  public record VisualConfig(
      boolean showLegend,
      boolean showDataLabels,
      boolean smooth,
      boolean showGrid) {
  }
}
