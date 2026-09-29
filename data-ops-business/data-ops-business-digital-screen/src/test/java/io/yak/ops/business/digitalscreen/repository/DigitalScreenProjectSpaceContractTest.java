package io.yak.ops.business.digitalscreen.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DigitalScreenProjectSpaceContractTest {

  @Test
  void rootCrudIsFailClosedOnTrustedCurrentProject() throws IOException {
    String adapter = read(
        "src/main/java/io/yak/ops/business/digitalscreen/repository/"
            + "DigitalScreenRepositoryAdapter.java");
    String po = read(
        "src/main/java/io/yak/ops/business/digitalscreen/dao/model/DigitalScreenPO.java");

    assertThat(po).contains("private Long projectId;");
    assertThat(adapter)
        .contains("CurrentProject")
        .contains("currentProject.requireProjectId()")
        .contains("DigitalScreenPO::getProjectId")
        .doesNotContain("mapper.deleteById(id)")
        .doesNotContain("mapper.selectById(id)");
  }

  @Test
  void inheritedPublishedVersionsProveOwningScreenBeforeAccess() throws IOException {
    String adapter = read(
        "src/main/java/io/yak/ops/business/digitalscreen/repository/"
            + "DigitalScreenVersionRepositoryAdapter.java");

    assertThat(adapter)
        .contains("DigitalScreenRepository screens")
        .contains("screens.findById(row.getScreenId()).isEmpty()")
        .contains("requireOwnedScreen(screenId)");
  }

  @Test
  void baselineRequiresProjectOwnershipOnlyOnTheScreenRoot() throws IOException {
    String baseline = read(
        "src/main/resources/db/migration/yak-digital-screen/V1__baseline_digital_screen.sql");

    assertThat(tableDefinition(baseline, "yak_digital_screen"))
        .contains("project_id BIGINT NOT NULL")
        .doesNotContain("project_id BIGINT NOT NULL DEFAULT");
    assertThat(tableDefinition(baseline, "yak_digital_screen_version"))
        .doesNotContain("project_id");
  }

  private String tableDefinition(String migration, String table) {
    String start = "CREATE TABLE IF NOT EXISTS " + table + " (";
    int definitionStart = migration.indexOf(start);
    if (definitionStart < 0) throw new AssertionError("Missing baseline table: " + table);
    int definitionEnd = migration.indexOf(") ENGINE=", definitionStart);
    if (definitionEnd < 0) throw new AssertionError("Unterminated baseline table: " + table);
    return migration.substring(definitionStart, definitionEnd);
  }

  private String read(String relative) throws IOException {
    return Files.readString(moduleRoot().resolve(relative));
  }

  private Path moduleRoot() {
    Path local = Path.of("src/main/java/io/yak/ops/business/digitalscreen");
    if (Files.isDirectory(local)) return Path.of(".");
    return Path.of("data-ops-business", "data-ops-business-digital-screen");
  }
}
