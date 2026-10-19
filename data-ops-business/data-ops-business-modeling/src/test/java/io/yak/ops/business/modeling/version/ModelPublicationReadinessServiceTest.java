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
    // Every physical column has a StandardField in a true VALID batch. The
    // default fixture's technical column has no standard and is blocked.
    StructureView v = saved("DIM");
    when(structures.getForUpdate(701L)).thenReturn(new StructureView(
        v.modelId(), v.modelCode(), v.modelName(), v.dialect(), v.status(),
        v.modelDescription(), v.tableName(), v.tableComment(),
        v.columns().subList(0, 1), v.primaryKey(), v.indexes(), v.partition(),
        v.tableProperties()));
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
  void sameCountButForeignColumnEvidenceCannotBePresentedAsValid() {
    mockModel("DWD");
    when(semantic.evaluate(any(BatchRequest.class))).thenAnswer(invocation -> {
      BatchRequest request = invocation.getArgument(0);
      return new BatchResult(Verdict.BLOCKED, request.projectId(), request.modelId(),
          request.layerCode(), request.physicalStructureSha256(), 41L, null, null,
          List.of(
              new ColumnEvidence("someone_else", "BIGINT", 11L,
                  "DIMENSION", "VALID", null, List.of(), null, null),
              new ColumnEvidence("etl_time", "TIMESTAMP", null,
                  "PROCESS", "BLOCKED", null, List.of(), null, "MISSING_STANDARD")),
          List.of("MISSING_STANDARD"));
    });
    var report = preview.inspect(701L);
    assertThat(report.standardsVerdict()).isEqualTo(Verdict.UNKNOWN);
    assertThat(report.columns()).isEmpty();
    assertThat(report.authorizedForPublication()).isFalse();
  }

  @Test
  void providerCannotSubstituteAnotherSemanticRoleForSamePhysicalColumn() {
    mockModel("DWD");
    when(semantic.evaluate(any(BatchRequest.class))).thenAnswer(invocation -> {
      BatchRequest request = invocation.getArgument(0);
      return new BatchResult(Verdict.BLOCKED, request.projectId(), request.modelId(),
          request.layerCode(), request.physicalStructureSha256(), 41L, null, null,
          List.of(
              new ColumnEvidence("order_id", "BIGINT", 11L,
                  "METRIC", "BLOCKED", null, List.of(), null, "FIELD_ROLE_MISMATCH"),
              new ColumnEvidence("etl_time", "TIMESTAMP", null,
                  "PROCESS", "BLOCKED", null, List.of(), null, "MISSING_STANDARD")),
          List.of("FIELD_ROLE_MISMATCH", "MISSING_STANDARD"));
    });
    var report = preview.inspect(701L);
    assertThat(report.standardsVerdict()).isEqualTo(Verdict.UNKNOWN);
    assertThat(report.columns()).isEmpty();
    assertThat(report.authorizationStatus()).isEqualTo("EVIDENCE_UNAVAILABLE");
  }

  @Test
  void providerCannotAdvertiseValidStandardsWithMissingRevisionOrDigest() {
    mockModel("DIM");
    StructureView fixture = saved("DIM");
    when(structures.getForUpdate(701L)).thenReturn(new StructureView(
        fixture.modelId(), fixture.modelCode(), fixture.modelName(), fixture.dialect(),
        fixture.status(), fixture.modelDescription(), fixture.tableName(),
        fixture.tableComment(), fixture.columns().subList(0, 1),
        fixture.primaryKey(), fixture.indexes(), fixture.partition(),
        fixture.tableProperties()));
    // The SPI constructor itself rejects malformed VALID results. That
    // provider exception must become UNKNOWN, not a green preview.
    when(semantic.evaluate(any(BatchRequest.class))).thenAnswer(invocation -> {
      BatchRequest request = invocation.getArgument(0);
      return new BatchResult(Verdict.VALID, request.projectId(), request.modelId(),
          request.layerCode(), request.physicalStructureSha256(), null, null, null,
          List.of(new ColumnEvidence("order_id", "BIGINT", 11L,
              "DIMENSION", "VALID", null, List.of(), null, null)), List.of());
    });
    var report = preview.inspect(701L);
    assertThat(report.standardsVerdict()).isEqualTo(Verdict.UNKNOWN);
    assertThat(report.authorizedForPublication()).isFalse();
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
  void dwdReleaseConfirmationShowsExactLogicalVersionAndFrozenMappingIdentity() {
    mockModel("DWD");
    var mappings = mock(io.yak.ops.business.modeling.mapping.DwdMappingDraftService.class);
    var draft = new io.yak.ops.business.modeling.mapping.DwdMappingDraftService.Draft(
        31L, 701L, 21L, 4, "c".repeat(64), "d".repeat(64),
        8L, List.of(), "DRAFT_SAVED_NOT_PUBLISHED");
    var frozen = new io.yak.ops.business.modeling.mapping.DwdMappingDraftService.Frozen(
        41L, 31L, 3, "e".repeat(64), 8L, "FROZEN_DESIGN_ONLY");
    var historical = new io.yak.ops.business.modeling.mapping.DwdMappingDraftService
        .HistoricalRevision(41L, 31L, 3, 21L, 4,
            "c".repeat(64), "d".repeat(64), "e".repeat(64), List.of());
    when(mappings.get(701L)).thenReturn(draft);
    when(mappings.revisions(701L)).thenReturn(List.of(frozen));
    when(mappings.revisionDetail(701L, 41L)).thenReturn(historical);
    preview = new ModelPublicationReadinessService(
        models, structures, project, semantic, mappings);
    when(semantic.evaluate(any(BatchRequest.class))).thenAnswer(invocation -> {
      BatchRequest req = invocation.getArgument(0);
      return new BatchResult(Verdict.BLOCKED, req.projectId(), req.modelId(),
          req.layerCode(), req.physicalStructureSha256(), 11L, null, null,
          List.of(new ColumnEvidence("order_id", "BIGINT", 11L,
              "DIMENSION", "VALID", null, List.of(), null, null),
              new ColumnEvidence("etl_time", "TIMESTAMP", null,
                  "PROCESS", "BLOCKED", null, List.of(), null, "MISSING_STANDARD")),
          List.of("MISSING_STANDARD"));
    });
    var result = preview.inspect(701L);
    assertThat(result.mapping()).isNotNull();
    assertThat(result.mapping().status()).isEqualTo("FROZEN_SAVED_DRAFT");
    assertThat(result.mapping().logicalModelId()).isEqualTo(21L);
    assertThat(result.mapping().logicalVersionNo()).isEqualTo(4);
    assertThat(result.mapping().mappingRevisionId()).isEqualTo(41L);
    assertThat(result.authorizedForPublication()).isFalse();
  }

  @Test
  void dwdChangedSavedDraftCannotReuseOldFrozenMappingAsCurrent() {
    mockModel("DWD");
    var mappings = mock(io.yak.ops.business.modeling.mapping.DwdMappingDraftService.class);
    var draft = new io.yak.ops.business.modeling.mapping.DwdMappingDraftService.Draft(
        31L, 701L, 21L, 4, "c".repeat(64), "d".repeat(64),
        9L, List.of(), "DRAFT_SAVED_NOT_PUBLISHED");
    var frozen = new io.yak.ops.business.modeling.mapping.DwdMappingDraftService.Frozen(
        41L, 31L, 3, "e".repeat(64), 8L, "FROZEN_DESIGN_ONLY");
    var historical = new io.yak.ops.business.modeling.mapping.DwdMappingDraftService
        .HistoricalRevision(41L, 31L, 3, 21L, 4,
            "c".repeat(64), "d".repeat(64), "e".repeat(64), List.of());
    when(mappings.get(701L)).thenReturn(draft);
    when(mappings.revisions(701L)).thenReturn(List.of(frozen));
    when(mappings.revisionDetail(701L, 41L)).thenReturn(historical);
    preview = new ModelPublicationReadinessService(
        models, structures, project, semantic, mappings);
    when(semantic.evaluate(any(BatchRequest.class))).thenAnswer(invocation -> {
      BatchRequest req = invocation.getArgument(0);
      return new BatchResult(Verdict.BLOCKED, req.projectId(), req.modelId(),
          req.layerCode(), req.physicalStructureSha256(), 11L, null, null,
          List.of(new ColumnEvidence("order_id", "BIGINT", 11L,
              "DIMENSION", "VALID", null, List.of(), null, null),
              new ColumnEvidence("etl_time", "TIMESTAMP", null,
                  "PROCESS", "BLOCKED", null, List.of(), null, "MISSING_STANDARD")),
          List.of("MISSING_STANDARD"));
    });
    var result = preview.inspect(701L);
    assertThat(result.mapping()).isNotNull();
    assertThat(result.mapping().status()).isEqualTo("STALE");
    assertThat(result.mapping().mappingRevisionId()).isEqualTo(41L);
    assertThat(result.mapping().finding()).contains("重新核验");
    assertThat(result.authorizedForPublication()).isFalse();
  }

  @Test
  void odsCannotEnterNonOdsPublicationEvidenceFlow() {
    mockModel("ODS");
    assertThrows(IllegalArgumentException.class, () -> preview.inspect(701L));
    verify(structures, never()).getForUpdate(701L);
    verify(semantic, never()).evaluate(any(BatchRequest.class));
  }
}
