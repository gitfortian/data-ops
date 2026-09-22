package io.yak.ops.business.dataservice.config;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

/** Owns the Data Service migration namespace independently from Datasource schema history. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDataSourceEnabled
public class DataServiceFlywayConfiguration {

  @Bean(initMethod = "migrate")
  @DependsOn("opsDataSourceFlyway")
  public Flyway dataServiceFlyway(
      @Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/yak-data-service")
        .table("yak_data_service_schema_history")
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .baselineOnMigrate(true)
        .load();
  }
}
