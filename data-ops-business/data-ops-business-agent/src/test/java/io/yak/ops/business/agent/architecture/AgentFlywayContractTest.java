package io.yak.ops.business.agent.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Locks Agent's versioned upgrade history and cumulative baseline migration. */
class AgentFlywayContractTest {

    @Test
    void agentSchemaShouldContainVersionedHistoryAndCumulativeBaseline() throws IOException {
        // V1/V2 remain the upgrade history; B2 is the cumulative baseline for new schemas.
        List<String> migrations = sqlFiles(migrationRoot());
        assertThat(migrations.stream().filter(name -> name.startsWith("V")).toList())
                .containsExactly("V1__baseline_agent.sql", "V2__agent_add_project_id.sql");
        assertThat(migrations).contains("B2__agent_baseline.sql");
    }

    @Test
    void baselineShouldContainFinalSchemaWithoutAlterOrUpdate() throws IOException {
        String baseline = Files.readString(migrationRoot().resolve("V1__baseline_agent.sql"));

        assertThat(baseline)
                .contains("CREATE TABLE IF NOT EXISTS `yak_agent_session`")
                .contains("CREATE TABLE IF NOT EXISTS `yak_agent_report`")
                .contains("CREATE TABLE IF NOT EXISTS `yak_agent_query_log`")
                .contains("CREATE TABLE IF NOT EXISTS `yak_agent_step`")
                .contains("CREATE TABLE IF NOT EXISTS `yak_agent_message`")
                .contains("CREATE TABLE IF NOT EXISTS `yak_agent_turn`")
                .contains("CREATE TABLE IF NOT EXISTS `yak_agent_turn_event`")
                .contains("CREATE TABLE IF NOT EXISTS `yak_agent_memory`")
                .contains("CREATE TABLE IF NOT EXISTS `yak_config`")
                .contains("CREATE TABLE IF NOT EXISTS `yak_agent_skill`")
                .contains("elapsed_millis")
                .contains("parent_step_id")
                .contains("started_at")
                .contains("attempt")
                .doesNotContain("ALTER TABLE")
                .doesNotContain("UPDATE yak_agent")
                .doesNotContain("CHANGE COLUMN");
    }

    private List<String> sqlFiles(Path root) throws IOException {
        try (var paths = Files.list(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .sorted()
                    .toList();
        }
    }

    private Path migrationRoot() {
        Path local = Path.of("src/main/resources/db/migration/yak-agent");
        if (Files.isDirectory(local)) {
            return local;
        }
        return Path.of(
                "data-ops-business",
                "data-ops-business-agent",
                "src",
                "main",
                "resources",
                "db",
                "migration",
                "yak-agent");
    }
}
