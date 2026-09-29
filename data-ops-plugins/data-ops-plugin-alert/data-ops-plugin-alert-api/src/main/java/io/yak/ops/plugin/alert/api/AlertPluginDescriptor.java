package io.yak.ops.plugin.alert.api;

/**
 * Alert plugin descriptor providing identity and capability metadata.
 *
 * @param type unique alert channel type identifier (e.g. {@code DINGTALK})
 * @param name human-readable display name
 * @param description short description of the alert channel
 * @param version plugin implementation version
 */
public record AlertPluginDescriptor(
    String type,
    String name,
    String description,
    String version) {

  public AlertPluginDescriptor {
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("Alert plugin type must not be blank");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Alert plugin name must not be blank");
    }
  }
}
