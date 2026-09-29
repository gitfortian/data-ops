package io.yak.ops.business.analysis.domain;

import io.yak.ops.business.analysis.query.AnalysisQuerySpec;
import io.yak.ops.business.analysis.visualization.AnalysisChartType;
import io.yak.ops.business.analysis.visualization.AnalysisVisualConfig;
import java.time.Instant;

/** Current reusable BI analysis definition. Dashboard layout remains outside Analysis. */
public record AnalysisAsset(
    long id,
    String name,
    String description,
    long datasetId,
    AnalysisChartType chartType,
    AnalysisQuerySpec querySpec,
    AnalysisVisualConfig visualConfig,
    Instant createTime,
    Instant updateTime) {
}
