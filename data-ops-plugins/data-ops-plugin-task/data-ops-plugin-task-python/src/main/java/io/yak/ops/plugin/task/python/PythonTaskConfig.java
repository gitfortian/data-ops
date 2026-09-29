
package io.yak.ops.plugin.task.python;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Python task plugin-owned schemaVersion=1 configuration. */
record PythonTaskConfig(
    String pythonExecutable,
    List<String> scriptArgs,
    Map<String, String> envVars,
    int timeoutSeconds,
    String workingDirectory,
    long resourceId,
    int resourceVersion) {

  static final String PYTHON_HOME_ENV = "PYTHON_HOME";
  static final int DEFAULT_TIMEOUT_SECONDS = 60;
  static final int MAX_TIMEOUT_SECONDS = 3600;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /**
   * Resolve the default Python executable path from the {@code PYTHON_HOME} environment variable.
   *
   * <p>If {@code PYTHON_HOME} is set, the resolved path is platform-specific:
   * <ul>
   *   <li>Windows: {@code $PYTHON_HOME\python.exe}</li>
   *   <li>Linux/macOS: {@code $PYTHON_HOME/bin/python}</li>
   * </ul>
   *
   * <p>If {@code PYTHON_HOME} is not set, falls back to {@code python} (resolved via system PATH).
   * On Windows, if {@code python} is the App Execution Alias (WindowsApps), this will return
   * exit code 9009; users should set {@code PYTHON_HOME} to point to the real Python installation.
   */
  static String defaultPythonExecutable() {
    return defaultPythonExecutable(System.getenv());
  }

  /**
   * Resolve the default Python executable path from the given environment variable map.
   *
   * <p>If {@code PYTHON_HOME} is present in {@code env}, the resolved path is platform-specific:
   * <ul>
   *   <li>Windows: {@code $PYTHON_HOME\python.exe}</li>
   *   <li>Linux/macOS: {@code $PYTHON_HOME/bin/python}</li>
   * </ul>
   *
   * <p>If {@code PYTHON_HOME} is not found, falls back to {@code python}.
   */
  static String defaultPythonExecutable(Map<String, String> env) {
    String pythonHome = env != null ? env.get(PYTHON_HOME_ENV) : null;
    if (pythonHome != null && !pythonHome.isBlank()) {
      return resolvePythonFromHome(pythonHome.trim());
    }
    return "python";
  }

  private static String resolvePythonFromHome(String pythonHome) {
    if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
      return Path.of(pythonHome, "python.exe").toString();
    }
    return Path.of(pythonHome, "bin", "python").toString();
  }

  static PythonTaskConfig parse(String configJson) {
    return parse(configJson, null);
  }

  static PythonTaskConfig parse(String configJson, Map<String, String> globalEnv) {
    String raw = configJson == null || configJson.isBlank() ? "{}" : configJson.trim();
    try {
      JsonNode root = OBJECT_MAPPER.readTree(raw);
      if (root == null || !root.isObject()) {
        throw new IllegalArgumentException("Python configJson must be a JSON object");
      }
      String pythonExecutable = textOrDefault(root, "pythonExecutable",
          defaultPythonExecutable(globalEnv != null ? globalEnv : System.getenv()));
      List<String> scriptArgs = stringList(root, "scriptArgs");
      Map<String, String> envVars = stringMap(root, "envVars");
      int timeoutSeconds =
          integer(root, "timeoutSeconds", DEFAULT_TIMEOUT_SECONDS, 1, MAX_TIMEOUT_SECONDS);
      String workingDirectory = textOrNull(root, "workingDirectory");
      long resourceId = root.path("resourceId").asLong(0);
      int resourceVersion = integer(root, "resourceVersion", 0, 0, Integer.MAX_VALUE);
      return new PythonTaskConfig(
          pythonExecutable, scriptArgs, envVars, timeoutSeconds, workingDirectory,
          resourceId, resourceVersion);
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("Python configJson is not valid JSON", exception);
    }
  }

  private static String textOrDefault(JsonNode root, String key, String defaultValue) {
    JsonNode value = root.get(key);
    if (value == null || value.isNull() || value.asText().isBlank()) return defaultValue;
    String text = value.asText().trim();
    return text.isEmpty() ? defaultValue : text;
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
