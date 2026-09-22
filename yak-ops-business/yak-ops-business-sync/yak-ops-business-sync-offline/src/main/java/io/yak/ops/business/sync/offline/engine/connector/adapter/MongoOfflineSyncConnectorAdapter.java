package io.yak.ops.business.sync.offline.engine.connector.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Translates Yak Ops single-table/fan-out child definitions to the Link-Up MongoDB connector. */
@ConditionalOnOfflineSyncEnabled
@Component
public class MongoOfflineSyncConnectorAdapter implements OfflineSyncConnectorAdapter {

  private static final int DEFAULT_PORT = 27017;
  private static final List<String> DATASOURCE_OWNED_OPTIONS =
      List.of(
          "uri",
          "url",
          "host",
          "hosts",
          "hostname",
          "port",
          "username",
          "user",
          "password",
          "passwd",
          "authSource",
          "auth_source");

  private final ObjectMapper objectMapper;

  public MongoOfflineSyncConnectorAdapter(
      @Qualifier("offlineSyncJsonMapper") ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean supports(String connectorId, Role role) {
    return "mongodb".equalsIgnoreCase(connectorId);
  }

  @Override
  public boolean requiresDataSource(String connectorId, Role role) {
    return true;
  }

  @Override
  public BuildResult build(BuildContext context) {
    requireSingle(context);
    ObjectNode options = context.options();
    removeDatasourceOwned(options);

    if (context.role() == Role.SOURCE) {
      String readMode = text(context.config(), "readMode", "table");
      if ("sql".equalsIgnoreCase(readMode)) {
        throw new IllegalArgumentException("MongoDB Source 不支持 SQL，请选择 Collection");
      }
      MongoPath path = sourcePath(context.config());
      putDatabase(options, path.database());
      options.put("collection", path.collection());
      copyFields(context.config(), options);
      if (!options.hasNonNull("fetch_size")) {
        options.put("fetch_size", context.fetchSize());
      }
      return new BuildResult(options, path.qualified(), List.of(path.qualified()));
    }

    String writeMode = text(context.config(), "writeMode", "append").toLowerCase(Locale.ROOT);
    if (!"append".equals(writeMode)) {
      throw new IllegalArgumentException("MongoDB bounded Sink 当前仅开放 Append/INSERT 写入语义");
    }
    if (context.config().path("autoCreateTable").asBoolean(false)) {
      throw new IllegalArgumentException(
          "MongoDB Link-Up Connector 未声明 AUTO_CREATE_TABLE；请关闭自动创建能力开关");
    }

    MongoPath path = sinkPath(context.config());
    putDatabase(options, path.database());
    options.put("collection", path.collection());
    if (!options.hasNonNull("batch_size")) {
      options.put("batch_size", context.batchSize());
    }
    return new BuildResult(options, path.qualified(), List.of());
  }

  @Override
  public void resolveForExecution(ExecutionContext context) {
    if (context.dataSource() == null) {
      throw new IllegalArgumentException("MongoDB Connector 必须选择数据源");
    }
    ObjectNode options = context.options();
    removeDatasourceOwned(options);

    JsonNode connection = readConnection(context.dataSource().getConnectionParams());
    String defaultDatabase = firstText(connection, "database");
    if (!options.hasNonNull("database") && StringUtils.hasText(defaultDatabase)) {
      options.put("database", defaultDatabase);
    }
    options.put("uri", buildUri(connection));
  }

  private String buildUri(JsonNode connection) {
    String host = firstText(connection, "host");
    if (!StringUtils.hasText(host)) host = "127.0.0.1";
    int port = connection == null ? DEFAULT_PORT : connection.path("port").asInt(DEFAULT_PORT);
    if (port <= 0 || port > 65535) {
      throw new IllegalArgumentException("MongoDB 数据源 port 必须在 1-65535 范围内");
    }

    List<String> seeds = new ArrayList<>();
    String hosts = firstText(connection, "hosts");
    if (StringUtils.hasText(hosts)) {
      for (String item : hosts.split(",")) {
        String seed = item.trim();
        if (!seed.isEmpty()) seeds.add(normalizeSeed(seed, port));
      }
    }
    if (seeds.isEmpty()) seeds.add(normalizeSeed(host, port));

    String database = firstText(connection, "database");
    if (!StringUtils.hasText(database)) {
      throw new IllegalArgumentException("MongoDB 数据源缺少默认 Database");
    }
    String username = firstText(connection, "username", "user");
    String password = valueText(connection, "password");
    String authSource = firstText(connection, "authSource", "auth_source");

    StringBuilder uri = new StringBuilder("mongodb://");
    if (StringUtils.hasText(username)) {
      uri.append(encode(username));
      if (password != null) uri.append(':').append(encode(password));
      uri.append('@');
    }
    uri.append(String.join(",", seeds));
    uri.append('/').append(encode(database));
    if (StringUtils.hasText(authSource)) {
      uri.append("?authSource=").append(encode(authSource));
    }
    return uri.toString();
  }

  private void copyFields(JsonNode config, ObjectNode options) {
    JsonNode fields = config == null ? null : config.get("fields");
    if (fields == null || fields.isNull()) return;
    if (!fields.isArray()) {
      throw new IllegalArgumentException("MongoDB fields 必须是字段名数组");
    }
    ArrayNode normalized = objectMapper.createArrayNode();
    for (JsonNode field : fields) {
      if (!field.isValueNode() || !StringUtils.hasText(field.asText())) {
        throw new IllegalArgumentException("MongoDB fields 只能包含非空字段名");
      }
      normalized.add(field.asText().trim());
    }
    if (!normalized.isEmpty()) options.set("fields", normalized);
    else options.remove("fields");
  }

  private MongoPath sourcePath(JsonNode config) {
    return path(
        text(config, "database", null),
        text(config, "table", text(config, "collection", null)),
        "请选择来源 Collection");
  }

  private MongoPath sinkPath(JsonNode config) {
    return path(
        text(config, "database", null),
        text(
            config,
            "targetTableName",
            text(config, "table", text(config, "collection", null))),
        "请选择或填写目标 Collection");
  }

  private MongoPath path(String configuredDatabase, String rawTable, String message) {
    if (!StringUtils.hasText(rawTable)) throw new IllegalArgumentException(message);
    String database = trim(configuredDatabase);
    String collection = rawTable.trim();

    if (database != null) {
      String prefix = database + ".";
      if (collection.regionMatches(true, 0, prefix, 0, prefix.length())) {
        collection = collection.substring(prefix.length());
      }
      return new MongoPath(database, requireCollection(collection));
    }

    // Collection names may contain dots. Without an explicit database field, keep the entire value
    // as the collection name and inherit the datasource default database at execution time.
    return new MongoPath(null, requireCollection(collection));
  }

  private String normalizeSeed(String value, int defaultPort) {
    if (value.contains("://") || value.contains("/") || value.contains("?") || value.contains("@")) {
      throw new IllegalArgumentException("MongoDB Seed Host 只允许 host 或 host:port：" + value);
    }
    return value.contains(":") ? value : value + ":" + defaultPort;
  }

  private String requireCollection(String value) {
    String normalized = trim(value);
    if (normalized == null) throw new IllegalArgumentException("MongoDB Collection 不能为空");
    return normalized;
  }

  private JsonNode readConnection(String json) {
    if (!StringUtils.hasText(json)) return objectMapper.createObjectNode();
    try {
      JsonNode value = objectMapper.readTree(json);
      return value != null && value.isObject() ? value : objectMapper.createObjectNode();
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("MongoDB 数据源连接参数不是有效 JSON", exception);
    }
  }

  private String firstText(JsonNode node, String... fields) {
    if (node == null || !node.isObject()) return null;
    for (String field : fields) {
      JsonNode value = node.get(field);
      if (value != null && value.isValueNode() && StringUtils.hasText(value.asText())) {
        return value.asText().trim();
      }
    }
    return null;
  }

  private String valueText(JsonNode node, String field) {
    if (node == null || !node.isObject()) return null;
    JsonNode value = node.get(field);
    return value == null || value.isNull() || !value.isValueNode() ? null : value.asText();
  }

  private void requireSingle(BuildContext context) {
    if (!"GUIDE_SINGLE".equals(context.mode())) {
      throw new IllegalArgumentException(
          "MongoDB Link-Up Connector 当前使用单 Collection JobSpec；GUIDE_MULTI 由 Yak Ops FAN_OUT 冻结为多个子任务");
    }
  }

  private void putDatabase(ObjectNode options, String database) {
    if (StringUtils.hasText(database)) options.put("database", database);
    else options.remove("database");
  }

  private String text(JsonNode node, String field, String fallback) {
    JsonNode value = node == null ? null : node.get(field);
    return value == null || value.isNull() || !value.isValueNode()
        ? fallback
        : value.asText(fallback);
  }

  private String trim(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private void removeDatasourceOwned(ObjectNode options) {
    options.remove(DATASOURCE_OWNED_OPTIONS);
  }

  private record MongoPath(String database, String collection) {
    String qualified() {
      return database == null ? collection : database + "." + collection;
    }
  }
}
