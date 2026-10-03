package io.yak.ops.business.agent.config;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration(proxyBeanMethods = false)
@ConditionalOnAgentEnabled
@ConditionalOnDataSourceEnabled
@EnableConfigurationProperties(AgentProperties.class)
@MapperScan(
    basePackages = "io.yak.ops.business.agent.dao.mapper",
    sqlSessionFactoryRef = "yakBusinessSqlSessionFactory")
public class AgentPersistenceConfiguration {

  @Bean(name = "yakAgentFlyway", initMethod = "migrate")
  @DependsOn("yakDatasetFlyway")
  public Flyway agentFlyway(@Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/yak-agent")
        .table("flyway_schema_history_agent")
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .baselineOnMigrate(true)
        .load();
  }
}
