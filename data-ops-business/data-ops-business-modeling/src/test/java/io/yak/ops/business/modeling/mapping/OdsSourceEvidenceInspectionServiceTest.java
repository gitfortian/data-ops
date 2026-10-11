package io.yak.ops.business.modeling.mapping;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.modeling.dao.model.ModelingColumnMappingPO;
import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.MappingRepository;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.ModelStructureRepository;
import io.yak.ops.core.security.ActionAuthorization;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OdsSourceEvidenceInspectionServiceTest {
  private ModelRepository models;
  private ModelStructureRepository structure;
  private MappingRepository mappings;
  private DataSourceCatalogReader catalog;
  private ActionAuthorization authorization;
  private OdsSourceEvidenceInspectionService service;
  private List<ModelingColumnMappingPO> existing;
  private List<ColumnDefinition> targets;
  private List<CatalogColumn> sources;

  private static Model model(ModelStatus status) {
    return new Model(50L, "ods_order", "订单", ModelDialect.MYSQL,
        null, status, null, null, null, "ODS", null, null, null, null, null,
        9L, "trade", "trade_order");
  }

  private static ColumnDefinition target(long id, String name, String type, boolean nullable) {
    return new ColumnDefinition(id, name, type, null, null, nullable,
        null, null, null, (int) id);
  }

  private static CatalogColumn source(String name, String type, boolean nullable) {
    return new CatalogColumn(name, type, -5, null, null, nullable, 1, false, null);
  }

  private static ModelingColumnMappingPO mapped(String target, String source) {
    var po = new ModelingColumnMappingPO();
    po.setModelId(50L);
    po.setTargetColumn(target);
    po.setSourceDatasourceId(9L);
    po.setSourceDatabase("trade");
    po.setSourceTable("trade_order");
    po.setSourceColumn(source);
    return po;
  }

  @BeforeEach void setup() {
    models = mock(ModelRepository.class);
    structure = mock(ModelStructureRepository.class);
    mappings = mock(MappingRepository.class);
    catalog = mock(DataSourceCatalogReader.class);
    authorization = mock(ActionAuthorization.class);
    targets = new ArrayList<>(List.of(
        target(1L, "order_id", "BIGINT", false),
        target(2L, "amount", "DECIMAL", true),
        target(3L, "process_time", "TIMESTAMP", true)));
    existing = new ArrayList<>(List.of(mapped("order_id", "order_id"),
        mapped("amount", "amount")));
    sources = new ArrayList<>(List.of(source("order_id", "BIGINT", false),
        source("amount", "DECIMAL", true)));
    when(models.findById(50L)).thenReturn(Optional.of(model(ModelStatus.DRAFT)));
    when(structure.findColumns(50L)).thenAnswer(i -> new ArrayList<>(targets));
    when(mappings.listByModel(50L)).thenAnswer(i -> new ArrayList<>(existing));
    when(catalog.listColumnsFresh(9L, "trade", null, "trade_order"))
        .thenAnswer(i -> new ArrayList<>(sources));
    service = new OdsSourceEvidenceInspectionService(models, structure, mappings,
        catalog, authorization, new ObjectMapper());
  }

  @Test void goodCurrentDraftEvidenceIsReadOnlyWithStableFingerprint() {
    var first = service.inspect(50L);
    assertEquals("CONSISTENT_NOW", first.status());
    assertEquals(64, first.evidenceSha256().length());
    assertEquals(3, first.targetCount());
    assertEquals(2, first.sourceCount());
    assertTrue(first.blockers().isEmpty());
    assertEquals("TECHNICAL", first.fields().get(2).status());
    java.util.Collections.reverse(sources);
    assertEquals(first.evidenceSha256(), service.inspect(50L).evidenceSha256());
    verify(mappings, never()).upsert(any(), anyString());
    verify(models, never()).assignSource(anyLong(), anyLong(), any(), any(), any());
  }

  @Test void publishedModelCanBeInspectedWithoutAnyWrite() {
    when(models.findById(50L)).thenReturn(Optional.of(model(ModelStatus.PUBLISHED)));
    var inspection = service.inspect(50L);
    assertEquals("PUBLISHED", inspection.modelStatus());
    assertEquals("CONSISTENT_NOW", inspection.status());
    verify(mappings, never()).upsert(any(), anyString());
  }

  @Test void changedTypeAndNullabilityRequireReviewNotClaimedIncompatible() {
    sources.set(1, source("amount", "VARCHAR", true));
    assertEquals("TYPE_REVIEW", service.inspect(50L).fields().get(1).status());
    sources.set(1, source("amount", "DECIMAL", true));
    targets.set(1, target(2L, "amount", "DECIMAL", false));
    assertEquals("NULLABILITY_REVIEW", service.inspect(50L).fields().get(1).status());
  }

  @Test void missingSourceAndNewUnreferencedSourceAreVisibleWithoutRepair() {
    sources.remove(1);
    sources.add(source("new_amount", "DECIMAL", true));
    var report = service.inspect(50L);
    assertEquals("NEEDS_REVIEW", report.status());
    assertTrue(report.fields().stream().anyMatch(f -> "MISSING_SOURCE".equals(f.status())));
    assertTrue(report.fields().stream().anyMatch(f -> "UNREFERENCED_SOURCE".equals(f.status())));
    verify(mappings, never()).upsert(any(), anyString());
  }

  @Test void missingMappingAndOrphanHistoryAreNotSilentlyIgnored() {
    existing.remove(1);
    existing.add(mapped("old_deleted_target", "amount"));
    var report = service.inspect(50L);
    assertEquals("NEEDS_REVIEW", report.status());
    assertTrue(report.fields().stream().anyMatch(f -> "UNMAPPED".equals(f.status())));
    assertTrue(report.fields().stream().anyMatch(f -> "ORPHAN_MAPPING".equals(f.status())));
  }

  @Test void detectsCrossSourceAndDuplicateSourceReferences() {
    existing.get(0).setSourceDatasourceId(99L);
    var report = service.inspect(50L);
    assertTrue(report.fields().stream().anyMatch(f -> "SOURCE_CONFLICT".equals(f.status())));
    existing.get(0).setSourceDatasourceId(9L);
    existing.get(1).setSourceColumn("order_id");
    report = service.inspect(50L);
    assertTrue(report.fields().stream().anyMatch(f -> "DUPLICATE_SOURCE".equals(f.status())));
  }

  @Test void transformedOdsSourcesRemainExplicitReviewItems() {
    existing.get(1).setTransformExpr("COALESCE(amount, 0)");
    var report = service.inspect(50L);
    assertEquals("TRANSFORM_REVIEW", report.fields().get(1).status());
    assertNotEquals("CONSISTENT_NOW", report.status());
  }

  @Test void missingFormalBindingNeverLooksVerifiedAndSkipsCatalog() {
    when(models.findById(50L)).thenReturn(Optional.of(
        model(ModelStatus.DRAFT).withSource(null, null, null)));
    var report = service.inspect(50L);
    assertEquals("NOT_CHECKABLE", report.status());
    assertNull(report.evidenceSha256());
    verifyNoInteractions(catalog);
  }

  @Test void noSourceCatalogDoesNotMeanDeletedTableOrVerifiedModel() {
    sources.clear();
    assertThrows(ModelingException.class, () -> service.inspect(50L));
  }

  @Test void projectAndDatasourceFailuresAreNeverSwallowed() {
    when(models.findById(50L)).thenReturn(Optional.empty());
    assertThrows(ModelingException.class, () -> service.inspect(50L));
    verifyNoInteractions(catalog);
  }
}
