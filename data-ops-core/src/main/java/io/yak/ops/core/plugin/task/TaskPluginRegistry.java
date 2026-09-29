package io.yak.ops.core.plugin.task;

import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.TreeMap;

/** Discovers task plugins through Java ServiceLoader and provides stable type-based routing. */
public final class TaskPluginRegistry {

  private final Map<String, TaskPlugin> plugins;

  private TaskPluginRegistry(Map<String, TaskPlugin> plugins) {
    this.plugins = Collections.unmodifiableMap(new LinkedHashMap<>(plugins));
  }

  public static TaskPluginRegistry load() {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    if (classLoader == null) {
      classLoader = TaskPlugin.class.getClassLoader();
    }
    return load(classLoader);
  }

  public static TaskPluginRegistry load(ClassLoader classLoader) {
    Objects.requireNonNull(classLoader, "classLoader");
    return from(ServiceLoader.load(TaskPlugin.class, classLoader));
  }

  public static TaskPluginRegistry from(Iterable<TaskPlugin> candidates) {
    Objects.requireNonNull(candidates, "candidates");

    Map<String, TaskPlugin> discovered = new TreeMap<>();
    for (TaskPlugin plugin : candidates) {
      Objects.requireNonNull(plugin, "Task plugin must not be null");
      String type = normalizeType(plugin.type());
      TaskPlugin previous = discovered.putIfAbsent(type, plugin);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate task plugin for type "
                + type
                + ": "
                + previous.getClass().getName()
                + " and "
                + plugin.getClass().getName());
      }
    }
    return new TaskPluginRegistry(discovered);
  }

  public Optional<TaskPlugin> find(String type) {
    String normalized = normalizeNullableType(type);
    return normalized == null ? Optional.empty() : Optional.ofNullable(plugins.get(normalized));
  }

  public TaskPlugin require(String type) {
    String normalized = normalizeNullableType(type);
    if (normalized == null) {
      throw new IllegalArgumentException("Task plugin type must not be blank");
    }
    TaskPlugin plugin = plugins.get(normalized);
    if (plugin == null) {
      throw new IllegalArgumentException("Task plugin not found: " + normalized);
    }
    return plugin;
  }

  public Map<String, TaskPlugin> plugins() {
    return plugins;
  }

  public List<TaskPluginDescriptor> descriptors() {
    List<TaskPluginDescriptor> descriptors = new ArrayList<>(plugins.size());
    for (TaskPlugin plugin : plugins.values()) {
      descriptors.add(plugin.descriptor());
    }
    return List.copyOf(descriptors);
  }

  private static String normalizeType(String type) {
    String normalized = normalizeNullableType(type);
    if (normalized == null) {
      throw new IllegalArgumentException("Task plugin type must not be blank");
    }
    return normalized;
  }

  private static String normalizeNullableType(String type) {
    if (type == null || type.trim().isEmpty()) {
      return null;
    }
    return type.trim().toUpperCase(Locale.ROOT);
  }
}
