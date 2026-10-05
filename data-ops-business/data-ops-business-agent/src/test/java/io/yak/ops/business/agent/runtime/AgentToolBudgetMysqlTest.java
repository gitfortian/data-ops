package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.domain.AgentExecutionContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Isolated CI database verifies SDK persistence and restoration using a new store instance. */
@EnabledIfEnvironmentVariable(named = "ARCHITECTURE_MYSQL_URL", matches = ".+")
class AgentToolBudgetMysqlTest {
  @Test void restartedStoreRestoresConsumedFrozenBudgetAndOwnerIsolation() throws Exception {
    var source = new DriverManagerDataSource(System.getenv("ARCHITECTURE_MYSQL_URL"),
        System.getenv("ARCHITECTURE_MYSQL_USERNAME"), System.getenv("ARCHITECTURE_MYSQL_PASSWORD"));
    String database;
    try (var connection = source.getConnection()) { database = connection.getCatalog(); }
    var store = new MysqlAgentStateStore(source, database, "agent_budget_ci", true);
    String session = "budget-" + UUID.randomUUID();
    var initial = RuntimeContext.builder().userId("budget-owner").sessionId(session).build();
    initial.put(AgentExecutionContext.class, new AgentExecutionContext(null));
    var config = new AgentProperties.Execution(); config.setMaxToolCalls(1);
    try {
      TurnToolBudgetState.attach(store, initial, "turn-budget", false, config);
      initial.get(AgentExecutionContext.class).reserveTool("request_clarification");
      var restarted = new MysqlAgentStateStore(source, database, "agent_budget_ci", false);
      assertTrue(restarted.get("other-owner", session, TurnToolBudgetState.KEY, TurnToolBudgetState.class).isEmpty());
      var restored = RuntimeContext.builder().userId("budget-owner").sessionId(session).build();
      restored.put(AgentExecutionContext.class, new AgentExecutionContext(null));
      config.setMaxToolCalls(100);
      TurnToolBudgetState.attach(restarted, restored, "turn-budget", true, config);
      assertEquals(1, restored.get(AgentExecutionContext.class).toolBudget().usedCalls());
      assertThrows(IllegalStateException.class, () -> restored.get(AgentExecutionContext.class).reserveTool("current_date_info"));
    } finally {
      store.delete("budget-owner", session);
    }
  }
}
