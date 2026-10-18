package io.yak.ops.business.modeling.version;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.domain.ModelVersion;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.standard.PhysicalStandardCoverageInspectionService;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureFingerprint;
import io.yak.ops.business.modeling.structure.StructureView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Exact version evidence only; it is never a DataDev task creation permit. */
class PhysicalDataDevHandoffReadinessServiceTest {
  private ModelRepository models;
  private ModelVersionService versions;
  private ModelStructureService structures;
  private PhysicalStandardCoverageInspectionService coverage;
  private PhysicalDataDevHandoffReadinessService service;
  private Model model;
  private StructureView frozen;
  private StructureView live;
  private String coverageStatus;

  private static ColumnDefinition column(long id, String name, Long standardId) {
    return new ColumnDefinition(id, name, "BIGINT", null, null, true,
        null, null, null, "order_id".equals(name) ? 1 : 2, null, null,
        null, null, null, null, standardId, null, null, null);
  }
  private StructureView physical(ColumnDefinition... columns) {
    return StructureView.of(model, "dwd_orders", null, List.of(columns),
        List.of("order_id"), List.of(), null, Map.of());
  }
  private PhysicalStandardCoverageInspectionService.Report coverageReport() {
    return new PhysicalStandardCoverageInspectionService.Report(
        50L, "dwd_orders", "DWD", model.status().name(), 22L, "下单",
        coverageStatus, StructureFingerprint.of(live), "e".repeat(64),
        live.columns().size(), live.columns().size(), 0, 0, 0, 0, 0,
        List.of(), List.of(), List.of());
  }
  private ModelVersionService.VersionDetailView version(long modelId, int no, String layer) {
    return new ModelVersionService.VersionDetailView(
        new ModelVersion(90L, modelId, no, "snapshot", "meta",
            frozen.columns().size(), "checksum", "owner", null),
        frozen, Map.of("layerCode", layer));
  }

  @BeforeEach void setup() {
    models = mock(ModelRepository.class);
    versions = mock(ModelVersionService.class);
    structures = mock(ModelStructureService.class);
    coverage = mock(PhysicalStandardCoverageInspectionService.class);
    service = new PhysicalDataDevHandoffReadinessService(models, versions, structures, coverage);
    model = Model.create("dwd_orders", "订单明细", ModelDialect.MYSQL, null,
        null, "DWD", 22L).withPersisted(50L, "owner", null, null)
        .withPublishState(ModelStatus.PUBLISHED, 90L, 2);
    frozen = physical(column(1L, "order_id", 11L), column(2L, "amount", 12L));
    live = physical(column(101L, "order_id", 11L), column(102L, "amount", 12L));
    coverageStatus = "COVERED_NOW";
    when(models.findById(50L)).thenAnswer(i -> Optional.of(model));
    when(versions.getVersionDetail(50L, 2)).thenAnswer(i -> version(50L, 2, "DWD"));
    when(structures.get(50L)).thenAnswer(i -> live);
    when(coverage.inspect(50L)).thenAnswer(i -> coverageReport());
  }

  @Test void exactDesignBasisIsReviewableButNeverGrantsTaskCreation() {
    var report = service.inspect(50L, 2);
    assertEquals(90L, report.version().versionId());
    assertEquals(2, report.version().versionNo());
    assertEquals(64, report.version().frozenStructureSha256().length());
    assertEquals(64, report.inspectionSha256().length());
    assertTrue(report.selectedLatestVersion());
    assertEquals("DESIGN_REVIEWABLE", report.designStatus());
    assertEquals("NOT_AUTHORIZED_FOR_TASK_CREATION", report.dataDevStatus());
    assertTrue(report.blockers().isEmpty());
    assertTrue(report.dependencies().stream().anyMatch(d -> d.code().equals("DATADEV_PROVENANCE")
        && d.status().equals("NOT_APPROVED")));
    assertTrue(report.dependencies().stream().anyMatch(d -> d.code().equals("TARGET_ENVIRONMENT")
        && d.status().equals("NOT_CHECKED")));
    verify(versions).getVersionDetail(50L, 2);
    verify(coverage).inspect(50L);
    verify(structures).get(50L);
    verifyNoMoreInteractions(coverage, structures, versions);
  }

  @Test void frozenVersionUnboundFieldNeverBecomesApprovedException() {
    frozen = physical(column(1L, "order_id", 11L), column(2L, "process_time", null));
    var report = service.inspect(50L, 2);
    assertEquals("DESIGN_NEEDS_REVIEW", report.designStatus());
    assertEquals(1, report.frozenUnboundColumnCount());
    assertTrue(report.blockers().stream().anyMatch(s -> s.contains("技术列豁免")));
  }

  @Test void oldPublishedVersionCannotClaimCurrentPointer() {
    when(versions.getVersionDetail(50L, 1)).thenAnswer(i -> version(50L, 1, "DWD"));
    var report = service.inspect(50L, 1);
    assertFalse(report.selectedLatestVersion());
    assertEquals("DESIGN_NEEDS_REVIEW", report.designStatus());
    assertTrue(report.blockers().stream().anyMatch(s -> s.contains("当前发布指针")));
  }

  @Test void physicalStructureDriftIsExplicitEvenWhenStandardOwnerLooksHealthy() {
    live = physical(column(101L, "order_id", 11L),
        new ColumnDefinition(102L, "amount", "VARCHAR", null, null, true, null,
            null, null, 2, null, null, null, null, null, null, 12L, null, null, null));
    var report = service.inspect(50L, 2);
    assertEquals("DESIGN_NEEDS_REVIEW", report.designStatus());
    assertTrue(report.blockers().stream().anyMatch(s -> s.contains("不同")));
    assertNotEquals(report.version().frozenStructureSha256(), report.currentStructureSha256());
  }

  @Test void currentSemanticStandardGapsRemainBlockersNotPastPublishFacts() {
    coverageStatus = "NEEDS_REVIEW";
    var report = service.inspect(50L, 2);
    assertEquals("DESIGN_NEEDS_REVIEW", report.designStatus());
    assertTrue(report.blockers().stream().anyMatch(s -> s.contains("当前 DWD/DIM")));
  }

  @Test void rejectOldOrDifferentModelVersionBeforeCurrentRead() {
    when(versions.getVersionDetail(50L, 2)).thenAnswer(i -> version(99L, 2, "DWD"));
    assertThrows(ModelingException.class, () -> service.inspect(50L, 2));
    verifyNoInteractions(coverage);
    when(versions.getVersionDetail(50L, 2)).thenAnswer(i -> version(50L, 2, "DIM"));
    assertThrows(ModelingException.class, () -> service.inspect(50L, 2));
    verifyNoInteractions(coverage);
  }

  @Test void rejectsForeignProjectAndOdsBeforeInspectingStandards() {
    when(models.findById(50L)).thenReturn(Optional.empty());
    assertThrows(ModelingException.class, () -> service.inspect(50L, 2));
    verifyNoInteractions(coverage);
    when(models.findById(50L)).thenAnswer(i -> Optional.of(model));
    model = Model.create("ods_orders", "订单来源", ModelDialect.MYSQL,
        null, null, "ODS", 22L).withPersisted(50L, "owner", null, null);
    assertThrows(ModelingException.class, () -> service.inspect(50L, 2));
    verifyNoInteractions(coverage);
  }

  @Test void refusesStaleOrCrossModelStandardEvidenceAndPropagatesPermissionFailure() {
    when(coverage.inspect(50L)).thenReturn(new PhysicalStandardCoverageInspectionService.Report(
        50L, "dwd_orders", "DWD", "PUBLISHED", 22L, "下单",
        "COVERED_NOW", "wrong-hash", "a".repeat(64), 2, 2, 0, 0, 0, 0, 0,
        List.of(), List.of(), List.of()));
    assertThrows(ModelingException.class, () -> service.inspect(50L, 2));
    when(coverage.inspect(50L)).thenThrow(SecurityException.class);
    assertThrows(SecurityException.class, () -> service.inspect(50L, 2));
  }

  @Test void refusesDuplicateFrozenOrCurrentColumns() {
    frozen = physical(column(1L, "order_id", 11L), column(2L, "ORDER_ID", 12L));
    assertThrows(ModelingException.class, () -> service.inspect(50L, 2));
    frozen = physical(column(1L, "order_id", 11L), column(2L, "amount", 12L));
    live = physical(column(1L, "order_id", 11L), column(2L, "ORDER_ID", 12L));
    assertThrows(ModelingException.class, () -> service.inspect(50L, 2));
  }
}
