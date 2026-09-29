package io.yak.ops.spi.task.model;

import java.util.Locale;

/**
 * Canonical SQL dialect identity shared by authoring and SQL task runtimes.
 *
 * <p>Task identity remains {@code taskType=SQL}. The dialect describes which database SQL
 * semantics the task targets without turning every database into a separate task type.
 */
public enum SqlDialect {
  GENERIC,
  MYSQL,
  TIDB,
  GOLDENDB,
  GBASE8C,
  GBASE8A,
  GBASE8S,
  HANA,
  ORACLE,
  POSTGRE_SQL,
  DB2,
  OPEN_GAUSS,
  SQL_SERVER,
  OCEANBASE,
  YASHAN_DB,
  HIGHGO,
  IRIS,
  XUGU,
  DUCKDB,
  DORIS,
  STARROCKS,
  CLICKHOUSE,
  KINGBASE,
  DAMENG;

  /** Missing dialect is the legacy SQL-node shape and intentionally resolves to GENERIC. */
  public static SqlDialect parseOrGeneric(String value) {
    if (value == null || value.isBlank()) return GENERIC;

    String normalized = value.trim().toUpperCase(Locale.ROOT)
        .replace('-', '_')
        .replace(' ', '_');
    normalized = switch (normalized) {
      case "AUTO", "UNKNOWN", "GENERIC_SQL" -> "GENERIC";
      case "POSTGRES", "POSTGRESQL", "PGSQL" -> "POSTGRE_SQL";
      case "MARIADB" -> "MYSQL";
      case "GOLDEN_DB", "ZTE_GOLDENDB" -> "GOLDENDB";
      case "GBASE_8C" -> "GBASE8C";
      case "GBASE_8A" -> "GBASE8A";
      case "GBASE_8S" -> "GBASE8S";
      case "SAP_HANA", "SAPHANA" -> "HANA";
      case "OPENGAUSS" -> "OPEN_GAUSS";
      case "SQLSERVER", "MSSQL" -> "SQL_SERVER";
      case "YASHANDB", "YASDB" -> "YASHAN_DB";
      case "HIGH_GO", "HGDB" -> "HIGHGO";
      case "INTERSYSTEMS_IRIS" -> "IRIS";
      case "XUGUDB" -> "XUGU";
      case "DUCK_DB" -> "DUCKDB";
      case "STAR_ROCKS" -> "STARROCKS";
      case "KINGBASEES" -> "KINGBASE";
      case "DM" -> "DAMENG";
      default -> normalized;
    };

    try {
      return valueOf(normalized);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("不支持的 SQL dialect：" + value, exception);
    }
  }

  public boolean isMysqlFamily() {
    return switch (this) {
      case MYSQL, TIDB, GOLDENDB, DORIS, STARROCKS -> true;
      default -> false;
    };
  }

  public boolean isPostgresFamily() {
    return switch (this) {
      case POSTGRE_SQL, OPEN_GAUSS, GBASE8C, HIGHGO, KINGBASE -> true;
      default -> false;
    };
  }
}
