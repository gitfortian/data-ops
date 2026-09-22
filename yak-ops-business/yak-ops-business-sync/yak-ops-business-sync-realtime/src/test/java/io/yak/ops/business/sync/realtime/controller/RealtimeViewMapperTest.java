package io.yak.ops.business.sync.realtime.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.realtime.controller.v1.mapper.RealtimeViewMapper;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RealtimeViewMapperTest {

  @Test
  void keepsExistingNumericIdContract() throws Exception {
    long largeId = 9_007_199_254_740_993L;
    RealtimeJobView value = new RealtimeJobView(
        largeId,
        "job",
        null,
        null,
        3L,
        "DRAFT",
        "STOPPED",
        "STOPPED",
        1,
        null,
        null,
        null,
        LocalDateTime.of(2026, 8, 22, 10, 0),
        LocalDateTime.of(2026, 8, 22, 10, 0),
        null);

    JsonNode json =
        new ObjectMapper()
            .findAndRegisterModules()
            .valueToTree(new RealtimeViewMapper().toView(value));

    assertThat(json.path("id").isIntegralNumber()).isTrue();
    assertThat(json.path("id").asLong()).isEqualTo(largeId);
    assertThat(json.path("runtimeEnvironmentId").asLong()).isEqualTo(3L);
  }
}
