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
  void dedicatedNamespaceContainsForwardOnlyAccessMigrations() throws IOException {
    assertThat(sqlFiles(dedicatedMigrationRoot()))
        .containsExactly(
            "V1__baseline_data_service.sql",
            "V2__ip_access_policy.sql",
            "V3__consumer_access_model.sql");

    String baseline = Files.readString(
        dedicatedMigrationRoot().resolve("V1__baseline_data_service.sql"));
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

    String accessPolicy = Files.readString(
        dedicatedMigrationRoot().resolve("V2__ip_access_policy.sql"));
    assertThat(accessPolicy)
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_data_service_ip_access_policy")
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_data_service_ip_access_rule")
        .contains("ALLOWLIST")
        .contains("DENYLIST")
        .doesNotContain("ALTER TABLE yak_ops_data_service_api");

    String consumerAccess = Files.readString(
        dedicatedMigrationRoot().resolve("V3__consumer_access_model.sql"));
    assertThat(consumerAccess)
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_data_service_consumer")
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_data_service_consumer_api_grant")
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_data_service_consumer_ip_access_policy")
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_data_service_consumer_ip_access_rule")
        .contains("ADD COLUMN consumer_id")
        .contains("legacy-")
        .contains("MODIFY COLUMN api_id")
        .doesNotContain("DROP TABLE");
  }

  @Test
  void dataServiceDoesNotContributeToDatasourceMigrationNamespace() throws IOException {
    assertThat(sqlFiles(legacyDatasourceMigrationRoot())).isEmpty();
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
        "yak-ops-business", "yak-ops-business-data-service", "src", "main", "java", "io", "yak",
        "ops", "business", "dataservice", "config", "DataServiceFlywayConfiguration.java");
  }

  private Path dedicatedMigrationRoot() {
    Path local = Path.of("src/main/resources/db/migration/yak-data-service");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "yak-ops-business", "yak-ops-business-data-service", "src", "main", "resources", "db",
        "migration", "yak-data-service");
  }

  private Path legacyDatasourceMigrationRoot() {
    Path local = Path.of("src/main/resources/db/migration/yak-datasource");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "yak-ops-business", "yak-ops-business-data-service", "src", "main", "resources", "db",
        "migration", "yak-datasource");
  }
}
