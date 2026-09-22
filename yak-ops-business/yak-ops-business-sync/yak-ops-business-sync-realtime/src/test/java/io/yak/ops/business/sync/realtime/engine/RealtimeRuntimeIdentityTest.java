package io.yak.ops.business.sync.realtime.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RealtimeRuntimeIdentityTest {

  @Test
  void createsStableRuntimeNameAndRewritesPipelineName() {
    String key = "same-request-key";
    String name = RealtimeRuntimeIdentity.jobName(key);
    String yaml =
        "pipeline:\n  name: user-facing-name\n  parallelism: 1\nsource:\n  type: mysql\n";

    assertThat(name).startsWith("yak-rt-").hasSize(39);
    assertThat(RealtimeRuntimeIdentity.jobName(key)).isEqualTo(name);
    assertThat(RealtimeRuntimeIdentity.decoratePipeline(yaml, key))
        .contains("pipeline:\n  name: " + name + "\n")
        .doesNotContain("user-facing-name");
  }

  @Test
  void rejectsPipelineWithoutName() {
    assertThatThrownBy(
            () -> RealtimeRuntimeIdentity.decoratePipeline("pipeline:\n  parallelism: 1\n", "key"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("pipeline.name");
  }
}
