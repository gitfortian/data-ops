package io.yak.ops.business.sync.realtime.repository.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.ExactTableSelector;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.FixedDelayRestart;
import io.yak.ops.business.sync.realtime.repository.support.CdcPipelineSpecCompatibilityMapper.MappingResult;
import io.yak.ops.business.sync.realtime.repository.support.CdcPipelineSpecCompatibilityMapper.UnsupportedLegacyDefinitionException;
import java.util.List;
import org.junit.jupiter.api.Test;

class CdcPipelineSpecCompatibilityMapperTest {

  private final CdcPipelineSpecCompatibilityMapper mapper =
      new CdcPipelineSpecCompatibilityMapper();

  @Test
  void mapsLegacySpecToCoreDefinitionAndBackWithoutLeakingAdapterTuning() {
    CdcPipelineSpec legacy =
        spec(new CdcPipelineSpec.RestartPolicy("fixed-delay", 3, 10_000), 256);

    MappingResult mapped = mapper.toDomain(legacy);

    assertThat(mapped.definition().source().dataSourceRef()).isEqualTo(11);
    assertThat(mapped.definition().sink().dataSourceRef()).isEqualTo(22);
    assertThat(mapped.definition().routes()).hasSize(1);
    assertThat(mapped.definition().routes().getFirst().source())
        .isInstanceOf(ExactTableSelector.class);
    assertThat(mapped.definition().executionPolicy().restartPolicy())
        .isEqualTo(new FixedDelayRestart(3, 10_000));
    assertThat(mapped.legacyAdapterTuning().statementCacheSize()).isEqualTo(256);

    CdcPipelineSpec roundTrip =
        mapper.toLegacy(mapped.definition(), mapped.legacyAdapterTuning());
    assertThat(roundTrip).isEqualTo(legacy);
  }

  @Test
  void incompleteLegacyFailureRatePolicyIsNotPretendedToBeCoreDomain() {
    CdcPipelineSpec legacy =
        spec(new CdcPipelineSpec.RestartPolicy("failure-rate", 3, 10_000), 128);

    assertThatThrownBy(() -> mapper.toDomain(legacy))
        .isInstanceOf(UnsupportedLegacyDefinitionException.class)
        .hasMessageContaining("failure-rate");
  }

  private CdcPipelineSpec spec(
      CdcPipelineSpec.RestartPolicy restart, int statementCacheSize) {
    return new CdcPipelineSpec(
        11L,
        22L,
        List.of(
            new CdcPipelineSpec.TableRoute(
                "orders",
                "ods_orders",
                CdcPipelineSpec.MatchMode.EXACT,
                List.of("tenant_id", "id"))),
        "initial",
        CdcPipelineSpec.SchemaEvolution.EVOLVE,
        2,
        60_000,
        restart,
        new CdcPipelineSpec.SinkTuning(
            3, 1_000, 2_000, 16_777_216, statementCacheSize, true));
  }
}
