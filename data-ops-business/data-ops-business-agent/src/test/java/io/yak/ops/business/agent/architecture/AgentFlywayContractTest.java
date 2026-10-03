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
        // 模块迁移已合并为单文件;历史版本以 -- Source: 段的形式保留在文件内。
        assertThat(sqlFiles(migrationRoot())).containsExactly("V1__agent_baseline.sql");
    }

    @Test
    void baselineShouldContainFinalSchemaWithoutAlterOrUpdate() throws IOException {
        String baseline = section(
            Files.readString(migrationRoot().resolve("V1__agent_baseline.sql")),
            "V1__baseline_agent.sql");

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

  /**
   * 合并后的单文件按 {@code -- Source: <原路径>} 分段;按文件名取回原迁移的正文,
   * 使原先针对单个文件的断言继续有效。
   */
  private static String section(String sql, String sourceFileName) {
    String[] lines = sql.split("\n");
    int start = -1;
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].startsWith("-- Source:") && lines[i].trim().endsWith(sourceFileName)) {
        start = i + 1;
        break;
      }
    }
    if (start < 0) throw new IllegalStateException("missing source section: " + sourceFileName);
    StringBuilder body = new StringBuilder();
    for (int i = start; i < lines.length; i++) {
      if (lines[i].startsWith("-- Source:")) break;
      body.append(lines[i]).append('\n');
    }
    return body.toString();
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
