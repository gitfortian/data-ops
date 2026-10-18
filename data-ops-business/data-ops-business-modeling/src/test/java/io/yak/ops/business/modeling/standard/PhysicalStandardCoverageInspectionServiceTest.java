package io.yak.ops.business.modeling.standard;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.business.semantic.api.BusinessProcess;
import io.yak.ops.business.semantic.api.ProcessApi;
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardField;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardQueryApi;
import io.yak.ops.business.semantic.api.StandardStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PhysicalStandardCoverageInspectionServiceTest {
  private ModelRepository models;
  private ModelStructureService structures;
  private ProcessApi semantic;
  private StandardQueryApi standardQuery;
  private PhysicalStandardCoverageInspectionService service;
  private Model model;
  private List<ColumnDefinition> columns;

  private static ColumnDefinition col(long id, String name, Long ref, String aggregate) {
    return new ColumnDefinition(id, name, "BIGINT", null, null, false, null,
        null, null, (int) id, null, null, null, null, null, null,
        ref, "DIMENSION", aggregate, null);
  }

  private static StandardField field(long id, String status, String role) {
    return new StandardField(id, "order_" + id, "订单 #" + id, role, status,
        "BIGINT", id + 100, null, null, null, null, null,
        StandardField.SOURCE_MANUAL, 1, false, "owner", null, null);
  }

  private static Standard typeStandard(long id) {
    return new Standard(id, StandardKind.TYPE, "type_" + id, "类型标准 " + id,
        StandardStatus.ENABLED, 1, 0, false, "测试类型标准",
        new Standard.KindFields(null, null, null, null, null, "BIGINT",
            null, null, null, null, null, null, null, null, null, null, null),
        "owner", null, null);
  }

  private static Standard securityStandard(long id) {
    return new Standard(id, StandardKind.SECURITY, "security_level", "安全级别",
        StandardStatus.ENABLED, 1, 0, false, "测试安全标准",
        new Standard.KindFields(null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, "L2", "secret-mask-rule"),
        "owner", null, null);
  }

  private static BusinessProcess process(String grain) {
    return new BusinessProcess(22L, "place_order", "下单", 9L, grain,
        "FACT", "owner", null, 0, "owner", null, null);
  }

  @BeforeEach void setup() {
    models = mock(ModelRepository.class);
    structures = mock(ModelStructureService.class);
    semantic = mock(ProcessApi.class);
    standardQuery = mock(StandardQueryApi.class);
    service = new PhysicalStandardCoverageInspectionService(
        models, structures, semantic, standardQuery,
        new ObjectMapper().findAndRegisterModules());
    model = Model.create("dwd_orders", "订单明细", ModelDialect.MYSQL, null,
        null, "DWD", 22L).withPersisted(50L, "owner", null, null);
    columns = List.of(col(1L, "order_id", 11L, null), col(2L, "status", 12L, null));
    when(models.findById(50L)).thenAnswer(i -> Optional.of(model));
    when(structures.get(50L)).thenAnswer(i -> StructureView.of(model, "dwd_orders", null,
        columns, List.of(), List.of(), null, Map.of()));
    when(semantic.listProcesses(null)).thenReturn(List.of(process("每个订单一行")));
    when(semantic.getField(11L)).thenReturn(field(11L, StandardField.STATUS_ENABLED, "PROCESS"));
    when(semantic.getField(12L)).thenReturn(field(12L, StandardField.STATUS_ENABLED, "DIMENSION"));
    when(standardQuery.get(111L)).thenReturn(typeStandard(111L));
    when(standardQuery.get(112L)).thenReturn(typeStandard(112L));
  }

  @Test void currentAuthorizedDwdCoverageIsReadOnlyAndReproducible() {
    var first = service.inspect(50L);
    assertEquals("COVERED_NOW", first.status());
    assertEquals(2, first.enabledCount());
    assertEquals(0, first.missingCount());
    assertEquals(64, first.evidenceSha256().length());
    assertEquals(64, first.structureSha256().length());
    assertEquals("PROCESS", first.columns().get(0).semanticRole());
    assertEquals("DIMENSION", first.columns().get(0).physicalRole());
    assertTrue(first.limitations().stream().anyMatch(x -> x.contains("PD-010")));
    columns = List.of(col(2L, "status", 12L, null), col(1L, "order_id", 11L, null));
    // Physical column order is part of the owned saved design, not an unordered set.
    assertNotEquals(first.evidenceSha256(), service.inspect(50L).evidenceSha256());
    verify(models, never()).assignSource(anyLong(), anyLong(), any(), any(), any());
  }

  @Test void missingRefOnTechnicalLookingColumnIsNotUnapprovedException() {
    columns = List.of(col(1L, "order_id", 11L, null),
        col(2L, "process_time", null, null));
    var report = service.inspect(50L);
    assertEquals("NEEDS_REVIEW", report.status());
    assertEquals(1, report.missingCount());
    assertEquals("MISSING_STANDARD", report.columns().get(1).status());
    assertTrue(report.columns().get(1).explanation().contains("豁免"));
  }

  @Test void disabledAndAbsentStandardsDoNotCreateFalseCoverage() {
    when(semantic.getField(11L)).thenReturn(field(11L, StandardField.STATUS_DISABLED, "PROCESS"));
    when(semantic.getField(12L)).thenReturn(null);
    var report = service.inspect(50L);
    assertEquals(2, report.invalidCount());
    assertEquals("NEEDS_REVIEW", report.status());
    assertTrue(report.columns().stream().allMatch(c -> "INVALID_STANDARD".equals(c.status())));
    assertEquals(0, report.enabledCount());
  }

  @Test void physicalAggregateIsHumanReviewWithoutSemanticRoleInference() {
    columns = List.of(col(1L, "amount_total", 11L, "SUM"));
    var report = service.inspect(50L);
    assertEquals(1, report.reviewCount());
    assertEquals("AGGREGATE_REVIEW", report.columns().get(0).status());
    assertEquals("PROCESS", report.columns().get(0).semanticRole());
    assertEquals("NEEDS_REVIEW", report.status());
  }

  @Test void missingAndStaleProcessOrGrainAreVisibleNotMadeIntoNewProcesses() {
    model = Model.create("dwd_orders", "订单明细", ModelDialect.MYSQL,
        null, null, "DWD", null).withPersisted(50L, "owner", null, null);
    assertEquals("NEEDS_REVIEW", service.inspect(50L).status());
    verify(semantic, never()).listProcesses(null);
    model = Model.create("dwd_orders", "订单明细", ModelDialect.MYSQL, null,
        null, "DWD", 22L).withPersisted(50L, "owner", null, null);
    when(semantic.listProcesses(null)).thenReturn(List.of());
    assertTrue(service.inspect(50L).findings().stream().anyMatch(x -> x.contains("不可访问")));
    when(semantic.listProcesses(null)).thenReturn(List.of(process("")));
    assertTrue(service.inspect(50L).findings().stream().anyMatch(x -> x.contains("粒度")));
  }

  @Test void scopeAndLayerAndStructureFailuresFailClosedWithoutCatalogReads() {
    when(models.findById(50L)).thenReturn(Optional.empty());
    assertThrows(ModelingException.class, () -> service.inspect(50L));
    verifyNoInteractions(semantic);
    when(models.findById(50L)).thenAnswer(i -> Optional.of(model));
    model = Model.create("ods_orders", "订单", ModelDialect.MYSQL,
        null, null, "ODS", 22L).withPersisted(50L, "owner", null, null);
    assertThrows(ModelingException.class, () -> service.inspect(50L));
    verifyNoInteractions(semantic);
    model = Model.create("dwd_orders", "订单明细", ModelDialect.MYSQL,
        null, null, "DWD", 22L).withPersisted(50L, "owner", null, null);
    when(structures.get(50L)).thenReturn(StructureView.of(
        Model.create("foreign", "外部", ModelDialect.MYSQL, null,
            null, "DWD", 22L).withPersisted(99L, "owner", null, null),
        "foreign", null, columns, List.of(), List.of(), null, Map.of()));
    assertThrows(ModelingException.class, () -> service.inspect(50L));
  }

  @Test void unauthorizedSemanticErrorsMustPropagate() {
    when(semantic.getField(11L)).thenThrow(SecurityException.class);
    assertThrows(SecurityException.class, () -> service.inspect(50L));
  }

  @Test void duplicatePhysicalIdentitiesCannotAppearValid() {
    columns = List.of(col(1L, "order_id", 11L, null),
        col(1L, "order_id_dup", 12L, null));
    assertThrows(ModelingException.class, () -> service.inspect(50L));
    columns = List.of(col(1L, "ORDER_ID", 11L, null),
        col(2L, "order_id", 12L, null));
    assertThrows(ModelingException.class, () -> service.inspect(50L));
  }

  @Test void dimWithAvailableStandardsCanBeInspectedWithoutPublishMutation() {
    model = Model.create("dim_buyer", "买家维度", ModelDialect.MYSQL,
        null, null, "DIM", 22L).withPersisted(50L, "owner", null, null)
        .withPublishState(ModelStatus.PUBLISHED, 20L, 2);
    assertEquals("COVERED_NOW", service.inspect(50L).status());
    verify(models, never()).assignImportLineage(anyLong(), anyString(), any(), anyString());
  }
  @Test void dwsCoverageIsReadOnlyAndDoesNotPretendToValidateMetricOrGrain() {
    model = Model.create("dws_order_daily", "订单日汇总", ModelDialect.MYSQL,
        null, null, "DWS", null).withPersisted(50L, "owner", null, null);
    var report = service.inspect(50L);
    assertEquals("COVERED_NOW", report.status());
    assertNull(report.processId());
    assertTrue(report.limitations().stream().anyMatch(x -> x.contains("MetricVersionRef")));
    assertTrue(report.limitations().stream().anyMatch(x -> x.contains("DWS 粒度")));
    verify(semantic, never()).listProcesses(null);
    verify(models, never()).assignSource(anyLong(), anyLong(), any(), any(), any());
  }

  @Test void adsCoverageIsReadOnlyAndDoesNotPretendToValidateConsumerContract() {
    model = Model.create("ads_order_board", "订单看板", ModelDialect.MYSQL,
        null, null, "ADS", null).withPersisted(50L, "owner", null, null);
    var report = service.inspect(50L);
    assertEquals("COVERED_NOW", report.status());
    assertNull(report.processId());
    assertTrue(report.limitations().stream().anyMatch(x -> x.contains("ADS 消费场景")));
    assertTrue(report.limitations().stream().anyMatch(x -> x.contains("Usage Evidence")));
    verify(semantic, never()).listProcesses(null);
    verify(models, never()).assignSource(anyLong(), anyLong(), any(), any(), any());
  }



  @Test void securityStandardEvidenceDoesNotExposeMaskingRuleText() {
    StandardField dimension = new StandardField(12L, "order_12", "订单 #12",
        "DIMENSION", StandardField.STATUS_ENABLED, "BIGINT", 112L, null, null,
        null, 113L, null, StandardField.SOURCE_MANUAL, 1, false, "owner", null, null);
    when(semantic.getField(12L)).thenReturn(dimension);
    when(standardQuery.get(113L)).thenReturn(securityStandard(113L));
    var report = service.inspect(50L);
    var evidence = report.columns().stream()
        .filter(c -> Long.valueOf(12L).equals(c.stdFieldId())).findFirst().orElseThrow();
    assertEquals("L2", evidence.dataStandards().securityStandard().definition());
    assertFalse(evidence.dataStandards().securityStandard().definition().contains("secret-mask-rule"));
  }

  @Test void linkedStandardVersionChangesTheEvidenceFingerprint() {
    var first = service.inspect(50L);
    when(standardQuery.get(111L)).thenReturn(typeStandard(111L).withVersion(2, null));
    var second = service.inspect(50L);
    assertNotEquals(first.evidenceSha256(), second.evidenceSha256());
    var orderId = second.columns().stream()
        .filter(c -> Long.valueOf(11L).equals(c.stdFieldId())).findFirst().orElseThrow();
    assertEquals(2, orderId.dataStandards().typeStandard().version());
  }

  @Test void missingRequiredTypeStandardIsReportedWithoutAutomaticRepair() {
    StandardField missingType = new StandardField(11L, "order_11", "订单 #11",
        "PROCESS", StandardField.STATUS_ENABLED, "BIGINT", null, null, null,
        null, null, null, StandardField.SOURCE_MANUAL, 1, false, "owner", null, null);
    when(semantic.getField(11L)).thenReturn(missingType);
    var report = service.inspect(50L);
    var evidence = report.columns().stream()
        .filter(c -> Long.valueOf(11L).equals(c.stdFieldId())).findFirst().orElseThrow();
    assertEquals(1, report.missingDataStandardCount());
    assertEquals("MISSING_REQUIRED_REFERENCE", evidence.dataStandards().status());
    assertTrue(report.findings().stream().anyMatch(x -> x.contains("类型标准引用")));
    verify(models, never()).assignSource(anyLong(), anyLong(), any(), any(), any());
  }

  @Test void metricFieldRequiresUnitAndAllowsOptionalCaliberToRemainUnconfigured() {
    StandardField metric = new StandardField(11L, "order_amount", "订单金额",
        "METRIC", StandardField.STATUS_ENABLED, "BIGINT", 111L, null, null,
        null, null, "金额度量", StandardField.SOURCE_MANUAL, 1, false, "owner", null, null);
    when(semantic.getField(11L)).thenReturn(metric);
    var report = service.inspect(50L);
    var evidence = report.columns().stream()
        .filter(c -> Long.valueOf(11L).equals(c.stdFieldId())).findFirst().orElseThrow();
    assertEquals(1, report.missingDataStandardCount());
    assertEquals("MISSING_REQUIRED_REFERENCE", evidence.dataStandards().status());
    assertEquals("MISSING_REQUIRED_REFERENCE", evidence.dataStandards().unitStandard().evidenceStatus());
    assertEquals("NOT_CONFIGURED", evidence.dataStandards().caliberStandard().evidenceStatus());
  }

  @Test void disabledTypeStandardAndUnavailableCodeSetAreReportedAsInvalid() {
    when(standardQuery.get(111L)).thenReturn(typeStandard(111L).withStatus(StandardStatus.DISABLED, null));
    StandardField dimensionWithCodeSet = new StandardField(12L, "order_12", "订单 #12",
        "DIMENSION", StandardField.STATUS_ENABLED, "BIGINT", 112L, null, null,
        "order_status", null, null, StandardField.SOURCE_MANUAL, 1, false, "owner", null, null);
    when(semantic.getField(12L)).thenReturn(dimensionWithCodeSet);
    when(standardQuery.existsCodeSet("order_status")).thenReturn(false);
    var report = service.inspect(50L);
    assertEquals(2, report.invalidDataStandardCount());
    assertTrue(report.columns().stream().allMatch(c ->
        "INVALID_REFERENCE".equals(c.dataStandards().status())));
    assertTrue(report.findings().stream().anyMatch(x -> x.contains("码集不存在或已停用")));
  }

}
