package io.yak.ops.business.modeling.importer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.datasource.catalog.DataSourceCatalogReader;
import io.yak.ops.business.datasource.domain.catalog.CatalogColumn;
import io.yak.ops.business.modeling.api.ModelingStructureApi;
import io.yak.ops.business.modeling.dao.model.ModelingColumnMappingPO;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.MappingRepository;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureFingerprint;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.core.security.ActionAuthorization;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** One user confirmation must produce one atomic, verifiable ODS design fact. */
class OdsManualImportCommitServiceTest {
  private ModelRepository models;
  private MappingRepository mappings;
  private ModelStructureService structures;
  private DataSourceCatalogReader catalog;
  private OdsManualImportCommitService service;
  private Model model;
  private StructureView empty;
  private List<ModelingColumnMappingPO> rows;

  private static CatalogColumn source(String name) {
    return new CatalogColumn(name, "BIGINT", -5, 0, 0, false, 1, true, null);
  }

  private static ModelingStructureApi.ColumnInput target(String name) {
    return new ModelingStructureApi.ColumnInput(name, "BIGINT", null, null,
        false, null, null, null);
  }

  private OdsManualImportCommitService.Request request(
      String definition, List<ModelingStructureApi.ColumnInput> physical,
      List<OdsManualImportCommitService.ColumnSelection> pairs) {
    return new OdsManualImportCommitService.Request(definition, 10L, "trade_db",
        "trade_order", new ModelingStructureApi.SaveStructureRequest(
            "ods_orders", "订单", physical, List.of(), List.of(), null, Map.of()), pairs);
  }

  private static OdsManualImportCommitService.ColumnSelection select(String target, String source) {
    return new OdsManualImportCommitService.ColumnSelection(target, source);
  }

  @BeforeEach void setUp() {
    models = mock(ModelRepository.class);
    mappings = mock(MappingRepository.class);
    structures = mock(ModelStructureService.class);
    catalog = mock(DataSourceCatalogReader.class);
    rows = new ArrayList<>();
    model = Model.create("ods_orders", "订单", ModelDialect.MYSQL, "", null, "ODS", null)
        .withPersisted(50L, "tester", null, null);
    empty = StructureView.of(model, "ods_orders", "", List.of(), List.of(),
        List.of(), null, Map.of());
    when(models.findByIdForUpdate(50L)).thenReturn(Optional.of(model));
    when(structures.getForUpdate(50L)).thenReturn(empty);
    when(mappings.listByModel(50L)).thenAnswer(i -> new ArrayList<>(rows));
    when(catalog.listColumnsFresh(10L, "trade_db", null, "trade_order"))
        .thenReturn(List.of(source("order_id"), source("amount")));
    when(models.assignSource(eq(50L), eq(10L), eq("trade_db"),
        eq("trade_order"), anyString())).thenReturn(true);
    when(models.assignImportLineage(eq(50L), eq("SOURCE_TABLE"),
        isNull(), anyString())).thenReturn(true);
    when(mappings.upsert(any(), anyString())).thenAnswer(i -> {
      ModelingColumnMappingPO mapping = i.getArgument(0);
      rows.add(mapping);
      return mapping;
    });
    when(structures.saveEditContext(eq(50L), any(), eq("tester"), anyString()))
        .thenAnswer(i -> new ModelStructureService.EditContext(empty, "b".repeat(64)));
    service = new OdsManualImportCommitService(
        models, mappings, structures, catalog, mock(ActionAuthorization.class));
  }

  private String baseline() { return StructureFingerprint.of(empty); }

  @Test void wholeSourceIsSavedAndReadBackInOneCommand() {
    var result = service.commit(50L, request(baseline(),
        List.of(target("order_id"), target("amount")),
        List.of(select("order_id", "order_id"), select("amount", "amount"))),
        "tester");
    assertEquals("SAVED_AND_VERIFIED", result.status());
    assertEquals(2, result.confirmedMappingCount());
    assertEquals("b".repeat(64), result.newStructureDefinition());
    assertEquals(2, rows.size());
    assertNull(rows.get(0).getTransformExpr());
    verify(structures).saveEditContext(eq(50L), any(), eq("tester"), eq(baseline()));
    verify(models).assignSource(50L, 10L, "trade_db", "trade_order", "tester");
    verify(models).assignImportLineage(50L, "SOURCE_TABLE", null, "tester");
  }

  @Test void explicitMappingSupportsRenamedTargetButDoesNotInventSource() {
    var result = service.commit(50L, request(baseline(),
        List.of(target("business_order_key"), target("amount")),
        List.of(select("business_order_key", "order_id"), select("amount", "amount"))),
        "tester");
    assertEquals(2, result.confirmedMappingCount());
    assertEquals("business_order_key", rows.get(0).getTargetColumn());
    assertEquals("order_id", rows.get(0).getSourceColumn());
  }

  @Test void staleStructureCannotWriteSourceOrMappings() {
    assertThrows(ModelingException.class, () -> service.commit(50L, request("0".repeat(64),
        List.of(target("order_id"), target("amount")),
        List.of(select("order_id", "order_id"), select("amount", "amount"))), "tester"));
    verify(structures, never()).saveEditContext(anyLong(), any(), anyString(), anyString());
    verify(models, never()).assignSource(anyLong(), anyLong(), any(), any(), any());
  }

  @Test void missingOrDuplicatedSourceColumnRejectsBeforeAnyWrite() {
    assertThrows(ModelingException.class, () -> service.commit(50L, request(baseline(),
        List.of(target("order_id"), target("amount")),
        List.of(select("order_id", "order_id"))), "tester"));
    assertThrows(ModelingException.class, () -> service.commit(50L, request(baseline(),
        List.of(target("order_id"), target("amount")),
        List.of(select("order_id", "order_id"), select("order_id", "amount"))), "tester"));
    verify(structures, never()).saveEditContext(anyLong(), any(), anyString(), anyString());
  }

  @Test void technicalColumnIsNotAFalseSourceColumn() {
    var result = service.commit(50L, request(baseline(),
        List.of(target("order_id"), target("amount"), target("process_time")),
        List.of(select("order_id", "order_id"), select("amount", "amount"))), "tester");
    assertEquals(2, result.confirmedMappingCount());
    assertTrue(rows.stream().noneMatch(m -> "process_time".equals(m.getTargetColumn())));
  }

  @Test void unknownExtraTargetOrStaleSourceFailsClosed() {
    assertThrows(ModelingException.class, () -> service.commit(50L, request(baseline(),
        List.of(target("order_id"), target("amount"), target("guess_from_name")),
        List.of(select("order_id", "order_id"), select("amount", "amount"))), "tester"));
    when(catalog.listColumnsFresh(10L, "trade_db", null, "trade_order"))
        .thenReturn(List.of(source("order_id"), source("amount"), source("new_status")));
    assertThrows(ModelingException.class, () -> service.commit(50L, request(baseline(),
        List.of(target("order_id"), target("amount")),
        List.of(select("order_id", "order_id"), select("amount", "amount"))), "tester"));
    verify(structures, never()).saveEditContext(anyLong(), any(), anyString(), anyString());
  }

  @Test void refusesPublishedOrExistingStructureOrCrossSourceBindings() {
    when(models.findByIdForUpdate(50L)).thenReturn(Optional.of(model.withPublishState(
        ModelStatus.PUBLISHED, 3L, 1)));
    assertThrows(ModelingException.class, () -> service.commit(50L, request(baseline(),
        List.of(target("order_id"), target("amount")),
        List.of(select("order_id", "order_id"), select("amount", "amount"))), "tester"));
    when(models.findByIdForUpdate(50L)).thenReturn(Optional.of(model.withSource(99L, "else", "other")));
    assertThrows(ModelingException.class, () -> service.commit(50L, request(baseline(),
        List.of(target("order_id"), target("amount")),
        List.of(select("order_id", "order_id"), select("amount", "amount"))), "tester"));
    when(models.findByIdForUpdate(50L)).thenReturn(Optional.of(model));
    rows.add(new ModelingColumnMappingPO());
    assertThrows(ModelingException.class, () -> service.commit(50L, request(baseline(),
        List.of(target("order_id"), target("amount")),
        List.of(select("order_id", "order_id"), select("amount", "amount"))), "tester"));
  }

  @Test void missingPersistentReadbackCannotClaimSuccess() {
    when(mappings.upsert(any(), anyString())).thenAnswer(i -> i.getArgument(0));
    assertThrows(ModelingException.class, () -> service.commit(50L, request(baseline(),
        List.of(target("order_id"), target("amount")),
        List.of(select("order_id", "order_id"), select("amount", "amount"))), "tester"));
  }
}
