package io.yak.ops.business.development.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.repository.DevelopmentLineageOutboxRepository;
import io.yak.ops.business.development.repository.DevelopmentLineageOutboxRepository.DiagnosticRecord;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.development.repository.DevelopmentTaskRevisionRepository;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DevelopmentLineageEvidenceServiceTest {

  @Test
  void missingSqlDeliveryEvidenceIsUnavailableNotEmpty() {
    Fixture fixture = fixture("SQL");
    when(fixture.outbox.findDiagnostic(7L, 91L)).thenReturn(Optional.empty());

    var evidence = fixture.service.get(7L, 3);

    assertEquals("UNAVAILABLE", evidence.status());
    assertEquals("sql-task:data-development:7", evidence.lineageAssetKey());
    assertEquals(91L, evidence.revisionId());
    assertEquals(3, evidence.revisionNo());
  }

  @Test
  void failedDeliveryKeepsRetryAndFailureEvidence() {
    Fixture fixture = fixture("SQL");
    LocalDateTime now = LocalDateTime.now();
    when(fixture.outbox.findDiagnostic(7L, 91L)).thenReturn(Optional.of(
        new DiagnosticRecord(
            "task-1", 7L, 91L, "FAILED", 4, "lineage unavailable",
            now.plusMinutes(2), now.minusMinutes(2), now)));

    var evidence = fixture.service.get(7L, 3);

    assertEquals("FAILED", evidence.status());
    assertEquals(4, evidence.attempts());
    assertEquals("lineage unavailable", evidence.lastError());
    assertEquals("task-1", evidence.deliveryTaskId());
    assertEquals(now.plusMinutes(2), evidence.nextAttemptTime());
  }

  @Test
  void succeededDeliveryExposesCanonicalLineageAssetKey() {
    Fixture fixture = fixture("SQL");
    LocalDateTime now = LocalDateTime.now();
    when(fixture.outbox.findDiagnostic(7L, 91L)).thenReturn(Optional.of(
        new DiagnosticRecord(
            "task-2", 7L, 91L, "SUCCEEDED", 1, null,
            now, now.minusSeconds(5), now)));

    var evidence = fixture.service.get(7L, 3);

    assertEquals("SUCCEEDED", evidence.status());
    assertEquals("sql-task:data-development:7", evidence.lineageAssetKey());
    assertNull(evidence.lastError());
  }

  @Test
  void nonSqlRevisionIsNotApplicableInsteadOfEmpty() {
    Fixture fixture = fixture("PYTHON");

    var evidence = fixture.service.get(7L, 3);

    assertEquals("NOT_APPLICABLE", evidence.status());
    assertNull(evidence.lineageAssetKey());
    assertEquals(0, evidence.attempts());
  }

  private static Fixture fixture(String taskType) {
    DevelopmentNodeRepository nodes = mock(DevelopmentNodeRepository.class);
    DevelopmentTaskRevisionRepository revisions = mock(DevelopmentTaskRevisionRepository.class);
    DevelopmentLineageOutboxRepository outbox = mock(DevelopmentLineageOutboxRepository.class);
    DevelopmentNode node = new DevelopmentNode(
        7L, "orders", taskType, 23L, null, true, Instant.now(), Instant.now());
    DevelopmentTaskRevision revision = new DevelopmentTaskRevision(
        91L,
        7L,
        3,
        12,
        new TaskDefinition(taskType, 1, "select * from orders", "{}"),
        "checksum",
        Instant.parse("2026-09-24T06:00:00Z"));
    when(nodes.findById(7L)).thenReturn(Optional.of(node));
    when(revisions.findByRevisionNo(7L, 3)).thenReturn(Optional.of(revision));
    return new Fixture(
        new DevelopmentLineageEvidenceService(nodes, revisions, outbox),
        outbox);
  }

  private record Fixture(
      DevelopmentLineageEvidenceService service,
      DevelopmentLineageOutboxRepository outbox) {}
}
