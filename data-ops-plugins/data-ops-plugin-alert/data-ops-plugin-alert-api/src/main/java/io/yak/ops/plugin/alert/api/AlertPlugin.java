package io.yak.ops.plugin.alert.api;

/**
 * Stable alert plugin contract.
 *
 * <p>Each implementation corresponds to a specific alert channel (DingTalk, WeCom, Email, etc.)
 * and is discovered via Java ServiceLoader.
 */
public interface AlertPlugin {

  /** Plugin identity and capability metadata. */
  AlertPluginDescriptor descriptor();

  /** Alert channel type identifier (shortcut for {@code descriptor().type()}). */
  default String type() {
    return descriptor().type();
  }

  /**
   * Send an alert message through this channel.
   *
   * @param message the alert to deliver; {@code message.configJson()} carries channel-specific
   *     configuration such as webhook URL and secrets
   * @return delivery result
   */
  AlertResult send(AlertMessage message);

  /**
   * Test channel connectivity using the supplied configuration.
   *
   * @param configJson channel-specific configuration JSON
   * @return {@code true} if the channel is reachable and credentials are valid
   */
  boolean testConnection(String configJson);
}
