package io.yak.ops.business.dataset.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.dataset.DatasetQueryService;
import io.yak.ops.business.dataset.DatasetService;
import io.yak.ops.business.dataset.DevelopmentDatasetFacade;
import io.yak.ops.business.dataset.definition.DatasetBindingPolicy;
import io.yak.ops.business.dataset.definition.DatasetManager;
import io.yak.ops.business.dataset.definition.DatasetOverviewReader;
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
import io.yak.ops.business.dataset.schema.DatasetFieldIdentity;
import io.yak.ops.business.dataset.schema.DatasetFieldNormalizer;
import io.yak.ops.business.dataset.schema.DatasetSchemaDiscovery;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

class DatasetRoleConventionTest {

  @Test
  void stableApplicationFacadesRemainServices() {
    for (Class<?> facade :
        List.of(DatasetService.class, DatasetQueryService.class, DevelopmentDatasetFacade.class)) {
      assertThat(facade.getAnnotation(Service.class))
          .as("%s must remain a stable Dataset application facade", facade.getSimpleName())
          .isNotNull();
    }
  }

  @Test
  void internalRolesRemainExplicitComponents() {
    for (Class<?> role :
        List.of(
            DatasetReader.class,
            DatasetOverviewReader.class,
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
      assertThat(role.getAnnotation(Component.class))
          .as("%s must remain an explicit internal Dataset role", role.getSimpleName())
          .isNotNull();
      assertThat(role.getAnnotation(Service.class))
          .as("%s must not masquerade as a stable Application Service", role.getSimpleName())
          .isNull();
    }
  }
}
