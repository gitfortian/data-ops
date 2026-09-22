package io.yak.ops.boot.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DataDevelopmentProjectCompatibilityBackfillContractTest {

  @Test
  void backfillInfersOwnershipBeforeUsingCompatibilityProject() throws Exception {
    String source = Files.readString(source());
    assertThat(source)
        .contains("COUNT(DISTINCT project_id) > 1")
        .contains("COUNT(DISTINCT project_id) = 1")
        .contains("UPDATE yak_dev_directory SET project_id = ? WHERE project_id IS NULL")
        .contains("UPDATE yak_dev_node SET project_id = ? WHERE project_id IS NULL")
        .contains("UPDATE yak_dev_task_execution e JOIN yak_dev_node n")
        .contains("UPDATE yak_dev_lineage_outbox o JOIN yak_dev_node n")
        .contains("assertNoUnscopedRows(\"yak_dev_directory\")")
        .contains("assertNoUnscopedRows(\"yak_dev_node\")")
        .contains("assertNoUnscopedRows(\"yak_dev_task_execution\")")
        .contains("assertNoUnscopedRows(\"yak_dev_lineage_outbox\")");
  }

  private Path source() {
    Path local = Path.of(
        "src/main/java/io/yak/ops/boot/project/DataDevelopmentProjectCompatibilityBackfill.java");
    if (Files.isRegularFile(local)) return local;
    return Path.of(
        "yak-ops-boot/src/main/java/io/yak/ops/boot/project/DataDevelopmentProjectCompatibilityBackfill.java");
  }
}
