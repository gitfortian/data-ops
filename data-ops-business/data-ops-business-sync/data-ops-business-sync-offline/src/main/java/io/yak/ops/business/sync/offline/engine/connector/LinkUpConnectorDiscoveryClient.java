package io.yak.ops.business.sync.offline.engine.connector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.config.OfflineSyncProperties;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Read-only client for Link-Up Connector Schema discovery. */
@ConditionalOnOfflineSyncEnabled
@Component
public class LinkUpConnectorDiscoveryClient {

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final OfflineSyncProperties properties;

  public LinkUpConnectorDiscoveryClient(
      @Qualifier("offlineSyncHttpClient") HttpClient httpClient,
      @Qualifier("offlineSyncJsonMapper") ObjectMapper objectMapper,
      OfflineSyncProperties properties) {
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  /** Returns schemas exported by factories that are actually loaded in the configured Worker. */
  public List<JsonNode> schemas() {
    JsonNode value = get("/api/v1/connectors");
    if (!value.isArray()) {
      throw new DiscoveryException(
          502,
          "Link-Up /api/v1/connectors 返回格式不正确：预期 JSON 数组");
    }

    List<JsonNode> result = new ArrayList<>();
    value.forEach(schema -> result.add(schema.deepCopy()));
    return List.copyOf(result);
  }

  /** Reads one current schema directly from the Worker; stale cache is never used here. */
  public JsonNode schema(String connectorId, String role) {
    String id = requireText(connectorId, "connectorId 不能为空");
    String normalizedRole = normalizeRole(role);
    return get(
        "/api/v1/connectors/"
            + encode(id)
            + "/schema?role="
            + encode(normalizedRole));
  }

  private JsonNode get(String path) {
    requireEnabled();
    HttpRequest request =
        HttpRequest.newBuilder(uri(path))
            .timeout(properties.getEngine().getRequestTimeout())
            .header("Accept", "application/json")
            .GET()
            .build();

    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return read(response.body());
      }
      throw new DiscoveryException(
          response.statusCode(),
          "Link-Up Connector Schema 请求失败：HTTP "
              + response.statusCode()
              + " - "
              + errorMessage(response.body()));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new DiscoveryException(503, "Link-Up Connector Schema 请求被中断", exception);
    } catch (IOException exception) {
      throw new DiscoveryException(
          503,
          "无法连接 Link-Up Connector Schema API：" + baseUrl(),
          exception);
    }
  }

  private JsonNode read(String body) {
    if (!StringUtils.hasText(body)) {
      throw new DiscoveryException(502, "Link-Up Connector Schema 返回为空");
    }
    try {
      return objectMapper.readTree(body);
    } catch (JsonProcessingException exception) {
      throw new DiscoveryException(502, "Link-Up Connector Schema 返回了无效 JSON", exception);
    }
  }

  private String errorMessage(String body) {
    if (!StringUtils.hasText(body)) {
      return "empty response";
    }
    try {
      JsonNode value = objectMapper.readTree(body);
      String message = value.path("message").asText(null);
      if (!StringUtils.hasText(message)) {
        message = value.path("error").asText(null);
      }
      return sanitize(StringUtils.hasText(message) ? message : body);
    } catch (JsonProcessingException ignored) {
      return sanitize(body);
    }
  }

  private String sanitize(String value) {
    String sanitized =
        String.valueOf(value)
            .replaceAll(
                "(?i)(password|passwd|pwd|token|secret)\\s*[=:]\\s*[^,;\\s]+",
                "$1=***");
    return sanitized.length() <= 500 ? sanitized : sanitized.substring(0, 500);
  }

  private URI uri(String path) {
    try {
      return URI.create(baseUrl() + path);
    } catch (IllegalArgumentException exception) {
      throw new DiscoveryException(
          500,
          "Link-Up 地址不合法：" + properties.getEngine().getBaseUrl(),
          exception);
    }
  }

  private String baseUrl() {
    String value = properties.getEngine().getBaseUrl();
    if (!StringUtils.hasText(value)) {
      throw new DiscoveryException(500, "yak.sync.offline.engine.base-url 不能为空");
    }

    String normalized = value.trim();
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
      throw new DiscoveryException(500, "Link-Up 地址必须使用 HTTP 或 HTTPS");
    }
    return normalized;
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private String normalizeRole(String role) {
    String value = requireText(role, "role 不能为空").toUpperCase(Locale.ROOT);
    if (!"SOURCE".equals(value) && !"SINK".equals(value)) {
      throw new IllegalArgumentException("role 必须是 SOURCE 或 SINK");
    }
    return value;
  }

  private String requireText(String value, String message) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }

  private void requireEnabled() {
    if (!properties.getEngine().isEnabled()) {
      throw new DiscoveryException(503, "Link-Up 引擎对接已关闭");
    }
  }

  public static class DiscoveryException extends RuntimeException {
    private final int statusCode;

    public DiscoveryException(int statusCode, String message) {
      super(message);
      this.statusCode = statusCode;
    }

    public DiscoveryException(int statusCode, String message, Throwable cause) {
      super(message, cause);
      this.statusCode = statusCode;
    }

    public int getStatusCode() {
      return statusCode;
    }
  }
}
