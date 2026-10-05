package io.yak.framework.common.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.WeakHashMap;
import javax.sql.DataSource;

/** Vendor selection for platform persistence; external datasource SQL keeps its own dialect. */
public final class JdbcDatabase {
  // Platform pools have a fixed vendor. Do not borrow another connection on each transactional write.
  private static final Map<DataSource, Boolean> VENDORS = new WeakHashMap<>();
  private JdbcDatabase() {}

  public static boolean isPostgresql(String url) {
    return url != null && url.startsWith("jdbc:postgresql:");
  }

  public static boolean isPostgresql(DataSource dataSource) {
    synchronized (VENDORS) {
      Boolean vendor = VENDORS.get(dataSource);
      if (vendor != null) return vendor;
    }
    try (Connection connection = dataSource.getConnection()) {
      boolean vendor = isPostgresql(connection.getMetaData().getURL());
      synchronized (VENDORS) {
        VENDORS.put(dataSource, vendor);
      }
      return vendor;
    } catch (SQLException exception) {
      throw new IllegalStateException("Unable to identify platform database", exception);
    }
  }

  /** Preserve the existing MySQL migration locations and their recorded checksums. */
  public static String migrationLocation(DataSource dataSource, String mysqlLocation) {
    return isPostgresql(dataSource)
        ? mysqlLocation.replace("/migration", "/migration-postgresql") : mysqlLocation;
  }
}
