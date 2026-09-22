package io.yak.ops.business.analysis.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.analysis.AnalysisService;
import io.yak.ops.business.analysis.controller.v1.AnalysisController;
import io.yak.ops.business.analysis.definition.AnalysisDefinitionNormalizer;
import io.yak.ops.business.analysis.definition.AnalysisManager;
import io.yak.ops.business.analysis.gateway.dataset.AnalysisDatasetGateway;
import io.yak.ops.business.analysis.gateway.lineage.AnalysisLineageGraphGateway;
import io.yak.ops.business.analysis.lineage.AnalysisLineageSynchronizer;
import io.yak.ops.business.analysis.repository.AnalysisRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AnalysisLayeringConventionTest {

  @Test
  void controllerDoesNotEnterPersistenceOrGatewayAdapters() {
    for (Field field : AnalysisController.class.getDeclaredFields()) {
      String name = field.getType().getName();
      assertThat(name)
          .doesNotContain(".repository.", ".dao.", ".gateway.", "JdbcTemplate");
    }
  }

  @Test
  void compatibilityFacadeOnlyCoordinatesExplicitApplicationRoles() {
    for (Field field : AnalysisService.class.getDeclaredFields()) {
      assertThat(field.getType().getName())
          .isIn(
              "io.yak.ops.business.analysis.definition.AnalysisManager",
              "io.yak.ops.business.analysis.definition.AnalysisReader");
    }
  }

  @Test
  void definitionRolesDoNotReachDaoOrCrossDomainServicesDirectly() {
    for (Class<?> type : Arrays.asList(AnalysisManager.class, AnalysisDefinitionNormalizer.class)) {
      for (Field field : type.getDeclaredFields()) {
        assertThat(field.getType().getName())
            .doesNotContain(
                ".dao.",
                "DatasetService",
                "LineageService",
                "LineageMaintenanceService");
      }
    }
  }

  @Test
  void crossDomainCapabilitiesAreOwnerDefinedPorts() {
    assertThat(AnalysisDatasetGateway.class.isInterface()).isTrue();
    assertThat(AnalysisLineageGraphGateway.class.isInterface()).isTrue();
    for (Field field : AnalysisLineageSynchronizer.class.getDeclaredFields()) {
      assertThat(field.getType().getName())
          .doesNotContain("LineageService", "LineageMaintenanceService", "ObjectMapper");
    }
  }

  @Test
  void repositoryContractOnlyExposesAnalysisDomain() {
    assertThat(AnalysisRepository.class.isInterface()).isTrue();
    for (Method method : AnalysisRepository.class.getDeclaredMethods()) {
      assertBoundaryType(method.getReturnType());
      Arrays.stream(method.getParameterTypes()).forEach(this::assertBoundaryType);
    }
  }

  @Test
  void broadServiceAndSupportBucketsAreRemoved() {
    Path root = productionRoot();
    assertThat(Files.exists(root.resolve("service"))).isFalse();
    assertThat(Files.exists(root.resolve("repository/support"))).isFalse();
  }

  private void assertBoundaryType(Class<?> type) {
    String name = type.getName();
    assertThat(name)
        .doesNotContain(".controller.", ".dao.", "com.baomidou.mybatisplus", "JdbcTemplate");
  }

  private Path productionRoot() {
    Path local = Path.of("src/main/java/io/yak/ops/business/analysis");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "yak-ops-business",
        "yak-ops-business-analysis",
        "src",
        "main",
        "java",
        "io",
        "yak",
        "ops",
        "business",
        "analysis");
  }
}
