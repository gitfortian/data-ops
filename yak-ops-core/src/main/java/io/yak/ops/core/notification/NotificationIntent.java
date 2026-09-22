package io.yak.ops.core.notification;

/**
 * A user-facing business event that may be routed to one or more notification destinations.
 *
 * <p>The owning business module supplies durable business identity and copy only. Recipient
 * resolution, policy selection, transaction timing, and delivery belong to the notification
 * routing layer.</p>
 */
public record NotificationIntent(
    long projectId,
    Type type,
    Level level,
    String title,
    String summary,
    String content,
    String sourceType,
    String sourceId,
    String actionPath) {

  public NotificationIntent {
    if (projectId <= 0L) throw new IllegalArgumentException("notification projectId must be positive");
    if (type == null) throw new IllegalArgumentException("notification type must not be null");
    if (level == null) throw new IllegalArgumentException("notification level must not be null");
    title = required(title, "notification title must not be blank");
    sourceType = required(sourceType, "notification sourceType must not be blank");
    sourceId = required(sourceId, "notification sourceId must not be blank");
    summary = trimToNull(summary);
    content = trimToNull(content);
    actionPath = internalPath(actionPath);
  }

  public enum Type {
    TASK,
    QUALITY,
    SYSTEM
  }

  public enum Level {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
  }

  private static String required(String value, String message) {
    String normalized = trimToNull(value);
    if (normalized == null) throw new IllegalArgumentException(message);
    return normalized;
  }

  private static String trimToNull(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private static String internalPath(String value) {
    String normalized = trimToNull(value);
    if (normalized == null) return null;
    if (!normalized.startsWith("/") || normalized.startsWith("//")) {
      throw new IllegalArgumentException("notification actionPath must be an internal route");
    }
    return normalized;
  }
}
