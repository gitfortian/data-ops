package io.yak.ops.business.mdm.config;

import io.yak.framework.common.jdbc.JdbcDatabase;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** MDM persistence and Flyway configuration (self-owned migration chain). */
@Configuration(proxyBeanMethods = false)
@ConditionalOnMdmPersistence
@MapperScan(
    basePackages = "io.yak.ops.business.mdm.dao.mapper",
    sqlSessionFactoryRef = "yakBusinessSqlSessionFactory")
public class MdmPersistenceConfiguration {

  @Bean(name = "yakMdmFlyway", initMethod = "migrate")
  public Flyway mdmFlyway(@Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations(JdbcDatabase.migrationLocation(dataSource, "classpath:db/migration/yak-mdm"))
        .table("flyway_schema_history_mdm")
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .baselineOnMigrate(true)
        .load();
  }
}
