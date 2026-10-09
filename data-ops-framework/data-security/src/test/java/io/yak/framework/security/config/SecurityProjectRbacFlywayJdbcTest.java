package io.yak.framework.security.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real database smoke/regression of the unchanged consolidated Security Flyway
 * baselines. Runs on both live CI vendors; excluded from local builds without
 * database environment variables, not a historical V1/V2/V3 upgrade simulation.
 */
class SecurityProjectRbacFlywayJdbcTest {

  @Test
  void mysqlHostSchemaBaselineRetainsProjectIsolationAndRbacGrant() throws Exception {
    String url = System.getenv("ARCHITECTURE_MYSQL_URL");
    Assumptions.assumeTrue(url != null && !url.isBlank(), "MySQL service not configured");
    String user = System.getenv("ARCHITECTURE_MYSQL_USERNAME");
    String pass = System.getenv("ARCHITECTURE_MYSQL_PASSWORD");
    String database = unique("a82j_m");
    try (Connection admin = DriverManager.getConnection(url, user, pass);
         Statement statement = admin.createStatement()) {
      statement.execute("CREATE DATABASE `" + database + "`");
    }
    try {
      String target = mysqlDatabaseUrl(url, database);
      assertBaselineAndRelations(target, user, pass,
          "classpath:yak-security/db/migration", null);
    } finally {
      try (Connection admin = DriverManager.getConnection(url, user, pass);
           Statement statement = admin.createStatement()) {
        statement.execute("DROP DATABASE IF EXISTS `" + database + "`");
      }
    }
  }

  @Test
  void postgresHostSchemaBaselineRetainsProjectIsolationAndRbacGrant() throws Exception {
    String url = System.getenv("ARCHITECTURE_PG_URL");
    Assumptions.assumeTrue(url != null && !url.isBlank(), "PostgreSQL service not configured");
    String user = System.getenv("ARCHITECTURE_PG_USERNAME");
    String pass = System.getenv("ARCHITECTURE_PG_PASSWORD");
    String schema = unique("a82j_p");
    try (Connection admin = DriverManager.getConnection(url, user, pass);
         Statement statement = admin.createStatement()) {
      statement.execute("CREATE SCHEMA \"" + schema + "\"");
    }
    try {
      String target = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
      assertBaselineAndRelations(target, user, pass,
          "classpath:yak-security/db/migration-postgresql", schema);
    } finally {
      try (Connection admin = DriverManager.getConnection(url, user, pass);
           Statement statement = admin.createStatement()) {
        statement.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
      }
    }
  }

  @Test
  void mysqlExistingHostFlywayHistoryBeforeSecurityV1CanMigrate() throws Exception {
    String url = System.getenv("ARCHITECTURE_MYSQL_URL");
    Assumptions.assumeTrue(url != null && !url.isBlank(), "MySQL service not configured");
    String user = System.getenv("ARCHITECTURE_MYSQL_USERNAME");
    String pass = System.getenv("ARCHITECTURE_MYSQL_PASSWORD");
    String database = unique("a82hist_m");
    try (Connection admin = DriverManager.getConnection(url, user, pass);
         Statement statement = admin.createStatement()) {
      statement.execute("CREATE DATABASE `" + database + "`");
    }
    try {
      assertHostHistoryBeforeSecurityV1(mysqlDatabaseUrl(url, database), user, pass,
          "classpath:yak-security/db/migration", null);
    } finally {
      try (Connection admin = DriverManager.getConnection(url, user, pass);
           Statement statement = admin.createStatement()) {
        statement.execute("DROP DATABASE IF EXISTS `" + database + "`");
      }
    }
  }

  @Test
  void postgresExistingHostFlywayHistoryBeforeSecurityV1CanMigrate() throws Exception {
    String url = System.getenv("ARCHITECTURE_PG_URL");
    Assumptions.assumeTrue(url != null && !url.isBlank(), "PostgreSQL service not configured");
    String user = System.getenv("ARCHITECTURE_PG_USERNAME");
    String pass = System.getenv("ARCHITECTURE_PG_PASSWORD");
    String schema = unique("a82hist_p");
    try (Connection admin = DriverManager.getConnection(url, user, pass);
         Statement statement = admin.createStatement()) {
      statement.execute("CREATE SCHEMA \"" + schema + "\"");
    }
    try {
      String target = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
      assertHostHistoryBeforeSecurityV1(target, user, pass,
          "classpath:yak-security/db/migration-postgresql", schema);
    } finally {
      try (Connection admin = DriverManager.getConnection(url, user, pass);
           Statement statement = admin.createStatement()) {
        statement.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
      }
    }
  }

  /**
   * Existing host Flyway metadata can precede Security V1. A higher existing
   * baseline than 1 is a distinct unresolved production-history scenario.
   */
  private static void assertHostHistoryBeforeSecurityV1(String url, String user,
      String pass, String location, String schema) throws Exception {
    try (Connection connection = DriverManager.getConnection(url, user, pass);
         Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE a82_host_existing_row(id INTEGER PRIMARY KEY)");
      statement.execute("INSERT INTO a82_host_existing_row(id) VALUES (7)");
    }
    var hostConfig = Flyway.configure().dataSource(url, user, pass)
        .baselineVersion(MigrationVersion.fromVersion("0.5"))
        .baselineDescription("existing host installation");
    if (schema != null) hostConfig.schemas(schema).defaultSchema(schema);
    Flyway host = hostConfig.load();
    host.baseline();

    var securityConfig = Flyway.configure().dataSource(url, user, pass)
        .locations(location)
        .placeholders(Collections.singletonMap("appName", "a82_history_app"))
        .baselineOnMigrate(true)
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .outOfOrder(true);
    if (schema != null) securityConfig.schemas(schema).defaultSchema(schema);
    Flyway security = securityConfig.load();
    assertTrue(security.migrate().migrationsExecuted >= 1,
        "Security V1 must execute against existing host history older than V1");
    assertTrue(security.validateWithResult().validationSuccessful);
    try (Connection connection = DriverManager.getConnection(url, user, pass)) {
      assertEquals(1, count(connection,
          "SELECT COUNT(*) FROM a82_host_existing_row WHERE id = ?", 7));
      assertEquals(1, count(connection,
          "SELECT COUNT(*) FROM yak_security_permission WHERE permission_code = ? AND app_name = ?",
          "security:project:read", "a82_history_app"));
    }
    assertEquals(0, security.migrate().migrationsExecuted);
  }

  private static void assertBaselineAndRelations(String url, String user, String pass,
      String location, String schema) throws Exception {
    try (Connection connection = DriverManager.getConnection(url, user, pass);
         Statement statement = connection.createStatement()) {
      // Simulate a shared host database/schema that predates Security's Flyway history.
      statement.execute("CREATE TABLE a82j_host_marker(id INTEGER)");
    }

    var flywayConfig = Flyway.configure()
        .dataSource(url, user, pass)
        .locations(location)
        .placeholders(Collections.singletonMap("appName", "a82j_app"))
        .baselineOnMigrate(true)
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .outOfOrder(true);
    if (schema != null) {
      flywayConfig.schemas(schema).defaultSchema(schema);
    }
    Flyway flyway = flywayConfig.load();
    assertTrue(flyway.migrate().migrationsExecuted >= 1);
    assertTrue(flyway.validateWithResult().validationSuccessful);

    try (Connection connection = DriverManager.getConnection(url, user, pass)) {
      assertEquals(1, count(connection,
          "SELECT COUNT(*) FROM yak_security_permission WHERE permission_code = ? AND app_name = ?",
          "security:project:read", "a82j_app"));
      insertProject(connection, 91001L, 0, 81001L, "a82j_app");
      insertProject(connection, 91002L, 1, 81001L, "a82j_app");
      insertProject(connection, 91003L, 0, 81001L, "other_app");
      assertEquals(1, count(connection,
          "SELECT COUNT(*) FROM yak_security_user_project WHERE app_name = ? AND project_id = ? AND user_type = ?",
          "a82j_app", 81001L, 0));
      assertEquals(1, count(connection,
          "SELECT COUNT(*) FROM yak_security_user_project WHERE app_name = ? AND project_id = ? AND user_type = ?",
          "a82j_app", 81001L, 1));
      assertEquals(1, count(connection,
          "SELECT COUNT(*) FROM yak_security_user_project WHERE app_name = ? AND project_id = ? AND user_type = ?",
          "other_app", 81001L, 0));

      try (PreparedStatement grant = connection.prepareStatement(
          "INSERT INTO yak_security_role_permission(role_id, permission_id, app_name) VALUES (?, ?, ?)")) {
        grant.setLong(1, 99001L);
        grant.setLong(2, 17L);
        grant.setString(3, "a82j_app");
        assertEquals(1, grant.executeUpdate());
      }
      connection.setAutoCommit(false);
      insertProject(connection, 91004L, 0, 81001L, "a82j_app");
      connection.rollback();
      connection.setAutoCommit(true);
      assertEquals(0, count(connection,
          "SELECT COUNT(*) FROM yak_security_user_project WHERE app_name = ? AND user_id = ?",
          "a82j_app", 91004L));
    }

    // A repeat startup must not change historical permissions, project memberships or grants.
    assertEquals(0, flyway.migrate().migrationsExecuted);
    assertTrue(flyway.validateWithResult().validationSuccessful);
    try (Connection connection = DriverManager.getConnection(url, user, pass)) {
      assertEquals(1, count(connection,
          "SELECT COUNT(*) FROM yak_security_role_permission WHERE app_name = ? AND role_id = ? AND permission_id = ?",
          "a82j_app", 99001L, 17L));
      assertEquals(2, count(connection,
          "SELECT COUNT(*) FROM yak_security_user_project WHERE app_name = ? AND project_id = ?",
          "a82j_app", 81001L));
      assertEquals(1, count(connection,
          "SELECT COUNT(*) FROM yak_security_user_project WHERE app_name = ? AND project_id = ?",
          "other_app", 81001L));
    }
  }

  private static void insertProject(Connection connection, long userId, int type,
      long projectId, String app) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(
        "INSERT INTO yak_security_user_project(user_id,user_type,project_id,app_name) VALUES (?,?,?,?)")) {
      ps.setLong(1, userId);
      ps.setInt(2, type);
      ps.setLong(3, projectId);
      ps.setString(4, app);
      assertEquals(1, ps.executeUpdate());
    }
  }

  private static long count(Connection connection, String sql, Object... params) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      for (int i = 0; i < params.length; i++) {
        ps.setObject(i + 1, params[i]);
      }
      try (ResultSet rows = ps.executeQuery()) {
        assertTrue(rows.next());
        return rows.getLong(1);
      }
    }
  }

  private static String mysqlDatabaseUrl(String url, String database) {
    int slash = url.indexOf('/', url.indexOf("://") + 3);
    if (slash < 0) throw new IllegalArgumentException("MySQL URL must include a database");
    int question = url.indexOf('?', slash);
    return url.substring(0, slash + 1) + database +
        (question < 0 ? "" : url.substring(question));
  }

  private static String unique(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }
}
