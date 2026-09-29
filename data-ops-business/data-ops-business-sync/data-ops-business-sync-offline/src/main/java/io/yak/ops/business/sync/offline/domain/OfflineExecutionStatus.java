package io.yak.ops.business.sync.offline.domain;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Yak Ops stable Attempt status mapping for Link-Up offline execution. */
public enum OfflineExecutionStatus {
  CREATED,
  SUBMITTED,
  QUEUED,
  RUNNING,
  SUCCEEDED,
  FAILED,
  CANCELED,
  UNKNOWN;

  private static final Set<OfflineExecutionStatus> ACTIVE =
      EnumSet.of(CREATED, SUBMITTED, QUEUED, RUNNING, UNKNOWN);
  private static final Set<OfflineExecutionStatus> CONFIRMED_ACTIVE =
      EnumSet.of(CREATED, SUBMITTED, QUEUED, RUNNING);

  public boolean isActive() {
    return ACTIVE.contains(this);
  }

  /** UNKNOWN occupies the Batch slot but does not prove an active remote Job. */
  public boolean isConfirmedActive() {
    return CONFIRMED_ACTIVE.contains(this);
  }

  public boolean isTerminal() {
    return !isActive();
  }

  public static OfflineExecutionStatus parse(String value) {
    if (value == null || value.trim().isEmpty()) {
      return CREATED;
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if ("FINISHED".equals(normalized) || "COMPLETED".equals(normalized)) {
      return SUCCEEDED;
    }
    if ("CANCELLED".equals(normalized)
        || "CANCELING".equals(normalized)
        || "CANCELLING".equals(normalized)) {
      return CANCELED;
    }
    // LOST is a persisted compatibility value, not a current domain state.
    if ("LOST".equals(normalized)) {
      return UNKNOWN;
    }
    return valueOf(normalized);
  }

  public static boolean isActive(String value) {
    return parseSafely(value).map(OfflineExecutionStatus::isActive).orElse(false);
  }

  public static boolean isConfirmedActive(String value) {
    return parseSafely(value).map(OfflineExecutionStatus::isConfirmedActive).orElse(false);
  }

  public static boolean isCreated(String value) {
    return parseSafely(value).filter(status -> status == CREATED).isPresent();
  }

  private static Optional<OfflineExecutionStatus> parseSafely(String value) {
    if (value == null || value.trim().isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(parse(value));
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }
}
