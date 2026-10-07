package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Verifies the SDK's additive upgrade against a populated pre-2.0.3 table in isolated CI. */
@EnabledIfEnvironmentVariable(named = "ARCHITECTURE_MYSQL_URL", matches = ".+")
class AgentStateStoreUpgradeMysqlTest {
  @Test void legacyBudgetSurvivesVersionColumnUpgradeAndRestart() throws Exception {
    var source = new DriverManagerDataSource(System.getenv("ARCHITECTURE_MYSQL_URL"),
        System.getenv("ARCHITECTURE_MYSQL_USERNAME"), System.getenv("ARCHITECTURE_MYSQL_PASSWORD"));
    String database;
    try (var connection = source.getConnection()) { database = connection.getCatalog(); }
    // The identifier is generated here; cleanup only drops this test's freshly created table.
    String table = "agent_upgrade_" + UUID.randomUUID().toString().replace("-", "");
    var jdbc = new JdbcTemplate(source);
    jdbc.execute("CREATE TABLE `" + table + "` (session_id VARCHAR(255) NOT NULL,"
        + " state_key VARCHAR(255) NOT NULL, item_index INT NOT NULL DEFAULT 0,"
        + " state_data LONGTEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
        + " updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
        + " PRIMARY KEY (session_id, state_key, item_index))"
        + " DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    String legacyJson = """
        {"turnId":"legacy-turn","target":null,
         "budget":{"maxCalls":5,"maxFailuresPerTool":2,"usedCalls":3,"failures":{}}}
        """;
    try {
      // SDK 2.0.2 persisted the authenticated user/session pair in this slot format.
      jdbc.update("INSERT INTO `" + table + "` (session_id,state_key,state_data) VALUES (?,?,?)",
          "legacy-owner:legacy-session", TurnToolBudgetState.KEY, legacyJson);
      var upgraded = new MysqlAgentStateStore(source, database, table, false);
      assertEquals(legacyJson, jdbc.queryForObject("SELECT state_data FROM `" + table + "`", String.class));
      assertEquals(0L, jdbc.queryForObject("SELECT version FROM `" + table + "`", Long.class));
      var previous = upgraded.get("legacy-owner", "legacy-session", TurnToolBudgetState.KEY,
          TurnToolBudgetState.class).orElseThrow();
      assertEquals("legacy-turn", previous.turnId());
      assertEquals(3, previous.budget().usedCalls());
      assertEquals(5, previous.budget().maxCalls());
      assertTrue(upgraded.get("another-owner", "legacy-session", TurnToolBudgetState.KEY,
          TurnToolBudgetState.class).isEmpty());
      upgraded.save("legacy-owner", "legacy-session", TurnToolBudgetState.KEY, previous);
      var restarted = new MysqlAgentStateStore(source, database, table, false);
      assertEquals(previous, restarted.get("legacy-owner", "legacy-session", TurnToolBudgetState.KEY,
          TurnToolBudgetState.class).orElseThrow());
      assertEquals(1L, jdbc.queryForObject("SELECT version FROM `" + table + "`", Long.class));
    } finally {
      jdbc.execute("DROP TABLE `" + table + "`");
    }
  }
}
