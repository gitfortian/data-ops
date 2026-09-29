package io.yak.ops.core.plugin.alert;

import io.yak.ops.plugin.alert.api.AlertPlugin;
import io.yak.ops.plugin.alert.api.AlertPluginDescriptor;
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

/** Discovers alert plugins through Java ServiceLoader and provides stable type-based routing. */
public final class AlertPluginRegistry {

  private final Map<String, AlertPlugin> plugins;

  private AlertPluginRegistry(Map<String, AlertPlugin> plugins) {
    this.plugins = Collections.unmodifiableMap(new LinkedHashMap<>(plugins));
  }

  public static AlertPluginRegistry load() {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    if (classLoader == null) {
      classLoader = AlertPlugin.class.getClassLoader();
    }
    return load(classLoader);
  }

  public static AlertPluginRegistry load(ClassLoader classLoader) {
    Objects.requireNonNull(classLoader, "classLoader");
    return from(ServiceLoader.load(AlertPlugin.class, classLoader));
  }

  public static AlertPluginRegistry from(Iterable<AlertPlugin> candidates) {
    Objects.requireNonNull(candidates, "candidates");

    Map<String, AlertPlugin> discovered = new TreeMap<>();
    for (AlertPlugin plugin : candidates) {
      Objects.requireNonNull(plugin, "Alert plugin must not be null");
      String type = normalizeType(plugin.type());
      AlertPlugin previous = discovered.putIfAbsent(type, plugin);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate alert plugin for type "
                + type
                + ": "
                + previous.getClass().getName()
                + " and "
                + plugin.getClass().getName());
      }
    }
    return new AlertPluginRegistry(discovered);
  }

  public Optional<AlertPlugin> find(String type) {
    String normalized = normalizeNullableType(type);
    return normalized == null ? Optional.empty() : Optional.ofNullable(plugins.get(normalized));
  }

  public AlertPlugin require(String type) {
    String normalized = normalizeNullableType(type);
    if (normalized == null) {
      throw new IllegalArgumentException("Alert plugin type must not be blank");
    }
    AlertPlugin plugin = plugins.get(normalized);
    if (plugin == null) {
      throw new IllegalArgumentException("Alert plugin not found: " + normalized);
    }
    return plugin;
  }

  public Map<String, AlertPlugin> plugins() {
    return plugins;
  }

  public List<AlertPluginDescriptor> descriptors() {
    List<AlertPluginDescriptor> result = new ArrayList<>(plugins.size());
    for (AlertPlugin plugin : plugins.values()) {
      result.add(plugin.descriptor());
    }
    return List.copyOf(result);
  }

  private static String normalizeType(String type) {
    String normalized = normalizeNullableType(type);
    if (normalized == null) {
      throw new IllegalArgumentException("Alert plugin type must not be blank");
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
