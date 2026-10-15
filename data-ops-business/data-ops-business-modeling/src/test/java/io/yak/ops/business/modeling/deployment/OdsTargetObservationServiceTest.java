package io.yak.ops.business.modeling.deployment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.modeling.dao.mapper.OdsTargetObservationMapper;
import io.yak.ops.business.modeling.dao.model.OdsTargetObservationPO;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelVersion;
import io.yak.ops.business.modeling.exception.ModelingException;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.version.ModelVersionService;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.security.ActionAuthorization;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OdsTargetObservationServiceTest {
  private CurrentProject currentProject;
  private ModelRepository models;
  private ModelVersionService versions;
  private OdsPhysicalTargetInspectionService inspector;
  private OdsTargetObservationMapper mapper;
  private OdsTargetObservationService service;
  private Model model;

  @BeforeEach void setup() {
    currentProject = mock(CurrentProject.class);
    models = mock(ModelRepository.class);
    versions = mock(ModelVersionService.class);
    inspector = mock(OdsPhysicalTargetInspectionService.class);
    mapper = mock(OdsTargetObservationMapper.class);
    service = new OdsTargetObservationService(currentProject, mock(ActionAuthorization.class),
        models, versions, inspector, mapper);
    model = mock(Model.class);
    when(currentProject.requireProjectId()).thenReturn(17L);
    when(models.findByIdForUpdate(50L)).thenReturn(Optional.of(model));
    when(models.findById(50L)).thenReturn(Optional.of(model));
    when(model.layerCode()).thenReturn("ODS");
    when(model.latestVersionNo()).thenReturn(2);
    when(model.publishedVersionId()).thenReturn(88L);
    var frozen = new ModelVersion(88L, 50L, 2, "{}", "{}", 1,
        "b".repeat(64), "publisher", null);
    when(versions.getVersionDetail(50L, 2))
        .thenReturn(new ModelVersionService.VersionDetailView(frozen, null, Map.of()));
    when(inspector.inspect(50L, 2, 9L, "warehouse", "ods")).thenReturn(
        new OdsPhysicalTargetInspectionService.Report(
            50L, 2, 88L, 9L, "warehouse", "ods", "ods_order_detail",
            "COLUMN_MATCH_NEEDS_DDL_REVIEW", 1, 1, List.of(),
            List.of("Catalog 只是当前观察"), "a".repeat(64)));
    when(mapper.insert(any(OdsTargetObservationPO.class))).thenAnswer(invocation -> {
      OdsTargetObservationPO po = invocation.getArgument(0);
      po.setId(777L);
      return 1;
    });
  }

  @Test void createsScopedAppendOnlyObservedEvidenceOnlyAfterFreshOwnerVerification() {
    var view = service.observe(50L, 2, 9L, "warehouse", "ods", "tester");
    assertEquals(777L, view.id());
    assertEquals(17L, view.modelId() == 50L ? 17L : -1L);
    assertEquals("RECENT_CATALOG_OBSERVATION", view.status());
    assertEquals("a".repeat(64), view.observedCatalogSha256());
    assertTrue(view.limitations().stream().anyMatch(x -> x.contains("不证明谁执行了 DDL")));
    var capture = ArgumentCaptor.forClass(OdsTargetObservationPO.class);
    verify(mapper).insert(capture.capture());
    var saved = capture.getValue();
    assertEquals(17L, saved.getProjectId());
    assertEquals(88L, saved.getModelVersionId());
    assertEquals("warehouse", saved.getTargetDatabase());
    assertEquals("ods", saved.getTargetSchema());
    assertEquals("tester", saved.getObservedBy());
    verify(models).findByIdForUpdate(50L);
  }

  @Test void blocksUnverifiableOrHistoricalTargetsWithoutAnyStorageMutation() {
    when(inspector.inspect(50L, 2, 9L, "warehouse", "ods")).thenReturn(
        new OdsPhysicalTargetInspectionService.Report(
            50L, 2, 88L, 9L, "warehouse", "ods", "ods_order_detail",
            "NOT_CHECKABLE", 1, 0, List.of(), List.of("目标无法访问"), null));
    assertThrows(ModelingException.class,
        () -> service.observe(50L, 2, 9L, "warehouse", "ods", "tester"));
    when(model.latestVersionNo()).thenReturn(3);
    assertThrows(ModelingException.class,
        () -> service.observe(50L, 2, 9L, "warehouse", "ods", "tester"));
    verify(mapper, never()).insert(any(OdsTargetObservationPO.class));
  }

  @Test void historiesAreScopedAndVersionChangesMakeOldObservationsStale() {
    var row = new OdsTargetObservationPO();
    row.setId(701L);
    row.setProjectId(17L);
    row.setModelId(50L);
    row.setModelVersionId(88L);
    row.setVersionNo(2);
    row.setTargetDatasourceId(9L);
    row.setTargetDatabase("warehouse");
    row.setTargetSchema("ods");
    row.setTargetTable("ods_order_detail");
    row.setCatalogSha256("a".repeat(64));
    row.setFrozenChecksum("b".repeat(64));
    row.setObservedColumnCount(1);
    row.setObservedBy("tester");
    row.setObservedAt(LocalDateTime.now().minusMinutes(30));
    when(mapper.selectList(any())).thenReturn(List.of(row));
    assertEquals("STALE_CATALOG_OBSERVATION", service.history(50L).get(0).status());
    when(model.publishedVersionId()).thenReturn(91L);
    assertEquals("VERSION_CHANGED", service.history(50L).get(0).status());
    verify(mapper, times(2)).selectList(any());
  }
}
