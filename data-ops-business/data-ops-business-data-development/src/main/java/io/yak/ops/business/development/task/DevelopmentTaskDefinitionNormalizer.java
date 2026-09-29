package io.yak.ops.business.development.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.spi.task.model.SqlDialect;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Single normalization rule for TaskDefinition used by save, publish and editor run. */
@Component
public class DevelopmentTaskDefinitionNormalizer {

  private final ObjectMapper objectMapper;

  public DevelopmentTaskDefinitionNormalizer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public TaskDefinition normalize(
      DevelopmentNode node,
      String taskType,
      int schemaVersion,
      String content,
      String configJson) {
    if (taskType == null || taskType.isBlank()) {
      throw new IllegalArgumentException("taskType 不能为空");
    }
    String normalizedType = taskType.trim().toUpperCase(Locale.ROOT);
    if (!normalizedType.equals(node.type().trim().toUpperCase(Locale.ROOT))) {
      throw new IllegalArgumentException(
          "任务类型与节点类型不一致：node=" + node.type() + ", definition=" + normalizedType);
    }
    if (schemaVersion <= 0) {
      throw new IllegalArgumentException("schemaVersion 必须大于 0");
    }
    return new TaskDefinition(
        normalizedType,
        schemaVersion,
        content == null ? "" : content,
        normalizeConfigJson(normalizedType, configJson));
  }

  private String normalizeConfigJson(String taskType, String configJson) {
    String raw = configJson == null || configJson.isBlank() ? "{}" : configJson.trim();
    try {
      JsonNode node = objectMapper.readTree(raw);
      if (node == null || !node.isObject()) {
        throw new IllegalArgumentException("configJson 必须是 JSON Object");
      }
      ObjectNode object = (ObjectNode) node;
      if ("SQL".equals(taskType)) {
        normalizeSqlDialect(object);
      }
      return objectMapper.writeValueAsString(object);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("configJson 不是合法 JSON", exception);
    }
  }

  private void normalizeSqlDialect(ObjectNode config) {
    String rawDialect = text(config, "dialect");
    if (rawDialect == null) rawDialect = text(config, "databaseType");
    if (rawDialect == null) rawDialect = text(config, "dbType");

    SqlDialect dialect = SqlDialect.parseOrGeneric(rawDialect);
    config.put("dialect", dialect.name());
    config.remove("databaseType");
    config.remove("dbType");
  }

  private String text(ObjectNode config, String key) {
    JsonNode value = config.get(key);
    if (value == null || value.isNull()) return null;
    String text = value.asText();
    return text == null || text.isBlank() ? null : text.trim();
  }
}
