package io.yak.ops.business.sync.offline.engine.connector.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Translates Yak Ops GUIDE_SINGLE tasks to the bounded Elasticsearch 7 / 8 Link-Up connectors.
 * Vendor SDKs stay inside Link-Up runtime plugin islands; this adapter only owns option semantics.
 */
@ConditionalOnOfflineSyncEnabled
@Component
public class ElasticsearchOfflineSyncConnectorAdapter implements OfflineSyncConnectorAdapter {

  private static final List<String> DATASOURCE_OWNED_OPTIONS =
      List.of(
          "hosts",
          "host",
          "scheme",
          "port",
          "username",
          "user",
          "password",
          "passwd",
          "connect_timeout_ms",
          "socket_timeout_ms");

  private final ObjectMapper objectMapper;

  public ElasticsearchOfflineSyncConnectorAdapter(
      @Qualifier("offlineSyncJsonMapper") ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean supports(String connectorId, Role role) {
    return "elasticsearch7".equalsIgnoreCase(connectorId)
        || "elasticsearch8".equalsIgnoreCase(connectorId);
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
      String index = sourceIndex(context.config());
      options.put("index", index);
      normalizeQueryDsl(options);
      if (!options.hasNonNull("scroll_size")) {
        options.put("scroll_size", context.fetchSize());
      }
      return new BuildResult(options, index, List.of(index));
    }

    String index = sinkIndex(context.config());
    if (context.config().path("autoCreateTable").asBoolean(false)) {
      throw new IllegalArgumentException(
          "Elasticsearch Sink 当前不支持自动创建 index，请先创建目标 index 和 mapping");
    }
    String writeMode = text(context.config(), "writeMode", "append").toLowerCase(Locale.ROOT);
    if (!"append".equals(writeMode)) {
      throw new IllegalArgumentException(
          "Elasticsearch Sink 当前仅开放 Append 产品语义；稳定 _id 不等同于 UPSERT capability");
    }
    options.put("index", index);
    if (!options.hasNonNull("batch_size")) {
      options.put("batch_size", context.batchSize());
    }
    return new BuildResult(options, index, List.of());
  }

  @Override
  public void resolveForExecution(ExecutionContext context) {
    if (context.dataSource() == null) {
      throw new IllegalArgumentException("Elasticsearch Connector 必须选择数据源");
    }
    ObjectNode options = context.options();
    removeDatasourceOwned(options);

    JsonNode connection = readConnection(context.dataSource().getConnectionParams());
    ArrayNode hosts = hosts(connection);
    if (hosts.isEmpty()) {
      throw new IllegalArgumentException("Elasticsearch 数据源缺少 hosts/host 连接地址");
    }
    options.set("hosts", hosts);

    String username = firstText(connection, "username", "user");
    String password = firstTextAllowEmpty(connection, "password", "passwd");
    if (username != null) options.put("username", username);
    if (password != null) options.put("password", password);
    copyPositiveInt(connection, options, "connect_timeout_ms");
    copyPositiveInt(connection, options, "socket_timeout_ms");
  }

  private void normalizeQueryDsl(ObjectNode options) {
    JsonNode query = options.get("query");
    if (query == null || query.isNull()) return;
    if (query.isTextual()) {
      String value = query.asText();
      if (!StringUtils.hasText(value)) options.remove("query");
      else options.put("query", value.trim());
      return;
    }
    if (!query.isObject()) {
      throw new IllegalArgumentException("Elasticsearch query 必须是 Query DSL JSON 对象或 JSON 字符串");
    }
    try {
      options.put("query", objectMapper.writeValueAsString(query));
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("序列化 Elasticsearch Query DSL 失败", exception);
    }
  }

  private void requireSingle(BuildContext context) {
    if (!"GUIDE_SINGLE".equals(context.mode())) {
      throw new IllegalArgumentException(
          context.connectorId()
              + " 当前阶段仅支持单 index GUIDE_SINGLE；multi-index/FAN_OUT 不在 PR 8 范围内");
    }
  }

  private String sourceIndex(JsonNode config) {
    String readMode = text(config, "readMode", "table");
    if ("sql".equalsIgnoreCase(readMode)) {
      throw new IllegalArgumentException("Elasticsearch Source 不支持 SQL 读取，请使用 index + Query DSL");
    }
    return requireIndex(text(config, "table", text(config, "index", null)), "请选择来源 index");
  }

  private String sinkIndex(JsonNode config) {
    String explicitTarget = text(config, "targetTableName", null);
    String value = StringUtils.hasText(explicitTarget)
        ? explicitTarget : text(config, "table", text(config, "index", null));
    return requireIndex(value, "请选择或填写目标 index");
  }

  private String requireIndex(String value, String message) {
    if (!StringUtils.hasText(value)) throw new IllegalArgumentException(message);
    String index = value.trim();
    if (index.contains("*") || index.contains(",")) {
      throw new IllegalArgumentException(
          "当前阶段仅支持一个明确的 Elasticsearch index/alias，不支持 wildcard 或多 index");
    }
    return index;
  }

  private JsonNode readConnection(String json) {
    if (!StringUtils.hasText(json)) return objectMapper.createObjectNode();
    try {
      JsonNode value = objectMapper.readTree(json);
      return value != null && value.isObject() ? value : objectMapper.createObjectNode();
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Elasticsearch 数据源连接参数不是有效 JSON", exception);
    }
  }

  private ArrayNode hosts(JsonNode connection) {
    ArrayNode result = objectMapper.createArrayNode();
    JsonNode configured = connection.get("hosts");
    if (configured != null && configured.isArray()) {
      for (JsonNode item : configured) {
        addHost(result, item.asText(null));
      }
    } else if (configured != null && configured.isValueNode()) {
      for (String part : configured.asText("").split(",")) addHost(result, part);
    }
    if (!result.isEmpty()) return result;

    String host = firstText(connection, "host", "hostname");
    if (!StringUtils.hasText(host)) return result;
    String scheme = firstText(connection, "scheme", "protocol");
    if (!StringUtils.hasText(scheme)) scheme = "http";
    int port = intValue(connection, "port", "https".equalsIgnoreCase(scheme) ? 443 : 9200);
    addHost(result, scheme + "://" + host + ":" + port);
    return result;
  }

  private void addHost(ArrayNode target, String value) {
    if (!StringUtils.hasText(value)) return;
    String normalized = value.trim();
    while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
    if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
      normalized = "http://" + normalized;
    }
    target.add(normalized);
  }

  private void copyPositiveInt(JsonNode source, ObjectNode target, String field) {
    JsonNode value = source.get(field);
    if (value == null || value.isNull()) return;
    int parsed = value.isNumber() ? value.asInt() : intValue(source, field, -1);
    if (parsed > 0) target.put(field, parsed);
  }

  private int intValue(JsonNode node, String field, int fallback) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return fallback;
    if (value.isNumber()) return value.asInt(fallback);
    try {
      return Integer.parseInt(value.asText().trim());
    } catch (RuntimeException ignored) {
      return fallback;
    }
  }

  private String firstText(JsonNode node, String... fields) {
    String value = firstTextAllowEmpty(node, fields);
    return StringUtils.hasText(value) ? value.trim() : null;
  }

  private String firstTextAllowEmpty(JsonNode node, String... fields) {
    if (node == null || !node.isObject()) return null;
    for (String field : fields) {
      JsonNode value = node.get(field);
      if (value != null && value.isValueNode() && !value.isNull()) return value.asText();
    }
    return null;
  }

  private String text(JsonNode node, String field, String fallback) {
    JsonNode value = node == null ? null : node.get(field);
    return value == null || value.isNull() || !value.isValueNode()
        ? fallback
        : value.asText(fallback);
  }

  private void removeDatasourceOwned(ObjectNode options) {
    options.remove(DATASOURCE_OWNED_OPTIONS);
  }
}
