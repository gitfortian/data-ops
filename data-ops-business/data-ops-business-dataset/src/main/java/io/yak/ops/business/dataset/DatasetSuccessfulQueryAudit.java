package io.yak.ops.business.dataset;

import java.util.Objects;

/**
 * Persisted Dataset audit identity used only as an exclusive recovery cursor.
 * The database audit ID is not a DatasetVersion or Consumer identity.
 */
public record DatasetSuccessfulQueryAudit(long auditId, DatasetQueryPerformance trace) {

  public DatasetSuccessfulQueryAudit {
    if (auditId <= 0L) {
      throw new IllegalArgumentException("Persisted Dataset audit ID must be positive");
    }
    Objects.requireNonNull(trace, "trace");
  }
}
