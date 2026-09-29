package io.yak.ops.plugin.task.shell;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shell task plugin-owned schemaVersion=1 configuration. */
record ShellTaskConfig(
    String shellExecutable,
    List<String> scriptArgs,
    Map<String, String> envVars,
    int timeoutSeconds,
    String workingDirectory,
    long resourceId,
    int resourceVersion) {

  static final String SHELL_HOME_ENV = "SHELL_HOME";
  static final int DEFAULT_TIMEOUT_SECONDS = 60;
  static final int MAX_TIMEOUT_SECONDS = 3600;

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final boolean IS_WINDOWS =
      System.getProperty("os.name", "").toLowerCase().contains("win");

  /**
   * Resolve the default shell executable.
   *
   * <p>If {@code SHELL_HOME} is set, resolves the shell binary from that directory.
   * Otherwise:
   * <ul>
   *   <li>Linux/macOS: {@code bash} (via system PATH)</li>
   *   <li>Windows: {@code pwsh} (PowerShell Core via system PATH)</li>
   * </ul>
   */
  static String defaultShellExecutable() {
    return defaultShellExecutable(System.getenv());
  }

  static String defaultShellExecutable(Map<String, String> env) {
    String shellHome = env != null ? env.get(SHELL_HOME_ENV) : null;
    if (shellHome != null && !shellHome.isBlank()) {
      return resolveShellFromHome(shellHome.trim());
    }
    return IS_WINDOWS ? "pwsh" : "bash";
  }

  private static String resolveShellFromHome(String shellHome) {
    if (IS_WINDOWS) {
      return java.nio.file.Path.of(shellHome, "pwsh.exe").toString();
    }
    return java.nio.file.Path.of(shellHome, "bin", "bash").toString();
  }

  /**
   * Detect whether the given shell executable is a PowerShell variant
   * (pwsh, pwsh.exe, powershell, powershell.exe, or a full path ending with one of these).
   */
  static boolean isPowerShell(String shellExecutable) {
    if (shellExecutable == null) return false;
    String name = shellExecutable.toLowerCase();
    int sep = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
    if (sep >= 0) name = name.substring(sep + 1);
    return name.equals("pwsh") || name.equals("pwsh.exe")
        || name.equals("powershell") || name.equals("powershell.exe");
  }

  static ShellTaskConfig parse(String configJson) {
    return parse(configJson, null);
  }

  static ShellTaskConfig parse(String configJson, Map<String, String> globalEnv) {
    String raw = configJson == null || configJson.isBlank() ? "{}" : configJson.trim();
    try {
      JsonNode root = OBJECT_MAPPER.readTree(raw);
      if (root == null || !root.isObject()) {
        throw new IllegalArgumentException("Shell configJson must be a JSON object");
      }
      String shellExecutable = textOrDefault(root, "shellExecutable",
          defaultShellExecutable(globalEnv != null ? globalEnv : System.getenv()));
      List<String> scriptArgs = stringList(root, "scriptArgs");
      Map<String, String> envVars = stringMap(root, "envVars");
      int timeoutSeconds =
          integer(root, "timeoutSeconds", DEFAULT_TIMEOUT_SECONDS, 1, MAX_TIMEOUT_SECONDS);
      String workingDirectory = textOrNull(root, "workingDirectory");
      long resourceId = root.path("resourceId").asLong(0);
      int resourceVersion = integer(root, "resourceVersion", 0, 0, Integer.MAX_VALUE);
      return new ShellTaskConfig(
          shellExecutable, scriptArgs, envVars, timeoutSeconds, workingDirectory,
          resourceId, resourceVersion);
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalArgumentException("Shell configJson is not valid JSON", exception);
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
