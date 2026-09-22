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
  void migrationNeverGuessesHistoricalOwnershipAndThenContracts() throws IOException {
    String expand = read(
        "src/main/resources/db/migration/yak-digital-screen/V3__expand_project_scope.sql");
    String contract = read(
        "src/main/resources/db/migration/yak-digital-screen/V4__contract_project_scope.sql");

    assertThat(expand)
        .contains("ADD COLUMN project_id BIGINT NULL")
        .doesNotContainIgnoringCase("UPDATE")
        .doesNotContain("project_id = 1")
        .doesNotContain("DEFAULT 0");
    assertThat(contract)
        .contains("project_id BIGINT NOT NULL")
        .doesNotContainIgnoringCase("UPDATE");
  }

  private String read(String relative) throws IOException {
    return Files.readString(moduleRoot().resolve(relative));
  }

  private Path moduleRoot() {
    Path local = Path.of("src/main/java/io/yak/ops/business/digitalscreen");
    if (Files.isDirectory(local)) return Path.of(".");
    return Path.of("yak-ops-business", "yak-ops-business-digital-screen");
  }
}
