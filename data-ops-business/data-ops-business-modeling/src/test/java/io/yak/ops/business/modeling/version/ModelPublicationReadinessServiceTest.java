package io.yak.ops.business.modeling.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.structure.ModelStructureService;
import io.yak.ops.business.modeling.structure.StructureView;
import io.yak.ops.business.semantic.api.PublicationStandardEvidenceApi;
import io.yak.ops.business.semantic.api.PublicationStandardEvidenceApi.BatchRequest;
import io.yak.ops.business.semantic.api.PublicationStandardEvidenceApi.BatchResult;
import io.yak.ops.business.semantic.api.PublicationStandardEvidenceApi.ColumnEvidence;
import io.yak.ops.business.semantic.api.PublicationStandardEvidenceApi.Verdict;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Real server-side publish-preview entry, not a release-authorizing API.
 * The contract must not silently reuse catalog validity as Security authority.
 */
class ModelPublicationReadinessServiceTest {
  private ModelRepository models;
  private ModelStructureService structures;
  private CurrentProject project;
  private PublicationStandardEvidenceApi semantic;
  private ModelPublicationReadinessService preview;

  @BeforeEach
  void setUp() {
    models = mock(ModelRepository.class);
    structures = mock(ModelStructureService.class);
    project = mock(CurrentProject.class);
    semantic = mock(PublicationStandardEvidenceApi.class);
    preview = new ModelPublicationReadinessService(models, structures, project, semantic);
    when(project.requireProjectId()).thenReturn(7L);
  }

  private static StructureView saved(String layer) {
    var columns = List.of(
        new StructureView.ColumnView(1L, "order_id", "BIGINT",
            null, null, false, null, null, null, 1,
            null, null, null, null, null, null, 11L, "DIMENSION", null, null),
        new StructureView.ColumnView(2L, "etl_time", "TIMESTAMP",
            null, null, false, null, null, null, 2,
            null, null, null, null, null, null, null, "PROCESS", null, null));
    return new StructureView(701L, "ods_order_detail", "订单明细", "MYSQL",
        "DRAFT", null, "dwd_order_detail", null, columns, List.of(),
        List.of(), null, Map.of());
  }

  private void mockModel(String layer) {
    var model = mock(Model.class);
    when(model.id()).thenReturn(701L);
    when(model.layerCode()).thenReturn(layer);
    when(models.findByIdForUpdate(701L)).thenReturn(Optional.of(model));
    when(structures.getForUpdate(701L)).thenReturn(saved(layer));
  }

  @Test
  void previewInspectsAllColumnsIncludingTechnicalColumnUnderTrustedProject() {
    mockModel("DWD");
    when(semantic.evaluate(any(BatchRequest.class))).thenAnswer(invocation -> {
      BatchRequest request = invocation.getArgument(0);
      assertThat(request.projectId()).isEqualTo(7L);
      assertThat(request.modelId()).isEqualTo(701L);
      assertThat(request.columns()).hasSize(2);
      assertThat(request.columns().get(0).standardFieldId()).isEqualTo(11L);
      assertThat(request.columns().get(1).standardFieldId()).isNull();
      return new BatchResult(Verdict.BLOCKED, request.projectId(), request.modelId(),
          request.layerCode(), request.physicalStructureSha256(), 41L,
          null, null,
          List.of(
              new ColumnEvidence("order_id", "BIGINT", 11L,
                  "DIMENSION", "VALID", null, List.of(), null, null),
              new ColumnEvidence("etl_time", "TIMESTAMP", null,
                  "PROCESS", "BLOCKED", null, List.of(), null, "MISSING_STANDARD")),
          List.of("MISSING_STANDARD"));
    });
    var result = preview.inspect(701L);
    assertThat(result.standardsVerdict()).isEqualTo(Verdict.BLOCKED);
    assertThat(result.columns()).hasSize(2);
    assertThat(result.findings()).contains("MISSING_STANDARD");
    assertThat(result.authorizedForPublication()).isFalse();
    assertThat(result.authorizationStatus()).isEqualTo("SECURITY_COMMIT_FENCE_NOT_ADMITTED");
    verify(models).findByIdForUpdate(701L);
    verify(structures).getForUpdate(701L);
  }

  @Test
  void evenCatalogValidNeverAuthorizesUnfencedSecurityCommit() {
    mockModel("DIM");
    when(semantic.evaluate(any(BatchRequest.class))).thenAnswer(invocation -> {
      BatchRequest request = invocation.getArgument(0);
      return new BatchResult(Verdict.VALID, request.projectId(), request.modelId(),
          request.layerCode(), request.physicalStructureSha256(), 42L,
          "SEMANTIC-V1", "a".repeat(64),
          request.columns().stream().map(column -> new ColumnEvidence(
              column.columnName(), column.physicalType(), 11L, "DIMENSION",
              "VALID", null, List.of(), null, null)).toList(),
          List.of());
    });
    var report = preview.inspect(701L);
    assertThat(report.standardsVerdict()).isEqualTo(Verdict.VALID);
    assertThat(report.authorizedForPublication()).isFalse();
    assertThat(report.findings()).anyMatch(s -> s.contains("Security"));
  }

  @Test
  void unknownProviderOrForeignScopeCannotProduceReadyPreview() {
    mockModel("ADS");
    when(semantic.evaluate(any(BatchRequest.class))).thenAnswer(invocation -> {
      BatchRequest request = invocation.getArgument(0);
      return new BatchResult(Verdict.VALID, 99L, request.modelId(), request.layerCode(),
          request.physicalStructureSha256(), 42L, "SEMANTIC-V1", "a".repeat(64),
          request.columns().stream().map(column -> new ColumnEvidence(
              column.columnName(), column.physicalType(), 11L, "DIMENSION",
              "VALID", null, List.of(), null, null)).toList(), List.of());
    });
    assertThat(preview.inspect(701L).standardsVerdict()).isEqualTo(Verdict.UNKNOWN);
    assertThat(preview.inspect(701L).authorizationStatus())
        .isEqualTo("EVIDENCE_UNAVAILABLE");
  }

  @Test
  void missingProviderFailsClosed() {
    mockModel("DWS");
    when(semantic.evaluate(any(BatchRequest.class)))
        .thenThrow(new IllegalStateException("Semantic database unavailable"));
    assertThat(preview.inspect(701L).standardsVerdict()).isEqualTo(Verdict.UNKNOWN);
    assertThat(preview.inspect(701L).authorizedForPublication()).isFalse();
  }

  @Test
  void odsCannotEnterNonOdsPublicationEvidenceFlow() {
    mockModel("ODS");
    assertThrows(IllegalArgumentException.class, () -> preview.inspect(701L));
    verify(structures, never()).getForUpdate(701L);
    verify(semantic, never()).evaluate(any(BatchRequest.class));
  }
}
