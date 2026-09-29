package io.yak.ops.business.sync.realtime.engine;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Validates role-specific connector support against the Flink CDC capability manifest. */
@Component
public class RealtimeConnectorCapabilityResolver {

  public void requireSupported(
      JsonNode manifest, ResolvedCdcPipeline pipeline, CdcPipelineSpec spec) {
    JsonNode connectors = manifest.path("connectors");
    Set<String> sources = values(connectors.path("sources"));
    Set<String> sinks = values(connectors.path("sinks"));

    if (!sources.contains("mysql") || pipeline.source().dbType() != DataSourceDbType.MYSQL) {
      throw new IllegalStateException("Flink CDC 不支持 MySQL CDC Source");
    }

    String sinkCapability =
        pipeline.sink().dbType() == DataSourceDbType.POSTGRE_SQL
            ? "yak-jdbc:postgres"
            : "yak-jdbc:mysql";
    if (!sinks.contains(sinkCapability)) {
      throw new IllegalStateException("Flink CDC 不支持 Sink Connector：" + sinkCapability);
    }

    String delivery = manifest.path("deliverySemantics").asText("");
    if (!"at-least-once".equalsIgnoreCase(delivery)) {
      throw new IllegalStateException("Flink CDC 交付语义与一期 At-least-once 契约不一致");
    }
    if (spec.schemaEvolution() == CdcPipelineSpec.SchemaEvolution.EVOLVE
        && (!connectors.path("schemaEvolution").isArray()
            || connectors.path("schemaEvolution").isEmpty())) {
      throw new IllegalStateException("Flink CDC 未声明 Schema Evolution 能力");
    }
  }

  private Set<String> values(JsonNode array) {
    Set<String> values = new HashSet<>();
    if (array.isArray()) {
      array.forEach(value -> values.add(value.asText("").toLowerCase(Locale.ROOT)));
    }
    return values;
  }
}
