package io.yak.ops.boot.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DatasetProjectCompatibilityBackfillContractTest {

  @Test
  void trustedDatasetSourcesAreResolvedBeforeDefaultProjectFallback() throws Exception {
    String source = Files.readString(source());
    assertThat(source)
        .contains("dataSourceBackfill.backfillLegacyRows()")
        .contains("dataDevelopmentBackfill.backfillLegacyRows()")
        .contains("claimReferencedDataDevelopmentTaskAssets()")
        .contains("legacy.source_ref REGEXP '^[0-9]+$'")
        .contains("scoped.project_id = n.project_id")
        .contains("SET a.project_id = n.project_id")
        .contains("assertNoUnscopedReferencedTaskAssets()")
        .contains("Dataset-referenced TaskAsset rows without Project ownership")
        .contains("failOnAmbiguousSourceOwnership()")
        .contains("JOIN yak_dev_node n ON n.id = d.development_node_id")
        .contains("JOIN yak_task_asset a ON a.id = v.source_task_asset_id")
        .contains("JOIN yak_ops_data_source s")
        .contains("SET d.project_id = n.project_id")
        .contains("SET d.project_id = a.project_id")
        .contains("SET d.project_id = s.project_id")
        .contains("UPDATE yak_dataset SET project_id = ? WHERE project_id IS NULL")
        .contains("SET q.project_id = d.project_id")
        .contains("assertNoUnscopedRows(\"yak_dataset\")")
        .contains("assertNoSourceOwnershipMismatch()")
        .contains("assertNoDiagnosticOwnershipMismatch()");
  }

  private Path source() {
    Path local = Path.of(
        "src/main/java/io/yak/ops/boot/project/DatasetProjectCompatibilityBackfill.java");
    if (Files.isRegularFile(local)) return local;
    return Path.of(
        "yak-ops-boot/src/main/java/io/yak/ops/boot/project/DatasetProjectCompatibilityBackfill.java");
  }
}
