package io.yak.ops.business.sync.offline.engine.connector.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.BuildContext;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.ExecutionContext;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.Role;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import java.util.List;
import org.junit.jupiter.api.Test;

class ElasticsearchOfflineSyncConnectorAdapterTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final ElasticsearchOfflineSyncConnectorAdapter adapter =
      new ElasticsearchOfflineSyncConnectorAdapter(mapper);

  @Test
  void buildsCredentialFreeSingleIndexSourceAndInjectsConnectionOnlyAtExecution() throws Exception {
    ObjectNode config =
        (ObjectNode)
            mapper.readTree(
                """
                {
                  "table":"orders-v1",
                  "connectorOptions":{
                    "scroll_size":321,
                    "query":{"term":{"tenant_id":7}},
                    "password":"must-not-persist",
                    "connect_timeout_ms":999
                  }
                }
                """);
    ObjectNode options = (ObjectNode) config.path("connectorOptions").deepCopy();

    var result =
        adapter.build(
            context("elasticsearch7", Role.SOURCE, "GUIDE_SINGLE", config, options));

    assertThat(result.options().path("index").asText()).isEqualTo("orders-v1");
    assertThat(result.options().path("scroll_size").asInt()).isEqualTo(321);
    JsonNode query = mapper.readTree(result.options().path("query").asText());
    assertThat(query.path("term").path("tenant_id").asInt()).isEqualTo(7);
    assertThat(result.options().has("password")).isFalse();
    assertThat(result.options().has("connect_timeout_ms")).isFalse();

    DataSourcePO dataSource = new DataSourcePO();
    dataSource.setConnectionParams(
        "{\"hosts\":[\"http://es-a:9200\",\"http://es-b:9200\"],"
            + "\"username\":\"elastic\",\"password\":\"secret\","
            + "\"connect_timeout_ms\":12000}");
    adapter.resolveForExecution(
        new ExecutionContext(
            "elasticsearch7", Role.SOURCE, "source", dataSource, result.options()));

    assertThat(result.options().path("hosts")).hasSize(2);
    assertThat(result.options().path("username").asText()).isEqualTo("elastic");
    assertThat(result.options().path("password").asText()).isEqualTo("secret");
    assertThat(result.options().path("connect_timeout_ms").asInt()).isEqualTo(12000);
  }

  @Test
  void keepsDocumentIdAsAdvancedSinkOptionWithoutExposingUpsert() throws Exception {
    ObjectNode config =
        (ObjectNode)
            mapper.readTree(
                """
                {
                  "targetTableName":"orders-v2",
                  "autoCreateTable":false,
                  "writeMode":"append",
                  "connectorOptions":{
                    "document_id_field":"id",
                    "max_retries":3,
                    "batch_size":200
                  }
                }
                """);
    ObjectNode options = (ObjectNode) config.path("connectorOptions").deepCopy();

    var result =
        adapter.build(
            context("elasticsearch8", Role.SINK, "GUIDE_SINGLE", config, options));

    assertThat(result.options().path("index").asText()).isEqualTo("orders-v2");
    assertThat(result.options().path("document_id_field").asText()).isEqualTo("id");
    assertThat(result.options().path("max_retries").asInt()).isEqualTo(3);
    assertThat(result.options().path("batch_size").asInt()).isEqualTo(200);
    assertThat(result.options().has("write_mode")).isFalse();
  }

  @Test
  void rejectsMultiIndexAndUnsupportedSinkSemantics() throws Exception {
    ObjectNode source =
        (ObjectNode) mapper.readTree("{\"table\":\"orders-*\"}");
    assertThatThrownBy(
            () ->
                adapter.build(
                    context(
                        "elasticsearch7",
                        Role.SOURCE,
                        "GUIDE_SINGLE",
                        source,
                        mapper.createObjectNode())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("一个明确");

    ObjectNode sink =
        (ObjectNode)
            mapper.readTree(
                "{\"targetTableName\":\"orders\",\"autoCreateTable\":true,\"writeMode\":\"append\"}");
    assertThatThrownBy(
            () ->
                adapter.build(
                    context(
                        "elasticsearch8",
                        Role.SINK,
                        "GUIDE_SINGLE",
                        sink,
                        mapper.createObjectNode())))
        .hasMessageContaining("不支持自动创建");

    sink.put("autoCreateTable", false);
    sink.put("writeMode", "upsert");
    assertThatThrownBy(
            () ->
                adapter.build(
                    context(
                        "elasticsearch8",
                        Role.SINK,
                        "GUIDE_SINGLE",
                        sink,
                        mapper.createObjectNode())))
        .hasMessageContaining("Append");
  }

  @Test
  void rejectsGuideMultiEvenThoughPr7SupportsFanOutForOtherConnectors() throws Exception {
    ObjectNode config = (ObjectNode) mapper.readTree("{\"table\":\"orders\"}");

    assertThatThrownBy(
            () ->
                adapter.build(
                    context(
                        "elasticsearch7",
                        Role.SOURCE,
                        "GUIDE_MULTI",
                        config,
                        mapper.createObjectNode())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("GUIDE_SINGLE");
  }

  private BuildContext context(
      String connectorId,
      Role role,
      String mode,
      ObjectNode config,
      ObjectNode options) {
    return new BuildContext(
        connectorId,
        role,
        mode,
        "es-test",
        config,
        mapper.createObjectNode(),
        options,
        1000,
        500,
        role == Role.SINK ? List.of("orders") : List.of());
  }
}
