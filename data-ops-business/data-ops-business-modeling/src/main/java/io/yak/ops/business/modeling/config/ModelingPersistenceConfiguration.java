package io.yak.ops.business.modeling.config;

import io.yak.ops.business.datasource.config.BusinessDatabaseConfiguration;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;

/** Modeling persistence and Flyway configuration. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnModelingPersistence
@EnableConfigurationProperties(DataSourceProperties.class)
@Import(BusinessDatabaseConfiguration.class)
@MapperScan(
    basePackages = "io.yak.ops.business.modeling.dao.mapper",
    sqlSessionFactoryRef = "yakBusinessSqlSessionFactory")
public class ModelingPersistenceConfiguration {

  private static final String HISTORY_TABLE = "flyway_schema_history_modeling";
  private static final String COMMON_MIGRATION_LOCATION = "classpath:db/migration/yak-modeling";
  private static final String IMPACT_HISTORY_LOCATION =
      "classpath:db/migration/yak-modeling-history-impact";
  private static final String LOGICAL_HISTORY_LOCATION =
      "classpath:db/migration/yak-modeling-history-logical";
  private static final String V22_META_HISTORY_LOCATION =
      "classpath:db/migration/yak-modeling-history-v22-meta";
  private static final String V22_FOUNDATION_HISTORY_LOCATION =
      "classpath:db/migration/yak-modeling-history-v22-foundation";
  private static final String IMPACT_SNAPSHOT_LOCATION =
      "classpath:db/migration/yak-modeling-history-impact-snapshot";
  private static final String LOGICAL_SNAPSHOT_LOCATION =
      "classpath:db/migration/yak-modeling-history-logical-snapshot";
  private static final String IMPACT_FOUNDATION_LOCATION =
      "classpath:db/migration/yak-modeling-history-impact-foundation";
  private static final String LOGICAL_FOUNDATION_LOCATION =
      "classpath:db/migration/yak-modeling-history-logical-foundation";

  @Bean(name = "yakModelingFlyway", initMethod = "migrate")
  @DependsOn("yakSemanticFlyway")
  public Flyway modelingFlyway(@Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations(migrationLocations(dataSource))
        .table(HISTORY_TABLE)
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .baselineOnMigrate(true)
        .load();
  }

  private String[] migrationLocations(DataSource dataSource) {
    try (Connection connection = dataSource.getConnection()) {
      if (!historyTableExists(connection)) {
        return migrationLocations(null, null);
      }
      return migrationLocations(
          appliedMigrationScript(connection, "22"), appliedMigrationScript(connection, "24"));
    } catch (SQLException exception) {
      throw new IllegalStateException(
          "Unable to inspect " + HISTORY_TABLE + " before configuring modeling Flyway",
          exception);
    }
  }

  private String appliedMigrationScript(Connection connection, String version) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT script FROM " + HISTORY_TABLE + " WHERE version = ? AND success = 1")) {
      statement.setString(1, version);
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          return null;
        }
        String script = result.getString("script");
        if (script == null) {
          throw new IllegalStateException("Modeling Flyway V" + version + " has no script name");
        }
        return script;
      }
    }
  }

  private String[] migrationLocations(String v22Script, String v24Script) {
    List<String> locations = new ArrayList<>();
    locations.add(COMMON_MIGRATION_LOCATION);

    boolean snapshotAtV22 = "V22__model_version_meta_snapshot.sql".equals(v22Script);
    boolean foundationAtV22 = "V22__model_version_foundation.sql".equals(v22Script);
    if (snapshotAtV22) {
      locations.add(V22_META_HISTORY_LOCATION);
    } else if (foundationAtV22) {
      locations.add(V22_FOUNDATION_HISTORY_LOCATION);
    } else if (v22Script != null) {
      throw new IllegalStateException("Unsupported modeling Flyway V22 migration script: " + v22Script);
    }

    if (v24Script == null || "V24__model_impact_analysis.sql".equals(v24Script)) {
      locations.add(IMPACT_HISTORY_LOCATION);
      if (!snapshotAtV22) {
        locations.add(IMPACT_SNAPSHOT_LOCATION);
      }
      if (!foundationAtV22) {
        locations.add(IMPACT_FOUNDATION_LOCATION);
      }
    } else if ("V24__logical_modeling_foundation.sql".equals(v24Script)) {
      locations.add(LOGICAL_HISTORY_LOCATION);
      if (!snapshotAtV22) {
        locations.add(LOGICAL_SNAPSHOT_LOCATION);
      }
      if (!foundationAtV22) {
        locations.add(LOGICAL_FOUNDATION_LOCATION);
      }
    } else {
      throw new IllegalStateException("Unsupported modeling Flyway V24 migration script: " + v24Script);
    }
    return locations.toArray(String[]::new);
  }

  private boolean historyTableExists(Connection connection) throws SQLException {
    try (ResultSet tables =
        connection
            .getMetaData()
            .getTables(connection.getCatalog(), null, HISTORY_TABLE, new String[] {"TABLE"})) {
      while (tables.next()) {
        if (HISTORY_TABLE.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
          return true;
        }
      }
      return false;
    }
  }
}
