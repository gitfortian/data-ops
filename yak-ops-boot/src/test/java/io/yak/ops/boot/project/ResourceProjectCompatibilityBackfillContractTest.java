package io.yak.ops.boot.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ResourceProjectCompatibilityBackfillContractTest {

  @Test
  void legacyResourcesInheritParentProjectBeforeDefaultFallback() throws Exception {
    String source = Files.readString(source());
    assertThat(source)
        .contains("JOIN yak_ops_resource parent ON child.parent_id = parent.id")
        .contains("SET child.project_id = parent.project_id")
        .contains("WHERE child.project_id IS NULL AND parent.project_id IS NOT NULL")
        .contains("UPDATE yak_ops_resource SET project_id = ? WHERE project_id IS NULL")
        .contains("SELECT COUNT(1) FROM yak_ops_resource WHERE project_id IS NULL")
        .contains("child.project_id <> parent.project_id")
        .contains("ensureRequiredDefaultProject()");
  }

  private Path source() {
    Path local = Path.of(
        "src/main/java/io/yak/ops/boot/project/ResourceProjectCompatibilityBackfill.java");
    if (Files.isRegularFile(local)) return local;
    return Path.of(
        "yak-ops-boot/src/main/java/io/yak/ops/boot/project/ResourceProjectCompatibilityBackfill.java");
  }
}
