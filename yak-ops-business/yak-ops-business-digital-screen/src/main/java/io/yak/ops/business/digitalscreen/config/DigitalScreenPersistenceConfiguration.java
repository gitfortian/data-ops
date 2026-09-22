package io.yak.ops.business.digitalscreen.config;

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

/** Digital Screen database migration configuration. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDataSourceEnabled
@EnableConfigurationProperties(DataSourceProperties.class)
@Import(BusinessDatabaseConfiguration.class)
public class DigitalScreenPersistenceConfiguration {

  @Bean(name = "yakDigitalScreenFlyway", initMethod = "migrate")
  public Flyway digitalScreenFlyway(
      @Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/yak-digital-screen")
        .table("flyway_schema_history_digital_screen")
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .baselineOnMigrate(true)
        .load();
  }
}
