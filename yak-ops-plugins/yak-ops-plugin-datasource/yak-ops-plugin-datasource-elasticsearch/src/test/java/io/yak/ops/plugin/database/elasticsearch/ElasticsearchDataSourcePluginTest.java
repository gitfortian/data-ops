package io.yak.ops.plugin.database.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.spi.datasource.DataSourceCapability;
import io.yak.ops.spi.datasource.DataSourcePluginException.Operation;
import io.yak.ops.spi.datasource.catalog.DataSourceCatalogQuery;
import io.yak.ops.spi.datasource.catalog.DataSourceTablePath;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ElasticsearchDataSourcePluginTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void versionedPluginsNormalizeSdkFreeConnectionsAndOnlyExposeManagementCapabilities() throws Exception {
    Elasticsearch7DataSourcePlugin plugin = new Elasticsearch7DataSourcePlugin();

    ElasticsearchConnection connection =
        (ElasticsearchConnection)
            plugin.parseConnection(
                """
                {
                  "scheme":"https",
                  "host":"es.example.test",
                  "port":9243,
                  "username":"elastic",
                  "password":"secret",
                  "hosts":"https://es.example.test:9243,https://es-b.example.test:9243"
                }
                """);

    assertThat(plugin.dbType()).isEqualTo(DataSourceDbType.ELASTICSEARCH7);
    assertThat(connection.hosts())
        .containsExactly("https://es.example.test:9243", "https://es-b.example.test:9243");
    assertThat(connection.driverClassName()).isNull();
    assertThat(plugin.descriptor().capabilities())
        .containsExactlyInAnyOrder(
            DataSourceCapability.CONNECTION_TEST,
            DataSourceCapability.CATALOG_METADATA);
    assertThat(plugin.descriptor().secretFieldKeys()).contains("password");
    assertThat(plugin.descriptor().capabilities())
        .doesNotContain(DataSourceCapability.CATALOG_READ, DataSourceCapability.TRANSACTIONS);
    assertThat(connection.normalizedJson()).contains("ELASTICSEARCH7", "hosts", "username");
  }

  @Test
  void catalogListsConcreteIndicesAndOnlySingleTargetAliasesAndFlattensMappings() throws Exception {
    ElasticsearchConnection connection =
        new ElasticsearchConnection(
            DataSourceDbType.ELASTICSEARCH7,
            List.of("http://es:9200"),
            null,
            null,
            "{}");
    FakeClient client = new FakeClient(connection, mapper);
    client.add(
        "/",
        """
        {"version":{"number":"7.17.29"}}
        """);
    client.add(
        "/_aliases",
        """
        {
          "orders-0001":{"aliases":{"orders-current":{},"orders-all":{}}},
          "orders-0002":{"aliases":{"orders-all":{}}},
          "customers":{"aliases":{"customers-current":{}}}
        }
        """);
    client.add(
        "/orders-current/_mapping",
        """
        {
          "orders-0001":{
            "mappings":{
              "properties":{
                "id":{"type":"long"},
                "created_at":{"type":"date"},
                "customer":{"properties":{"name":{"type":"keyword"}}}
              }
            }
          }
        }
        """);

    ElasticsearchHttpCatalog catalog = new ElasticsearchHttpCatalog(client, 7);

    assertThat(catalog.listDatabases()).containsExactly("elasticsearch");
    assertThat(catalog.listTables(new DataSourceCatalogQuery("elasticsearch", null, null)))
        .extracting(value -> value.getName() + ":" + value.getType())
        .contains(
            "orders-0001:INDEX",
            "orders-0002:INDEX",
            "customers:INDEX",
            "orders-current:ALIAS",
            "customers-current:ALIAS")
        .doesNotContain("orders-all:ALIAS");

    assertThat(
            catalog.listColumns(
                new DataSourceTablePath("elasticsearch", null, "orders-current")))
        .extracting(column -> column.getName() + ":" + column.getTypeName())
        .containsExactly(
            "id:long",
            "created_at:date",
            "customer:object",
            "customer.name:keyword");
  }

  @Test
  void catalogRejectsWrongServerMajor() throws Exception {
    ElasticsearchConnection connection =
        new ElasticsearchConnection(
            DataSourceDbType.ELASTICSEARCH8,
            List.of("http://es:9200"),
            null,
            null,
            "{}");
    FakeClient client = new FakeClient(connection, mapper);
    client.add("/", "{\"version\":{\"number\":\"7.17.29\"}}");

    ElasticsearchHttpCatalog catalog = new ElasticsearchHttpCatalog(client, 8);
    assertThatThrownBy(catalog::listDatabases)
        .hasMessageContaining("期望 major=8")
        .hasMessageContaining("7.17.29");
  }

  private static final class FakeClient extends ElasticsearchHttpClient {
    private final ObjectMapper mapper;
    private final Map<String, JsonNode> responses = new LinkedHashMap<>();

    private FakeClient(ElasticsearchConnection connection, ObjectMapper mapper) {
      super(connection, 1);
      this.mapper = mapper;
    }

    private void add(String path, String json) throws Exception {
      responses.put(path, mapper.readTree(json));
    }

    @Override
    JsonNode get(String path, Operation operation) {
      JsonNode value = responses.get(path);
      if (value == null) {
        throw new IllegalStateException("missing fake response for " + path);
      }
      return value.deepCopy();
    }
  }
}
