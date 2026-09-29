package io.yak.ops.business.analysis.controller.v1.converter;

import io.yak.ops.business.analysis.AnalysisService;
import io.yak.ops.business.analysis.controller.v1.dto.AnalysisRequests.SaveAnalysisRequest;
import io.yak.ops.business.analysis.query.AnalysisFilterBinding;
import io.yak.ops.business.analysis.query.AnalysisMetricBinding;
import io.yak.ops.business.analysis.query.AnalysisQuerySpec;
import io.yak.ops.business.analysis.query.AnalysisSortBinding;
import io.yak.ops.business.analysis.visualization.AnalysisVisualConfig;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AnalysisRequestConverter {

  public AnalysisService.SaveCommand toCommand(SaveAnalysisRequest request) {
    var source = request.querySpec();
    AnalysisQuerySpec querySpec = new AnalysisQuerySpec(
        source.dimensions() == null ? List.of() : source.dimensions(),
        source.metrics() == null ? List.of() : source.metrics().stream()
            .map(value -> new AnalysisMetricBinding(value.fieldId(), value.aggregation()))
            .toList(),
        source.filters() == null ? List.of() : source.filters().stream()
            .map(value -> new AnalysisFilterBinding(value.fieldId(), value.operator(), value.value()))
            .toList(),
        source.sorts() == null ? List.of() : source.sorts().stream()
            .map(value -> new AnalysisSortBinding(
                value.fieldId(), value.aggregation(), value.direction()))
            .toList(),
        source.limit() == null ? 0 : source.limit(),
        source.timeoutSeconds() == null ? 0 : source.timeoutSeconds());
    AnalysisVisualConfig visualConfig = request.visualConfig() == null ? null
        : new AnalysisVisualConfig(
            request.visualConfig().showLegend(),
            request.visualConfig().showDataLabels(),
            request.visualConfig().smooth(),
            request.visualConfig().showGrid());
    return new AnalysisService.SaveCommand(
        request.name(),
        request.description(),
        request.datasetId(),
        request.chartType(),
        querySpec,
        visualConfig);
  }
}
