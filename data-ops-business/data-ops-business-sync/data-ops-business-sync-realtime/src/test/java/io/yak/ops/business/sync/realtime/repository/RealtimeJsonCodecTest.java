package io.yak.ops.business.sync.realtime.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.repository.support.RealtimeJsonCodec;
import org.junit.jupiter.api.Test;

class RealtimeJsonCodecTest {

  private final RealtimeJsonCodec codec =
      new RealtimeJsonCodec(new ObjectMapper().findAndRegisterModules());

  @Test
  void acceptsMissingSpecForTwoStageDrafts() {
    assertThat(codec.readSpec(null)).isNull();
    assertThat(codec.readSpec("  ")).isNull();
  }

  @Test
  void roundTripsPipelineSpec() {
    CdcPipelineSpec spec = RealtimeTestFixtures.spec();
    assertThat(codec.readSpec(codec.write(spec))).isEqualTo(spec);
  }

  @Test
  void keepsInvalidSpecAsRequestStyleArgumentError() {
    assertThatThrownBy(() -> codec.readSpec("{bad-json"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Spec 无效");
  }
}
