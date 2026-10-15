package io.yak.ops.business.modeling.deployment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.modeling.dao.mapper.OdsTargetObservationMapper;
import io.yak.ops.business.modeling.dao.model.OdsTargetObservationPO;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.mapping.OdsSourceEvidenceInspectionService;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.security.ActionAuthorization;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelingOdsSyncHandoffVerifierTest {
  private CurrentProject project;
  private ModelRepository models;
  private OdsTargetObservationMapper observations;
  private OdsPhysicalTargetInspectionService inspector;
  private OdsSourceEvidenceInspectionService sourceEvidence;
  private ModelingOdsSyncHandoffVerifier verifier;
  private Model model;
  private OdsTargetObservationPO observation;

  @BeforeEach void setup() {
    project = mock(CurrentProject.class);
    models = mock(ModelRepository.class);
    observations = mock(OdsTargetObservationMapper.class);
    inspector = mock(OdsPhysicalTargetInspectionService.class);
    sourceEvidence = mock(OdsSourceEvidenceInspectionService.class);
    model = mock(Model.class);
    observation = new OdsTargetObservationPO();
    when(project.requireProjectId()).thenReturn(3L);
    when(models.findById(50L)).thenReturn(Optional.of(model));
    when(model.layerCode()).thenReturn("ODS");
    when(model.publishedVersionId()).thenReturn(78L);
    when(model.latestVersionNo()).thenReturn(2);
    when(model.sourceDatasourceId()).thenReturn(10L);
    when(model.sourceDatabase()).thenReturn("trade_db");
    when(model.sourceSchema()).thenReturn("public");
    when(model.sourceTable()).thenReturn("trade_order_detail");
    observation.setId(99L);
    observation.setProjectId(3L);
    observation.setModelId(50L);
    observation.setModelVersionId(78L);
    observation.setVersionNo(2);
    observation.setTargetDatasourceId(20L);
    observation.setTargetDatabase("warehouse");
    observation.setTargetSchema("ods");
    observation.setTargetTable("ods_order_detail");
    observation.setCatalogSha256("a".repeat(64));
    observation.setObservedAt(LocalDateTime.now().minusSeconds(10));
    when(observations.selectById(99L)).thenReturn(observation);
    when(sourceEvidence.inspect(50L)).thenReturn(
        new OdsSourceEvidenceInspectionService.Inspection(
            50L, "PUBLISHED", 10L, "trade_db", "trade_order_detail", "CONSISTENT_NOW",
            "b".repeat(64), 1, 1, 1, List.of(), List.of()));
    when(inspector.inspect(50L, 2, 20L, "warehouse", "ods")).thenReturn(
        new OdsPhysicalTargetInspectionService.Report(
            50L, 2, 78L, 20L, "warehouse", "ods", "ods_order_detail",
            "COLUMN_MATCH_NEEDS_DDL_REVIEW", 1, 1, List.of(), List.of(), "a".repeat(64)));
    verifier = new ModelingOdsSyncHandoffVerifier(project, mock(ActionAuthorization.class),
        models, observations, inspector, sourceEvidence);
  }

  private void verifyExact() {
    verifier.verify(3L, 50L, 2, 99L, 10L, "public.trade_order_detail",
        20L, "ods.ods_order_detail");
  }

  @Test void exactFrozenModelAndLiveTargetAllowProvenanceNotExecution() {
    assertDoesNotThrow(this::verifyExact);
    verify(inspector).inspect(50L, 2, 20L, "warehouse", "ods");
  }

  @Test void rejectCrossProjectAndWrongSourceBeforeAnyTargetLookup() {
    assertThrows(IllegalArgumentException.class, () ->
        verifier.verify(4L, 50L, 2, 99L, 10L, "public.trade_order_detail",
            20L, "ods.ods_order_detail"));
    assertThrows(IllegalArgumentException.class, () ->
        verifier.verify(3L, 50L, 2, 99L, 10L, "archive.trade_order_detail",
            20L, "ods.ods_order_detail"));
    verifyNoInteractions(inspector);
  }

  @Test void rejectHistoricalModelVersionAndCrossSchemaTarget() {
    when(model.latestVersionNo()).thenReturn(3);
    assertThrows(IllegalArgumentException.class, this::verifyExact);
    when(model.latestVersionNo()).thenReturn(2);
    assertThrows(IllegalArgumentException.class, () ->
        verifier.verify(3L, 50L, 2, 99L, 10L, "public.trade_order_detail",
            20L, "other.ods_order_detail"));
    verifyNoInteractions(inspector);
  }

  @Test void rejectExpiredObservationAndChangedLiveCatalog() {
    observation.setObservedAt(LocalDateTime.now().minusMinutes(16));
    assertThrows(IllegalArgumentException.class, this::verifyExact);
    observation.setObservedAt(LocalDateTime.now());
    when(inspector.inspect(50L, 2, 20L, "warehouse", "ods")).thenReturn(
        new OdsPhysicalTargetInspectionService.Report(
            50L, 2, 78L, 20L, "warehouse", "ods", "ods_order_detail",
            "COLUMN_MATCH_NEEDS_DDL_REVIEW", 1, 1, List.of(), List.of(), "c".repeat(64)));
    assertThrows(IllegalArgumentException.class, this::verifyExact);
  }

  @Test void sourceDriftAlwaysBlocksEvenIfDestinationCatalogMatches() {
    when(sourceEvidence.inspect(50L)).thenReturn(
        new OdsSourceEvidenceInspectionService.Inspection(
            50L, "PUBLISHED", 10L, "trade_db", "trade_order_detail", "NEEDS_REVIEW",
            "b".repeat(64), 1, 1, 1, List.of(), List.of()));
    assertThrows(IllegalArgumentException.class, this::verifyExact);
    verifyNoInteractions(inspector);
  }
}
