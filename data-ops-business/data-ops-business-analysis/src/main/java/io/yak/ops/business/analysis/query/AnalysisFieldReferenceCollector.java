package io.yak.ops.business.analysis.query;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Collects all Dataset field ids referenced by one declarative Analysis query. */
@Component
public class AnalysisFieldReferenceCollector {

  public Set<String> collect(AnalysisQuerySpec spec) {
    LinkedHashSet<String> values = new LinkedHashSet<>();
    if (spec == null) return Set.of();
    values.addAll(spec.dimensions());
    spec.metrics().forEach(metric -> values.add(metric.fieldId()));
    spec.filters().forEach(filter -> values.add(filter.fieldId()));
    spec.sorts().forEach(sort -> values.add(sort.fieldId()));
    return Set.copyOf(values);
  }
}
