package io.yak.ops.business.modeling.domain;

import java.util.Locale;
import java.util.Optional;

/** Target database dialect a physical model lands on. Single dialect per model (decision D10). */
public enum ModelDialect {
  MYSQL,
  POSTGRESQL,
  ORACLE,
  DORIS,
  CLICKHOUSE,
  STARROCKS;

  /** Tolerant parse of the persisted column value; unknown values yield empty. */
  public static Optional<ModelDialect> fromStored(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(ModelDialect.valueOf(value.trim().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }
}
