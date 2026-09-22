package io.yak.ops.business.sync.offline.engine.connector.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.offline.engine.connector.adapter.OfflineSyncConnectorAdapter.BuildContext;
import io.yak.ops.common.bean.po.datasource.DataSourcePO;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

/** Small shared helpers for the native single-table adapters. */
final class NativeSingleTableSupport {

  private NativeSingleTableSupport() {}

  static void requireSingle(BuildContext context) {
    if (!"GUIDE_SINGLE".equals(context.mode())) {
      throw new IllegalArgumentException(
          context.connectorId() + " Native Connector 当前阶段仅支持单表离线同步");
    }
  }

  static String sourceTable(JsonNode config) {
    if ("sql".equalsIgnoreCase(text(config, "readMode", "table"))) {
      throw new IllegalArgumentException("当前 Native Source 未声明 CUSTOM_SQL，请使用数据表读取");
    }
    return requireText(text(config, "table", null), "请选择来源表");
  }

  static String sinkTable(JsonNode config) {
    String explicit = text(config, "targetTableName", null);
    String value = StringUtils.hasText(explicit) ? explicit : text(config, "table", null);
    return requireText(value, "请选择或填写目标表");
  }

  static TableRef tableRef(String value) {
    String normalized = requireText(value, "表名不能为空");
    String[] parts = normalized.split("\\.");
    if (parts.length == 1) {
      return new TableRef(null, requireText(parts[0], "表名不能为空"), normalized);
    }
    if (parts.length == 2) {
      return new TableRef(
          requireText(parts[0], "数据库名不能为空"),
          requireText(parts[1], "表名不能为空"),
          normalized);
    }
    throw new IllegalArgumentException("Native Connector 表路径仅支持 table 或 database.table：" + value);
  }

  static JsonNode dataSource(ObjectMapper objectMapper, DataSourcePO dataSource) {
    if (dataSource == null) {
      throw new IllegalArgumentException("Native Connector 必须选择数据源");
    }
    String value = dataSource.getConnectionParams();
    if (!StringUtils.hasText(value)) {
      return objectMapper.createObjectNode();
    }
    try {
      JsonNode root = objectMapper.readTree(value);
      return root != null && root.isObject() ? root : objectMapper.createObjectNode();
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("数据源连接参数不是有效 JSON", exception);
    }
  }

  static String text(JsonNode node, String field, String fallback) {
    JsonNode value = node == null ? null : node.get(field);
    if (value == null || value.isNull() || !value.isValueNode()) {
      return fallback;
    }
    return value.asText(fallback);
  }

  static String firstText(JsonNode node, String... keys) {
    String value = firstTextAllowEmpty(node, keys);
    return StringUtils.hasText(value) ? value.trim() : null;
  }

  static String firstTextAllowEmpty(JsonNode node, String... keys) {
    Set<String> normalizedKeys = new LinkedHashSet<>();
    for (String key : keys) {
      normalizedKeys.add(normalizeKey(key));
    }
    return findText(node, normalizedKeys);
  }

  private static String findText(JsonNode node, Set<String> normalizedKeys) {
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
        if (value != null) return value;
      }
    } else if (node.isArray()) {
      for (JsonNode item : node) {
        String value = findText(item, normalizedKeys);
        if (value != null) return value;
      }
    }
    return null;
  }

  static int firstInt(JsonNode node, int fallback, String... keys) {
    String value = firstText(node, keys);
    if (!StringUtils.hasText(value)) return fallback;
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  static ArrayNode nodes(ObjectMapper objectMapper, JsonNode dataSource, String... keys) {
    for (String key : keys) {
      JsonNode direct = directValue(dataSource, key);
      if (direct != null && direct.isArray()) {
        ArrayNode result = objectMapper.createArrayNode();
        for (JsonNode item : direct) {
          if (item != null && item.isValueNode() && StringUtils.hasText(item.asText())) {
            result.add(trimNode(item.asText()));
          }
        }
        if (!result.isEmpty()) return result;
      }
    }

    String value = firstText(dataSource, keys);
    ArrayNode result = objectMapper.createArrayNode();
    if (!StringUtils.hasText(value)) return result;
    for (String part : value.split(",")) {
      if (StringUtils.hasText(part)) result.add(trimNode(part));
    }
    return result;
  }

  static ObjectNode object(ObjectMapper objectMapper, JsonNode node, String... keys) {
    for (String key : keys) {
      JsonNode direct = directValue(node, key);
      if (direct != null && direct.isObject()) {
        return (ObjectNode) direct.deepCopy();
      }
    }
    return objectMapper.createObjectNode();
  }

  static String database(JsonNode dataSource) {
    return firstText(dataSource, "database", "databaseName");
  }

  static String username(JsonNode dataSource) {
    String value = firstTextAllowEmpty(dataSource, "username", "user");
    return value == null ? "" : value;
  }

  static String password(JsonNode dataSource) {
    String value = firstTextAllowEmpty(dataSource, "password", "passwd");
    return value == null ? "" : value;
  }

  static String stripWhere(String value) {
    if (!StringUtils.hasText(value)) return null;
    String normalized = value.trim();
    if (normalized.toLowerCase(Locale.ROOT).startsWith("where ")) {
      normalized = normalized.substring(6).trim();
    }
    return StringUtils.hasText(normalized) ? normalized : null;
  }

  static String requireText(String value, String message) {
    if (!StringUtils.hasText(value)) throw new IllegalArgumentException(message);
    return value.trim();
  }

  static String clickHouseHost(JsonNode dataSource) {
    String host = firstText(dataSource, "host", "hostname");
    int port = firstInt(dataSource, 8123, "port");
    if (StringUtils.hasText(host)) {
      String normalized = host.trim();
      return normalized.contains(":") ? normalized : normalized + ":" + port;
    }

    String jdbcUrl = firstText(dataSource, "jdbcUrl", "url");
    if (StringUtils.hasText(jdbcUrl)) {
      String lower = jdbcUrl.toLowerCase(Locale.ROOT);
      int scheme = lower.indexOf("jdbc:clickhouse://");
      if (scheme >= 0) {
        int start = scheme + "jdbc:clickhouse://".length();
        int end = jdbcUrl.length();
        int slash = jdbcUrl.indexOf('/', start);
        if (slash >= 0) end = Math.min(end, slash);
        int question = jdbcUrl.indexOf('?', start);
        if (question >= 0) end = Math.min(end, question);
        String authority = jdbcUrl.substring(start, end).trim();
        if (StringUtils.hasText(authority)) return authority;
      }
    }
    return null;
  }

  private static JsonNode directValue(JsonNode node, String key) {
    if (node == null || !node.isObject()) return null;
    JsonNode value = node.get(key);
    if (value != null) return value;
    String normalized = normalizeKey(key);
    Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      if (normalizeKey(entry.getKey()).equals(normalized)) return entry.getValue();
    }
    return null;
  }

  private static String trimNode(String value) {
    String normalized = value.trim();
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  private static String normalizeKey(String value) {
    return value == null
        ? ""
        : value.replace("_", "").replace("-", "").replace(".", "").toLowerCase(Locale.ROOT);
  }

  record TableRef(String database, String table, String path) {}
}
