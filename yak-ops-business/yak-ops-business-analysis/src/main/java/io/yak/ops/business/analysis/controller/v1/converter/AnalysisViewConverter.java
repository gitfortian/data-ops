package io.yak.ops.business.analysis.controller.v1.converter;

import io.yak.ops.business.analysis.controller.v1.vo.AnalysisViews;
import io.yak.ops.business.analysis.domain.AnalysisAsset;
import io.yak.ops.business.analysis.query.AnalysisQuerySpec;
import io.yak.ops.business.analysis.visualization.AnalysisVisualConfig;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AnalysisViewConverter {

  public AnalysisViews.Analysis toView(AnalysisAsset asset) {
    return new AnalysisViews.Analysis(
        asset.id(),
        asset.name(),
        asset.description(),
        asset.datasetId(),
        asset.chartType(),
        toQuerySpec(asset.querySpec()),
        toVisualConfig(asset.visualConfig()),
        asset.createTime(),
        asset.updateTime());
  }

  public List<AnalysisViews.Analysis> toViews(List<AnalysisAsset> assets) {
    return assets.stream().map(this::toView).toList();
  }

  private AnalysisViews.QuerySpec toQuerySpec(AnalysisQuerySpec spec) {
    return new AnalysisViews.QuerySpec(
        spec.dimensions(),
        spec.metrics().stream()
            .map(value -> new AnalysisViews.Metric(value.fieldId(), value.aggregation()))
            .toList(),
        spec.filters().stream()
            .map(value -> new AnalysisViews.Filter(value.fieldId(), value.operator(), value.value()))
            .toList(),
        spec.sorts().stream()
            .map(value -> new AnalysisViews.Sort(
                value.fieldId(), value.aggregation(), value.direction()))
            .toList(),
        spec.limit(),
        spec.timeoutSeconds());
  }

  private AnalysisViews.VisualConfig toVisualConfig(AnalysisVisualConfig config) {
    return new AnalysisViews.VisualConfig(
        config.showLegend(),
        config.showDataLabels(),
        config.smooth(),
        config.showGrid());
  }
}
