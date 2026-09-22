package io.yak.ops.business.analysis.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.yak.ops.business.analysis.domain.AnalysisDefinition;
import io.yak.ops.business.analysis.gateway.dataset.AnalysisDatasetGateway;
import io.yak.ops.business.analysis.query.AnalysisAggregation;
import io.yak.ops.business.analysis.query.AnalysisFieldReferenceCollector;
import io.yak.ops.business.analysis.query.AnalysisMetricBinding;
import io.yak.ops.business.analysis.query.AnalysisQueryNormalizer;
import io.yak.ops.business.analysis.query.AnalysisQuerySpec;
import io.yak.ops.business.analysis.visualization.AnalysisChartBindingPolicy;
import io.yak.ops.business.analysis.visualization.AnalysisChartType;
import io.yak.ops.business.analysis.visualization.AnalysisVisualPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisDefinitionNormalizerTest {

  @Test
  void normalizesDefinitionAndValidatesOnlyReferencedDatasetFields() {
    AnalysisDatasetGateway datasets = mock(AnalysisDatasetGateway.class);
    AnalysisDefinitionNormalizer normalizer = new AnalysisDefinitionNormalizer(
        new AnalysisQueryNormalizer(new AnalysisChartBindingPolicy()),
        new AnalysisVisualPolicy(),
        new AnalysisFieldReferenceCollector(),
        datasets);

    AnalysisDefinition definition = normalizer.normalize(new AnalysisSaveCommand(
        "  区域销售  ",
        null,
        9L,
        AnalysisChartType.BAR,
        new AnalysisQuerySpec(
            List.of("region"),
            List.of(new AnalysisMetricBinding("amount", AnalysisAggregation.SUM)),
            List.of(),
            List.of(),
            0,
            0),
        null));

    assertThat(definition.name()).isEqualTo("区域销售");
    assertThat(definition.querySpec().limit()).isEqualTo(500);
    assertThat(definition.querySpec().timeoutSeconds()).isEqualTo(30);
    assertThat(definition.visualConfig().showGrid()).isTrue();
    verify(datasets).requireBindable(
        eq(9L),
        argThat(fields -> fields.size() == 2
            && fields.contains("region")
            && fields.contains("amount")));
  }
}
