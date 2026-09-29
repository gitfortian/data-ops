package io.yak.ops.business.analysis.query;

import java.util.List;

/** Declarative use of a Dataset by one Analysis; this is not a query execution runtime. */
public record AnalysisQuerySpec(
    List<String> dimensions,
    List<AnalysisMetricBinding> metrics,
    List<AnalysisFilterBinding> filters,
    List<AnalysisSortBinding> sorts,
    int limit,
    int timeoutSeconds) {

  public AnalysisQuerySpec {
    dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
    metrics = metrics == null ? List.of() : List.copyOf(metrics);
    filters = filters == null ? List.of() : List.copyOf(filters);
    sorts = sorts == null ? List.of() : List.copyOf(sorts);
  }
}
