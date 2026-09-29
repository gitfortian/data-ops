package io.yak.ops.business.development.config;

import io.yak.ops.business.datasource.config.BusinessDatabaseConfiguration;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Data-development tree metadata shares the Yak business database and owns its migrations. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@Import(BusinessDatabaseConfiguration.class)
@MapperScan(
    basePackages = "io.yak.ops.business.development.dao.mapper",
    sqlSessionFactoryRef = "yakBusinessSqlSessionFactory")
public class DataDevelopmentPersistenceConfiguration {

  @Bean(initMethod = "migrate", name = "dataDevelopmentFlyway")
  public Flyway dataDevelopmentFlyway(
      @Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/yak-data-development")
        .table("yak_data_development_schema_history")
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .baselineOnMigrate(true)
        .load();
  }
}
