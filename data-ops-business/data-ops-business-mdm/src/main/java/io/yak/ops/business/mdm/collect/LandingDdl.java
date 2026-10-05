package io.yak.ops.business.mdm.collect;

import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import java.sql.Types;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 落地表 DDL 预建(R1):按源表列结构在平台业务库内生成 CREATE TABLE。
 * 列名/表名统一走安全标识符校验(与 MdmMasterSqlGenerator 同口径),类型
 * 采用 JDBC 标准类型映射,未知/大文本类型一律兜底 TEXT(落地只保真读取,不做约束).
 */
public final class LandingDdl {

  private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_]{1,64}");

  private LandingDdl() {}

  public static void requireSafeIdentifier(String name) {
    if (name == null || !SAFE_IDENTIFIER.matcher(name).matches()) {
      throw new IllegalArgumentException("不合法的标识符: " + name);
    }
  }

  public static boolean isSafeIdentifier(String name) {
    return name != null && SAFE_IDENTIFIER.matcher(name).matches();
  }

  /** database/table 均已在调用侧校验;返回可直接执行的建表语句(IF NOT EXISTS 幂等)。 */
  public static String createTable(String database, String table, List<CatalogColumn> columns) {
    return createTable(database, table, columns, false);
  }

  public static String createTable(String database, String table, List<CatalogColumn> columns,
      boolean postgresql) {
    requireSafeIdentifier(table);
    if (database != null && !database.isBlank()) {
      requireSafeIdentifier(database);
    }
    if (columns == null || columns.isEmpty()) {
      throw new IllegalArgumentException("源表无列结构,无法预建落地表");
    }
    StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
    qualify(database, table, sql);
    sql.append(" (");
    boolean first = true;
    for (CatalogColumn column : columns) {
      requireSafeIdentifier(column.name());
      if (!first) {
        sql.append(", ");
      }
      first = false;
      sql.append('`').append(column.name()).append("` ").append(columnType(column));
    }
    sql.append(
        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            + " COMMENT='MDM 采集落地表'");
    if (postgresql) {
      // All identifiers have already passed the strict whitelist; only this generated DDL is adapted.
      String ddl = sql.substring(0, sql.indexOf(") ENGINE=")) + ")";
      return ddl.replace('`', '"').replace(" DATETIME", " TIMESTAMP")
          .replace(" DOUBLE", " DOUBLE PRECISION").replace(" TINYINT", " BOOLEAN");
    }
    return sql.toString();
  }

  private static void qualify(String database, String table, StringBuilder sql) {
    if (database != null && !database.isBlank()) {
      sql.append('`').append(database).append("`.`").append(table).append('`');
    } else {
      sql.append('`').append(table).append('`');
    }
  }

  private static String columnType(CatalogColumn column) {
    int size = column.size() == null ? 0 : column.size();
    int scale = column.scale() == null ? 0 : column.scale();
    return switch (column.jdbcType()) {
      case Types.BIT, Types.BOOLEAN -> "TINYINT";
      case Types.TINYINT, Types.SMALLINT -> "SMALLINT";
      case Types.INTEGER -> "INT";
      case Types.BIGINT -> "BIGINT";
      case Types.FLOAT, Types.REAL, Types.DOUBLE -> "DOUBLE";
      case Types.DECIMAL, Types.NUMERIC -> decimalType(size, scale);
      case Types.DATE -> "DATE";
      case Types.TIME, Types.TIME_WITH_TIMEZONE -> "TIME";
      case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "DATETIME";
      case Types.CHAR, Types.NCHAR -> "CHAR(" + Math.min(size <= 0 ? 1 : size, 255) + ")";
      case Types.VARCHAR, Types.NVARCHAR -> varcharType(size);
      case Types.CLOB, Types.NCLOB, Types.LONGVARCHAR, Types.LONGNVARCHAR -> "TEXT";
      default -> "TEXT";
    };
  }

  private static String decimalType(int size, int scale) {
    int precision = size <= 0 || size > 65 ? 38 : size;
    int safeScale = Math.min(Math.max(scale, 0), Math.min(precision - 1, 30));
    return "DECIMAL(" + precision + "," + safeScale + ")";
  }

  private static String varcharType(int size) {
    // 超长字符串直接 TEXT,避免落地写入截断
    if (size <= 0 || size > 16000) {
      return "TEXT";
    }
    return "VARCHAR(" + size + ")";
  }
}
