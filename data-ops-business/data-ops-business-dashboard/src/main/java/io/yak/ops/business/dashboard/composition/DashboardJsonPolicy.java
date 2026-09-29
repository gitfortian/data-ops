package io.yak.ops.business.dashboard.composition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Validates and reads opaque Dashboard JSON values at the composition boundary. */
@Component
public class DashboardJsonPolicy {

  private final ObjectMapper objectMapper;

  public DashboardJsonPolicy(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public Object requireObject(Object value, String label, int maxLength) {
    if (value == null) return null;
    JsonNode node = objectMapper.valueToTree(value);
    if (!node.isObject()) {
      throw new IllegalArgumentException(label + " 必须是 JSON 对象");
    }
    ensureLength(value, label, maxLength);
    return value;
  }

  public Object requireScalar(Object value, String label, int maxLength) {
    if (value == null) return null;
    JsonNode node = objectMapper.valueToTree(value);
    if (!node.isValueNode()) {
      throw new IllegalArgumentException(label + " 必须是标量");
    }
    ensureLength(value, label, maxLength);
    return value;
  }

  public Long optionalPositiveLongField(Object value, String fieldName, String label) {
    if (value == null) return null;
    JsonNode root = objectMapper.valueToTree(value);
    if (!root.isObject()) {
      throw new IllegalArgumentException(label + " 必须是 JSON 对象");
    }
    JsonNode field = root.get(fieldName);
    if (field == null || field.isNull()) return null;

    long parsed;
    try {
      if (field.isIntegralNumber()) {
        parsed = field.longValue();
      } else if (field.isTextual()) {
        parsed = Long.parseLong(field.asText().trim());
      } else {
        throw new NumberFormatException("not an integer");
      }
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException(label + "." + fieldName + " 非法", exception);
    }
    if (parsed <= 0L) {
      throw new IllegalArgumentException(label + "." + fieldName + " 必须大于 0");
    }
    return parsed;
  }

  private void ensureLength(Object value, String label, int maxLength) {
    try {
      if (objectMapper.writeValueAsString(value).length() > maxLength) {
        throw new IllegalArgumentException(label + " 配置过大");
      }
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException(label + " 无法序列化", exception);
    }
  }
}
