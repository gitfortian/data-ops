package io.yak.ops.business.lineage.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Lineage persistence and Flyway configuration. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnLineagePersistence
@MapperScan(
    basePackages = "io.yak.ops.business.lineage.dao.mapper",
    sqlSessionFactoryRef = "yakBusinessSqlSessionFactory")
public class LineagePersistenceConfiguration {

  @Bean(name = "yakLineageFlyway", initMethod = "migrate")
  public Flyway lineageFlyway(@Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/yak-lineage")
        .table("flyway_schema_history_lineage")
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .baselineOnMigrate(true)
        .load();
  }
}
