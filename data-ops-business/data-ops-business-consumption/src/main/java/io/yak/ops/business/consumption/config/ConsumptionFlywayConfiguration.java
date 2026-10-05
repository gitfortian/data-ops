package io.yak.ops.business.consumption.config;

import io.yak.framework.common.jdbc.JdbcDatabase;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

/** Consumption owns subscription/usage relationship persistence independently from source domains. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDataSourceEnabled
public class ConsumptionFlywayConfiguration {

  @Bean(initMethod = "migrate")
  @DependsOn("opsDataSourceFlyway")
  public Flyway consumptionFlyway(
      @Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations(JdbcDatabase.migrationLocation(dataSource, "classpath:db/migration/yak-consumption"))
        .table("yak_consumption_schema_history")
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .baselineOnMigrate(true)
        .load();
  }
}
