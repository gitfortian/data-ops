package io.yak.ops.business.sync.realtime.repository;

import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import java.util.List;

final class RealtimeTestFixtures {
  private RealtimeTestFixtures() {}

  static CdcPipelineSpec spec() {
    return new CdcPipelineSpec(
        1L,
        2L,
        List.of(new CdcPipelineSpec.TableRoute(
            "source_table", "sink_table", CdcPipelineSpec.MatchMode.EXACT, List.of("id"))),
        "initial",
        CdcPipelineSpec.SchemaEvolution.EVOLVE,
        1,
        10_000,
        new CdcPipelineSpec.RestartPolicy("none", 0, 0),
        new CdcPipelineSpec.SinkTuning(0, 100, 1000, 1024, 16, true));
  }
}
