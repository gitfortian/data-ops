package io.yak.ops.plugin.task.sql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.spi.task.model.SqlDialect;

/** SQL task plugin-owned schemaVersion=1 configuration. */
record SqlTaskConfig(
    String dataSourceId,
    String databaseName,
    String schemaName,
    SqlDialect dialect,
    int maxRows,
    int timeoutSeconds) {

  static final int DEFAULT_MAX_ROWS = 200;
  static final int MAX_ROWS = 500;
  static final int DEFAULT_TIMEOUT_SECONDS = 30;
  static final int MAX_TIMEOUT_SECONDS = 300;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static SqlTaskConfig parse(String configJson) {
    String raw = configJson == null || configJson.isBlank() ? "{}" : configJson.trim();
    try {
      JsonNode root = OBJECT_MAPPER.readTree(raw);
      if (root == null || !root.isObject()) {
        throw new IllegalArgumentException("SQL configJson must be a JSON object");
      }
      String dataSourceId = text(root, "dataSourceId");
      int maxRows = integer(root, "maxRows", DEFAULT_MAX_ROWS, 1, MAX_ROWS);
      int timeoutSeconds =
          integer(
              root,
              "timeoutSeconds",
              DEFAULT_TIMEOUT_SECONDS,
              1,
              MAX_TIMEOUT_SECONDS);
      return new SqlTaskConfig(
          dataSourceId,
          firstText(root, "databaseName", "database", "catalog"),
          firstText(root, "schemaName", "schema"),
          SqlDialect.parseOrGeneric(firstText(root, "dialect", "databaseType", "dbType")),
          maxRows,
          timeoutSeconds);
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("SQL configJson is not valid JSON", exception);
    }
  }

  private static String text(JsonNode root, String key) {
    JsonNode value = root.get(key);
    if (value == null || value.isNull()) return null;
    String text = value.asText();
    return text == null || text.isBlank() ? null : text.trim();
  }

  private static String firstText(JsonNode root, String... keys) {
    for (String key : keys) {
      String value = text(root, key);
      if (value != null) return value;
    }
    return null;
  }

  private static int integer(
      JsonNode root,
      String key,
      int defaultValue,
      int min,
      int max) {
    JsonNode value = root.get(key);
    if (value == null || value.isNull() || value.asText().isBlank()) return defaultValue;
    int parsed;
    try {
      parsed = value.isNumber() ? value.intValue() : Integer.parseInt(value.asText().trim());
    } catch (Exception exception) {
      throw new IllegalArgumentException(key + " must be an integer", exception);
    }
    if (parsed < min || parsed > max) {
      throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
    }
    return parsed;
  }
}
