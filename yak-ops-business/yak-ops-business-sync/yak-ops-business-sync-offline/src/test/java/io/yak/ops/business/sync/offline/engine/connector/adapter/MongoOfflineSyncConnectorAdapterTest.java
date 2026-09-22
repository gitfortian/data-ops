package io.yak.ops.business.sync.offline.engine.connector.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.BuildContext;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.ExecutionContext;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.Role;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import java.util.List;
import org.junit.jupiter.api.Test;

class MongoOfflineSyncConnectorAdapterTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final MongoOfflineSyncConnectorAdapter adapter =
      new MongoOfflineSyncConnectorAdapter(mapper);

  @Test
  void buildsFieldSelectedSourceWithoutPersistingDatasourceSecrets() throws Exception {
    ObjectNode config =
        (ObjectNode)
            mapper.readTree(
                """
                {
                  "table":"users",
                  "fields":["_id","name","address.city"],
                  "connectorOptions":{
                    "filter":"{\"status\":\"ACTIVE\"}",
                    "uri":"mongodb://must-not-persist",
                    "password":"must-not-persist"
                  }
                }
                """);
    ObjectNode options = (ObjectNode) config.path("connectorOptions").deepCopy();

    var result = adapter.build(context(Role.SOURCE, "GUIDE_SINGLE", config, options));

    assertThat(result.options().path("collection").asText()).isEqualTo("users");
    assertThat(result.options().path("fields")).hasSize(3);
    assertThat(result.options().path("filter").asText()).contains("ACTIVE");
    assertThat(result.options().path("fetch_size").asInt()).isEqualTo(1000);
    assertThat(result.options().has("uri")).isFalse();
    assertThat(result.options().has("password")).isFalse();

    DataSourcePO dataSource = new DataSourcePO();
    dataSource.setConnectionParams(
        "{\"host\":\"mongo-a\",\"port\":27017,\"hosts\":\"mongo-a:27017,mongo-b:27017\","
            + "\"database\":\"business\",\"username\":\"yak user\","
            + "\"password\":\"s ecret\",\"authSource\":\"admin\"}");
    adapter.resolveForExecution(
        new ExecutionContext("mongodb", Role.SOURCE, "source", dataSource, result.options()));

    assertThat(result.options().path("database").asText()).isEqualTo("business");
    assertThat(result.options().path("uri").asText())
        .isEqualTo(
            "mongodb://yak%20user:s%20ecret@mongo-a:27017,mongo-b:27017/business?authSource=admin");
  }

  @Test
  void buildsInsertOnlySinkAndKeepsDocumentIdAsAdvancedOption() throws Exception {
    ObjectNode config =
        (ObjectNode)
            mapper.readTree(
                """
                {
                  "table":"users_copy",
                  "autoCreateTable":false,
                  "writeMode":"append",
                  "connectorOptions":{
                    "document_id_field":"user_id",
                    "batch_size":200
                  }
                }
                """);
    ObjectNode options = (ObjectNode) config.path("connectorOptions").deepCopy();

    var result = adapter.build(context(Role.SINK, "GUIDE_SINGLE", config, options));

    assertThat(result.options().path("collection").asText()).isEqualTo("users_copy");
    assertThat(result.options().path("document_id_field").asText()).isEqualTo("user_id");
    assertThat(result.options().path("batch_size").asInt()).isEqualTo(200);
  }

  @Test
  void rejectsUnsupportedSqlUpsertAndDirectGuideMulti() throws Exception {
    ObjectNode source =
        (ObjectNode) mapper.readTree("{\"readMode\":\"sql\",\"sql\":\"select 1\"}");
    assertThatThrownBy(
            () -> adapter.build(context(Role.SOURCE, "GUIDE_SINGLE", source, mapper.createObjectNode())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不支持 SQL");

    ObjectNode sink =
        (ObjectNode)
            mapper.readTree(
                "{\"table\":\"users\",\"autoCreateTable\":false,\"writeMode\":\"upsert\"}");
    assertThatThrownBy(
            () -> adapter.build(context(Role.SINK, "GUIDE_SINGLE", sink, mapper.createObjectNode())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Append/INSERT");

    ObjectNode multi = (ObjectNode) mapper.readTree("{\"table\":\"users\"}");
    assertThatThrownBy(
            () -> adapter.build(context(Role.SOURCE, "GUIDE_MULTI", multi, mapper.createObjectNode())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("FAN_OUT");
  }

  private BuildContext context(
      Role role,
      String mode,
      ObjectNode config,
      ObjectNode options) {
    return new BuildContext(
        "mongodb",
        role,
        mode,
        "mongo-test",
        config,
        mapper.createObjectNode(),
        options,
        1000,
        500,
        role == Role.SINK ? List.of("users") : List.of());
  }
}
