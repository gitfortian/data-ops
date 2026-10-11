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

class OdsSourceMappingReconcileServiceTest {
  private ModelRepository models;
  private ModelStructureRepository structures;
  private MappingRepository mappings;
  private DataSourceCatalogReader catalog;
  private OdsSourceMappingReconcileService service;
  private List<ModelingColumnMappingPO> rows;

  private static Model model(String layer, ModelStatus status) {
    return new Model(50L, "ods_orders", "订单", ModelDialect.MYSQL,
        null, status, null, null, null, layer, null, null, null, null, null,
        10L, "trade_db", "trade_order");
  }

  private static ColumnDefinition col(long id, String name) {
    return new ColumnDefinition(id, name, "BIGINT", null, null, true,
        null, null, null, (int) id);
  }

  private static CatalogColumn source(String name) {
    return new CatalogColumn(name, "BIGINT", -5, 0, 0, false, 1, true, null);
  }

  private static ModelingColumnMappingPO mapping(String target, String src) {
    ModelingColumnMappingPO row = new ModelingColumnMappingPO();
    row.setModelId(50L);
    row.setTargetColumn(target);
    row.setSourceDatasourceId(10L);
    row.setSourceDatabase("trade_db");
    row.setSourceTable("trade_order");
    row.setSourceColumn(src);
    return row;
  }

  @BeforeEach void setup() {
    models = mock(ModelRepository.class);
    structures = mock(ModelStructureRepository.class);
    mappings = mock(MappingRepository.class);
    catalog = mock(DataSourceCatalogReader.class);
    rows = new ArrayList<>();
    rows.add(mapping("order_id", "order_id"));
    when(models.findById(50L)).thenReturn(Optional.of(model("ODS", ModelStatus.DRAFT)));
    when(models.findByIdForUpdate(50L)).thenReturn(Optional.of(model("ODS", ModelStatus.DRAFT)));
    var physical = List.of(col(1, "order_id"), col(2, "amount"), col(3, "process_time"));
    when(structures.findColumns(50L)).thenReturn(physical);
    when(structures.findColumnsForUpdate(50L)).thenReturn(physical);
    when(mappings.listByModel(50L)).thenAnswer(i -> new ArrayList<>(rows));
    when(catalog.listColumnsFresh(10L, "trade_db", null, "trade_order"))
        .thenReturn(List.of(source("order_id"), source("amount")));
    when(mappings.upsert(any(), anyString())).thenAnswer(i -> {
      ModelingColumnMappingPO mapping = i.getArgument(0);
      rows.add(mapping);
      return mapping;
    });
    service = new OdsSourceMappingReconcileService(models, structures, mappings,
        catalog, mock(ActionAuthorization.class), new ObjectMapper());
  }

  @Test void previewsRealSourceColumnsAndSeparatesGeneratedTechnicalColumns() {
    var view = service.preview(50L);
    assertEquals(1, view.mappedCount());
    assertEquals(1, view.unmappedCount());
    assertEquals(1, view.technicalCount());
    assertEquals(64, view.definition().length());
    assertEquals("amount", view.rows().get(1).suggestedSourceColumn());
    assertEquals("TECHNICAL", view.rows().get(2).status());
    verify(mappings, never()).upsert(any(), anyString());
  }

  @Test void explicitlySelectedRepairIsSavedAndReadBackWithoutEditingOtherMappings() {
    var before = service.preview(50L);
    var result = service.confirm(50L,
        new OdsSourceMappingReconcileService.ConfirmRequest(before.definition(),
            List.of(new OdsSourceMappingReconcileService.Selection("amount", "amount"))),
        "tester");
    assertEquals("SAVED_AND_VERIFIED", result.status());
    assertEquals(1, result.applied());
    assertEquals(2, result.mappedCount());
    assertEquals("amount", rows.get(1).getSourceColumn());
    assertEquals("order_id", rows.get(0).getSourceColumn());
    assertNull(rows.get(1).getTransformExpr());
    verify(catalog, atLeast(2)).listColumnsFresh(10L, "trade_db", null, "trade_order");
  }

  @Test void rejectsChangedSourceBeforeFirstWrite() {
    var before = service.preview(50L);
    when(catalog.listColumnsFresh(10L, "trade_db", null, "trade_order"))
        .thenReturn(List.of(source("order_id"), source("amount"), source("new_field")));
    assertThrows(ModelingException.class, () -> service.confirm(50L,
        new OdsSourceMappingReconcileService.ConfirmRequest(before.definition(),
            List.of(new OdsSourceMappingReconcileService.Selection("amount", "amount"))),
        "tester"));
    verify(mappings, never()).upsert(any(), anyString());
  }

  @Test void rejectsChangedMappingBeforeFirstWrite() {
    var before = service.preview(50L);
    rows.add(mapping("amount", "amount"));
    assertThrows(ModelingException.class, () -> service.confirm(50L,
        new OdsSourceMappingReconcileService.ConfirmRequest(before.definition(),
            List.of(new OdsSourceMappingReconcileService.Selection("amount", "amount"))),
        "tester"));
    verify(mappings, never()).upsert(any(), anyString());
  }

  @Test void cannotOverwriteAlreadyMappedOrTechnicalColumns() {
    var before = service.preview(50L);
    assertThrows(ModelingException.class, () -> service.confirm(50L,
        new OdsSourceMappingReconcileService.ConfirmRequest(before.definition(),
            List.of(new OdsSourceMappingReconcileService.Selection("order_id", "amount"))),
        "tester"));
    assertThrows(ModelingException.class, () -> service.confirm(50L,
        new OdsSourceMappingReconcileService.ConfirmRequest(before.definition(),
            List.of(new OdsSourceMappingReconcileService.Selection("process_time", "amount"))),
        "tester"));
    verify(mappings, never()).upsert(any(), anyString());
  }

  @Test void duplicateSourceSelectionsFailAtomicallyBeforeAnyWrites() {
    var before = service.preview(50L);
    assertThrows(ModelingException.class, () -> service.confirm(50L,
        new OdsSourceMappingReconcileService.ConfirmRequest(before.definition(),
            List.of(new OdsSourceMappingReconcileService.Selection("amount", "amount"),
                new OdsSourceMappingReconcileService.Selection("process_time", "amount"))),
        "tester"));
    verify(mappings, never()).upsert(any(), anyString());
  }

  @Test void alreadyMappedSourceCannotBeReusedByOtherTarget() {
    var before = service.preview(50L);
    assertThrows(ModelingException.class, () -> service.confirm(50L,
        new OdsSourceMappingReconcileService.ConfirmRequest(before.definition(),
            List.of(new OdsSourceMappingReconcileService.Selection("amount", "order_id"))),
        "tester"));
    verify(mappings, never()).upsert(any(), anyString());
  }

  @Test void blocksPublishedNonOdsAndUnknownProjectWithoutSourceAccess() {
    when(models.findById(50L)).thenReturn(Optional.of(model("DWD", ModelStatus.DRAFT)));
    assertThrows(ModelingException.class, () -> service.preview(50L));
    when(models.findById(50L)).thenReturn(Optional.of(model("ODS", ModelStatus.PUBLISHED)));
    assertThrows(ModelingException.class, () -> service.preview(50L));
    when(models.findById(50L)).thenReturn(Optional.empty());
    assertThrows(ModelingException.class, () -> service.preview(50L));
    verify(catalog, never()).listColumnsFresh(any(), any(), any(), any());
  }

  @Test void staleSourceIdentityAndForgedTargetAreRejected() {
    var before = service.preview(50L);
    when(models.findByIdForUpdate(50L))
        .thenReturn(Optional.of(model("ODS", ModelStatus.DRAFT).withSource(11L, "other", "trade_order")));
    assertThrows(ModelingException.class, () -> service.confirm(50L,
        new OdsSourceMappingReconcileService.ConfirmRequest(before.definition(),
            List.of(new OdsSourceMappingReconcileService.Selection("amount", "amount"))),
        "tester"));
    when(models.findByIdForUpdate(50L)).thenReturn(Optional.of(model("ODS", ModelStatus.DRAFT)));
    assertThrows(ModelingException.class, () -> service.confirm(50L,
        new OdsSourceMappingReconcileService.ConfirmRequest(before.definition(),
            List.of(new OdsSourceMappingReconcileService.Selection("another_project_field", "amount"))),
        "tester"));
    verify(mappings, never()).upsert(any(), anyString());
  }
  @Test void crossSourceExistingMappingIsVisibleAndBlocksBulkRepair() {
    ModelingColumnMappingPO foreign = mapping("order_id", "order_id");
    foreign.setSourceDatasourceId(999L);
    rows.clear();
    rows.add(foreign);
    var view = service.preview(50L);
    assertEquals(1, view.conflictCount());
    assertEquals("SOURCE_CONFLICT", view.rows().get(0).status());
    assertThrows(ModelingException.class, () -> service.confirm(50L,
        new OdsSourceMappingReconcileService.ConfirmRequest(view.definition(),
            List.of(new OdsSourceMappingReconcileService.Selection("amount", "amount"))),
        "tester"));
    verify(mappings, never()).upsert(any(), anyString());
  }

  @Test void orphanedMappingsBlockBatchRepairAndRemainVisibleForCleanup() {
    rows.add(mapping("deleted_target", "amount"));
    var view = service.preview(50L);
    assertEquals(1, view.orphanCount());
    assertThrows(ModelingException.class, () -> service.confirm(50L,
        new OdsSourceMappingReconcileService.ConfirmRequest(view.definition(),
            List.of(new OdsSourceMappingReconcileService.Selection("amount", "amount"))),
        "tester"));
    verify(mappings, never()).upsert(any(), anyString());
  }

  @Test void realPersistedTimestampsDoNotBreakOrArtificiallyChangeEvidence() {
    var first = model("ODS", ModelStatus.DRAFT).withPersisted(50L, "tester",
        java.time.LocalDateTime.of(2026, 10, 1, 10, 0),
        java.time.LocalDateTime.of(2026, 10, 10, 11, 0));
    rows.get(0).setCreateTime(java.time.LocalDateTime.of(2026, 10, 1, 10, 0));
    rows.get(0).setUpdateTime(java.time.LocalDateTime.of(2026, 10, 10, 11, 0));
    when(models.findById(50L)).thenReturn(Optional.of(first));
    var baseline = service.preview(50L);
    assertEquals(64, baseline.definition().length());
    rows.get(0).setUpdateTime(java.time.LocalDateTime.of(2026, 10, 11, 13, 0));
    var updated = first.withPersisted(50L, "tester", first.createTime(),
        java.time.LocalDateTime.of(2026, 10, 11, 13, 0));
    when(models.findById(50L)).thenReturn(Optional.of(updated));
    assertEquals(baseline.definition(), service.preview(50L).definition());
  }

}
