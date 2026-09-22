package io.yak.ops.plugin.task.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.spi.resource.ResolvedResource;
import io.yak.ops.spi.resource.ResourceResolver;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Shared utilities for task plugins that support both inline content and
 * resource-reference modes (e.g. Python, Shell, Java).
 *
 * <p>Encapsulates common patterns to eliminate duplication across plugin
 * implementations.</p>
 */
public final class ScriptTaskSupport {

  public static final int DEFAULT_MAX_CAPTURE_LENGTH = 50_000;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private ScriptTaskSupport() {}

  /**
   * Extract a positive {@code resourceId} from the given {@code configJson}.
   *
   * <p>Returns {@code null} if configJson is null/blank, not valid JSON,
   * does not contain a {@code resourceId} field, or the value is ≤ 0.</p>
   */
  public static Long parseResourceId(String configJson) {
    if (configJson == null || configJson.isBlank()) return null;
    try {
      JsonNode root = OBJECT_MAPPER.readTree(configJson.trim());
      if (root != null && root.has("resourceId")) {
        long id = root.path("resourceId").asLong(0);
        return id > 0 ? id : null;
      }
    } catch (Exception ignored) {
      // Invalid JSON — caller handles via full config parse
    }
    return null;
  }

  /**
   * Check whether a task definition JSON (containing a {@code configJson} field)
   * carries a positive {@code resourceId} reference.
   *
   * <p>Used by adapters to decide whether to inject the {@code ResourceResolver}
   * capability into the execution context.</p>
   */
  public static boolean hasResourceReference(String definitionJson, ObjectMapper objectMapper) {
    if (definitionJson == null || definitionJson.isBlank()) return false;
    try {
      JsonNode root = objectMapper.readTree(definitionJson);
      JsonNode configJsonNode = root.get("configJson");
      if (configJsonNode != null && configJsonNode.isTextual()) {
        JsonNode config = objectMapper.readTree(configJsonNode.asText());
        return hasResourceInConfig(config);
      }
      if (configJsonNode != null && configJsonNode.isObject()) {
        return hasResourceInConfig(configJsonNode);
      }
    } catch (Exception ignored) {
      // Fall through — non-resource mode
    }
    return false;
  }

  /**
   * Summarize the first N validation issue messages into a single string,
   * suitable for use as an exception message.
   */
  public static String summarizeIssues(TaskValidationResult validation, String fallbackMessage) {
    if (validation.valid()) return null;
    return validation.issues().stream()
        .map(TaskValidationIssue::message)
        .limit(3)
        .reduce((left, right) -> left + "; " + right)
        .orElse(fallbackMessage);
  }

  /** Safely extract a message from a throwable, with a sensible fallback. */
  public static String safeMessage(Throwable throwable, String defaultFallback) {
    String message = throwable == null ? null : throwable.getMessage();
    if (message == null || message.isBlank()) {
      return throwable == null ? defaultFallback : throwable.getClass().getSimpleName();
    }
    return message.length() > 500 ? message.substring(0, 500) : message;
  }

  // ── Stream / output helpers ─────────────────────────────────────────

  /** Read all lines from a {@link BufferedReader} into a single string. */
  public static String readStream(BufferedReader reader) throws IOException {
    StringBuilder sb = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) {
      if (sb.length() > 0) sb.append('\n');
      sb.append(line);
    }
    return sb.toString();
  }

  /**
   * Truncate a string to {@link #DEFAULT_MAX_CAPTURE_LENGTH} characters,
   * appending a truncation marker if exceeded.
   */
  public static String truncate(String value) {
    return truncate(value, DEFAULT_MAX_CAPTURE_LENGTH);
  }

  /** Truncate a string to the given maximum length. */
  public static String truncate(String value, int maxLength) {
    if (value == null) return "";
    return value.length() > maxLength
        ? value.substring(0, maxLength) + "\n... [truncated]"
        : value;
  }

  // ── Script content resolution ───────────────────────────────────────

  /**
   * Resolve script content from either inline content or a resource reference.
   *
   * <p>If {@code definition.content()} is non-blank, returns it directly.
   * Otherwise resolves the resource file via {@code resourceResolver} and reads
   * its content as UTF-8.</p>
   *
   * @throws IOException if neither inline content nor a valid resource is available
   */
  public static String resolveScriptContent(
      TaskDefinition definition,
      ResourceResolver resourceResolver,
      long resourceId,
      int resourceVersion) throws IOException {
    if (definition.content() != null && !definition.content().isBlank()) {
      return definition.content();
    }
    if (resourceResolver == null || resourceId <= 0) {
      throw new IOException(
          "Task has no inline content and no valid resourceId in configJson");
    }
    try (ResolvedResource resource = resourceVersion > 0
        ? resourceResolver.resolve(resourceId, resourceVersion)
        : resourceResolver.resolve(resourceId)) {
      return Files.readString(resource.localPath(), StandardCharsets.UTF_8);
    }
  }

  // ── Multi-resource detection ────────────────────────────────────────

  /**
   * Check whether a config JSON object contains any resource reference,
   * supporting both single {@code resourceId} and {@code resources} array formats.
   */
  public static boolean hasResourceInConfig(JsonNode config) {
    if (config == null) return false;
    if (config.has("resourceId") && config.path("resourceId").asLong(0) > 0) return true;
    if (config.has("resources") && config.get("resources").isArray()
        && !config.get("resources").isEmpty()) return true;
    return false;
  }
}
