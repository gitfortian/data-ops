package io.yak.ops.business.sync.offline.engine.connector.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.BuildContext;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.BuildResult;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.ExecutionContext;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.Role;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Preserves the bounded JDBC source/sink JobSpec contract behind an adapter boundary. */
@ConditionalOnOfflineSyncEnabled
@Component
public class JdbcOfflineSyncConnectorAdapter implements OfflineSyncConnectorAdapter {

  private static final Set<String> DATASOURCE_OWNED_OPTIONS = Set.of(
      "url",
      "driver",
      "username",
      "password",
      "schema",
      "dialect",
      "compatible_mode",
      "properties",
      "connection_check_timeout_sec",
      "connect_timeout_ms",
      "socket_timeout_ms");

  private final ObjectMapper objectMapper;

  public JdbcOfflineSyncConnectorAdapter(
      @Qualifier("offlineSyncJsonMapper") ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean supports(String connectorId, Role role) {
    return "jdbc".equalsIgnoreCase(connectorId);
  }

  @Override
  public boolean requiresDataSource(String connectorId, Role role) {
    return true;
  }

  @Override
  public boolean supportsNativeMultiTable(String connectorId, Role role) {
    return true;
  }

  @Override
  public BuildResult build(BuildContext context) {
    removeDatasourceOwnedOptions(context.options());
    return context.role() == Role.SOURCE ? buildSource(context) : buildSink(context);
  }

  @Override
  public void resolveForExecution(ExecutionContext context) {
    if (context.dataSource() == null) {
      throw new IllegalArgumentException("JDBC Connector 必须选择数据源");
    }
    removeDatasourceOwnedOptions(context.options());
    appendConnection(context.options(), connection(context.dataSource()));

    if (context.role() == Role.SINK
        && context.dataSource().getDbType() == DataSourceDbType.GOLDENDB) {
      // Link-Up's GoldenDB sink support is intentionally limited to existing target tables. Keep
      // this execution-time guard even for stale definitions that still contain auto-create intent.
      context.options().put("schema_save_mode", "ERROR_WHEN_SCHEMA_NOT_EXIST");
    }

    if (context.role() == Role.SINK
        && isGBase(context.dataSource().getDbType())
        && "UPSERT".equalsIgnoreCase(text(context.options(), "write_mode", null))) {
      // Current Link-Up GBase dialects expose bounded INSERT/TRUNCATE and safe auto-create, but no
      // stable UPSERT/MERGE contract. Reject stale or API-created definitions before Worker submit.
      throw new IllegalArgumentException(
          context.dataSource().getDbType().getDisplayName()
              + " 当前离线 Sink 不支持 Upsert/MERGE，请选择 Append 或 Overwrite");
    }
  }

  private BuildResult buildSource(BuildContext context) {
    List<String> sourceTables =
        sourceTables(context.config(), context.mode(), context.jobName());
    String sourceQuery = sourceQuery(context.config());
    ObjectNode options = context.options();

    options.put("fetch_size", context.fetchSize());
    if ("GUIDE_MULTI".equals(context.mode())) {
      ArrayNode tableList = objectMapper.createArrayNode();
      for (String table : sourceTables) {
        tableList.addObject().put("table_path", table);
      }
      options.set("table_list", tableList);
      options.remove("table_path");
    } else {
      options.put("table_path", sourceTables.get(0));
      options.remove("table_list");
      if (StringUtils.hasText(sourceQuery)) {
        options.put("query", sourceQuery);
      } else {
        options.remove("query");
      }
    }

    String whereCondition = text(context.config(), "whereCondition", null);
    if (StringUtils.hasText(whereCondition)) {
      options.put("where_condition", whereCondition.trim());
    }

    return new BuildResult(options, tableView(sourceTables, context.mode()), sourceTables);
  }

  private BuildResult buildSink(BuildContext context) {
    ObjectNode options = context.options();
    List<String> sourceTables = context.sourceTables();

    String sinkTableTemplate;
    String sinkTableView;
    if (!sourceTables.isEmpty()) {
      sinkTableTemplate = sinkTable(context.config(), context.mode());
      sinkTableView = sinkTableView(sinkTableTemplate, sourceTables, context.mode());
    } else {
      String explicitTarget = text(context.config(), "targetTableName", null);
      sinkTableTemplate =
          StringUtils.hasText(explicitTarget)
              ? explicitTarget
              : text(context.config(), "table", text(options, "table_path", null));
      if (!StringUtils.hasText(sinkTableTemplate)) {
        throw new IllegalArgumentException("请选择或填写目标表");
      }
      sinkTableTemplate = sinkTableTemplate.trim();
      sinkTableView = sinkTableTemplate;
    }

    options.put("table_path", sinkTableTemplate);
    options.put(
        "schema_save_mode",
        context.config().path("autoCreateTable").asBoolean(false)
            ? "CREATE_SCHEMA_WHEN_NOT_EXIST"
            : "ERROR_WHEN_SCHEMA_NOT_EXIST");

    String writeMode =
        text(context.config(), "writeMode", "append").toLowerCase(Locale.ROOT);
    options.put(
        "data_save_mode",
        "overwrite".equals(writeMode) ? "DROP_DATA" : "APPEND_DATA");
    options.put("write_mode", "upsert".equals(writeMode) ? "UPSERT" : "INSERT");

    if ("upsert".equals(writeMode)) {
      List<String> primaryKeys = splitValues(text(context.config(), "primaryKey", null));
      if (primaryKeys.isEmpty()) {
        throw new IllegalArgumentException("UPSERT 写入模式必须配置主键字段");
      }
      options.set("primary_keys", objectMapper.valueToTree(primaryKeys));
    } else {
      options.remove("primary_keys");
    }

    String customSql = text(context.config(), "sql", null);
    if (StringUtils.hasText(customSql)) {
      options.put("custom_sql", customSql.trim());
    }

    options.put("batch_size", context.batchSize());
    String dirtyPolicy = text(context.channel(), "dirtyDataPolicy", "stop");
    options.put(
        "dirty_data_policy",
        "skip".equalsIgnoreCase(dirtyPolicy) ? "SKIP" : "FAIL_FAST");
    if ("skip".equalsIgnoreCase(dirtyPolicy)) {
      options.put(
          "dirty_data_max_count",
          Math.max(0L, longValue(context.channel(), "dirtyDataLimit", 0L)));
    } else {
      options.remove("dirty_data_max_count");
    }

    return new BuildResult(options, sinkTableView, List.of());
  }

  private void removeDatasourceOwnedOptions(ObjectNode options) {
    DATASOURCE_OWNED_OPTIONS.forEach(options::remove);
  }

  private void appendConnection(ObjectNode options, ConnectionDetails connection) {
    options.put("url", connection.url());
    options.put("driver", connection.driver());
    if (StringUtils.hasText(connection.dialect())) {
      options.put("dialect", connection.dialect().trim().toLowerCase(Locale.ROOT));
    }
    if (connection.username() != null) {
      options.put("username", connection.username());
    }
    if (connection.password() != null) {
      options.put("password", connection.password());
    }
    if (StringUtils.hasText(connection.schema())) {
      options.put("schema", connection.schema());
    }
    if (connection.properties() != null
        && connection.properties().isObject()
        && !connection.properties().isEmpty()) {
      options.set("properties", connection.properties().deepCopy());
    }
    if (StringUtils.hasText(connection.compatibleMode())) {
      options.put("compatible_mode", connection.compatibleMode().trim().toLowerCase(Locale.ROOT));
    }
  }

  private ConnectionDetails connection(DataSourcePO dataSource) {
    JsonNode parameters = parseJson(dataSource.getConnectionParams());
    String url = firstText(parameters, "url", "jdbcUrl", "jdbc_url", "jdbc-url");
    if (!StringUtils.hasText(url)) {
      url = dataSource.getJdbcUrl();
    }
    if (!StringUtils.hasText(url)) {
      throw new IllegalArgumentException("数据源 " + dataSource.getName() + " 缺少 JDBC URL");
    }

    String driver =
        firstText(
            parameters,
            "driver",
            "driverClassName",
            "driver_class_name",
            "driver-class-name");
    if (!StringUtils.hasText(driver)) {
      driver =
          defaultDriver(
              url,
              dataSource.getDbType() == null ? null : dataSource.getDbType().name());
    }
    if (!StringUtils.hasText(driver)) {
      throw new IllegalArgumentException("数据源 " + dataSource.getName() + " 缺少 JDBC Driver");
    }

    String dialect = firstText(parameters, "dialect");
    if (!StringUtils.hasText(dialect) && dataSource.getDbType() != null) {
      if (dataSource.getDbType() == DataSourceDbType.TIDB) {
        dialect = "tidb";
      } else if (dataSource.getDbType() == DataSourceDbType.GOLDENDB) {
        dialect = "goldendb";
      } else if (dataSource.getDbType() == DataSourceDbType.GBASE8C) {
        dialect = "gbase8c";
      } else if (dataSource.getDbType() == DataSourceDbType.GBASE8A) {
        dialect = "gbase8a";
      } else if (dataSource.getDbType() == DataSourceDbType.GBASE8S) {
        dialect = "gbase8s";
      }
    }

    JsonNode properties = parameters.get("properties");
    if (properties != null && !properties.isObject()) {
      properties = null;
    }

    return new ConnectionDetails(
        url.trim(),
        driver.trim(),
        firstTextAllowEmpty(parameters, "username", "user"),
        firstTextAllowEmpty(parameters, "password", "passwd"),
        firstText(parameters, "schema", "schemaName", "schema_name"),
        properties == null ? null : properties.deepCopy(),
        dialect,
        firstText(parameters, "compatibleMode", "compatible_mode"));
  }

  private JsonNode parseJson(String value) {
    if (!StringUtils.hasText(value)) {
      return objectMapper.createObjectNode();
    }
    try {
      return objectMapper.readTree(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("数据源连接参数不是有效 JSON", exception);
    }
  }

  private List<String> sourceTables(JsonNode config, String mode, String jobName) {
    if ("GUIDE_MULTI".equals(mode)) {
      List<String> tables = flattenTables(config.path("tables"));
      if (tables.isEmpty()) {
        String pattern = text(config, "tablePattern", null);
        if (StringUtils.hasText(pattern) && !containsWildcard(pattern)) {
          tables.add(pattern.trim());
        }
      }
      if (tables.isEmpty()) {
        throw new IllegalArgumentException(
            "多表同步必须选择至少一张来源表，Link-Up 暂不直接执行通配符表名");
      }
      return tables;
    }

    String table = text(config, "table", null);
    String query = sourceQuery(config);
    if (!StringUtils.hasText(table) && StringUtils.hasText(query)) {
      table = "yak_query." + safeIdentifier(jobName);
    }
    if (!StringUtils.hasText(table)) {
      throw new IllegalArgumentException("请选择来源表");
    }
    return new ArrayList<>(List.of(table.trim()));
  }

  private String sourceQuery(JsonNode config) {
    if (!"sql".equalsIgnoreCase(text(config, "readMode", "table"))) {
      return null;
    }
    String query = text(config, "sql", null);
    if (!StringUtils.hasText(query)) {
      throw new IllegalArgumentException("SQL 读取模式必须填写来源查询 SQL");
    }
    return query.trim();
  }

  private String sinkTable(JsonNode config, String mode) {
    String explicitTarget = text(config, "targetTableName", null);
    String explicit = StringUtils.hasText(explicitTarget)
        ? explicitTarget : text(config, "table", null);
    if ("GUIDE_SINGLE".equals(mode)) {
      if (!StringUtils.hasText(explicit)) {
        throw new IllegalArgumentException("请选择或填写目标表");
      }
      return explicit.trim();
    }
    if (StringUtils.hasText(explicit)) {
      return explicit.trim();
    }

    String rule = text(config, "tableNamingRule", "same_name");
    String affix = text(config, "tableNameAffix", "");
    if ("prefix".equalsIgnoreCase(rule)) {
      return affix + "${table_name}";
    }
    if ("suffix".equalsIgnoreCase(rule)) {
      return "${table_name}" + affix;
    }
    if (!"same_name".equalsIgnoreCase(rule) && StringUtils.hasText(affix)) {
      return affix + "${table_name}";
    }
    return "${table_name}";
  }

  private String sinkTableView(
      String template,
      List<String> sourceTables,
      String mode) {
    if ("GUIDE_SINGLE".equals(mode)) {
      return template;
    }

    List<String> targetTables = new ArrayList<>();
    for (String sourceTable : sourceTables) {
      String schemaName = "";
      String tableName = sourceTable;
      int separator = sourceTable.lastIndexOf('.');
      if (separator >= 0) {
        schemaName = sourceTable.substring(0, separator);
        tableName = sourceTable.substring(separator + 1);
      }
      targetTables.add(
          template
              .replace("${schema_name}", schemaName)
              .replace("${table_name}", tableName));
    }
    return writeJson(targetTables);
  }

  private String tableView(List<String> tables, String mode) {
    return "GUIDE_MULTI".equals(mode) ? writeJson(tables) : tables.get(0);
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("序列化表信息失败", exception);
    }
  }

  private List<String> flattenTables(JsonNode value) {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    flattenTables(value, null, result);
    return new ArrayList<>(result);
  }

  private void flattenTables(JsonNode value, String schema, Set<String> result) {
    if (value == null || value.isNull() || value.isMissingNode()) {
      return;
    }
    if (value.isTextual()) {
      String table = value.asText().trim();
      if (StringUtils.hasText(table)) {
        result.add(
            StringUtils.hasText(schema) && !table.contains(".")
                ? schema + "." + table
                : table);
      }
      return;
    }
    if (value.isArray()) {
      for (JsonNode item : value) {
        flattenTables(item, schema, result);
      }
      return;
    }
    if (value.isObject()) {
      value.fields().forEachRemaining(
          entry -> flattenTables(entry.getValue(), entry.getKey(), result));
    }
  }

  private String firstText(JsonNode node, String... keys) {
    String value = firstTextAllowEmpty(node, keys);
    return StringUtils.hasText(value) ? value.trim() : null;
  }

  private String firstTextAllowEmpty(JsonNode node, String... keys) {
    Set<String> normalizedKeys = new LinkedHashSet<>();
    Arrays.stream(keys).map(this::normalizeKey).forEach(normalizedKeys::add);
    return findText(node, normalizedKeys);
  }

  private String findText(JsonNode node, Set<String> normalizedKeys) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return null;
    }
    if (node.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        JsonNode value = entry.getValue();
        if (normalizedKeys.contains(normalizeKey(entry.getKey())) && value.isValueNode()) {
          return value.isNull() ? null : value.asText();
        }
      }
      fields = node.fields();
      while (fields.hasNext()) {
        String value = findText(fields.next().getValue(), normalizedKeys);
        if (value != null) {
          return value;
        }
      }
    } else if (node.isArray()) {
      for (JsonNode item : node) {
        String value = findText(item, normalizedKeys);
        if (value != null) {
          return value;
        }
      }
    }
    return null;
  }

  private String defaultDriver(String url, String dbType) {
    String normalized =
        (url + " " + (dbType == null ? "" : dbType)).toLowerCase(Locale.ROOT);
    if (normalized.contains("mariadb")) {
      return "org.mariadb.jdbc.Driver";
    }
    if (normalized.contains("oceanbase")) {
      return "com.oceanbase.jdbc.Driver";
    }
    if (normalized.contains("sqlserver") || normalized.contains("sql_server")) {
      return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    }
    if (normalized.contains("opengauss") || normalized.contains("open_gauss")) {
      return "org.opengauss.Driver";
    }
    if (normalized.contains("db2")) {
      return "com.ibm.db2.jcc.DB2Driver";
    }
    if (normalized.contains("gbase8c")) {
      return "com.gbase8c.Driver";
    }
    if (normalized.contains("gbase8a")) {
      return "com.gbase.jdbc.Driver";
    }
    if (normalized.contains("gbase8s") || normalized.contains("gbasedbt-sqli")) {
      return "com.gbasedbt.jdbc.Driver";
    }
    if (normalized.contains("goldendb")
        || normalized.contains("tidb")
        || normalized.contains("mysql")
        || normalized.contains("doris")) {
      return "com.mysql.cj.jdbc.Driver";
    }
    if (normalized.contains("postgres")) {
      return "org.postgresql.Driver";
    }
    if (normalized.contains("oracle")) {
      return "oracle.jdbc.OracleDriver";
    }
    if (normalized.contains("kingbase")) {
      return "com.kingbase8.Driver";
    }
    if (normalized.contains("dm:") || normalized.contains("dameng")) {
      return "dm.jdbc.driver.DmDriver";
    }
    return null;
  }

  private List<String> splitValues(String value) {
    if (!StringUtils.hasText(value)) {
      return new ArrayList<>();
    }
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(StringUtils::hasText)
        .distinct()
        .toList();
  }

  private boolean containsWildcard(String value) {
    return value.contains("*") || value.contains("?") || value.contains("[");
  }

  private String safeIdentifier(String value) {
    String normalized = value.replaceAll("[^A-Za-z0-9_]", "_");
    return normalized.isBlank() ? "dataset" : normalized;
  }

  private String text(JsonNode node, String field, String fallback) {
    JsonNode value = node == null ? null : node.get(field);
    if (value == null || value.isNull() || !value.isValueNode()) {
      return fallback;
    }
    return value.asText(fallback);
  }

  private long longValue(JsonNode node, String field, long fallback) {
    JsonNode value = node == null ? null : node.get(field);
    if (value == null || value.isNull() || !value.isValueNode()) {
      return fallback;
    }
    if (value.isNumber()) {
      return value.asLong(fallback);
    }
    String text = value.asText("").trim();
    if (!StringUtils.hasText(text)) {
      return fallback;
    }
    try {
      return Long.parseLong(text);
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private boolean isGBase(DataSourceDbType dbType) {
    return dbType == DataSourceDbType.GBASE8C
        || dbType == DataSourceDbType.GBASE8A
        || dbType == DataSourceDbType.GBASE8S;
  }

  private String normalizeKey(String value) {
    return value.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
  }

  private record ConnectionDetails(
      String url,
      String driver,
      String username,
      String password,
      String schema,
      JsonNode properties,
      String dialect,
      String compatibleMode) {}
}
