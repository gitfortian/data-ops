package io.yak.ops.business.sync.realtime.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CdcPipelineSpecValidatorTest {

  private final CdcPipelineSpecValidator validator = new CdcPipelineSpecValidator();

  @Test
  void rejectsUnsafeReplayConfiguration() {
    assertThatThrownBy(() -> validator.validate(spec(false, List.of("id"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("strict Replay Safety");
  }

  @Test
  void rejectsRoutesWithoutPrimaryKeyDeclaration() {
    assertThatThrownBy(() -> validator.validate(spec(true, List.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("主键");
  }

  private CdcPipelineSpec spec(boolean strict, List<String> keys) {
    return new CdcPipelineSpec(
        1L,
        2L,
        List.of(
            new CdcPipelineSpec.TableRoute(
                "orders", "dw.orders", CdcPipelineSpec.MatchMode.EXACT, keys)),
        "initial",
        CdcPipelineSpec.SchemaEvolution.EVOLVE,
        1,
        60_000,
        new CdcPipelineSpec.RestartPolicy("fixed-delay", 3, 1_000),
        new CdcPipelineSpec.SinkTuning(3, 100, 1_000, 1_048_576, 20, strict));
  }
}
