package io.yak.ops.plugin.database.jdbc;

import java.sql.DriverManager;
import java.sql.SQLException;

/** JDBC 驱动加载工具：兼容应用内驱动与上传到独立类加载器的外置驱动。 */
public final class JdbcDriverLoader {

  private JdbcDriverLoader() {}

  public static void loadDriver(String driverClassName, String jdbcUrl) throws ClassNotFoundException {
    try {
      Class.forName(driverClassName);
    } catch (ClassNotFoundException failure) {
      try {
        DriverManager.getDriver(jdbcUrl);
      } catch (SQLException notRegistered) {
        throw failure;
      }
    }
  }
}
