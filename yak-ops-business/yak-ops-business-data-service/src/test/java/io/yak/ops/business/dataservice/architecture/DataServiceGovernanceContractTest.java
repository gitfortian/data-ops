package io.yak.ops.business.dataservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataServiceGovernanceContractTest {

  private static final List<String> MANAGEMENT_CONTROLLERS = List.of(
      "DataServiceController.java",
      "DataServiceAccessController.java",
      "DataServiceDocumentationController.java",
      "DataServiceOverviewController.java",
      "DataServicePublicationStateController.java",
      "DataServiceRuntimeController.java");

  @Test
  void managementControllersRequireProjectSpace() throws IOException {
    for (String controller : MANAGEMENT_CONTROLLERS) {
      assertThat(Files.readString(controllerRoot().resolve(controller)))
          .as(controller)
          .contains("ProjectMigrationMode.PROJECT_REQUIRED");
    }
  }

  @Test
  void publicInvocationControllerCannotInheritConsoleProjectOrRbac() throws IOException {
    String source = Files.readString(controllerRoot().resolve("DataServiceInvocationController.java"));
    assertThat(source)
        .contains("/runtime/{*servicePath}")
        .doesNotContain("@ProjectScope")
        .doesNotContain("@RequiresPermission");
  }

  @Test
  void managementRbacHasExplicitYakSecurityCompileDependency() throws IOException {
    String pom = Files.readString(modulePom());
    assertThat(pom)
        .contains("<groupId>io.github.weifuwan</groupId>")
        .contains("<artifactId>yak-security-spring-boot-starter</artifactId>");
  }

  @Test
  void permissionVocabularyIsExplicitAndStable() throws IOException {
    String source = Files.readString(permissionSource());
    assertThat(source)
        .contains("data-service:read")
        .contains("data-service:publish")
        .contains("data-service:manage")
        .contains("data-service:delete")
        .contains("data-service:access")
        .contains("data-service:runtime")
        .contains("data-service:observe");
  }

  @Test
  void repositoryKeepsOnlyNarrowGlobalReadCorridors() throws IOException {
    String repository = Files.readString(repositorySource());
    String reader = Files.readString(readerSource());
    assertThat(repository)
        .contains("findByRuntimePath")
        .contains("public long count()")
        .contains("currentProject.requireProjectId()")
        .contains("DataServiceApiPO::getProjectId");
    assertThat(reader)
        .contains("repository.findByRuntimePath(path)")
        .contains("repository.count()");
  }

  private Path modulePom() {
    Path local = Path.of("pom.xml");
    if (Files.isRegularFile(local)) return local;
    return Path.of("yak-ops-business", "yak-ops-business-data-service", "pom.xml");
  }

  private Path controllerRoot() {
    Path local = Path.of("src/main/java/io/yak/ops/business/dataservice/controller/v1");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "yak-ops-business/yak-ops-business-data-service/src/main/java/io/yak/ops/business/dataservice/controller/v1");
  }

  private Path repositorySource() {
    Path local = Path.of(
        "src/main/java/io/yak/ops/business/dataservice/repository/DataServiceRepositoryAdapter.java");
    if (Files.isRegularFile(local)) return local;
    return Path.of(
        "yak-ops-business/yak-ops-business-data-service/src/main/java/io/yak/ops/business/dataservice/repository/DataServiceRepositoryAdapter.java");
  }

  private Path readerSource() {
    Path local = Path.of(
        "src/main/java/io/yak/ops/business/dataservice/query/DataServiceReader.java");
    if (Files.isRegularFile(local)) return local;
    return Path.of(
        "yak-ops-business/yak-ops-business-data-service/src/main/java/io/yak/ops/business/dataservice/query/DataServiceReader.java");
  }

  private Path permissionSource() {
    Path moduleRun = Path.of(
        "..", "..", "yak-ops-common", "src", "main", "java", "io", "yak", "ops", "common",
        "constant", "dataservice", "DataServicePermissionCode.java").normalize();
    if (Files.isRegularFile(moduleRun)) return moduleRun;
    return Path.of(
        "yak-ops-common", "src", "main", "java", "io", "yak", "ops", "common", "constant",
        "dataservice", "DataServicePermissionCode.java");
  }
}
