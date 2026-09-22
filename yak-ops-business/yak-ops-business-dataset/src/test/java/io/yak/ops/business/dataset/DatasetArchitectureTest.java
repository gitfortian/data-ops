package io.yak.ops.business.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.yak.ops.business.dataset.controller.v1.DatasetController;
import io.yak.ops.business.dataset.controller.v1.converter.DatasetRequestConverter;
import io.yak.ops.business.dataset.controller.v1.converter.DatasetViewConverter;
import io.yak.ops.business.dataset.dao.DatasetDao;
import io.yak.ops.business.dataset.definition.DatasetBindingPolicy;
import io.yak.ops.business.dataset.definition.DatasetManager;
import io.yak.ops.business.dataset.definition.DatasetReader;
import io.yak.ops.business.dataset.development.DevelopmentDatasetManager;
import io.yak.ops.business.dataset.gateway.datasource.DataSourceDatasetCatalogAdapter;
import io.yak.ops.business.dataset.gateway.datasource.DataSourceSchemaSqlAdapter;
import io.yak.ops.business.dataset.gateway.lineage.LineageGraphDatasetAdapter;
import io.yak.ops.business.dataset.gateway.lineage.LineageProjectionAnalyzerAdapter;
import io.yak.ops.business.dataset.gateway.taskcatalog.TaskCatalogDatasetAdapter;
import io.yak.ops.business.dataset.lineage.DatasetLineageRefreshListener;
import io.yak.ops.business.dataset.lineage.DatasetLineageRefreshPublisher;
import io.yak.ops.business.dataset.lineage.DatasetLineageSnapshotReader;
import io.yak.ops.business.dataset.lineage.DatasetLineageSourceResolver;
import io.yak.ops.business.dataset.lineage.DatasetLineageSynchronizer;
import io.yak.ops.business.dataset.lineage.DatasetLineageTransactionRunner;
import io.yak.ops.business.dataset.observability.DatasetQueryPerformanceReader;
import io.yak.ops.business.dataset.observability.DatasetQueryPerformanceRecorder;
import io.yak.ops.business.dataset.publication.DatasetPublisher;
import io.yak.ops.business.dataset.publication.DatasetVersionWriter;
import io.yak.ops.business.dataset.query.DatasetQueryCompiler;
import io.yak.ops.business.dataset.query.DatasetQueryCoordinator;
import io.yak.ops.business.dataset.query.DatasetSourceQueryRegistry;
import io.yak.ops.business.dataset.query.adapter.QueryRevisionDatasetSourceAdapter;
import io.yak.ops.business.dataset.query.adapter.SqlQueryDatasetSourceAdapter;
import io.yak.ops.business.dataset.repository.DatasetRepository;
import io.yak.ops.business.dataset.schema.DatasetFieldIdentity;
import io.yak.ops.business.dataset.schema.DatasetFieldNormalizer;
import io.yak.ops.business.dataset.schema.DatasetSchemaDiscovery;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/** Lightweight layering checks that complement the source-level dependency and style guards. */
class DatasetArchitectureTest {

  @Test
  void controllerUsesOnlyStableFacadesAndTransportMappers() {
    Set<Class<?>> allowed =
        Set.of(
            DatasetService.class,
            DatasetQueryService.class,
            DatasetRequestConverter.class,
            DatasetViewConverter.class);
    for (Field field : DatasetController.class.getDeclaredFields()) {
      assertThat(field.getType())
          .as("DatasetController.%s must use a stable facade or HTTP mapper", field.getName())
          .isIn(allowed);
      assertNoPersistence(field.getGenericType());
    }
  }

  @Test
  void serviceStereotypeIsReservedForStableDatasetFacades() {
    for (Class<?> facade :
        List.of(DatasetService.class, DatasetQueryService.class, DevelopmentDatasetFacade.class)) {
      assertThat(facade.getAnnotation(Service.class))
          .as("%s must remain a stable application facade", facade.getSimpleName())
          .isNotNull();
    }

    for (Class<?> internal :
        List.of(
            DatasetReader.class,
            DatasetManager.class,
            DatasetBindingPolicy.class,
            DatasetPublisher.class,
            DatasetVersionWriter.class,
            DatasetSchemaDiscovery.class,
            DatasetFieldNormalizer.class,
            DatasetFieldIdentity.class,
            DatasetQueryCoordinator.class,
            DatasetSourceQueryRegistry.class,
            DatasetQueryCompiler.class,
            QueryRevisionDatasetSourceAdapter.class,
            SqlQueryDatasetSourceAdapter.class,
            DatasetQueryPerformanceRecorder.class,
            DatasetQueryPerformanceReader.class,
            DevelopmentDatasetManager.class,
            DatasetLineageRefreshPublisher.class,
            DatasetLineageSnapshotReader.class,
            DatasetLineageSourceResolver.class,
            DatasetLineageSynchronizer.class,
            DatasetLineageTransactionRunner.class,
            DatasetLineageRefreshListener.class,
            TaskCatalogDatasetAdapter.class,
            DataSourceSchemaSqlAdapter.class,
            DataSourceDatasetCatalogAdapter.class,
            LineageProjectionAnalyzerAdapter.class,
            LineageGraphDatasetAdapter.class)) {
      assertThat(internal.getAnnotation(Component.class))
          .as("%s must remain an explicit internal role", internal.getSimpleName())
          .isNotNull();
      assertThat(internal.getAnnotation(Service.class))
          .as("%s must not masquerade as an Application Service", internal.getSimpleName())
          .isNull();
    }
  }

  @Test
  void stableFacadesDoNotDependOnPersistenceOrExternalImplementations() {
    for (Class<?> facade :
        List.of(DatasetService.class, DatasetQueryService.class, DevelopmentDatasetFacade.class)) {
      for (Field field : facade.getDeclaredFields()) {
        String type = field.getGenericType().getTypeName();
        assertFalse(type.contains(".repository."), type);
        assertFalse(type.contains(".dao."), type);
        assertFalse(type.contains("TaskCatalogService"), type);
        assertFalse(type.contains("DataSourceExecutionProvider"), type);
        assertFalse(type.contains("LineageService"), type);
        assertFalse(type.contains("ApplicationEventPublisher"), type);
      }
    }
  }

  @Test
  void repositoryContractCannotExposeHttpPoMybatisOrFacadeTypes() {
    for (Method method : DatasetRepository.class.getDeclaredMethods()) {
      assertRepositoryType(method.getGenericReturnType());
      for (Type parameter : method.getGenericParameterTypes()) {
        assertRepositoryType(parameter);
      }
    }
  }

  @Test
  void daoContractCannotExposeHttpTypes() {
    for (Method method : DatasetDao.class.getDeclaredMethods()) {
      assertNoHttp(method.getGenericReturnType());
      for (Type parameter : method.getGenericParameterTypes()) {
        assertNoHttp(parameter);
      }
    }
  }

  @Test
  void genericServiceBucketCannotReturn() {
    assertThat(Files.exists(productionRoot().resolve("service")))
        .as("Dataset production service/ bucket must remain retired")
        .isFalse();
  }

  private static void assertNoPersistence(Type type) {
    String name = type.getTypeName();
    assertFalse(name.contains(".repository."), name);
    assertFalse(name.contains(".dao."), name);
    assertFalse(name.contains("JdbcTemplate"), name);
    assertFalse(name.contains("com.baomidou.mybatisplus"), name);
  }

  private static void assertRepositoryType(Type type) {
    String name = type.getTypeName();
    assertNoHttp(type);
    assertFalse(name.contains(".dao."), name);
    assertFalse(name.contains("com.baomidou.mybatisplus"), name);
    assertFalse(name.contains("DatasetService"), name);
    assertFalse(name.contains("DatasetQueryService"), name);
  }

  private static void assertNoHttp(Type type) {
    String name = type.getTypeName();
    assertFalse(name.contains(".controller."), name);
    assertFalse(name.contains(".dto."), name);
    assertFalse(name.contains(".vo."), name);
  }

  private Path productionRoot() {
    Path moduleLocal = Path.of("src/main/java/io/yak/ops/business/dataset");
    if (Files.isDirectory(moduleLocal)) {
      return moduleLocal;
    }
    Path repositoryRelative =
        Path.of(
            "yak-ops-business",
            "yak-ops-business-dataset",
            "src",
            "main",
            "java",
            "io",
            "yak",
            "ops",
            "business",
            "dataset");
    assertThat(Files.isDirectory(repositoryRelative))
        .as("Dataset production source root must be available")
        .isTrue();
    return repositoryRelative;
  }
}
