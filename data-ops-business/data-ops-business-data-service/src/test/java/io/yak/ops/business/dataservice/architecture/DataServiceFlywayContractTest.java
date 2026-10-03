package io.yak.ops.business.dataservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataServiceFlywayContractTest {

  @Test
  void dataServiceOwnsDedicatedFlywayNamespace() throws IOException {
    String configuration = Files.readString(configurationSource());

    assertThat(configuration)
        .contains("@DependsOn(\"opsDataSourceFlyway\")")
        .contains("classpath:db/migration/yak-data-service")
        .contains("yak_data_service_schema_history")
        .contains("MigrationVersion.fromVersion(\"0\")");
  }

  @Test
  void dedicatedNamespaceContainsForwardOnlyMigrations() throws IOException {
    assertThat(sqlFiles(dedicatedMigrationRoot()))
        .containsExactly("V1__data_service_baseline.sql");

    String baseline = section(
        Files.readString(dedicatedMigrationRoot().resolve("V1__data_service_baseline.sql")),
        "V1__baseline_data_service.sql");
    assertThat(baseline)
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_data_service_api")
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_data_service_api_key")
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_data_service_documentation")
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_data_service_call_log")
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_data_service_rate_window")
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_data_service_call_log_hourly")
        .contains("project_id")
        .contains("runtime_generation")
        .doesNotContain("ALTER TABLE");

    String accessPolicy = section(
        Files.readString(dedicatedMigrationRoot().resolve("V1__data_service_baseline.sql")),
        "V2__ip_access_policy.sql");
    assertThat(accessPolicy)
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_data_service_ip_access_policy")
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_data_service_ip_access_rule")
        .contains("ALLOWLIST")
        .contains("DENYLIST")
        .doesNotContain("ALTER TABLE yak_ops_data_service_api");

    String consumerAccess = section(
        Files.readString(dedicatedMigrationRoot().resolve("V1__data_service_baseline.sql")),
        "V3__consumer_access_model.sql");
    assertThat(consumerAccess)
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_data_service_consumer")
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_data_service_consumer_api_grant")
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_data_service_consumer_ip_access_policy")
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_data_service_consumer_ip_access_rule")
        .contains("ADD COLUMN consumer_id")
        .contains("legacy-")
        .contains("MODIFY COLUMN api_id")
        .doesNotContain("DROP TABLE");

    String usageEvidenceSource = section(
        Files.readString(dedicatedMigrationRoot().resolve("V1__data_service_baseline.sql")),
        "V4__usage_evidence_source.sql");
    assertThat(usageEvidenceSource)
        .contains("ADD COLUMN consumer_id")
        .contains("ADD COLUMN source_revision_id")
        .contains("ADD COLUMN source_revision_no")
        .contains("idx_data_service_call_log_consumer_time")
        .contains("idx_data_service_call_log_api_revision_time")
        .doesNotContain("DROP TABLE");
  }

  @Test
  void dataServiceDoesNotContributeToDatasourceMigrationNamespace() throws IOException {
    assertThat(sqlFiles(legacyDatasourceMigrationRoot())).isEmpty();
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
    if (!Files.isDirectory(root)) return List.of();
    try (var paths = Files.list(root)) {
      return paths
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .filter(name -> name.endsWith(".sql"))
          .sorted()
          .toList();
    }
  }

  private Path configurationSource() {
    Path local = Path.of(
        "src/main/java/io/yak/ops/business/dataservice/config/DataServiceFlywayConfiguration.java");
    if (Files.isRegularFile(local)) return local;
    return Path.of(
        "data-ops-business", "data-ops-business-data-service", "src", "main", "java", "io", "yak",
        "ops", "business", "dataservice", "config", "DataServiceFlywayConfiguration.java");
  }

  private Path dedicatedMigrationRoot() {
    Path local = Path.of("src/main/resources/db/migration/yak-data-service");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "data-ops-business", "data-ops-business-data-service", "src", "main", "resources", "db",
        "migration", "yak-data-service");
  }

  private Path legacyDatasourceMigrationRoot() {
    Path local = Path.of("src/main/resources/db/migration/yak-datasource");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "data-ops-business", "data-ops-business-data-service", "src", "main", "resources", "db",
        "migration", "yak-datasource");
  }
}
