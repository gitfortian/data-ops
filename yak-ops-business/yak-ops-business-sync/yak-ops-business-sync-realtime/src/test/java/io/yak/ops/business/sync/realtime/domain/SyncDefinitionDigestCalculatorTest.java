package io.yak.ops.business.sync.realtime.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.sync.realtime.repository.support.CdcPipelineSpecCompatibilityMapper;
import io.yak.ops.business.sync.realtime.repository.support.CdcPipelineSpecCompatibilityMapper.MappingResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class SyncDefinitionDigestCalculatorTest {

  private final CdcPipelineSpecCompatibilityMapper mapper =
      new CdcPipelineSpecCompatibilityMapper();

  @Test
  void digestIgnoresRouteAndReplayKeyOrdering() {
    CdcPipelineSpec left =
        spec(
            List.of(
                route("orders", "ods_orders", List.of("tenant_id", "id")),
                route("customers", "ods_customers", List.of("id"))),
            128);
    CdcPipelineSpec right =
        spec(
            List.of(
                route("customers", "ods_customers", List.of("id")),
                route("orders", "ods_orders", List.of("id", "tenant_id"))),
            128);

    DefinitionDigest leftDigest = digest(left, 7);
    DefinitionDigest rightDigest = digest(right, 7);

    assertThat(rightDigest).isEqualTo(leftDigest);
  }

  @Test
  void adapterPrivateStatementCacheDoesNotChangeCoreDefinitionDigest() {
    CdcPipelineSpec left = spec(List.of(route("orders", "ods_orders", List.of("id"))), 64);
    CdcPipelineSpec right = spec(List.of(route("orders", "ods_orders", List.of("id"))), 512);

    assertThat(digest(right, 7)).isEqualTo(digest(left, 7));
    assertThat(mapper.toDomain(right).definition()).isEqualTo(mapper.toDomain(left).definition());
  }

  @Test
  void runtimeEnvironmentBindingChangesDefinitionDigest() {
    CdcPipelineSpec spec = spec(List.of(route("orders", "ods_orders", List.of("id"))), 128);

    assertThat(digest(spec, 8)).isNotEqualTo(digest(spec, 7));
  }

  private DefinitionDigest digest(CdcPipelineSpec spec, long environmentId) {
    MappingResult mapped = mapper.toDomain(spec);
    return SyncDefinitionDigestCalculator.calculate(
        mapped.definition(), new RuntimeEnvironmentRef(environmentId));
  }

  private CdcPipelineSpec spec(List<CdcPipelineSpec.TableRoute> routes, int statementCacheSize) {
    return new CdcPipelineSpec(
        11L,
        22L,
        routes,
        "initial",
        CdcPipelineSpec.SchemaEvolution.EVOLVE,
        2,
        60_000,
        new CdcPipelineSpec.RestartPolicy("fixed-delay", 3, 10_000),
        new CdcPipelineSpec.SinkTuning(
            3, 1_000, 2_000, 16_777_216, statementCacheSize, true));
  }

  private CdcPipelineSpec.TableRoute route(
      String source, String sink, List<String> replayKey) {
    return new CdcPipelineSpec.TableRoute(
        source, sink, CdcPipelineSpec.MatchMode.EXACT, replayKey);
  }
}
