package io.yak.ops.business.modeling.mapping;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.approval.api.ApprovalApi;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.approval.api.ApprovalInstanceView;
import io.yak.ops.business.modeling.dao.mapper.ModelingMappingRevisionMapper;
import io.yak.ops.business.modeling.dao.mapper.ModelingMappingSetMapper;
import io.yak.ops.business.modeling.dao.model.ModelingMappingRevisionPO;
import io.yak.ops.business.modeling.dao.model.ModelingMappingSetPO;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.logical.LogicalPhysicalHandoffPreviewService;
import io.yak.ops.business.modeling.logical.LogicalPhysicalHandoffReviewService;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DwdMappingDraftServiceTest {
  static final String LS = "a".repeat(64);
  static final String PS = "b".repeat(64);
  final CurrentProject project = mock(CurrentProject.class);
  final ApprovalApi approvals = mock(ApprovalApi.class);
  final ModelRepository models = mock(ModelRepository.class);
  final ModelingMappingSetMapper sets = mock(ModelingMappingSetMapper.class);
  final ModelingMappingRevisionMapper revisions = mock(ModelingMappingRevisionMapper.class);
  final LogicalPhysicalHandoffPreviewService previews = mock(LogicalPhysicalHandoffPreviewService.class);
  final LogicalPhysicalHandoffReviewService reviews = mock(LogicalPhysicalHandoffReviewService.class);
  final ObjectMapper json = new ObjectMapper();
  DwdMappingDraftService service;

  private DwdMappingDraftService.SaveRequest request(long expected) {
    return new DwdMappingDraftService.SaveRequest(77L, 2, LS, PS, expected,
        List.of(new DwdMappingDraftService.RuleInput(100L, 10L, "DIRECT")));
  }

  @BeforeEach void setup() {
    service = new DwdMappingDraftService(project, models, approvals, sets, revisions, previews, reviews, json);
    when(project.requireProjectId()).thenReturn(1L);
    when(approvals.find(ApprovalFlowCodes.MODEL_PUBLISH, "MODEL", "50")).thenReturn(null);
    Model m = mock(Model.class);
    when(m.layerCode()).thenReturn("DWD");
    when(m.status()).thenReturn(ModelStatus.DRAFT);
    when(models.findById(50L)).thenReturn(Optional.of(m));
    when(models.findByIdForUpdate(50L)).thenReturn(Optional.of(m));
    when(reviews.validate(eq(77L), eq(2), eq(50L), any())).thenReturn(
        new LogicalPhysicalHandoffReviewService.ReviewResult(
            77L, 2, 50L, LS, PS, true, true, List.of(),
            List.of(new LogicalPhysicalHandoffReviewService.ReviewRow(
                10L, 100L, 11L, "order_id", "订单标识")), "REVIEWABLE_ONLY"));
  }

  @Test void rejectsDraftEditWhilePublishApprovalIsPending() {
    when(approvals.find(ApprovalFlowCodes.MODEL_PUBLISH, "MODEL", "50"))
        .thenReturn(new ApprovalInstanceView(88L, ApprovalFlowCodes.MODEL_PUBLISH, "模型发布",
            "MODEL", "50", "DWD 设计发布", "{}", "applicant", "PENDING",
            1, null, null));
    assertThrows(IllegalStateException.class, () -> service.save(50L, request(0), "operator"));
    verifyNoInteractions(reviews, sets, revisions);
  }

  @Test void persistedRootStoresColumnNameAndNotTransientDatabaseColumnId() {
    when(sets.insert(any(ModelingMappingSetPO.class))).thenAnswer(inv -> {
      ModelingMappingSetPO saved = inv.getArgument(0);
      saved.setId(501L);
      return 1;
    });
    var out = service.save(50L, request(0), "operator");
    assertEquals(501L, out.mappingSetId());
    assertEquals(1L, out.draftRevision());
    assertEquals("order_id", out.rules().get(0).targetColumn());
    var cap = ArgumentCaptor.forClass(ModelingMappingSetPO.class);
    verify(sets).insert(cap.capture());
    assertEquals(1L, cap.getValue().getProjectId());
    assertFalse(cap.getValue().getMappingJson().contains("physicalColumnId"));
    assertTrue(cap.getValue().getMappingJson().contains("order_id"));
    verify(reviews).validate(eq(77L), eq(2), eq(50L), any());
    verifyNoInteractions(revisions);
  }

  @Test void staleRootRevisionRejectsWriteWithoutOverwriting() {
    ModelingMappingSetPO existing = root();
    existing.setDraftRevision(8L);
    when(sets.selectOne(any())).thenReturn(existing);
    assertThrows(IllegalStateException.class, () -> service.save(50L, request(7), "operator"));
    verify(sets, never()).updateById(any(ModelingMappingSetPO.class));
    verifyNoInteractions(revisions);
  }

  @Test void deniesUnreviewedOrUnsupportedMappingEvenWhenClientSuppliesIds() {
    var unsupported = new DwdMappingDraftService.SaveRequest(77L, 2, LS, PS, 0L,
        List.of(new DwdMappingDraftService.RuleInput(100L, 10L, "DERIVED")));
    assertThrows(IllegalArgumentException.class, () -> service.save(50L, unsupported, "operator"));
    verifyNoInteractions(reviews, sets, revisions);
  }

  @Test void refusesToSaveIfOwnerReviewFlagsDrift() {
    when(reviews.validate(eq(77L), eq(2), eq(50L), any())).thenReturn(
        new LogicalPhysicalHandoffReviewService.ReviewResult(
            77L, 2, 50L, LS, PS, false, false, List.of("stale"), List.of(), "BLOCKED"));
    assertThrows(IllegalStateException.class, () -> service.save(50L, request(0), "operator"));
    verifyNoInteractions(sets, revisions);
  }

  @Test void freezeRechecksSourceAndAppendsImmutableSnapshot() {
    ModelingMappingSetPO existing = root();
    when(sets.selectOne(any())).thenReturn(existing);
    when(previews.preview(77L, 2, 50L)).thenReturn(new LogicalPhysicalHandoffPreviewService.Preview(
        77L, 2, 50L, "DWD", "DRAFT", true, List.of(),
        List.of(new LogicalPhysicalHandoffPreviewService.ColumnCheck(
            "order_id", 11L, "订单", "订单标识", "CANDIDATE_ONLY", "candidate",
            505L, 10L, 10L)), LS, PS));
    when(reviews.validate(eq(77L), eq(2), eq(50L), any())).thenAnswer(inv -> {
      LogicalPhysicalHandoffReviewService.ReviewRequest req = inv.getArgument(3);
      assertEquals(505L, req.selections().get(0).physicalColumnId());
      return new LogicalPhysicalHandoffReviewService.ReviewResult(
          77L, 2, 50L, LS, PS, true, true, List.of(),
          List.of(new LogicalPhysicalHandoffReviewService.ReviewRow(
              10L, 505L, 11L, "order_id", "订单标识")), "REVIEWABLE_ONLY");
    });
    when(revisions.selectList(any())).thenReturn(List.of());
    when(revisions.insert(any(ModelingMappingRevisionPO.class))).thenAnswer(inv -> {
      ModelingMappingRevisionPO saved = inv.getArgument(0);
      saved.setId(900L);
      return 1;
    });
    var frozen = service.freeze(50L, 3L, "operator");
    assertEquals(900L, frozen.mappingRevisionId());
    assertEquals(1, frozen.revisionNo());
    assertEquals("FROZEN_DESIGN_ONLY", frozen.state());
    var captor = ArgumentCaptor.forClass(ModelingMappingRevisionPO.class);
    verify(revisions).insert(captor.capture());
    assertEquals(64, captor.getValue().getContentSha().length());
    assertEquals(1L, captor.getValue().getProjectId());
    assertFalse(captor.getValue().getFrozenJson().contains("physicalColumnId"));
  }

  @Test void repeatedFreezeOfIdenticalSavedDraftReturnsExistingVersion() {
    ModelingMappingSetPO existing = root();
    when(sets.selectOne(any())).thenReturn(existing);
    when(previews.preview(77L, 2, 50L)).thenReturn(new LogicalPhysicalHandoffPreviewService.Preview(
        77L, 2, 50L, "DWD", "DRAFT", true, List.of(),
        List.of(new LogicalPhysicalHandoffPreviewService.ColumnCheck(
            "order_id", 11L, "订单", "订单标识", "CANDIDATE_ONLY", "candidate",
            800L, 10L, 10L)), LS, PS));
    when(reviews.validate(eq(77L), eq(2), eq(50L), any())).thenReturn(
        new LogicalPhysicalHandoffReviewService.ReviewResult(
            77L, 2, 50L, LS, PS, true, true, List.of(),
            List.of(new LogicalPhysicalHandoffReviewService.ReviewRow(
                10L, 800L, 11L, "order_id", "订单标识")), "REVIEWABLE_ONLY"));
    ModelingMappingRevisionPO old = new ModelingMappingRevisionPO();
    old.setId(700L);
    old.setProjectId(1L);
    old.setMappingSetId(501L);
    old.setRevisionNo(2);
    old.setDraftRevision(3L);
    old.setContentSha(io.yak.ops.common.version.VersionDigests.sha256Hex(
        LS + ":" + PS + ":" + existing.getMappingJson()));
    when(revisions.selectList(any())).thenReturn(List.of(old));
    var result = service.freeze(50L, 3L, "operator");
    assertEquals(700L, result.mappingRevisionId());
    assertEquals(2, result.revisionNo());
    verify(revisions, never()).insert(any(ModelingMappingRevisionPO.class));
  }

  @Test void freezeRefusesStalePhysicalSnapshotAndMakesNoRevision() {
    when(sets.selectOne(any())).thenReturn(root());
    when(previews.preview(77L, 2, 50L)).thenReturn(new LogicalPhysicalHandoffPreviewService.Preview(
        77L, 2, 50L, "DWD", "DRAFT", true, List.of(), List.of(), LS, "f".repeat(64)));
    assertThrows(IllegalStateException.class, () -> service.freeze(50L, 3L, "operator"));
    verifyNoInteractions(revisions);
  }

  @Test void publishedMappingEvidenceRefusesOlderFrozenRevision() {
    when(sets.selectOne(any())).thenReturn(root());
    ModelingMappingRevisionPO prior = new ModelingMappingRevisionPO();
    prior.setId(900L);
    prior.setDraftRevision(2L);
    when(revisions.selectOne(any())).thenReturn(prior);
    assertThrows(IllegalStateException.class,
        () -> service.requireFrozenForPublication(50L, 900L));
    verify(previews, never()).preview(anyLong(), anyInt(), anyLong());
    verify(revisions, never()).insert(any(ModelingMappingRevisionPO.class));
  }

  @Test void publishedMappingEvidenceMustMatchRevalidatedCurrentFreeze() {
    ModelingMappingSetPO current = root();
    when(sets.selectOne(any())).thenReturn(current);
    ModelingMappingRevisionPO selected = new ModelingMappingRevisionPO();
    selected.setId(900L);
    selected.setProjectId(1L);
    selected.setMappingSetId(501L);
    selected.setRevisionNo(1);
    selected.setDraftRevision(3L);
    selected.setLogicalSnapshotSha(LS);
    selected.setPhysicalStructureSha(PS);
    selected.setFrozenJson(current.getMappingJson());
    selected.setContentSha(io.yak.ops.common.version.VersionDigests.sha256Hex(
        LS + ":" + PS + ":" + current.getMappingJson()));
    when(revisions.selectOne(any())).thenReturn(selected);
    when(revisions.selectList(any())).thenReturn(List.of(selected));
    when(previews.preview(77L, 2, 50L)).thenReturn(new LogicalPhysicalHandoffPreviewService.Preview(
        77L, 2, 50L, "DWD", "DRAFT", true, List.of(),
        List.of(new LogicalPhysicalHandoffPreviewService.ColumnCheck(
            "order_id", 11L, "订单", "订单标识", "CANDIDATE_ONLY", "candidate",
            100L, 10L, 10L)), LS, PS));
    var evidence = service.requireFrozenForPublication(50L, 900L);
    assertEquals(900L, evidence.mappingRevisionId());
    assertEquals(77L, evidence.logicalModelId());
    assertEquals(PS, evidence.physicalStructureSha256());
    verify(revisions, never()).insert(any(ModelingMappingRevisionPO.class));
  }

  @Test void immutableHistoryReadUsesFrozenJsonAndRejectsContentCorruption() {
    ModelingMappingSetPO current = root();
    when(sets.selectOne(any())).thenReturn(current);
    ModelingMappingRevisionPO selected = new ModelingMappingRevisionPO();
    selected.setId(900L);
    selected.setProjectId(1L);
    selected.setMappingSetId(501L);
    selected.setRevisionNo(1);
    selected.setDraftRevision(3L);
    selected.setLogicalSnapshotSha(LS);
    selected.setPhysicalStructureSha(PS);
    selected.setFrozenJson(current.getMappingJson());
    selected.setContentSha("f".repeat(64));
    when(revisions.selectOne(any())).thenReturn(selected);
    assertThrows(IllegalStateException.class, () -> service.revisionDetail(50L, 900L));
  }

  @Test void projectOrWrongModelIsNotSilentlyInterpretedAsEmptyRoot() {
    when(models.findById(50L)).thenReturn(Optional.empty());
    assertThrows(IllegalArgumentException.class, () -> service.get(50L));
    verifyNoInteractions(sets, revisions);
  }

  private static ModelingMappingSetPO root() {
    ModelingMappingSetPO existing = new ModelingMappingSetPO();
    existing.setId(501L);
    existing.setProjectId(1L);
    existing.setPhysicalModelId(50L);
    existing.setLogicalModelId(77L);
    existing.setLogicalVersionNo(2);
    existing.setLogicalSnapshotSha(LS);
    existing.setPhysicalStructureSha(PS);
    existing.setDraftRevision(3L);
    existing.setMappingJson("[{\"targetColumn\":\"order_id\",\"logicalAttributeId\":10,\"stdFieldId\":11,\"kind\":\"DIRECT\"}]");
    return existing;
  }
}
