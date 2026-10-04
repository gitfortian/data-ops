package io.yak.ops.business.alert.config;

import io.yak.framework.common.jdbc.JdbcDatabase;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 告警管理模块基础设施配置。 */
@Configuration(proxyBeanMethods = false)
@MapperScan(
    basePackages = "io.yak.ops.business.alert.dao.mapper",
    sqlSessionFactoryRef = "yakBusinessSqlSessionFactory")
public class AlertConfiguration {

  @Bean(initMethod = "migrate")
  public Flyway opsAlertFlyway(
      @Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations(JdbcDatabase.migrationLocation(dataSource, "classpath:db/migration/yak-alert"))
        .table("yak_alert_schema_history")
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .baselineOnMigrate(true)
        .load();
  }
}
