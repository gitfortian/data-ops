package io.yak.ops.boot.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DataServiceProjectCompatibilityBackfillContractTest {

  @Test
  void sourceManagedApisKeepAuthoringProjectBeforeLegacyFallback() throws Exception {
    String source = Files.readString(source());
    assertThat(source)
        .contains("dataDevelopmentBackfill.backfillLegacyRows()")
        .contains("DATA_DEVELOPMENT_DATA_SERVICE")
        .contains("JOIN yak_dev_node node")
        .contains("api.project_id <> node.project_id")
        .contains("SET api.project_id = node.project_id")
        .contains("UPDATE yak_ops_data_service_api SET project_id = ? WHERE project_id IS NULL")
        .contains("JOIN yak_ops_data_service_api api ON api.id = log.api_id")
        .contains("SET log.project_id = api.project_id")
        .contains("UPDATE yak_ops_data_service_call_log SET project_id = ? WHERE project_id IS NULL")
        .contains("assertNoUnscopedRows(\"yak_ops_data_service_api\")")
        .contains("assertNoUnscopedRows(\"yak_ops_data_service_call_log\")");
  }

  private Path source() {
    Path local = Path.of(
        "src/main/java/io/yak/ops/boot/project/DataServiceProjectCompatibilityBackfill.java");
    if (Files.isRegularFile(local)) return local;
    return Path.of(
        "yak-ops-boot/src/main/java/io/yak/ops/boot/project/DataServiceProjectCompatibilityBackfill.java");
  }
}
