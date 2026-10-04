package io.yak.ops.business.resource.config;

import io.yak.framework.common.jdbc.JdbcDatabase;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 资源管理模块基础设施配置。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnResourceEnabled
@EnableConfigurationProperties(ResourceProperties.class)
@MapperScan(
    basePackages = "io.yak.ops.business.resource.dao.mapper",
    sqlSessionFactoryRef = "yakBusinessSqlSessionFactory")
public class ResourceConfiguration {

  @Bean(initMethod = "migrate")
  public Flyway opsResourceFlyway(
      @Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations(JdbcDatabase.migrationLocation(dataSource, "classpath:db/migration/yak-resource"))
        .table("yak_resource_schema_history")
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .baselineOnMigrate(true)
        .load();
  }
}
