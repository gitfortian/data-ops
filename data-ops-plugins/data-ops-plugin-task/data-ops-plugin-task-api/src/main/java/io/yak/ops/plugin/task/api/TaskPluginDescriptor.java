package io.yak.ops.plugin.task.api;

import java.util.Locale;
import java.util.Objects;

/** Stable metadata exposed by a task plugin. */
public record TaskPluginDescriptor(
    String type,
    String displayName,
    String description,
    String version,
    int schemaVersion,
    boolean executable,
    boolean cancellable) {

  public TaskPluginDescriptor {
    type = normalizeType(type);
    displayName = requireText(displayName, "displayName");
    description = Objects.requireNonNullElse(description, "");
    version = requireText(version, "version");
    if (schemaVersion <= 0) {
      throw new IllegalArgumentException("schemaVersion must be positive");
    }
  }

  private static String normalizeType(String value) {
    return requireText(value, "type").toUpperCase(Locale.ROOT);
  }

  private static String requireText(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
