package io.yak.ops.business.lifecycle.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Lifecycle persistence and Flyway configuration (self-owned migration chain). */
@Configuration(proxyBeanMethods = false)
@ConditionalOnLifecyclePersistence
@MapperScan(
    basePackages = "io.yak.ops.business.lifecycle.dao.mapper",
    sqlSessionFactoryRef = "yakBusinessSqlSessionFactory")
public class LifecyclePersistenceConfiguration {

  @Bean(name = "yakLifecycleFlyway", initMethod = "migrate")
  public Flyway lifecycleFlyway(@Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/yak-lifecycle")
        .table("flyway_schema_history_lifecycle")
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .baselineOnMigrate(true)
        .load();
  }
}
