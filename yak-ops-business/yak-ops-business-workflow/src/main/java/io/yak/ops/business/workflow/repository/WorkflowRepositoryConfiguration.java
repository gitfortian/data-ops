package io.yak.ops.business.workflow.repository;

import io.yak.framework.workflow.engine.spi.ExecutionRepository;
import io.yak.framework.workflow.engine.support.CachingExecutionRepository;
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
import org.springframework.context.annotation.Primary;

/** Workflow persistence shares the Yak Ops business database and MyBatis infrastructure. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@Import(BusinessDatabaseConfiguration.class)
@MapperScan(
    basePackages = "io.yak.ops.business.workflow.dao.mapper",
    sqlSessionFactoryRef = "yakBusinessSqlSessionFactory")
public class WorkflowRepositoryConfiguration {

  @Bean(initMethod = "migrate", name = "workflowFlyway")
  public Flyway workflowFlyway(
      @Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/yak-workflow")
        .table("yak_workflow_schema_history")
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .baselineOnMigrate(true)
        .load();
  }

  /**
   * Active executions use a write-through cache while the MyBatis repository adapter remains the
   * restart and history source of truth.
   */
  @Bean
  @Primary
  public ExecutionRepository workflowExecutionRepository(
      WorkflowExecutionRepositoryAdapter durableRepository) {
    return new CachingExecutionRepository(durableRepository);
  }
}
