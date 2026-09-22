package io.yak.ops.business.sync.offline.engine.connector.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.ExecutionContext;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.Role;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import org.junit.jupiter.api.Test;

class JdbcOfflineSyncConnectorAdapterExecutionTest {

  @Test
  void injectsOceanBaseCompatibilityAndDatasourcePropertiesOnlyAtExecutionTime() {
    ObjectMapper mapper = new ObjectMapper();
    JdbcOfflineSyncConnectorAdapter adapter = new JdbcOfflineSyncConnectorAdapter(mapper);
    DataSourcePO dataSource = new DataSourcePO();
    dataSource.setId(10L);
    dataSource.setName("oceanbase");
    dataSource.setDbType(DataSourceDbType.OCEANBASE);
    dataSource.setConnectionParams(
        "{\"jdbcUrl\":\"jdbc:oceanbase://127.0.0.1:2881/app\","
            + "\"driverClassName\":\"com.oceanbase.jdbc.Driver\","
            + "\"username\":\"root\",\"password\":\"secret\","
            + "\"compatibleMode\":\"oracle\","
            + "\"properties\":{\"connectTimeout\":\"30000\"}}");

    ObjectNode options = mapper.createObjectNode();
    options.put("table_path", "APP.ORDERS");
    adapter.resolveForExecution(
        new ExecutionContext("jdbc", Role.SOURCE, "来源端", dataSource, options));

    assertThat(options.path("url").asText())
        .isEqualTo("jdbc:oceanbase://127.0.0.1:2881/app");
    assertThat(options.path("driver").asText()).isEqualTo("com.oceanbase.jdbc.Driver");
    assertThat(options.path("compatible_mode").asText()).isEqualTo("oracle");
    assertThat(options.path("properties").path("connectTimeout").asText()).isEqualTo("30000");
    assertThat(options.path("password").asText()).isEqualTo("secret");
  }

  @Test
  void injectsExplicitTiDbDialectForMysqlProtocolUrls() {
    ObjectMapper mapper = new ObjectMapper();
    JdbcOfflineSyncConnectorAdapter adapter = new JdbcOfflineSyncConnectorAdapter(mapper);
    DataSourcePO dataSource = new DataSourcePO();
    dataSource.setId(11L);
    dataSource.setName("tidb");
    dataSource.setDbType(DataSourceDbType.TIDB);
    dataSource.setConnectionParams(
        "{\"jdbcUrl\":\"jdbc:mysql://127.0.0.1:4000/app\","
            + "\"driverClassName\":\"com.mysql.cj.jdbc.Driver\","
            + "\"username\":\"root\",\"password\":\"secret\"}");

    ObjectNode options = mapper.createObjectNode();
    options.put("dialect", "mysql");
    options.put("table_path", "app.orders");
    adapter.resolveForExecution(
        new ExecutionContext("jdbc", Role.SOURCE, "来源端", dataSource, options));

    assertThat(options.path("url").asText())
        .isEqualTo("jdbc:mysql://127.0.0.1:4000/app");
    assertThat(options.path("driver").asText()).isEqualTo("com.mysql.cj.jdbc.Driver");
    assertThat(options.path("dialect").asText()).isEqualTo("tidb");
    assertThat(options.path("password").asText()).isEqualTo("secret");
  }

  @Test
  void injectsGoldenDbDialectAndForcesExistingTableSink() {
    ObjectMapper mapper = new ObjectMapper();
    JdbcOfflineSyncConnectorAdapter adapter = new JdbcOfflineSyncConnectorAdapter(mapper);
    DataSourcePO dataSource = new DataSourcePO();
    dataSource.setId(13L);
    dataSource.setName("goldendb");
    dataSource.setDbType(DataSourceDbType.GOLDENDB);
    dataSource.setConnectionParams(
        "{\"jdbcUrl\":\"jdbc:mysql://127.0.0.1:1111/archive\","
            + "\"driverClassName\":\"com.mysql.cj.jdbc.Driver\","
            + "\"username\":\"root\",\"password\":\"secret\"}");

    ObjectNode options = mapper.createObjectNode();
    options.put("dialect", "mysql");
    options.put("schema_save_mode", "CREATE_SCHEMA_WHEN_NOT_EXIST");
    options.put("table_path", "archive.orders");
    adapter.resolveForExecution(
        new ExecutionContext("jdbc", Role.SINK, "目标端", dataSource, options));

    assertThat(options.path("url").asText())
        .isEqualTo("jdbc:mysql://127.0.0.1:1111/archive");
    assertThat(options.path("driver").asText()).isEqualTo("com.mysql.cj.jdbc.Driver");
    assertThat(options.path("dialect").asText()).isEqualTo("goldendb");
    assertThat(options.path("schema_save_mode").asText())
        .isEqualTo("ERROR_WHEN_SCHEMA_NOT_EXIST");
    assertThat(options.path("password").asText()).isEqualTo("secret");
  }

  @Test
  void injectsGBaseDialectAndDriverFallbacks() {
    assertGBaseExecution(
        DataSourceDbType.GBASE8C,
        "jdbc:gbase8c://127.0.0.1:5432/app",
        "gbase8c",
        "com.gbase8c.Driver");
    assertGBaseExecution(
        DataSourceDbType.GBASE8A,
        "jdbc:gbase://127.0.0.1:5258/app",
        "gbase8a",
        "com.gbase.jdbc.Driver");
    assertGBaseExecution(
        DataSourceDbType.GBASE8S,
        "jdbc:gbasedbt-sqli://127.0.0.1:9088/app:GBASEDBTSERVER=node1",
        "gbase8s",
        "com.gbasedbt.jdbc.Driver");
  }

  @Test
  void rejectsGBaseUpsertAtExecutionBoundary() {
    ObjectMapper mapper = new ObjectMapper();
    JdbcOfflineSyncConnectorAdapter adapter = new JdbcOfflineSyncConnectorAdapter(mapper);
    DataSourcePO dataSource = new DataSourcePO();
    dataSource.setId(14L);
    dataSource.setName("gbase8c");
    dataSource.setDbType(DataSourceDbType.GBASE8C);
    dataSource.setConnectionParams(
        "{\"jdbcUrl\":\"jdbc:gbase8c://127.0.0.1:5432/app\","
            + "\"driverClassName\":\"com.gbase8c.Driver\","
            + "\"username\":\"root\",\"password\":\"secret\"}");

    ObjectNode options = mapper.createObjectNode();
    options.put("table_path", "public.orders");
    options.put("write_mode", "UPSERT");

    assertThatThrownBy(
            () ->
                adapter.resolveForExecution(
                    new ExecutionContext("jdbc", Role.SINK, "目标端", dataSource, options)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("GBase 8c")
        .hasMessageContaining("Upsert/MERGE");
  }

  @Test
  void injectsHanaDialectAndSchemaFromDatasourceOwnedConnection() {
    ObjectMapper mapper = new ObjectMapper();
    JdbcOfflineSyncConnectorAdapter adapter = new JdbcOfflineSyncConnectorAdapter(mapper);
    DataSourcePO dataSource = new DataSourcePO();
    dataSource.setId(12L);
    dataSource.setName("hana");
    dataSource.setDbType(DataSourceDbType.HANA);
    dataSource.setConnectionParams(
        "{\"jdbcUrl\":\"jdbc:sap://127.0.0.1:30013/?databaseName=HXE\","
            + "\"driverClassName\":\"com.sap.db.jdbc.Driver\","
            + "\"username\":\"SYSTEM\",\"password\":\"secret\","
            + "\"schema\":\"SALES\",\"dialect\":\"hana\"}");

    ObjectNode options = mapper.createObjectNode();
    options.put("url", "jdbc:mysql://stale:3306/source");
    options.put("dialect", "mysql");
    options.put("table_path", "SALES.ORDERS");
    adapter.resolveForExecution(
        new ExecutionContext("jdbc", Role.SINK, "目标端", dataSource, options));

    assertThat(options.path("url").asText())
        .isEqualTo("jdbc:sap://127.0.0.1:30013/?databaseName=HXE");
    assertThat(options.path("driver").asText()).isEqualTo("com.sap.db.jdbc.Driver");
    assertThat(options.path("dialect").asText()).isEqualTo("hana");
    assertThat(options.path("schema").asText()).isEqualTo("SALES");
    assertThat(options.path("password").asText()).isEqualTo("secret");
  }

  private static void assertGBaseExecution(
      DataSourceDbType dbType,
      String jdbcUrl,
      String expectedDialect,
      String expectedDriver) {
    ObjectMapper mapper = new ObjectMapper();
    JdbcOfflineSyncConnectorAdapter adapter = new JdbcOfflineSyncConnectorAdapter(mapper);
    DataSourcePO dataSource = new DataSourcePO();
    dataSource.setId(20L + dbType.ordinal());
    dataSource.setName(dbType.getDisplayName());
    dataSource.setDbType(dbType);
    dataSource.setConnectionParams(
        "{\"jdbcUrl\":\""
            + jdbcUrl
            + "\",\"username\":\"root\",\"password\":\"secret\"}");

    ObjectNode options = mapper.createObjectNode();
    options.put("table_path", "orders");
    adapter.resolveForExecution(
        new ExecutionContext("jdbc", Role.SOURCE, "来源端", dataSource, options));

    assertThat(options.path("url").asText()).isEqualTo(jdbcUrl);
    assertThat(options.path("driver").asText()).isEqualTo(expectedDriver);
    assertThat(options.path("dialect").asText()).isEqualTo(expectedDialect);
  }
}
