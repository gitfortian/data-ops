package io.yak.ops.plugin.task.java;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Java task plugin-owned schemaVersion=1 configuration. */
record JavaTaskConfig(
    List<ResourceRef> resources,
    String mainClass,
    List<String> jvmArgs,
    List<String> programArgs,
    Map<String, String> envVars,
    int timeoutSeconds) {

  /**
   * A single resource reference within a Java task configuration.
   * Supports multiple JARs on the classpath.
   */
  record ResourceRef(long resourceId, int resourceVersion) {}

  static final String JAVA_HOME_ENV = "JAVA_HOME";
  static final int DEFAULT_TIMEOUT_SECONDS = 300;
  static final int MAX_TIMEOUT_SECONDS = 7200;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /**
   * Resolve the default java executable path from JAVA_HOME.
   *
   * <p>Priority: config.envVars.JAVA_HOME > globalEnvVars.JAVA_HOME > system env > "java"
   */
  static String defaultJavaExecutable(Map<String, String> globalEnv, Map<String, String> taskEnv) {
    String javaHome = resolveJavaHome(globalEnv, taskEnv);
    if (javaHome != null && !javaHome.isBlank()) {
      return resolveJavaFromHome(javaHome.trim());
    }
    return "java";
  }

  private static String resolveJavaHome(Map<String, String> globalEnv, Map<String, String> taskEnv) {
    if (taskEnv != null && taskEnv.containsKey(JAVA_HOME_ENV)) return taskEnv.get(JAVA_HOME_ENV);
    if (globalEnv != null && globalEnv.containsKey(JAVA_HOME_ENV)) return globalEnv.get(JAVA_HOME_ENV);
    return System.getenv(JAVA_HOME_ENV);
  }

  private static String resolveJavaFromHome(String javaHome) {
    if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
      return Path.of(javaHome, "bin", "java.exe").toString();
    }
    return Path.of(javaHome, "bin", "java").toString();
  }

  static JavaTaskConfig parse(String configJson) {
    return parse(configJson, null);
  }

  static JavaTaskConfig parse(String configJson, Map<String, String> globalEnv) {
    String raw = configJson == null || configJson.isBlank() ? "{}" : configJson.trim();
    try {
      JsonNode root = OBJECT_MAPPER.readTree(raw);
      if (root == null || !root.isObject()) {
        throw new IllegalArgumentException("Java configJson must be a JSON object");
      }

      // Parse resources: prefer new `resources` array, fallback to legacy `resourceId`
      List<ResourceRef> resources = parseResources(root);
      if (resources.isEmpty()) {
        throw new IllegalArgumentException(
            "Java configJson must contain at least one resource reference (resources array or resourceId)");
      }

      String mainClass = textOrNull(root, "mainClass");
      List<String> jvmArgs = stringList(root, "jvmArgs");
      List<String> programArgs = stringList(root, "programArgs");
      Map<String, String> envVars = stringMap(root, "envVars");
      int timeoutSeconds =
          integer(root, "timeoutSeconds", DEFAULT_TIMEOUT_SECONDS, 1, MAX_TIMEOUT_SECONDS);
      return new JavaTaskConfig(
          resources, mainClass, jvmArgs, programArgs, envVars, timeoutSeconds);
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("Java configJson is not valid JSON", exception);
    }
  }

  /**
   * Parse resource references from config JSON.
   *
   * <p>Supports two formats:
   * <ul>
   *   <li><strong>New</strong>: {@code {"resources": [{"resourceId": "123", "resourceVersion": 1}, ...]}}</li>
   *   <li><strong>Legacy</strong>: {@code {"resourceId": 123, "resourceVersion": 1}} (single resource)</li>
   * </ul>
   */
  private static List<ResourceRef> parseResources(JsonNode root) {
    JsonNode resourcesNode = root.get("resources");
    if (resourcesNode != null && resourcesNode.isArray() && !resourcesNode.isEmpty()) {
      List<ResourceRef> refs = new ArrayList<>(resourcesNode.size());
      for (JsonNode item : resourcesNode) {
        long id = item.path("resourceId").asLong(0);
        if (id > 0) {
          int version = item.path("resourceVersion").asInt(0);
          refs.add(new ResourceRef(id, version));
        }
      }
      if (!refs.isEmpty()) return Collections.unmodifiableList(refs);
    }

    // Legacy single-resource format
    long resourceId = root.path("resourceId").asLong(0);
    if (resourceId > 0) {
      int resourceVersion = root.path("resourceVersion").asInt(0);
      return List.of(new ResourceRef(resourceId, resourceVersion));
    }
    return List.of();
  }

  private static String textOrNull(JsonNode root, String key) {
    JsonNode value = root.get(key);
    if (value == null || value.isNull()) return null;
    String text = value.asText().trim();
    return text.isEmpty() ? null : text;
  }

  private static List<String> stringList(JsonNode root, String key) {
    JsonNode value = root.get(key);
    if (value == null || !value.isArray()) return List.of();
    List<String> items = new ArrayList<>(value.size());
    for (JsonNode item : value) {
      String text = item.asText();
      if (text != null && !text.isBlank()) items.add(text.trim());
    }
    return Collections.unmodifiableList(items);
  }

  private static Map<String, String> stringMap(JsonNode root, String key) {
    JsonNode value = root.get(key);
    if (value == null || !value.isObject()) return Map.of();
    Map<String, String> map = new LinkedHashMap<>();
    var fields = value.fields();
    while (fields.hasNext()) {
      var entry = fields.next();
      String val = entry.getValue().asText();
      if (val != null) map.put(entry.getKey(), val);
    }
    return Collections.unmodifiableMap(map);
  }

  private static int integer(JsonNode root, String key, int defaultValue, int min, int max) {
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
