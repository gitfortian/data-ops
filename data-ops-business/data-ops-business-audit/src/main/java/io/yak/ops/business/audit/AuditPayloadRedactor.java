package io.yak.ops.business.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 审计请求体脱敏器（票 03）：字段名命中敏感词（内置 + 配置追加）即替换为 "***"，
 * 超长截断；解析失败只落安全摘要，绝不回显原文。
 */
public final class AuditPayloadRedactor {

  private static final Set<String> BUILTIN_TOKENS =
      Set.of(
          "password", "passwd", "pwd", "secret", "token", "credential", "authorization",
          "cookie", "apikey", "api_key", "accesskey", "access_key", "privatekey", "private_key",
          "session", "signature", "connectionstring", "jdbcurl");

  private static final String REDACTED = "***";
  private static final int MAX_PAYLOAD_CHARS = 8 * 1024;

  private final ObjectMapper objectMapper;
  private final Set<String> tokens;

  public AuditPayloadRedactor(ObjectMapper objectMapper, Collection<String> extraTokens) {
    this.objectMapper = objectMapper;
    Set<String> merged = new LinkedHashSet<>(BUILTIN_TOKENS);
    if (extraTokens != null) {
      extraTokens.stream()
          .filter(v -> v != null && !v.isBlank())
          .map(AuditPayloadRedactor::normalize)
          .forEach(merged::add);
    }
    this.tokens = Set.copyOf(merged);
  }

  public static AuditPayloadRedactor withDefaults(ObjectMapper objectMapper) {
    return new AuditPayloadRedactor(objectMapper, List.of());
  }

  /**
   * 脱敏 JSON 请求体。
   *
   * @return null 表示无可记录内容；无法按 JSON 解析时只记录安全摘要标记，不落原文
   */
  public String redactJsonBody(byte[] rawBody) {
    if (rawBody == null || rawBody.length == 0) {
      return null;
    }
    String raw;
    try {
      raw = new String(rawBody, java.nio.charset.StandardCharsets.UTF_8);
    } catch (RuntimeException exception) {
      return "<undecodable-body bytes=" + rawBody.length + ">";
    }
    if (raw.isBlank()) {
      return null;
    }
    try {
      JsonNode root = objectMapper.readTree(raw);
      redactNode(root);
      String rendered = objectMapper.writeValueAsString(root);
      return truncate(rendered);
    } catch (Exception exception) {
      return "<non-json-body bytes=" + rawBody.length + ">";
    }
  }

  /** 表单/查询参数只留参数名清单，不记录取值。 */
  public List<String> parameterNames(Map<String, String[]> parameters) {
    if (parameters == null || parameters.isEmpty()) {
      return List.of();
    }
    Set<String> names = new TreeSet<>();
    parameters.keySet().stream()
        .map(name -> sensitive(name) ? REDACTED : name)
        .forEach(names::add);
    return List.copyOf(names);
  }

  public boolean sensitive(String fieldName) {
    if (fieldName == null) {
      return false;
    }
    String normalized = normalize(fieldName);
    return tokens.stream().anyMatch(normalized::contains);
  }

  /** Ignore common key separators so api-key, api_key and apiKey are equally protected. */
  private static String normalize(String fieldName) {
    return fieldName.toLowerCase(Locale.ROOT).replaceAll("[_\\-\\s.]", "");
  }

  private void redactNode(JsonNode node) {
    if (node instanceof ObjectNode objectNode) {
      Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        if (sensitive(field.getKey())) {
          objectNode.put(field.getKey(), REDACTED);
        } else {
          redactNode(field.getValue());
        }
      }
    } else if (node instanceof ArrayNode arrayNode) {
      arrayNode.forEach(this::redactNode);
    }
  }

  private String truncate(String value) {
    return value.length() <= MAX_PAYLOAD_CHARS
        ? value
        : value.substring(0, MAX_PAYLOAD_CHARS) + "...<truncated>";
  }
}
