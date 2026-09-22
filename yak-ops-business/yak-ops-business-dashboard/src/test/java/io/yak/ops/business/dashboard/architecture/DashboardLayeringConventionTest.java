package io.yak.ops.business.dashboard.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.dashboard.DashboardService;
import io.yak.ops.business.dashboard.controller.v1.DashboardController;
import io.yak.ops.business.dashboard.definition.DashboardManager;
import io.yak.ops.business.dashboard.gateway.analysis.DashboardAnalysisGateway;
import io.yak.ops.business.dashboard.gateway.lineage.DashboardLineageGraphGateway;
import io.yak.ops.business.dashboard.lineage.DashboardLineageSynchronizer;
import io.yak.ops.business.dashboard.publication.DashboardPublisher;
import io.yak.ops.business.dashboard.repository.DashboardOverviewRepository;
import io.yak.ops.business.dashboard.repository.DashboardReferenceRepository;
import io.yak.ops.business.dashboard.repository.DashboardRepository;
import io.yak.ops.business.dashboard.repository.DashboardVersionRepository;
import io.yak.ops.business.dashboard.version.DashboardVersionManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardLayeringConventionTest {

  @Test
  void controllerDoesNotEnterPersistenceOrGateways() {
    for (Field field : DashboardController.class.getDeclaredFields()) {
      assertThat(field.getType().getName())
          .doesNotContain(".repository.", ".dao.", ".gateway.", "JdbcTemplate");
    }
  }

  @Test
  void stableFacadeOnlyCoordinatesExplicitApplicationRoles() {
    for (Field field : DashboardService.class.getDeclaredFields()) {
      assertThat(field.getType().getName())
          .matches("io\\.yak\\.ops\\.business\\.dashboard\\.(definition|read|version|publication)\\..+");
    }
  }

  @Test
  void businessRolesDoNotReachDaoOrCrossDomainServicesDirectly() {
    for (Class<?> type : List.of(
        DashboardManager.class,
        DashboardVersionManager.class,
        DashboardPublisher.class,
        DashboardLineageSynchronizer.class)) {
      for (Field field : type.getDeclaredFields()) {
        assertThat(field.getType().getName())
            .doesNotContain(
                ".dao.",
                "AnalysisReferenceService",
                "LineageService",
                "LineageMaintenanceService");
      }
    }
  }

  @Test
  void crossDomainCapabilitiesAreDashboardOwnedPorts() {
    assertThat(DashboardAnalysisGateway.class.isInterface()).isTrue();
    assertThat(DashboardLineageGraphGateway.class.isInterface()).isTrue();
  }

  @Test
  void repositoryContractsDoNotExposeDaoOrHttpTypes() {
    for (Class<?> contract : List.of(
        DashboardRepository.class,
        DashboardVersionRepository.class,
        DashboardReferenceRepository.class,
        DashboardOverviewRepository.class)) {
      for (Method method : contract.getDeclaredMethods()) {
        assertThat(method.toGenericString())
            .doesNotContain(".dao.", "com.baomidou.mybatisplus", "controller.v1", "JdbcTemplate");
      }
    }
  }

  @Test
  void broadServiceAndSupportBucketsAreRemoved() {
    Path root = productionRoot();
    assertThat(Files.exists(root.resolve("service"))).isFalse();
    assertThat(Files.exists(root.resolve("repository/support"))).isFalse();
  }

  private Path productionRoot() {
    Path local = Path.of("src/main/java/io/yak/ops/business/dashboard");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "yak-ops-business",
        "yak-ops-business-dashboard",
        "src",
        "main",
        "java",
        "io",
        "yak",
        "ops",
        "business",
        "dashboard");
  }
}
