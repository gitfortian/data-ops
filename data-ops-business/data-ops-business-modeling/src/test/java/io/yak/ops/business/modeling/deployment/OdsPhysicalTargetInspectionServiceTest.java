package io.yak.ops.business.modeling.deployment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.domain.ModelVersion;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.business.modeling.version.ModelVersionService;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.core.security.ActionAuthorization;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OdsPhysicalTargetInspectionServiceTest {
  private ModelRepository models;
  private ModelVersionService versions;
  private DataSourceReader dataSources;
  private DataSourceCatalogReader catalog;
  private OdsPhysicalTargetInspectionService service;
  private Model model;
  private StructureView structure;
  private DataSourceDefinition target;

  @BeforeEach void setup() {
    models = mock(ModelRepository.class);
    versions = mock(ModelVersionService.class);
    dataSources = mock(DataSourceReader.class);
    catalog = mock(DataSourceCatalogReader.class);
    service = new OdsPhysicalTargetInspectionService(models, versions, dataSources, catalog,
        mock(ActionAuthorization.class));
    model = mock(Model.class);
    structure = mock(StructureView.class);
    target = mock(DataSourceDefinition.class);
    when(model.layerCode()).thenReturn("ODS");
    when(model.latestVersionNo()).thenReturn(2);
    when(model.publishedVersionId()).thenReturn(78L);
    when(model.dialect()).thenReturn(ModelDialect.MYSQL);
    when(models.findById(50L)).thenReturn(Optional.of(model));
    when(structure.modelId()).thenReturn(50L);
    when(structure.tableName()).thenReturn("ods_order_detail");
    StructureView.ColumnView id = mock(StructureView.ColumnView.class);
    when(id.columnName()).thenReturn("order_id");
    when(id.dataType()).thenReturn("BIGINT");
    when(id.nullable()).thenReturn(false);
    when(structure.columns()).thenReturn(List.of(id));
    var version = new ModelVersion(78L, 50L, 2, "{}", "{}", 1, "hash", "owner", null);
    when(versions.getVersionDetail(50L, 2)).thenReturn(
        new ModelVersionService.VersionDetailView(version, structure, Map.of()));
    when(dataSources.require(9L)).thenReturn(target);
    when(target.getDbType()).thenReturn(DataSourceDbType.MYSQL);
    when(catalog.listColumnsFresh(9L, "warehouse", "ods", "ods_order_detail"))
        .thenReturn(List.of(new CatalogColumn("order_id", "BIGINT", -5, 19, 0,
            false, 1, false, null)));
  }

  @Test void reportsCatalogMatchWithoutClaimingDeploymentOrDataReadiness() {
    var report = service.inspect(50L, 2, 9L, "warehouse", "ods");
    assertEquals("COLUMN_MATCH_NEEDS_DDL_REVIEW", report.state());
    assertEquals(78L, report.versionId());
    assertEquals("ods_order_detail", report.targetTable());
    assertEquals(1, report.observedColumns());
    assertTrue(report.differences().isEmpty());
    assertTrue(report.limitations().stream().anyMatch(x -> x.contains("不是可持久回读的部署回执")));
  }

  @Test void driftAndMissingColumnsNeverReportMatch() {
    when(catalog.listColumnsFresh(9L, "warehouse", "ods", "ods_order_detail"))
        .thenReturn(List.of(new CatalogColumn("another_id", "BIGINT", -5, 19, 0,
            false, 1, false, null)));
    var report = service.inspect(50L, 2, 9L, "warehouse", "ods");
    assertEquals("STRUCTURE_DIFFERS", report.state());
    assertTrue(report.differences().stream().anyMatch(x -> "MISSING".equals(x.issue())));
    assertTrue(report.differences().stream().anyMatch(x -> "UNEXPECTED".equals(x.issue())));
  }

  @Test void missingTargetOrCatalogFailureIsNotAnAssertionOfNotDeployed() {
    when(catalog.listColumnsFresh(9L, "warehouse", "ods", "ods_order_detail"))
        .thenThrow(new IllegalStateException("timeout"));
    var report = service.inspect(50L, 2, 9L, "warehouse", "ods");
    assertEquals("NOT_CHECKABLE", report.state());
    assertEquals(0, report.observedColumns());
  }

  @Test void rejectDraftHistoricAndWrongDialectBeforeReadingCatalog() {
    assertThrows(ModelingException.class,
        () -> service.inspect(50L, 1, 9L, "warehouse", "ods"));
    when(model.publishedVersionId()).thenReturn(null);
    assertThrows(ModelingException.class,
        () -> service.inspect(50L, 2, 9L, "warehouse", "ods"));
    when(model.publishedVersionId()).thenReturn(78L);
    when(target.getDbType()).thenReturn(DataSourceDbType.POSTGRE_SQL);
    assertThrows(ModelingException.class,
        () -> service.inspect(50L, 2, 9L, "warehouse", "ods"));
    verify(catalog, never()).listColumnsFresh(anyLong(), any(), any(), any());
  }

  @Test void wrongPublishedPointerCannotBorrowAnotherVersion() {
    when(model.publishedVersionId()).thenReturn(80L);
    assertThrows(ModelingException.class,
        () -> service.inspect(50L, 2, 9L, "warehouse", "ods"));
    verify(catalog, never()).listColumnsFresh(anyLong(), any(), any(), any());
  }
}
