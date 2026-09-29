package io.yak.ops.business.analysis.lineage;

import io.yak.ops.business.analysis.query.AnalysisQuerySpec;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Extracts deterministic Dataset-field usage roles from Analysis query semantics. */
@Component
public class AnalysisFieldUsageExtractor {

  public Map<String, FieldUsage> extract(AnalysisQuerySpec querySpec) {
    LinkedHashMap<String, MutableUsage> usages = new LinkedHashMap<>();
    if (querySpec == null) return Map.of();

    querySpec.dimensions().forEach(fieldId -> usage(usages, fieldId).roles.add("DIMENSION"));
    querySpec.metrics().forEach(metric -> {
      if (metric == null) return;
      MutableUsage usage = usage(usages, metric.fieldId());
      usage.roles.add("METRIC");
      if (metric.aggregation() != null) usage.metricAggregations.add(metric.aggregation().name());
    });
    querySpec.filters().forEach(filter -> {
      if (filter != null) usage(usages, filter.fieldId()).roles.add("FILTER");
    });
    querySpec.sorts().forEach(sort -> {
      if (sort == null) return;
      MutableUsage usage = usage(usages, sort.fieldId());
      usage.roles.add("SORT");
      if (sort.aggregation() != null) usage.sortAggregations.add(sort.aggregation().name());
    });

    LinkedHashMap<String, FieldUsage> result = new LinkedHashMap<>();
    usages.forEach((fieldId, value) -> result.put(
        fieldId,
        new FieldUsage(
            List.copyOf(value.roles),
            List.copyOf(value.metricAggregations),
            List.copyOf(value.sortAggregations))));
    return Collections.unmodifiableMap(result);
  }

  private MutableUsage usage(Map<String, MutableUsage> usages, String fieldId) {
    if (fieldId == null || fieldId.isBlank()) {
      throw new IllegalStateException("Analysis lineage fieldId 不能为空");
    }
    return usages.computeIfAbsent(fieldId.trim(), ignored -> new MutableUsage());
  }

  public record FieldUsage(
      List<String> roles,
      List<String> metricAggregations,
      List<String> sortAggregations) {
  }

  private static final class MutableUsage {
    private final LinkedHashSet<String> roles = new LinkedHashSet<>();
    private final LinkedHashSet<String> metricAggregations = new LinkedHashSet<>();
    private final LinkedHashSet<String> sortAggregations = new LinkedHashSet<>();
  }
}
