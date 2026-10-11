package io.yak.ops.business.modeling.version;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.domain.ModelVersion;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.mapping.OdsSourceEvidenceInspectionService;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OdsPublishedVersionEvidenceServiceTest {
  private ModelRepository models;
  private ModelVersionService versions;
  private ModelStructureService structures;
  private OdsSourceEvidenceInspectionService sources;
  private OdsPublishedVersionEvidenceService service;
  private Model model;
  private StructureView frozen;
  private StructureView live;

  private static ColumnDefinition col(long id, String name, String type) {
    // Database row IDs change after full-replace. The field's design order does not.
    int designOrder = "order_id".equals(name) ? 1
        : "amount".equals(name) ? 2 : 3;
    return new ColumnDefinition(id, name, type, null, null, true,
        null, null, null, designOrder);
  }

  private StructureView structure(String tableName, ColumnDefinition... columns) {
    return StructureView.of(model, tableName, null, List.of(columns), List.of(),
        List.of(), null, Map.of());
  }

  private OdsSourceEvidenceInspectionService.Inspection inspected(String status) {
    return new OdsSourceEvidenceInspectionService.Inspection(
        50L, "PUBLISHED", 9L, "trade", "trade_order", status,
        "a".repeat(64), 2, 2, 2, List.of(), List.of());
  }

  @BeforeEach void setup() {
    models = mock(ModelRepository.class);
    versions = mock(ModelVersionService.class);
    structures = mock(ModelStructureService.class);
    sources = mock(OdsSourceEvidenceInspectionService.class);
    service = new OdsPublishedVersionEvidenceService(models, versions, structures, sources);
    model = Model.create("ods_order", "订单", ModelDialect.MYSQL, null,
        null, "ODS", null).withPersisted(50L, "owner", null, null)
        .withSource(9L, "trade", "trade_order")
        .withPublishState(ModelStatus.PUBLISHED, 101L, 2);
    frozen = structure("ods_order", col(1L, "order_id", "BIGINT"),
        col(2L, "amount", "DECIMAL"));
    live = structure("ods_order", col(111L, "order_id", "BIGINT"),
        col(222L, "amount", "DECIMAL"));
    when(models.findById(50L)).thenReturn(Optional.of(model));
    when(versions.getVersionDetail(50L, 2)).thenAnswer(i -> new ModelVersionService.VersionDetailView(
        new ModelVersion(101L, 50L, 2, "frozen", "meta", 2, "checksum", "owner", null),
        frozen, Map.of("layerCode", "ODS")));
    when(structures.get(50L)).thenAnswer(i -> live);
    when(sources.inspect(50L)).thenReturn(inspected("CONSISTENT_NOW"));
  }

  @Test void exactPublishedVersionIsNotMistakenForHistoricalProvenance() {
    var result = service.review(50L, 2);
    assertEquals("MATCHES_CURRENT", result.structureStatus());
    assertTrue(result.latestPublishedVersion());
    assertEquals("NOT_FROZEN", result.historicalSourceStatus());
    assertEquals("CONSISTENT_NOW", result.currentSourceStatus());
    assertTrue(result.differences().isEmpty());
    assertFalse(result.limitations().isEmpty());
    verify(versions).getVersionDetail(50L, 2);
    verify(sources).inspect(50L);
    verifyNoMoreInteractions(versions, sources);
    verify(models, never()).assignSource(anyLong(), anyLong(), any(), any(), any());
  }

  @Test void changedAndAddedPhysicalColumnsAreExplained() {
    live = structure("ods_order", col(41L, "order_id", "VARCHAR"),
        col(42L, "new_amount", "DECIMAL"));
    var result = service.review(50L, 2);
    assertEquals("DIFFERS_FROM_CURRENT", result.structureStatus());
    assertTrue(result.differences().stream().anyMatch(d -> "CHANGED".equals(d.status())));
    assertTrue(result.differences().stream().anyMatch(d -> "REMOVED".equals(d.status())));
    assertTrue(result.differences().stream().anyMatch(d -> "ADDED".equals(d.status())));
  }

  @Test void tableLevelChangesDoNotLookEqualJustBecauseColumnsMatch() {
    live = structure("ods_order_new", col(11L, "order_id", "BIGINT"),
        col(12L, "amount", "DECIMAL"));
    var result = service.review(50L, 2);
    assertEquals("DIFFERS_FROM_CURRENT", result.structureStatus());
    assertTrue(result.differences().stream()
        .anyMatch(d -> "TABLE_DEFINITION_CHANGED".equals(d.status())));
  }

  @Test void sourceUnavailabilityIsVisibleAndNoHistoricIdentityIsInvented() {
    when(sources.inspect(50L)).thenReturn(inspected("NOT_CHECKABLE"));
    var result = service.review(50L, 2);
    assertEquals("NOT_CHECKABLE", result.currentSourceStatus());
    assertEquals("NOT_FROZEN", result.historicalSourceStatus());
    when(sources.inspect(50L)).thenThrow(new IllegalStateException("source unreachable"));
    assertThrows(IllegalStateException.class, () -> service.review(50L, 2));
  }

  @Test void rejectsForeignProjectAndIdentityMismatchBeforeReadingSource() {
    when(models.findById(50L)).thenReturn(Optional.empty());
    assertThrows(ModelingException.class, () -> service.review(50L, 2));
    verifyNoInteractions(sources);
    when(models.findById(50L)).thenReturn(Optional.of(model));
    when(versions.getVersionDetail(50L, 2)).thenReturn(new ModelVersionService.VersionDetailView(
        new ModelVersion(101L, 99L, 2, "wrong", "meta", 1, "checksum", "owner", null),
        frozen, Map.of("layerCode", "ODS")));
    assertThrows(ModelingException.class, () -> service.review(50L, 2));
    verifyNoInteractions(sources);
  }

  @Test void rejectsOtherLayerAndUntrustedHistoricLayer() {
    when(models.findById(50L)).thenReturn(Optional.of(
        Model.create("dwd_order", "订单", ModelDialect.MYSQL, null, null, "DWD", null)
            .withPersisted(50L, "owner", null, null)));
    assertThrows(ModelingException.class, () -> service.review(50L, 2));
    when(models.findById(50L)).thenReturn(Optional.of(model));
    when(versions.getVersionDetail(50L, 2)).thenReturn(new ModelVersionService.VersionDetailView(
        new ModelVersion(101L, 50L, 2, "wrong", "meta", 1, "checksum", "owner", null),
        frozen, Map.of("layerCode", "DWD")));
    assertThrows(ModelingException.class, () -> service.review(50L, 2));
    verifyNoInteractions(sources);
  }

  @Test void historicalRevisionDoesNotBecomeLatestByAccident() {
    var older = new ModelVersionService.VersionDetailView(
        new ModelVersion(90L, 50L, 1, "old", "meta", 2, "checksum", "owner", null),
        frozen, Map.of("layerCode", "ODS"));
    when(versions.getVersionDetail(50L, 1)).thenReturn(older);
    var report = service.review(50L, 1);
    assertEquals(1, report.versionNo());
    assertFalse(report.latestPublishedVersion());
    assertEquals("NOT_FROZEN", report.historicalSourceStatus());
  }
}
