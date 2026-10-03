package io.yak.ops.business.digitalscreen.config;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Digital Screen database migration configuration. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnDataSourceEnabled
@MapperScan(
    basePackages = "io.yak.ops.business.digitalscreen.dao.mapper",
    sqlSessionFactoryRef = "yakBusinessSqlSessionFactory")
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
