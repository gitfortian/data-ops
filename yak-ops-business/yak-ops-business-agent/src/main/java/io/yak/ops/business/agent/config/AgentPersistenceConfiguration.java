package io.yak.ops.business.agent.config;

import io.yak.ops.business.datasource.config.BusinessDatabaseConfiguration;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@ConditionalOnAgentEnabled
@ConditionalOnDataSourceEnabled
@EnableConfigurationProperties(AgentProperties.class)
@Import(BusinessDatabaseConfiguration.class)
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
