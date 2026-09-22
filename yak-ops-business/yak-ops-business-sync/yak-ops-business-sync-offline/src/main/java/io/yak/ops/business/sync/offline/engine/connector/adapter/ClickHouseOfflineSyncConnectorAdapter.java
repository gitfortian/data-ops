package io.yak.ops.business.sync.offline.engine.connector.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.engine.connector.adapter.NativeSingleTableSupport.TableRef;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Translates GUIDE_SINGLE tasks to the Link-Up ClickHouse native Source / Sink. */
@ConditionalOnOfflineSyncEnabled
@Component
public class ClickHouseOfflineSyncConnectorAdapter implements OfflineSyncConnectorAdapter {

  private final ObjectMapper objectMapper;

  public ClickHouseOfflineSyncConnectorAdapter(
      @Qualifier("offlineSyncJsonMapper") ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean supports(String connectorId, Role role) {
    return "clickhouse".equalsIgnoreCase(connectorId);
  }

  @Override
  public boolean requiresDataSource(String connectorId, Role role) {
    return true;
  }

  @Override
  public BuildResult build(BuildContext context) {
    NativeSingleTableSupport.requireSingle(context);
    ObjectNode options = context.options();
    removeDatasourceOwned(options);

    if (context.role() == Role.SOURCE) {
      String sourceTable = NativeSingleTableSupport.sourceTable(context.config());
      options.put("table_path", sourceTable);
      String filter = NativeSingleTableSupport.stripWhere(
          NativeSingleTableSupport.text(context.config(), "whereCondition", null));
      if (StringUtils.hasText(filter)) options.put("filter_query", filter);
      options.put("batch_size", context.fetchSize());
      return new BuildResult(options, sourceTable, List.of(sourceTable));
    }

    String sinkTable = NativeSingleTableSupport.sinkTable(context.config());
    TableRef table = NativeSingleTableSupport.tableRef(sinkTable);
    if (StringUtils.hasText(table.database())) options.put("database", table.database());
    options.put("table", table.table());
    options.put("sink.batch_size", context.batchSize());

    String writeMode =
        NativeSingleTableSupport.text(context.config(), "writeMode", "append").toLowerCase(Locale.ROOT);
    if (!"append".equals(writeMode)) {
      throw new IllegalArgumentException("ClickHouse Native Sink 当前仅支持追加写入 Append");
    }
    return new BuildResult(options, sinkTable, List.of());
  }

  @Override
  public void resolveForExecution(ExecutionContext context) {
    JsonNode dataSource = NativeSingleTableSupport.dataSource(objectMapper, context.dataSource());
    ObjectNode options = context.options();
    removeDatasourceOwned(options);

    String host = NativeSingleTableSupport.clickHouseHost(dataSource);
    if (!StringUtils.hasText(host)) {
      throw new IllegalArgumentException("ClickHouse Native Connector 无法从数据源解析 HTTP host");
    }
    options.put("host", host);
    options.put("username", NativeSingleTableSupport.username(dataSource));
    options.put("password", NativeSingleTableSupport.password(dataSource));

    String serverTimeZone =
        NativeSingleTableSupport.firstText(dataSource, "serverTimeZone", "server_time_zone");
    if (StringUtils.hasText(serverTimeZone)) options.put("server_time_zone", serverTimeZone);
    ObjectNode properties = NativeSingleTableSupport.object(objectMapper, dataSource, "properties");
    if (!properties.isEmpty()) options.set("clickhouse.config", properties);

    String database = NativeSingleTableSupport.database(dataSource);
    if (context.role() == Role.SOURCE) {
      String tablePath = options.path("table_path").asText();
      if (!tablePath.contains(".")) {
        options.put(
            "table_path",
            NativeSingleTableSupport.requireText(database, "ClickHouse 数据源缺少 database")
                + "."
                + tablePath);
      }
    } else if (!StringUtils.hasText(options.path("database").asText(null))) {
      options.put(
          "database",
          NativeSingleTableSupport.requireText(database, "ClickHouse 数据源缺少 database"));
    }
  }

  private void removeDatasourceOwned(ObjectNode options) {
    options.remove(
        List.of(
            "host",
            "username",
            "password",
            "server_time_zone",
            "clickhouse.config"));
  }
}
