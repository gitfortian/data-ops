package io.yak.ops.business.agent.runtime;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import io.agentscope.extensions.postgresql.state.PostgresAgentStateStore;
import io.agentscope.core.state.AgentStateStore;
import io.yak.framework.common.jdbc.JdbcDatabase;
import io.yak.ops.business.agent.config.AgentProperties;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 官方 StateStore 按平台数据库装配；消息历史继续只由 SDK 存储承载。
 * PostgreSQL 的 database 配置表示业务数据库内的 schema，MySQL 仍表示数据库名。
 */
@ConditionalOnAgentEnabled
@Configuration(proxyBeanMethods = false)
public class AgentStateStoreWiring {

  /**
   * F-039 SDK persistence remains owned by runtime; conversation receives only
   * the typed, fail-closed task/artifact bridge.
   */
  @Bean
  @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
      prefix = "yak.agent.source-semantic", name = "enabled", havingValue = "true")
  public SourceSemanticStateBridge sourceSemanticStateBridge(AgentStateStore store) {
    return new SourceSemanticStateBridge(store);
  }

  @Bean
  public AgentStateStore agentAgentStateStore(
      @Qualifier("yakBusinessDataSource") DataSource dataSource, AgentProperties properties) {
    String database = properties.getStateStore().getDatabase();
    if (JdbcDatabase.isPostgresql(dataSource)) {
      return new PostgresAgentStateStore(dataSource, database,
          properties.getStateStore().getTable(), true);
    }
    return new MysqlAgentStateStore(
        dataSource,
        database == null || database.isBlank() ? null : database,
        properties.getStateStore().getTable(),
        true);
  }
}
