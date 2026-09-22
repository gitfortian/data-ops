package io.yak.ops.business.development.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.common.constant.development.DataDevelopmentPermissionCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataDevelopmentGovernanceContractTest {

  private static final List<String> CONTROLLERS = List.of(
      "DevelopmentDirectoryController.java",
      "DevelopmentNodeController.java",
      "DevelopmentTaskController.java",
      "DevelopmentTaskExecutionController.java",
      "DevelopmentReleaseController.java",
      "DevelopmentEditorSettingsController.java",
      "DevelopmentDatasetNodeController.java",
      "DevelopmentDataServiceNodeController.java",
      "DevelopmentDataServicePublicationController.java");

  @Test
  void everyDataDevelopmentControllerRequiresProjectAndReadPermission() throws IOException {
    for (String controller : CONTROLLERS) {
      String source = Files.readString(controllerRoot().resolve(controller));
      assertThat(source)
          .as(controller)
          .contains("@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)")
          .contains("@RequiresPermission(DataDevelopmentPermissionCode.READ)");
    }
  }

  @Test
  void mutationsUseDedicatedActionPermissions() throws IOException {
    assertContains("DevelopmentDirectoryController.java",
        "@RequiresPermission(DataDevelopmentPermissionCode.EDIT)",
        "@RequiresPermission(DataDevelopmentPermissionCode.DELETE)");
    assertContains("DevelopmentNodeController.java",
        "@RequiresPermission(DataDevelopmentPermissionCode.EDIT)",
        "@RequiresPermission(DataDevelopmentPermissionCode.DELETE)");
    assertContains("DevelopmentTaskController.java",
        "@RequiresPermission(DataDevelopmentPermissionCode.EDIT)",
        "@RequiresPermission(DataDevelopmentPermissionCode.EXECUTE)",
        "@RequiresPermission(DataDevelopmentPermissionCode.PUBLISH)");
    assertContains("DevelopmentTaskExecutionController.java",
        "@RequiresPermission(DataDevelopmentPermissionCode.EXECUTE)");
    assertContains("DevelopmentReleaseController.java",
        "@RequiresPermission(DataDevelopmentPermissionCode.RELEASE)");
    assertContains("DevelopmentEditorSettingsController.java",
        "@RequiresPermission(DataDevelopmentPermissionCode.EDIT)");
    assertContains("DevelopmentDatasetNodeController.java",
        "@RequiresPermission(DataDevelopmentPermissionCode.EXECUTE)",
        "@RequiresPermission(DataDevelopmentPermissionCode.EDIT)");
    assertContains("DevelopmentDataServiceNodeController.java",
        "@RequiresPermission(DataDevelopmentPermissionCode.EXECUTE)",
        "@RequiresPermission(DataDevelopmentPermissionCode.EDIT)",
        "@RequiresPermission(DataDevelopmentPermissionCode.PUBLISH)");
    assertContains("DevelopmentDataServicePublicationController.java",
        "@RequiresPermission(DataDevelopmentPermissionCode.RELEASE)");
  }

  @Test
  void permissionContractKeepsHighRiskActionsSeparate() {
    assertThat(List.of(
        DataDevelopmentPermissionCode.READ,
        DataDevelopmentPermissionCode.EDIT,
        DataDevelopmentPermissionCode.DELETE,
        DataDevelopmentPermissionCode.EXECUTE,
        DataDevelopmentPermissionCode.PUBLISH,
        DataDevelopmentPermissionCode.RELEASE))
        .containsExactly(
            "data-development:read",
            "data-development:edit",
            "data-development:delete",
            "data-development:execute",
            "data-development:publish",
            "data-development:release");
  }

  private void assertContains(String controller, String... fragments) throws IOException {
    assertThat(Files.readString(controllerRoot().resolve(controller)))
        .as(controller)
        .contains(fragments);
  }

  private Path controllerRoot() {
    return moduleRoot().resolve(
        "src/main/java/io/yak/ops/business/development/controller/v1");
  }

  private Path moduleRoot() {
    Path local = Path.of(".").toAbsolutePath().normalize();
    if (Files.isRegularFile(local.resolve("pom.xml"))
        && local.getFileName() != null
        && "yak-ops-business-data-development".equals(local.getFileName().toString())) {
      return local;
    }
    return Path.of("yak-ops-business", "yak-ops-business-data-development");
  }
}
