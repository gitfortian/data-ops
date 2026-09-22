package io.yak.ops.business.taskcatalog.config;

import io.yak.ops.business.datasource.config.BusinessDatabaseConfiguration;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.config.DataSourceProperties;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Task Catalog reuses the Yak business database while keeping an isolated Flyway history. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDataSourceEnabled
@EnableConfigurationProperties(DataSourceProperties.class)
@Import(BusinessDatabaseConfiguration.class)
public class TaskCatalogPersistenceConfiguration {

  @Bean(name = "yakTaskCatalogFlyway", initMethod = "migrate")
  public Flyway taskCatalogFlyway(
      @Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/yak-task-catalog")
        .table("flyway_schema_history_task_catalog")
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .baselineOnMigrate(true)
        .load();
  }
}
