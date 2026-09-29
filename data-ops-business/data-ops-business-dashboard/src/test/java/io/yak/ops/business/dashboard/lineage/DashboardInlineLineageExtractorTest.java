package io.yak.ops.business.dashboard.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DashboardInlineLineageExtractorTest {

  @Test
  void extractsDatasetAndDeterministicFieldUsageRoles() {
    DashboardInlineLineageExtractor extractor =
        new DashboardInlineLineageExtractor(new ObjectMapper());
    Object inline = Map.of(
        "datasetId", 21L,
        "chartType", "BAR",
        "querySpec", Map.of(
            "dimensions", List.of("region"),
            "metrics", List.of(Map.of("fieldId", "amount")),
            "filters", List.of(Map.of("fieldId", "region")),
            "sorts", List.of(Map.of("fieldId", "amount"))));

    var binding = extractor.extract(inline);

    assertThat(binding.datasetId()).isEqualTo(21L);
    assertThat(binding.parseStatus()).isEqualTo("SUCCESS");
    assertThat(binding.fieldUsages().get("region")).containsExactly("DIMENSION", "FILTER");
    assertThat(binding.fieldUsages().get("amount")).containsExactly("METRIC", "SORT");
  }
}
