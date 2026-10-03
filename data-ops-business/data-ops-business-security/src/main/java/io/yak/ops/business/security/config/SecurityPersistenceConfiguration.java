package io.yak.ops.business.security.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Data-security persistence and Flyway configuration (self-owned migration chain). */
@Configuration(proxyBeanMethods = false)
@ConditionalOnSecurityPersistence
@MapperScan(
    basePackages = "io.yak.ops.business.security.dao.mapper",
    sqlSessionFactoryRef = "yakBusinessSqlSessionFactory")
public class SecurityPersistenceConfiguration {

  @Bean(name = "yakDataSecurityFlyway", initMethod = "migrate")
  public Flyway dataSecurityFlyway(@Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/yak-security")
        .table("flyway_schema_history_security")
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .baselineOnMigrate(true)
        .load();
  }
}
