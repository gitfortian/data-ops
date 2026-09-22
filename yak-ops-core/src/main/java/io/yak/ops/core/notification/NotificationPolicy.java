package io.yak.ops.core.notification;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Effective routing policy for one notification intent.
 *
 * <p>Business modules do not interpret this policy directly. Resolvers choose an effective policy
 * and sinks consume only the parts relevant to their delivery destination.</p>
 */
public record NotificationPolicy(
    boolean enabled,
    RecipientStrategy recipientStrategy,
    List<Long> recipientUserIds,
    Set<Destination> destinations,
    List<Long> alertChannelIds) {

  public NotificationPolicy {
    if (recipientStrategy == null) {
      throw new IllegalArgumentException("notification recipientStrategy must not be null");
    }
    recipientUserIds = normalizeIds(recipientUserIds);
    destinations = destinations == null ? Set.of() : Set.copyOf(destinations);
    alertChannelIds = normalizeIds(alertChannelIds);
    if (enabled && destinations.isEmpty()) {
      throw new IllegalArgumentException("enabled notification policy requires a destination");
    }
    if (recipientStrategy == RecipientStrategy.PROJECT_OWNER && !recipientUserIds.isEmpty()) {
      throw new IllegalArgumentException("PROJECT_OWNER policy must not contain explicit user ids");
    }
    if (enabled
        && destinations.contains(Destination.IN_APP)
        && recipientStrategy == RecipientStrategy.EXPLICIT_USERS
        && recipientUserIds.isEmpty()) {
      throw new IllegalArgumentException("EXPLICIT_USERS in-app policy requires recipient user ids");
    }
    if (enabled
        && destinations.contains(Destination.ALERT)
        && alertChannelIds.isEmpty()) {
      throw new IllegalArgumentException("ALERT notification policy requires alert channel ids");
    }
  }

  /** Current backward-compatible default inherited from the original Message Center integration. */
  public static NotificationPolicy projectOwnersInApp() {
    return new NotificationPolicy(
        true,
        RecipientStrategy.PROJECT_OWNER,
        List.of(),
        Set.of(Destination.IN_APP),
        List.of());
  }

  public static NotificationPolicy disabled() {
    return new NotificationPolicy(
        false,
        RecipientStrategy.PROJECT_OWNER,
        List.of(),
        Set.of(),
        List.of());
  }

  public boolean routesTo(Destination destination) {
    return enabled && destination != null && destinations.contains(destination);
  }

  public enum RecipientStrategy {
    PROJECT_OWNER,
    EXPLICIT_USERS
  }

  public enum Destination {
    IN_APP,
    ALERT
  }

  private static List<Long> normalizeIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) return List.of();
    return ids.stream()
        .filter(Objects::nonNull)
        .filter(id -> id > 0L)
        .distinct()
        .toList();
  }
}
