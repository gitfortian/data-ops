package io.yak.ops.business.analysis.controller.v1.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.analysis.domain.AnalysisAsset;
import io.yak.ops.business.analysis.query.AnalysisAggregation;
import io.yak.ops.business.analysis.query.AnalysisMetricBinding;
import io.yak.ops.business.analysis.query.AnalysisQuerySpec;
import io.yak.ops.business.analysis.visualization.AnalysisChartType;
import io.yak.ops.business.analysis.visualization.AnalysisVisualConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisViewConverterTest {

  @Test
  void httpIdsRemainStrings() throws Exception {
    AnalysisAsset asset = new AnalysisAsset(
        9007199254740993L,
        "A",
        null,
        9007199254740995L,
        AnalysisChartType.METRIC,
        new AnalysisQuerySpec(
            List.of(),
            List.of(new AnalysisMetricBinding("amount", AnalysisAggregation.SUM)),
            List.of(),
            List.of(),
            100,
            30),
        new AnalysisVisualConfig(false, false, false, false),
        Instant.EPOCH,
        Instant.EPOCH);

    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    JsonNode json = mapper.readTree(mapper.writeValueAsString(new AnalysisViewConverter().toView(asset)));

    assertThat(json.path("id").asText()).isEqualTo("9007199254740993");
    assertThat(json.path("datasetId").asText()).isEqualTo("9007199254740995");
  }
}
