package io.yak.ops.business.approval.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Approval persistence and Flyway configuration (self-owned migration chain). */
@Configuration(proxyBeanMethods = false)
@ConditionalOnApprovalPersistence
@MapperScan(
    basePackages = "io.yak.ops.business.approval.dao.mapper",
    sqlSessionFactoryRef = "yakBusinessSqlSessionFactory")
public class ApprovalPersistenceConfiguration {

  @Bean(name = "yakApprovalFlyway", initMethod = "migrate")
  public Flyway approvalFlyway(@Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/yak-approval")
        .table("flyway_schema_history_approval")
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .baselineOnMigrate(true)
        .load();
  }
}
