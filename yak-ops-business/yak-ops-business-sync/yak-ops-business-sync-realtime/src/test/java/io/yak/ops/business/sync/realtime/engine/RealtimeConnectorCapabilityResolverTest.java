package io.yak.ops.business.sync.realtime.engine;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.util.List;
import org.junit.jupiter.api.Test;

class RealtimeConnectorCapabilityResolverTest {

  private final ObjectMapper json = new ObjectMapper();
  private final RealtimeConnectorCapabilityResolver resolver =
      new RealtimeConnectorCapabilityResolver();

  @Test
  void distinguishesSourceAndSinkCapabilities() throws Exception {
    ResolvedCdcPipeline pipeline =
        new ResolvedCdcPipeline(
            endpoint(1, DataSourceDbType.MYSQL), endpoint(2, DataSourceDbType.POSTGRE_SQL));
    resolver.requireSupported(
        json.readTree(
            "{\"connectors\":{\"sources\":[\"mysql\"],\"sinks\":[\"yak-jdbc:postgres\"],\"schemaEvolution\":[\"create-table\"]},\"deliverySemantics\":\"at-least-once\"}"),
        pipeline,
        spec());

    assertThatThrownBy(
            () ->
                resolver.requireSupported(
                    json.readTree(
                        "{\"connectors\":{\"sources\":[\"mysql\"],\"sinks\":[\"jdbc\"]},\"deliverySemantics\":\"at-least-once\"}"),
                    pipeline,
                    spec()))
        .hasMessageContaining("yak-jdbc:postgres");
  }

  private ResolvedCdcPipeline.Endpoint endpoint(long id, DataSourceDbType type) {
    return new ResolvedCdcPipeline.Endpoint(
        id, "ds", type, "host", 3306, "jdbc:test", "driver", "user", "db");
  }

  private CdcPipelineSpec spec() {
    return new CdcPipelineSpec(
        1L,
        2L,
        List.of(
            new CdcPipelineSpec.TableRoute(
                "orders", "public.orders", CdcPipelineSpec.MatchMode.EXACT, List.of("id"))),
        "initial",
        CdcPipelineSpec.SchemaEvolution.EVOLVE,
        1,
        60_000,
        new CdcPipelineSpec.RestartPolicy("fixed-delay", 3, 1_000),
        new CdcPipelineSpec.SinkTuning(3, 100, 1_000, 1_048_576, 20, true));
  }
}
