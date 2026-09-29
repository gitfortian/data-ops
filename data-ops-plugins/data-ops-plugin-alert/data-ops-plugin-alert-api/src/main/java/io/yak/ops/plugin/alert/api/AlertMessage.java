package io.yak.ops.plugin.alert.api;

/**
 * Alert message to be sent through an alert channel.
 *
 * @param title alert title (may be used as subject or heading depending on channel)
 * @param content alert body content
 * @param level severity level
 * @param configJson channel-specific configuration JSON (e.g. webhook URL, secret)
 */
public record AlertMessage(
    String title,
    String content,
    AlertLevel level,
    String configJson) {

  public AlertMessage {
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("Alert content must not be blank");
    }
    if (level == null) {
      level = AlertLevel.INFO;
    }
  }

  /** Convenience factory for a simple info-level alert. */
  public static AlertMessage of(String title, String content, String configJson) {
    return new AlertMessage(title, content, AlertLevel.INFO, configJson);
  }

  /** Convenience factory for an alert with explicit level. */
  public static AlertMessage of(String title, String content, AlertLevel level, String configJson) {
    return new AlertMessage(title, content, level, configJson);
  }
}
