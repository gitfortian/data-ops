package io.yak.ops.business.analysis.definition;

import io.yak.ops.business.analysis.domain.AnalysisDefinition;
import io.yak.ops.business.analysis.gateway.dataset.AnalysisDatasetGateway;
import io.yak.ops.business.analysis.query.AnalysisFieldReferenceCollector;
import io.yak.ops.business.analysis.query.AnalysisQueryNormalizer;
import io.yak.ops.business.analysis.query.AnalysisQuerySpec;
import io.yak.ops.business.analysis.visualization.AnalysisChartType;
import io.yak.ops.business.analysis.visualization.AnalysisVisualConfig;
import io.yak.ops.business.analysis.visualization.AnalysisVisualPolicy;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Coordinates definition normalization without owning Dataset or query internals. */
@Component
public class AnalysisDefinitionNormalizer {

  private final AnalysisQueryNormalizer queries;
  private final AnalysisVisualPolicy visuals;
  private final AnalysisFieldReferenceCollector fieldReferences;
  private final AnalysisDatasetGateway datasets;

  public AnalysisDefinitionNormalizer(
      AnalysisQueryNormalizer queries,
      AnalysisVisualPolicy visuals,
      AnalysisFieldReferenceCollector fieldReferences,
      AnalysisDatasetGateway datasets) {
    this.queries = queries;
    this.visuals = visuals;
    this.fieldReferences = fieldReferences;
    this.datasets = datasets;
  }

  public AnalysisDefinition normalize(AnalysisSaveCommand command) {
    Objects.requireNonNull(command, "command");
    String name = required(command.name(), "Analysis 名称", 200);
    String description = optional(command.description(), 2000, "Analysis 描述");
    if (command.datasetId() <= 0L) throw new IllegalArgumentException("datasetId 必须大于 0");
    AnalysisChartType chartType = Objects.requireNonNull(command.chartType(), "chartType");

    AnalysisQuerySpec querySpec = queries.normalize(command.querySpec(), chartType);
    AnalysisVisualConfig visualConfig = visuals.normalize(command.visualConfig(), chartType);
    datasets.requireBindable(command.datasetId(), fieldReferences.collect(querySpec));

    return new AnalysisDefinition(
        name,
        description,
        command.datasetId(),
        chartType,
        querySpec,
        visualConfig);
  }

  private static String required(String value, String label, int maxLength) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(label + "不能超过 " + maxLength + " 个字符");
    }
    return normalized;
  }

  private static String optional(String value, int maxLength, String label) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(label + "不能超过 " + maxLength + " 个字符");
    }
    return normalized;
  }
}
