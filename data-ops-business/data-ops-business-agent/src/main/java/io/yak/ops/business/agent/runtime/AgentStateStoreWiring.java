package io.yak.ops.business.agent.runtime;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import io.yak.ops.business.agent.config.AgentProperties;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 官方 MySQL StateStore 装配：复用平台共享数据源，auto-DDL 指向业务库。
 * 本类是 extensions-mysql 的唯一装配点（SDK 白名单：io.agentscope.* 仅 runtime 可 import）。
 */
@ConditionalOnAgentEnabled
@Configuration(proxyBeanMethods = false)
public class AgentStateStoreWiring {

  @Bean
  public MysqlAgentStateStore agentAgentStateStore(
      @Qualifier("yakBusinessDataSource") DataSource dataSource, AgentProperties properties) {
    String database = properties.getStateStore().getDatabase();
    return new MysqlAgentStateStore(
        dataSource,
        database == null || database.isBlank() ? null : database,
        properties.getStateStore().getTable(),
        true);
  }
}
