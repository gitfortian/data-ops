package io.yak.ops.business.analysis.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.analysis.visualization.AnalysisChartBindingPolicy;
import io.yak.ops.business.analysis.visualization.AnalysisChartType;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisQueryNormalizerTest {

  private final AnalysisQueryNormalizer normalizer =
      new AnalysisQueryNormalizer(new AnalysisChartBindingPolicy());

  @Test
  void appliesCurrentDefaultsWithoutChangingQueryMeaning() {
    AnalysisQuerySpec normalized = normalizer.normalize(
        new AnalysisQuerySpec(
            List.of("region"),
            List.of(new AnalysisMetricBinding("amount", AnalysisAggregation.SUM)),
            List.of(),
            List.of(),
            0,
            0),
        AnalysisChartType.BAR);

    assertThat(normalized.limit()).isEqualTo(500);
    assertThat(normalized.timeoutSeconds()).isEqualTo(30);
    assertThat(normalized.dimensions()).containsExactly("region");
  }

  @Test
  void metricCardStillRejectsDimensions() {
    assertThatThrownBy(() -> normalizer.normalize(
        new AnalysisQuerySpec(
            List.of("region"),
            List.of(new AnalysisMetricBinding("amount", AnalysisAggregation.SUM)),
            List.of(),
            List.of(),
            100,
            30),
        AnalysisChartType.METRIC))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("指标卡不能配置维度");
  }
}
