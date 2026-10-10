package io.yak.ops.business.modeling.importer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.modeling.api.ModelingStructureApi;
import io.yak.ops.business.modeling.catalog.ModelCatalogService;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.mapping.MappingService;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Reverse-import transaction: source mappings are created with the structure, never for generated columns. */
class ReverseImportWriterTest {
  private ModelCatalogService catalogService;
  private ModelStructureService structureService;
  private ModelRepository modelRepository;
  private MappingService mappingService;
  private ReverseImportWriter writer;

  @BeforeEach
  void setUp() {
    catalogService = mock(ModelCatalogService.class);
    structureService = mock(ModelStructureService.class);
    modelRepository = mock(ModelRepository.class);
    mappingService = mock(MappingService.class);
    writer = new ReverseImportWriter(catalogService, structureService, modelRepository, mappingService);
  }

  private static Model model(Long sourceId, String sourceDatabase, String sourceTable) {
    return new Model(42L, "ods_orders", "订单贴源模型", ModelDialect.DORIS,
        null, ModelStatus.DRAFT, "tester", null, null, "ODS", null, null,
        List.of(), null, null).withSource(sourceId, sourceDatabase, sourceTable);
  }

  private static ReverseImportPlan plan() {
    return new ReverseImportPlan("订单贴源模型", "ods_orders", "DORIS", null, null,
        "ods_orders", "ODS", null, 10L, "trade_db", "trade_order",
        List.<ModelingStructureApi.ColumnInput>of(), List.of("order_id"),
        List.of("order_id", "order_amount"));
  }

  @Test
  void savesOnlyCatalogColumnsAsDirectMappingsAlongsideModelStructure() {
    when(modelRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(model(null, null, null)));

    writer.fillStructure(42L, plan(), "tester");

    verify(structureService).save(eq(42L), any(), eq("tester"));
    verify(modelRepository).assignSource(42L, 10L, "trade_db", "trade_order", "tester");
    verify(mappingService).setMapping(42L, "order_id", 10L, "trade_db", "trade_order",
        "order_id", null, "tester");
    verify(mappingService).setMapping(42L, "order_amount", 10L, "trade_db", "trade_order",
        "order_amount", null, "tester");
    verify(mappingService, never()).setMapping(eq(42L), eq("process_time"), any(), any(),
        any(), any(), any(), any());
    verify(mappingService, never()).setMapping(eq(42L), eq("event_time"), any(), any(),
        any(), any(), any(), any());
  }

  @Test
  void keepsMatchingExistingSourceBinding() {
    when(modelRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(model(10L, "trade_db", "trade_order")));

    writer.fillStructure(42L, plan(), "tester");

    verify(modelRepository, never()).assignSource(any(), any(), any(), any(), any());
    verify(mappingService).setMapping(42L, "order_id", 10L, "trade_db", "trade_order",
        "order_id", null, "tester");
  }

  @Test
  void doesNotAssumeDirectSourceMappingForNonOdsTransformation() {
    Model dwd = new Model(42L, "dwd_orders", "订单明细", ModelDialect.DORIS,
        null, ModelStatus.DRAFT, "tester", null, null, "DWD", null, null,
        List.of(), null, null);
    when(modelRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(dwd));
    ReverseImportPlan sourcePlan = new ReverseImportPlan("订单明细", "dwd_orders", "DORIS",
        null, null, "dwd_orders", null, null, 10L, "trade_db", "trade_order",
        List.<ModelingStructureApi.ColumnInput>of(), List.of("order_id"), List.of("order_id"));

    writer.fillStructure(42L, sourcePlan, "tester");

    verify(structureService).save(eq(42L), any(), eq("tester"));
    verify(mappingService, never()).setMapping(any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void rejectsExistingModelBoundToOtherTableBeforeWritingAnything() {
    when(modelRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(model(10L, "trade_db", "other_table")));

    assertThrows(ModelingException.class, () -> writer.fillStructure(42L, plan(), "tester"));

    verify(structureService, never()).save(any(), any(), any());
    verify(mappingService, never()).setMapping(any(), any(), any(), any(), any(), any(), any(), any());
  }
}
