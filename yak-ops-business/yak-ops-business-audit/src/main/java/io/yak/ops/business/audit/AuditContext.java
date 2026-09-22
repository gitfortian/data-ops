package io.yak.ops.business.audit;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Explicit thread-local scope for restoring an audit carrier across asynchronous boundaries. */
public final class AuditContext {

  private static final ThreadLocal<AuditCarrier> CURRENT = new ThreadLocal<>();

  private AuditContext() {}

  public static Optional<AuditCarrier> current() {
    return Optional.ofNullable(CURRENT.get());
  }

  public static Scope open(AuditCarrier carrier) {
    Objects.requireNonNull(carrier, "carrier must not be null");
    AuditCarrier previous = CURRENT.get();
    CURRENT.set(carrier);
    return () -> restore(previous);
  }

  public static void run(AuditCarrier carrier, Runnable action) {
    Objects.requireNonNull(action, "action must not be null");
    try (Scope ignored = open(carrier)) {
      action.run();
    }
  }

  public static <T> T call(AuditCarrier carrier, Supplier<T> action) {
    Objects.requireNonNull(action, "action must not be null");
    try (Scope ignored = open(carrier)) {
      return action.get();
    }
  }

  private static void restore(AuditCarrier previous) {
    if (previous == null) {
      CURRENT.remove();
    } else {
      CURRENT.set(previous);
    }
  }

  @FunctionalInterface
  public interface Scope extends AutoCloseable {
    @Override
    void close();
  }
}
