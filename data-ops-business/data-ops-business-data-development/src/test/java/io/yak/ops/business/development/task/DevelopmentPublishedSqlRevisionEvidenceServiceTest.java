package io.yak.ops.business.development.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskDraft;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DevelopmentPublishedSqlRevisionEvidenceServiceTest {
  private final DevelopmentTaskNodeResolver nodes = mock(DevelopmentTaskNodeResolver.class);
  private final DevelopmentTaskRevisionReader revisions = mock(DevelopmentTaskRevisionReader.class);
  private final DevelopmentTaskDraftManager drafts = mock(DevelopmentTaskDraftManager.class);
  private final DevelopmentTaskDefinitionNormalizer normalizer =
      new DevelopmentTaskDefinitionNormalizer(new ObjectMapper());
  private final TaskDefinitionDigestCalculator digests = new TaskDefinitionDigestCalculator();
  private final DevelopmentPublishedSqlRevisionEvidenceService subject =
      new DevelopmentPublishedSqlRevisionEvidenceService(
          nodes, revisions, drafts, normalizer, digests);

  private final Instant now = Instant.parse("2026-10-17T00:00:00Z");
  private final DevelopmentNode node = new DevelopmentNode(
      7L, "订单明细 SQL", "SQL", 42L, null, true, now, now);
  private TaskDefinition publishedDefinition;

  @BeforeEach void setup() {
    publishedDefinition = normalizer.normalize(
        node, "SQL", 1, "select detail_id from ods_trade_order_detail", "{}");
    when(nodes.requireTaskNode(7L)).thenReturn(node);
    when(revisions.get(node, 2)).thenReturn(revision(92L, publishedDefinition,
        digests.calculate(publishedDefinition)));
    when(drafts.get(node)).thenReturn(
        new DevelopmentTaskDraft(7L, publishedDefinition, 4L, now, now));
  }

  private DevelopmentTaskRevision revision(
      long id, TaskDefinition definition, String checksum) {
    return new DevelopmentTaskRevision(id, 7L, 2, 3L, definition, checksum, now);
  }

  @Test void exactPublishedSqlSnapshotIsVerifiedWithoutAnyRuntimeOrWrite() {
    var result = subject.inspect(7L, 2);
    assertEquals("7", result.nodeId());
    assertEquals("92", result.taskRevisionId());
    assertEquals(2, result.taskRevisionNo());
    assertEquals(3L, result.publishedFromDraftRevision());
    assertEquals(4L, result.currentDraftRevision());
    assertEquals(digests.calculate(publishedDefinition), result.immutableDefinitionSha256());
    assertEquals("PUBLISHED_SQL_REVISION_VERIFIED_NOT_EXECUTED", result.status());
    assertFalse(result.authorizedForFrozenExecution());
    assertTrue(result.currentDraftMatchesPublishedDefinition());
    verify(nodes).requireTaskNode(7L);
    verify(revisions).get(node, 2);
    verify(drafts).get(node);
    verifyNoMoreInteractions(nodes, revisions, drafts);
  }

  @Test void changedEditorDraftDoesNotMutateThePublishedTaskSnapshot() {
    TaskDefinition edited = normalizer.normalize(node, "SQL", 1,
        "select detail_id, amount from ods_trade_order_detail", "{}");
    when(drafts.get(node)).thenReturn(new DevelopmentTaskDraft(
        7L, edited, 5L, now, now));
    var result = subject.inspect(7L, 2);
    assertFalse(result.currentDraftMatchesPublishedDefinition());
    assertEquals(5, result.currentDraftRevision());
    assertEquals(digests.calculate(publishedDefinition), result.immutableDefinitionSha256());
    assertFalse(result.authorizedForFrozenExecution());
  }

  @Test void invalidRevisionChecksumOrChangedPublishedDefinitionFailsClosed() {
    when(revisions.get(node, 2)).thenReturn(revision(92L, publishedDefinition, "a".repeat(64)));
    assertThrows(IllegalStateException.class, () -> subject.inspect(7L, 2));
    when(revisions.get(node, 2)).thenReturn(revision(92L,
        new TaskDefinition("SQL", 1, "select 1", "{}"),
        digests.calculate(new TaskDefinition("SQL", 1, "select 1", "{}"))));
    assertThrows(IllegalStateException.class, () -> subject.inspect(7L, 2));
    verifyNoInteractions(drafts);
  }

  @Test void foreignOrCorruptNodeAndRevisionNeverBecomeExecutableEvidence() {
    when(revisions.get(node, 2)).thenReturn(new DevelopmentTaskRevision(
        92L, 8L, 2, 3L, publishedDefinition, digests.calculate(publishedDefinition), now));
    assertThrows(IllegalStateException.class, () -> subject.inspect(7L, 2));
    verifyNoInteractions(drafts);

    when(nodes.requireTaskNode(7L)).thenThrow(new SecurityException("foreign project"));
    assertThrows(SecurityException.class, () -> subject.inspect(7L, 2));
  }

  @Test void nonSqlTasksMustUseTheirOwnPublisherAndRunContracts() {
    var shell = new DevelopmentNode(7L, "shell", "SHELL", 42L, null, true, now, now);
    when(nodes.requireTaskNode(7L)).thenReturn(shell);
    assertThrows(IllegalArgumentException.class, () -> subject.inspect(7L, 2));
    verifyNoInteractions(drafts);
  }

  @Test void missingProjectCannotYieldPublishedSqlEvidence() {
    var projectless = new DevelopmentNode(7L, "sql", "SQL", null, null, true, now, now);
    when(nodes.requireTaskNode(7L)).thenReturn(projectless);
    assertThrows(IllegalStateException.class, () -> subject.inspect(7L, 2));
    verifyNoInteractions(drafts);
  }
}
