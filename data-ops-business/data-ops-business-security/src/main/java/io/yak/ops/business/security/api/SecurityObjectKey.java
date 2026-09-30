package io.yak.ops.business.security.api;

/** Canonical physical-resource keys shared by Security and governed query consumers. */
public final class SecurityObjectKey {

  private SecurityObjectKey() {}

  public static String column(String datasourceId, String database, String table, String column) {
    return String.join(":", "COLUMN", safe(datasourceId), safe(database), safe(table), safe(column));
  }

  private static String safe(String value) {
    return value == null || value.isBlank() ? "-" : value.trim();
  }
}
