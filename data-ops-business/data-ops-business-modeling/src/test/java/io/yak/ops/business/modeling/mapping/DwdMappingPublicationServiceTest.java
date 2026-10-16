package io.yak.ops.business.modeling.mapping;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.approval.api.ApprovalApi;
import io.yak.ops.business.approval.api.ApprovalSubmitCommand;
import io.yak.ops.business.modeling.domain.Model;
import io.yak.ops.business.modeling.domain.ModelDialect;
import io.yak.ops.business.modeling.domain.ModelStatus;
import io.yak.ops.business.modeling.domain.ModelVersion;
import io.yak.ops.business.modeling.repository.ModelRepository;
import io.yak.ops.business.modeling.version.ModelVersionService;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DwdMappingPublicationServiceTest {
  final ModelRepository models = mock(ModelRepository.class);
  final DwdMappingDraftService mappings = mock(DwdMappingDraftService.class);
  final ModelVersionService versions = mock(ModelVersionService.class);
  final ApprovalApi approvals = mock(ApprovalApi.class);
  final DwdMappingPublicationService service =
      new DwdMappingPublicationService(models, mappings, versions, approvals,
          new ObjectMapper());
  final DwdMappingDraftService.PublicationEvidence evidence =
      new DwdMappingDraftService.PublicationEvidence(
          901L, 501L, 77L, 2, "a".repeat(64), "b".repeat(64), "c".repeat(64));

  private Model root(ModelStatus status) {
    Model result = Model.create("order_detail", "订单明细", ModelDialect.MYSQL,
        null, null, "DWD", null).withPersisted(50L, "tom", null, null);
    return status == ModelStatus.PUBLISHED
        ? result.withPublishState(ModelStatus.PUBLISHED, 600L, 4) : result;
  }

  @BeforeEach
  void setup() {
    when(models.findByIdForUpdate(50L)).thenReturn(Optional.of(root(ModelStatus.DRAFT)));
    when(models.findById(50L)).thenReturn(Optional.of(root(ModelStatus.DRAFT)));
    when(mappings.requireFrozenForPublication(50L, 901L)).thenReturn(evidence);
  }

  @Test
  void selectedFrozenRevisionPublishesInOneVersionTransaction() {
    when(versions.publishWithMapping(50L, null, evidence, "tom"))
        .thenReturn(new ModelVersionService.PublishResult(version(), true));
    var receipt = service.publish(50L, 901L, "tom");
    assertEquals(600L, receipt.modelVersionId());
    assertEquals(901L, receipt.mappingRevisionId());
    assertEquals("DESIGN_PUBLISHED_NOT_EXECUTED", receipt.state());
    verify(mappings).requireFrozenForPublication(50L, 901L);
    verify(versions).publishWithMapping(50L, null, evidence, "tom");
  }

  @Test
  void staleFrozenSelectionCannotPublishPhysicalModel() {
    when(mappings.requireFrozenForPublication(50L, 901L))
        .thenThrow(new IllegalStateException("stale mapping"));
    assertThrows(IllegalStateException.class, () -> service.publish(50L, 901L, "tom"));
    verify(versions, never()).publishWithMapping(any(), any(), any(), any());
  }

  @Test
  void crossProjectUnavailableModelCannotPublish() {
    when(models.findByIdForUpdate(50L)).thenReturn(Optional.empty());
    assertThrows(IllegalArgumentException.class, () -> service.publish(50L, 901L, "tom"));
    verifyNoInteractions(mappings, versions);
  }

  @Test
  void approvalSubmissionPinsStructureAndMappingDigest() {
    when(versions.structureFingerprint(50L)).thenReturn("d".repeat(64));
    service.submitApproval(50L, 901L, "tom");
    ArgumentCaptor<ApprovalSubmitCommand> cap =
        ArgumentCaptor.forClass(ApprovalSubmitCommand.class);
    verify(approvals).submit(cap.capture());
    assertTrue(cap.getValue().payloadJson().contains("\"mappingRevisionId\":901"));
    assertTrue(cap.getValue().payloadJson().contains("\"mappingContentSha256\":\""
        + "c".repeat(64) + "\""));
  }

  @Test
  void approvedMappingFailsIfApprovedDigestDoesNotMatchRevalidatedSnapshot() {
    assertThrows(IllegalStateException.class, () -> service.publishApproved(
        50L, 901L, "f".repeat(64), "d".repeat(64), "tom"));
    verify(versions, never()).publishWithMapping(any(), any(), any(), any());
  }

  @Test
  void priorLegacyVersionMustNotBePresentedAsMappedDwd() {
    when(versions.getVersionDetail(50L, 4)).thenReturn(
        new ModelVersionService.VersionDetailView(version(), null, Map.of()));
    var result = service.history(50L, 4);
    assertEquals("LEGACY_UNBOUND", result.state());
    assertNull(result.mapping());
    verifyNoInteractions(approvals);
  }

  @Test
  void historyPinsImmutableRevisionNotCurrentDraft() {
    when(versions.getVersionDetail(50L, 4)).thenReturn(
        new ModelVersionService.VersionDetailView(version(), null, Map.of(
            "mappingRevisionId", 901, "mappingContentSha256", "c".repeat(64),
            "logicalSnapshotSha256", "a".repeat(64), "physicalStructureSha256",
            "b".repeat(64), "logicalModelId", 77, "logicalVersionNo", 2)));
    when(mappings.revisionDetail(50L, 901L)).thenReturn(
        new DwdMappingDraftService.HistoricalRevision(
            901L, 501L, 1, 77L, 2, "a".repeat(64), "b".repeat(64),
            "c".repeat(64), java.util.List.of()));
    var result = service.history(50L, 4);
    assertEquals(901L, result.mapping().mappingRevisionId());
    assertEquals("DESIGN_PUBLISHED_NOT_EXECUTED", result.state());
  }

  private ModelVersion version() {
    return new ModelVersion(600L, 50L, 4, "{}", "{}", 1, "hash", "tom",
        LocalDateTime.now());
  }
}
