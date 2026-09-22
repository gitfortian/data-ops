package io.yak.ops.business.sync.offline.engine.connector.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.engine.connector.adapter.NativeSingleTableSupport.TableRef;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Translates GUIDE_SINGLE tasks to Link-Up StarRocks Native Source / bounded Stream Load Sink. */
@ConditionalOnOfflineSyncEnabled
@Component
public class StarRocksOfflineSyncConnectorAdapter implements OfflineSyncConnectorAdapter {

  private final ObjectMapper objectMapper;
  private final ObjectProvider<DataSourceCatalogReader> catalogReaderProvider;

  public StarRocksOfflineSyncConnectorAdapter(
      @Qualifier("offlineSyncJsonMapper") ObjectMapper objectMapper,
      ObjectProvider<DataSourceCatalogReader> catalogReaderProvider) {
    this.objectMapper = objectMapper;
    this.catalogReaderProvider = catalogReaderProvider;
  }

  @Override
  public boolean supports(String connectorId, Role role) {
    return "starrocks".equalsIgnoreCase(connectorId);
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
      TableRef table = NativeSingleTableSupport.tableRef(sourceTable);
      if (StringUtils.hasText(table.database())) options.put("database", table.database());
      options.put("table", table.table());
      String filter = NativeSingleTableSupport.stripWhere(
          NativeSingleTableSupport.text(context.config(), "whereCondition", null));
      if (StringUtils.hasText(filter)) options.put("scan_filter", filter);
      options.put("scan_batch_rows", context.fetchSize());
      return new BuildResult(options, sourceTable, List.of(sourceTable));
    }

    String sinkTable = NativeSingleTableSupport.sinkTable(context.config());
    TableRef table = NativeSingleTableSupport.tableRef(sinkTable);
    if (StringUtils.hasText(table.database())) options.put("database", table.database());
    options.put("table", table.table());
    options.put("batch_max_rows", context.batchSize());

    String writeMode =
        NativeSingleTableSupport.text(context.config(), "writeMode", "append").toLowerCase(Locale.ROOT);
    if (!"append".equals(writeMode)) {
      throw new IllegalArgumentException("StarRocks Native Sink 当前仅支持追加写入 Append");
    }
    return new BuildResult(options, sinkTable, List.of());
  }

  @Override
  public void resolveForExecution(ExecutionContext context) {
    JsonNode dataSource = NativeSingleTableSupport.dataSource(objectMapper, context.dataSource());
    ObjectNode options = context.options();
    removeDatasourceOwned(options);

    ArrayNode nodes =
        NativeSingleTableSupport.nodes(objectMapper, dataSource, "nodeUrls", "node_urls", "fenodes");
    if (nodes.isEmpty()) {
      String host = NativeSingleTableSupport.firstText(dataSource, "host", "hostname");
      if (StringUtils.hasText(host)) {
        nodes.add(host + ":8030");
      }
    }
    if (nodes.isEmpty()) {
      throw new IllegalArgumentException(
          "StarRocks Native Connector 无法解析 FE HTTP 节点，请在数据源配置 nodeUrls");
    }
    options.set("node_urls", nodes);
    options.put("username", NativeSingleTableSupport.username(dataSource));
    options.put("password", NativeSingleTableSupport.password(dataSource));
    if (!StringUtils.hasText(options.path("database").asText(null))) {
      options.put(
          "database",
          NativeSingleTableSupport.requireText(
              NativeSingleTableSupport.database(dataSource), "StarRocks 数据源缺少 database"));
    }

    if (context.role() == Role.SOURCE) {
      options.set("schema.fields", loadSchemaFields(context, options));
    }
  }

  private ObjectNode loadSchemaFields(ExecutionContext context, ObjectNode options) {
    DataSourceCatalogReader catalogReader = catalogReaderProvider.getIfAvailable();
    if (catalogReader == null) {
      throw new IllegalStateException("StarRocks Native Source 无法读取 schema.fields：DataSource Catalog 未启用");
    }
    String database = options.path("database").asText();
    String table = options.path("table").asText();
    List<CatalogColumn> columns =
        catalogReader.listColumns(context.dataSource().getId(), database, null, table);
    if (columns.isEmpty()) {
      throw new IllegalArgumentException(
          "StarRocks Native Source 未发现字段：" + database + "." + table);
    }

    ObjectNode schemaFields = objectMapper.createObjectNode();
    for (CatalogColumn column : columns) {
      schemaFields.put(column.name(), starRocksType(column));
    }
    return schemaFields;
  }

  private String starRocksType(CatalogColumn column) {
    String type = NativeSingleTableSupport.requireText(
        column.typeName(), "StarRocks 字段 " + column.name() + " 缺少类型信息");
    String normalized = type.toLowerCase(Locale.ROOT);
    if (normalized.startsWith("array") || normalized.startsWith("map")) {
      throw new IllegalArgumentException(
          "StarRocks Native Source 当前不支持 ARRAY/MAP 字段：" + column.name() + " " + type);
    }
    if (normalized.startsWith("decimal") && !normalized.contains("(") && column.size() != null) {
      return type + "(" + column.size() + "," + (column.scale() == null ? 0 : column.scale()) + ")";
    }
    return type;
  }

  private void removeDatasourceOwned(ObjectNode options) {
    options.remove(List.of("node_urls", "nodeUrls", "username", "password"));
  }
}
