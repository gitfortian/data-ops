package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.state.AgentState;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * F-039/#494 isolated CI MySQL store contract. The task/file namespace still requires a
 * separately approved persistent workspace backend; MySQL state alone is not the plan document.
 */
@EnabledIfEnvironmentVariable(named = "ARCHITECTURE_MYSQL_URL", matches = ".+")
class SourceSemanticPlanMysqlStateContractTest {

  @Test
  void planModeAndPermissionSessionAreScopedAcrossStoreReconstruction() throws Exception {
    var datasource = new DriverManagerDataSource(System.getenv("ARCHITECTURE_MYSQL_URL"),
        System.getenv("ARCHITECTURE_MYSQL_USERNAME"),
        System.getenv("ARCHITECTURE_MYSQL_PASSWORD"));
    String database;
    try (var connection = datasource.getConnection()) {
      database = connection.getCatalog();
    }
    String table = "agent_plan_" + UUID.randomUUID().toString().replace("-", "");
    var jdbc = new JdbcTemplate(datasource);
    var store = new MysqlAgentStateStore(datasource, database, table, true);
    String sessionA = "source-a-" + UUID.randomUUID();
    String sessionB = "source-b-" + UUID.randomUUID();
    try {
      var stateA = AgentState.builder().userId("owner").sessionId(sessionA).build();
      stateA.getPlanModeContext().setPlanActive(true);
      stateA.getPlanModeContext().setCurrentPlanFile("plans/PLAN.md");
      store.save("owner", sessionA, "agent_state", stateA);

      var stateB = AgentState.builder().userId("owner").sessionId(sessionB).build();
      stateB.getPlanModeContext().setPlanActive(false);
      store.save("owner", sessionB, "agent_state", stateB);

      var reopened = new MysqlAgentStateStore(datasource, database, table, false);
      var restoredA = reopened.get("owner", sessionA, "agent_state", AgentState.class).orElseThrow();
      var restoredB = reopened.get("owner", sessionB, "agent_state", AgentState.class).orElseThrow();

      assertTrue(restoredA.getPlanModeContext().isPlanActive());
      assertEquals("plans/PLAN.md", restoredA.getPlanModeContext().getCurrentPlanFile());
      assertFalse(restoredB.getPlanModeContext().isPlanActive());
      assertTrue(reopened.get("other-user", sessionA, "agent_state", AgentState.class).isEmpty());
      assertTrue(reopened.get("owner", "unknown-task", "agent_state", AgentState.class).isEmpty());

      restoredA.getPlanModeContext().setPlanActive(false);
      reopened.save("owner", sessionA, "agent_state", restoredA);
      var secondRestart = new MysqlAgentStateStore(datasource, database, table, false);
      assertFalse(secondRestart.get("owner", sessionA, "agent_state", AgentState.class)
          .orElseThrow().getPlanModeContext().isPlanActive());
      assertFalse(secondRestart.get("owner", sessionB, "agent_state", AgentState.class)
          .orElseThrow().getPlanModeContext().isPlanActive());
    } finally {
      jdbc.execute("DROP TABLE IF EXISTS `" + table + "`");
    }
  }
}
