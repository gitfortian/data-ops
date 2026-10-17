package io.yak.ops.business.modeling.mapping;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.modeling.version.PhysicalDataDevHandoffReadinessService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** R12-2: Modeling read-only snapshot, NEVER a TaskRevision / SQL execution permit. */
class DwdDataDevHandoffEvidenceServiceTest {
  private final PhysicalDataDevHandoffReadinessService readiness =
      mock(PhysicalDataDevHandoffReadinessService.class);
  private final DwdMappingPublicationService publication =
      mock(DwdMappingPublicationService.class);
  private final DwdDataDevHandoffEvidenceService service =
      new DwdDataDevHandoffEvidenceService(readiness, publication);

  private PhysicalDataDevHandoffReadinessService.Report report(List<String> blockers) {
    return new PhysicalDataDevHandoffReadinessService.Report(
        50L, "dwd_order_detail", "DWD", "PUBLISHED",
        new PhysicalDataDevHandoffReadinessService.VersionRef(50L, 90L, 3, "a".repeat(64)),
        "b".repeat(64), "c".repeat(64),
        true, blockers.isEmpty() ? "DESIGN_REVIEWABLE" : "DESIGN_NEEDS_REVIEW",
        "NOT_AUTHORIZED_FOR_TASK_CREATION",
        2, 2, 2, 0,
        List.of(), blockers, List.of("人工继续"), "d".repeat(64));
  }

  private DwdMappingDraftService.HistoricalRevision revision(
      List<DwdMappingDraftService.StoredRule> rules) {
    return new DwdMappingDraftService.HistoricalRevision(
        901L, 501L, 2, 77L, 4, "e".repeat(64),
        "f".repeat(64), "0".repeat(64), rules);
  }

  private DwdMappingDraftService.HistoricalRevision valid() {
    return revision(List.of(
        new DwdMappingDraftService.StoredRule("order_id", 1L, 11L, "DIRECT"),
        new DwdMappingDraftService.StoredRule("amount", 2L, 12L, "RENAME")));
  }

  @BeforeEach
  void prepare() {
    when(readiness.inspect(50L, 3)).thenReturn(report(List.of()));
    when(publication.history(50L, 3)).thenReturn(
        new DwdMappingPublicationService.History(
            90L, 3, "DESIGN_PUBLISHED_NOT_EXECUTED", valid()));
  }

  @Test
  void exactHistoricalDwdEvidenceIsReviewableButNeverPermitsDataDevWrites() {
    var result = service.inspect(50L, 3);
    assertEquals("REVIEWABLE_ONLY", result.status());
    assertFalse(result.authorizedForTaskCreation());
    assertTrue(result.blockers().isEmpty());
    assertEquals(901L, result.publication().mapping().mappingRevisionId());
    assertEquals(90L, result.readiness().version().versionId());
    verify(readiness).inspect(50L, 3);
    verify(publication).history(50L, 3);
  }

  @Test
  void staleCurrentModelRemainsBlockedWithoutDiscardingImmutableHistory() {
    when(readiness.inspect(50L, 3)).thenReturn(
        report(List.of("选中的历史版本不是当前发布指针")));
    var result = service.inspect(50L, 3);
    assertEquals("DESIGN_BLOCKED", result.status());
    assertFalse(result.authorizedForTaskCreation());
    assertTrue(result.blockers().get(0).contains("历史版本"));
  }

  @Test
  void legacyVersionIsExplicitlyBlockedNotReplacedWithLatestMapping() {
    when(publication.history(50L, 3)).thenReturn(
        new DwdMappingPublicationService.History(90L, 3, "LEGACY_UNBOUND", null));
    var result = service.inspect(50L, 3);
    assertEquals("DESIGN_BLOCKED", result.status());
    assertFalse(result.authorizedForTaskCreation());
    assertTrue(result.blockers().stream().anyMatch(x -> x.contains("MappingRevision")));
  }

  @Test
  void exactPhysicalVersionMismatchFailsClosed() {
    when(publication.history(50L, 3)).thenReturn(
        new DwdMappingPublicationService.History(91L, 3,
            "DESIGN_PUBLISHED_NOT_EXECUTED", valid()));
    assertThrows(IllegalStateException.class, () -> service.inspect(50L, 3));
  }

  @Test
  void brokenFrozenShaOrMappingIdentityFailsClosed() {
    var baseline = valid();
    var broken = List.of(
        new DwdMappingDraftService.HistoricalRevision(
            baseline.mappingRevisionId(), baseline.mappingSetId(), baseline.revisionNo(),
            baseline.logicalModelId(), baseline.logicalVersionNo(), "bad",
            baseline.physicalStructureSha256(), baseline.contentSha256(), baseline.rules()),
        new DwdMappingDraftService.HistoricalRevision(
            baseline.mappingRevisionId(), baseline.mappingSetId(), baseline.revisionNo(),
            baseline.logicalModelId(), baseline.logicalVersionNo(),
            baseline.logicalSnapshotSha256(), baseline.physicalStructureSha256(), "bad",
            baseline.rules()),
        new DwdMappingDraftService.HistoricalRevision(
            0L, baseline.mappingSetId(), baseline.revisionNo(),
            baseline.logicalModelId(), baseline.logicalVersionNo(),
            baseline.logicalSnapshotSha256(), baseline.physicalStructureSha256(),
            baseline.contentSha256(), baseline.rules()));
    for (var corrupt : broken) {
      when(publication.history(50L, 3)).thenReturn(
          new DwdMappingPublicationService.History(
              90L, 3, "DESIGN_PUBLISHED_NOT_EXECUTED", corrupt));
      assertThrows(IllegalStateException.class, () -> service.inspect(50L, 3));
    }
  }

  @Test
  void duplicateColumnOrAttributeAndUnapprovedRuleFailClosed() {
    var original = valid().rules().get(0);
    var second = new DwdMappingDraftService.StoredRule("ORDER_ID", 2L, 12L, "RENAME");
    var invalidMappings = List.of(
        revision(List.of(original, second)),
        revision(List.of(original, new DwdMappingDraftService.StoredRule(
            "amount", 1L, 12L, "DIRECT"))),
        revision(List.of(new DwdMappingDraftService.StoredRule(
            "order_id", 1L, 11L, "DERIVED"))),
        revision(List.of(new DwdMappingDraftService.StoredRule(
            "order_id", 1L, null, "DIRECT"))));
    for (var invalid : invalidMappings) {
      when(publication.history(50L, 3)).thenReturn(
          new DwdMappingPublicationService.History(
              90L, 3, "DESIGN_PUBLISHED_NOT_EXECUTED", invalid));
      assertThrows(IllegalStateException.class, () -> service.inspect(50L, 3));
    }
  }

  @Test
  void unauthorizedOrUnavailableModelEvidenceNeverFallsBackToAnotherVersion() {
    when(readiness.inspect(50L, 3)).thenThrow(new SecurityException("foreign project"));
    assertThrows(SecurityException.class, () -> service.inspect(50L, 3));
    verify(publication, never()).history(anyLong(), anyInt());
  }

  @Test
  void invalidModelCoordinatesNeverQueryOwner() {
    assertThrows(IllegalArgumentException.class, () -> service.inspect(0L, 3));
    assertThrows(IllegalArgumentException.class, () -> service.inspect(50L, 0));
    verifyNoInteractions(readiness, publication);
  }
}
